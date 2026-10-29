package com.juca.homewatermonitorbackend.session.application.automatic;

import com.juca.homewatermonitorbackend.cultivation.application.CultivationException;
import com.juca.homewatermonitorbackend.session.application.control.*;
import com.juca.homewatermonitorbackend.session.infrastructure.persistence.AutomaticMeasurementRunStore;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AutomaticMeasurementServiceTest {
    final Instant hour=Instant.parse("2026-10-29T03:00:00Z");
    final UUID cultivation=UUID.randomUUID();
    final AutomaticMeasurementRunStore runs=mock(AutomaticMeasurementRunStore.class);
    final ControlRuntime control=mock(ControlRuntime.class);
    AutomaticMeasurementService service(boolean enabled) {
        return new AutomaticMeasurementService(new AutomaticMeasurementProperties(enabled,"sensor",cultivation),runs,control,hour.minusSeconds(1));
    }
    @Test void defaultOffOrNonDueDoesNotTouchTheLedgerOrHardware() {
        service(false).runAt(hour); service(true).runAt(hour.plusSeconds(60));
        verifyNoInteractions(runs);verify(control,never()).submit(any(),any(),any(),any(),any());
    }
    @Test void losingACommittedClaimNeverSubmitsASecondStart() {
        when(runs.claim(anyString(),any(),any(),any())).thenReturn(false);
        service(true).runAt(hour);
        verify(control,never()).submit(any(),any(),any(),any(),any());
    }
    @Test void claimCommitFailurePreventsHardwareSubmission() {
        when(runs.claim(anyString(),any(),any(),any())).thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class,()->service(true).runAt(hour));
        verify(control,never()).submit(any(),any(),any(),any(),any());
    }
    @Test void successfulSubmissionUsesTheConfiguredCultivationAndNewV4Key() {
        when(runs.claim(anyString(),any(),any(),any())).thenReturn(true);
        service(true).runAt(hour);
        var key=org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(control).submit(key.capture(),eq("sensor"),eq("START"),isNull(),eq(cultivation));
        assertEquals(4,key.getValue().version());verify(runs).finish(key.getValue(),"SUBMITTED",null);
    }
    @Test void busyOrEndedIsSkippedWithoutAnyRetry() {
        when(runs.claim(anyString(),any(),any(),any())).thenReturn(true);
        when(control.submit(any(),anyString(),anyString(),isNull(),eq(cultivation)))
            .thenThrow(new ControlException(409,"CONTROL_BUSY"),new CultivationException(409,"CULTIVATION_ENDED"));
        service(true).runAt(hour);service(true).runAt(hour.plusSeconds(3600));
        verify(runs).finish(any(),eq("SKIPPED"),eq("CONTROL_BUSY"));
        verify(runs).finish(any(),eq("SKIPPED"),eq("CULTIVATION_ENDED"));
        verify(control,times(2)).submit(any(),anyString(),anyString(),isNull(),eq(cultivation));
    }
    @Test void unexpectedFailureIsUncertainNotAFalseSuccessAndDoesNotLeakTheMessage() {
        when(runs.claim(anyString(),any(),any(),any())).thenReturn(true);
        when(control.submit(any(),anyString(),anyString(),isNull(),eq(cultivation))).thenThrow(new IllegalStateException("fixture-private-detail"));
        service(true).runAt(hour);
        verify(runs).finish(any(),eq("UNKNOWN"),eq("SUBMISSION_UNCERTAIN"));
    }
    @Test void enabledSettingsRequireExplicitValidTargetsButOffNeedsNone() {
        assertDoesNotThrow(()->new AutomaticMeasurementProperties(false,null,null));
        assertThrows(IllegalArgumentException.class,()->new AutomaticMeasurementProperties(true,"sensor",null));
        assertThrows(IllegalArgumentException.class,()->new AutomaticMeasurementProperties(true,"bad/+",cultivation));
        assertThrows(IllegalArgumentException.class,()->new AutomaticMeasurementProperties(true,"sensor",new UUID(0,0)));
    }
}
