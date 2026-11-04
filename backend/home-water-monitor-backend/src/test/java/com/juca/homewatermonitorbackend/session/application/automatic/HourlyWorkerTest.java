package com.juca.homewatermonitorbackend.session.application.automatic;

import com.juca.homewatermonitorbackend.session.infrastructure.scheduling.HourlyMeasurementJob;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HourlyWorkerTest {
    @Test void waitingForRecoveryDoesNotBlockSchedulerOrQueueAnotherHour() throws Exception {
        var service=mock(AutomaticMeasurementService.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var stopped=new CountDownLatch(1);
        doAnswer(i->{
            entered.countDown();
            try {release.await(5,TimeUnit.SECONDS);}finally{stopped.countDown();}
            return null;
        }).when(service).runAt(any());
        var job=new HourlyMeasurementJob(service);
        try {
            job.run();assertTrue(entered.await(3,TimeUnit.SECONDS));
            // A callback on the scheduler completes even while the actual worker is waiting.
            assertTimeout(java.time.Duration.ofMillis(500),job::run);
            verify(service,times(1)).runAt(any());
            job.stop();assertTrue(stopped.await(3,TimeUnit.SECONDS));
        } finally {release.countDown();job.stop();}
    }
}
