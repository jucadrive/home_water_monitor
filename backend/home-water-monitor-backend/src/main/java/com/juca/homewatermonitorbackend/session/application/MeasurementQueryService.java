package com.juca.homewatermonitorbackend.session.application;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.MeasurementQueryStore;
import com.juca.homewatermonitorbackend.measurement.infrastructure.persistence.TdsMeasurementJpaRepository;
import com.juca.homewatermonitorbackend.session.application.control.ControlException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service @Transactional(readOnly=true)
public class MeasurementQueryService {
    private final MeasurementQueryStore store;
    private final TdsMeasurementJpaRepository samples;
    public MeasurementQueryService(MeasurementQueryStore store,TdsMeasurementJpaRepository samples) { this.store=store;this.samples=samples; }
    public record Sample(int seq,int elapsedMs,BigDecimal tdsPpm,boolean isWarmup,BigDecimal rawVoltageMv) { }
    public record Detail(MeasurementSessionView session,List<Sample> measurements) { }
    public List<MeasurementSessionView> list(UUID cultivation,boolean unlinked,boolean trend,int limit,int offset) {
        if(limit<1 || limit>200 || offset<0)throw new IllegalArgumentException();
        // The MVP trend contains known real sensor sessions only, never a mixture of simulated/unknown sources.
        return store.list(cultivation,unlinked,trend,trend,limit,offset);
    }
    public MeasurementSessionView latest(UUID cultivation) {
        var rows=list(cultivation,false,true,1,0);return rows.isEmpty()?null:rows.getFirst();
    }
    public Detail detail(UUID id) {
        var session=store.get(id);if(session==null)throw new ControlException(404,"SESSION_NOT_FOUND");
        return new Detail(session,samples.findBySessionIdOrderBySeqAsc(id).stream().map(m->
                new Sample(m.getSeq(),m.getElapsedMs(),m.getTdsPpm(),m.isWarmup(),m.getRawVoltageMv())).toList());
    }
}
