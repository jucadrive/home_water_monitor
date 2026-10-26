package com.juca.homewatermonitorbackend.cultivation.infrastructure.http;

import com.juca.homewatermonitorbackend.cultivation.application.*;
import com.juca.homewatermonitorbackend.session.application.MeasurementSessionView;
import com.juca.homewatermonitorbackend.session.application.control.ControlRuntime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController @RequestMapping("/api/cultivations/{id}")
public class CultivationMeasurementController {
    private final ObjectProvider<ControlRuntime> runtime;
    private final CultivationOverviewService overview;
    public CultivationMeasurementController(ObjectProvider<ControlRuntime> runtime,CultivationOverviewService overview) {
        this.runtime=runtime;this.overview=overview;
    }
    public record Start(String deviceId) { }
    @PostMapping("/measurement-commands") public ResponseEntity<?> start(@PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,@RequestBody Start body) {
        CultivationService.key(key);
        if(body.deviceId()==null || !body.deviceId().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))throw new IllegalArgumentException();
        var control=runtime.getIfAvailable();if(control==null)throw new CultivationException(503,"CONTROL_UNAVAILABLE");
        var result=control.submit(key,body.deviceId(),"START",null,id);var c=result.command();
        return ResponseEntity.status(result.created()?202:200).location(URI.create("/api/measurement-commands/"+c.commandId()))
                .body(Map.of("commandId",c.commandId(),"sessionId",c.sessionId(),"commandState",c.commandState()));
    }
    @GetMapping("/measurement-sessions") public List<MeasurementSessionView> sessions(@PathVariable UUID id,
            @RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        return overview.sessions(id,false,limit,offset);
    }
    @GetMapping("/tds-trend") public List<MeasurementSessionView> trend(@PathVariable UUID id,
            @RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        return overview.sessions(id,true,limit,offset);
    }
}
