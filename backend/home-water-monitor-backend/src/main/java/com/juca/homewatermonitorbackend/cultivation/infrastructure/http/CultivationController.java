package com.juca.homewatermonitorbackend.cultivation.infrastructure.http;

import com.juca.homewatermonitorbackend.cultivation.application.CultivationService;
import com.juca.homewatermonitorbackend.cultivation.domain.Cultivation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/cultivations")
public class CultivationController {
    private final CultivationService service;
    private final com.juca.homewatermonitorbackend.cultivation.application.CultivationOverviewService overview;
    public CultivationController(CultivationService service,com.juca.homewatermonitorbackend.cultivation.application.CultivationOverviewService overview) { this.service=service;this.overview=overview; }
    @PostMapping
    public ResponseEntity<Cultivation> create(@RequestHeader("Idempotency-Key") UUID key,
                                             @RequestBody CultivationService.Create body) {
        var result=service.create(key,body);
        return ResponseEntity.status(result.created()?201:200)
                .location(URI.create("/api/cultivations/"+result.cultivation().id())).body(result.cultivation());
    }
    @GetMapping public Object list(@RequestParam(defaultValue="50") int limit,
                                             @RequestParam(defaultValue="0") int offset) { return overview.list(limit,offset); }
    @GetMapping("/{id}") public Object get(@PathVariable UUID id) { return overview.get(id); }
    @PostMapping("/{id}/end") public Cultivation end(@PathVariable UUID id) { return service.end(id); }
}
