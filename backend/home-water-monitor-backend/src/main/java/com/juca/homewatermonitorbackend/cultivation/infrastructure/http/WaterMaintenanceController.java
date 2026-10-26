package com.juca.homewatermonitorbackend.cultivation.infrastructure.http;

import com.juca.homewatermonitorbackend.cultivation.application.WaterMaintenanceService;
import com.juca.homewatermonitorbackend.cultivation.domain.WaterMaintenance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/cultivations/{id}/water-maintenances")
public class WaterMaintenanceController {
    private final WaterMaintenanceService service;
    public WaterMaintenanceController(WaterMaintenanceService service) { this.service=service; }
    @PostMapping public ResponseEntity<WaterMaintenance> create(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,
                                                               @RequestBody WaterMaintenanceService.Create body) {
        var r=service.create(id,key,body);return ResponseEntity.status(r.created()?201:200).body(r.maintenance());
    }
    @GetMapping public List<WaterMaintenance> list(@PathVariable UUID id,@RequestParam(defaultValue="50") int limit,
                                                  @RequestParam(defaultValue="0") int offset) { return service.list(id,limit,offset); }
}
