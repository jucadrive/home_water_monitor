package com.juca.homewatermonitorbackend.cultivation.infrastructure.http;

import com.juca.homewatermonitorbackend.cultivation.application.CultivationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestControllerAdvice(basePackages="com.juca.homewatermonitorbackend.cultivation.infrastructure.http")
public class CultivationErrors {
    @ExceptionHandler(com.juca.homewatermonitorbackend.session.application.control.ControlException.class)
    public ResponseEntity<?> control(com.juca.homewatermonitorbackend.session.application.control.ControlException e) { return error(e.status(),e.code()); }
    @ExceptionHandler(CultivationException.class)
    public ResponseEntity<?> business(CultivationException e) { return error(e.status(),e.code()); }
    @ExceptionHandler({IllegalArgumentException.class,NullPointerException.class,ServletRequestBindingException.class,
            HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> input(Exception e) { return error(400,"CULTIVATION_INPUT_INVALID"); }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> unavailable(RuntimeException e) { return error(503,"CULTIVATION_UNAVAILABLE"); }
    private ResponseEntity<?> error(int status,String code) {
        return ResponseEntity.status(status).body(Map.of("code",code,"message",code));
    }
}
