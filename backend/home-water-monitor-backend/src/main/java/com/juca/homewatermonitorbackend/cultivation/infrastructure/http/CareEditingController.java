package com.juca.homewatermonitorbackend.cultivation.infrastructure.http;

import com.juca.homewatermonitorbackend.cultivation.application.CareEditingService;
import com.juca.homewatermonitorbackend.cultivation.domain.WaterMaintenance;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/cultivations/{id}")
public class CareEditingController {
    private final CareEditingService service;
    public CareEditingController(CareEditingService service) { this.service=service; }
    @PutMapping(value="/profile",consumes="application/json",produces="application/json")
    public ResponseEntity<String> profile(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@RequestBody JsonNode body) {
        fields(body,"expectedVersion","plantName","name","memo");
        return json(service.profile(id,key,new CareEditingService.Profile(version(body),text(body,"plantName"),text(body,"name"),text(body,"memo"))));
    }
    @PutMapping(value="/water-maintenances/{maintenanceId}",consumes="application/json",produces="application/json")
    public ResponseEntity<String> correct(@PathVariable UUID id,@PathVariable UUID maintenanceId,
                                         @RequestHeader("Idempotency-Key") UUID key,@RequestBody JsonNode body) {
        fields(body,"expectedVersion","type","replacementScope","occurredAt","amountMl","memo");
        var scope=body.get("replacementScope");var amount=body.get("amountMl");
        if(!amount.isNull() && !amount.isNumber())throw new IllegalArgumentException("amount");
        return json(service.correct(id,maintenanceId,key,new CareEditingService.Correction(version(body),
                WaterMaintenance.Type.valueOf(text(body,"type")),scope.isNull()?null:WaterMaintenance.Scope.valueOf(text(body,"replacementScope")),
                instant(body),amount.isNull()?null:amount.decimalValue(),text(body,"memo"))));
    }
    @GetMapping("/water-maintenances/{maintenanceId}")
    public WaterMaintenance get(@PathVariable UUID id,@PathVariable UUID maintenanceId) { return service.get(id,maintenanceId); }
    private static ResponseEntity<String> json(String body) { return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body); }
    private static void fields(JsonNode body,String... fields) {
        if(!body.isObject() || body.size()!=fields.length)throw new IllegalArgumentException("fields");
        for(String field:fields)if(!body.has(field))throw new IllegalArgumentException("Missing field");
    }
    private static String text(JsonNode body,String field) {
        var node=body.get(field);if(!node.isString())throw new IllegalArgumentException("text");return node.asString();
    }
    private static long version(JsonNode body) {
        var node=body.get("expectedVersion");
        if(!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong()<0)throw new IllegalArgumentException("version");return node.asLong();
    }
    private static Instant instant(JsonNode body) {
        try { return Instant.parse(text(body,"occurredAt")); }
        catch(java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("occurredAt",e); }
    }
}
