package com.juca.homewatermonitorbackend.session.infrastructure.http;

import com.juca.homewatermonitorbackend.session.application.control.*;
import com.juca.homewatermonitorbackend.session.infrastructure.mqtt.ControlMessageCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@ConditionalOnProperty(prefix="water-monitor.control",name="enabled",havingValue="true")
public class MeasurementCommandController {
    private final ControlRuntime runtime;
    private final DeviceControlQueryService query;
    private final ControlMessageCodec codec=new ControlMessageCodec();
    public MeasurementCommandController(ControlRuntime runtime,DeviceControlQueryService query){this.runtime=runtime;this.query=query;}
    @PostMapping(value="/api/devices/{device}/measurement-commands",consumes="application/json")
    public ResponseEntity<?> create(@PathVariable String device,@RequestHeader("Idempotency-Key") String key,@RequestBody byte[] bytes) {
        var body=codec.tree(bytes);
        ControlPolicy.require(body.get("type")!=null && body.get("type").isString());String type=body.get("type").asString();
        ControlPolicy.type(type);
        ControlPolicy.require(Set.copyOf(body.propertyNames()).equals("START".equals(type)?Set.of("type"):Set.of("type","sessionId")));
        UUID session="RETRY".equals(type)?UUID.fromString(body.get("sessionId").asString()):null;
        if(!key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw new IllegalArgumentException();
        var submitted=runtime.submit(UUID.fromString(key),device,type,session);var c=submitted.command();
        var location=URI.create("/api/measurement-commands/"+c.commandId());
        // 예약이 커밋된 뒤에는 후속 조회 장애로 접수 성공을 실패로 바꾸지 않는다.
        return ResponseEntity.status(submitted.created()?202:200).location(location)
                .body(java.util.Map.of("commandId",c.commandId(),"sessionId",c.sessionId(),"commandState",c.commandState()));
    }
    @GetMapping("/api/measurement-commands/{id}") public Object command(@PathVariable UUID id){return query.command(id);}
    @GetMapping("/api/devices/{device}/status") public Object status(@PathVariable String device){return query.device(device);}
    @ExceptionHandler(ControlException.class) public ResponseEntity<?> conflict(ControlException e){return error(e.status(),e.code());}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.web.bind.MissingRequestHeaderException.class})
    public ResponseEntity<?> input(Exception e){return error(400,"CONTROL_INPUT_INVALID");}
    @ExceptionHandler(RuntimeException.class) public ResponseEntity<?> failure(RuntimeException e){return error(503,"CONTROL_UNAVAILABLE");}
    private ResponseEntity<?> error(int status,String code){return ResponseEntity.status(status).body(java.util.Map.of("code",code,"message",code));}
}
