package com.juca.homewatermonitorbackend.session.infrastructure.http;

import com.juca.homewatermonitorbackend.session.application.*;
import com.juca.homewatermonitorbackend.session.application.control.ControlException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/measurement-sessions")
public class MeasurementQueryController {
    private final MeasurementQueryService service;
    public MeasurementQueryController(MeasurementQueryService service) { this.service=service; }
    @GetMapping public List<MeasurementSessionView> list(@RequestParam(defaultValue="false") boolean unlinked,
            @RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        return service.list(null,unlinked,false,limit,offset);
    }
    @GetMapping("/{id}") public MeasurementQueryService.Detail detail(@PathVariable UUID id) { return service.detail(id); }
    @ExceptionHandler(ControlException.class) public ResponseEntity<?> missing(ControlException e) { return error(e.status(),e.code()); }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> input(Exception e) { return error(400,"MEASUREMENT_INPUT_INVALID"); }
    @ExceptionHandler(RuntimeException.class) public ResponseEntity<?> failure(Exception e) { return error(503,"MEASUREMENT_UNAVAILABLE"); }
    private ResponseEntity<?> error(int status,String code) { return ResponseEntity.status(status).body(Map.of("code",code,"message",code)); }
}
