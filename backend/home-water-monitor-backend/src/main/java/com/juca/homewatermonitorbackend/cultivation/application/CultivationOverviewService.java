package com.juca.homewatermonitorbackend.cultivation.application;

import com.juca.homewatermonitorbackend.cultivation.domain.Cultivation;
import com.juca.homewatermonitorbackend.session.application.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service @Transactional(readOnly=true)
public class CultivationOverviewService {
    private final CultivationService cultivations;
    private final WaterMaintenanceService maintenance;
    private final MeasurementQueryService measurements;
    public CultivationOverviewService(CultivationService cultivations,WaterMaintenanceService maintenance,MeasurementQueryService measurements) {
        this.cultivations=cultivations;this.maintenance=maintenance;this.measurements=measurements;
    }
    public record Overview(Cultivation cultivation,Instant lastFullReplacementAt,MeasurementSessionView latestMeasurement) { }
    public Overview get(UUID id) { return overview(cultivations.get(id)); }
    public List<Overview> list(int limit,int offset) { return cultivations.list(limit,offset).stream().map(this::overview).toList(); }
    private Overview overview(Cultivation c) { return new Overview(c,maintenance.lastFullReplacement(c.id()),measurements.latest(c.id())); }
    public List<MeasurementSessionView> sessions(UUID id,boolean trend,int limit,int offset) {
        cultivations.get(id);return measurements.list(id,false,trend,limit,offset);
    }
}
