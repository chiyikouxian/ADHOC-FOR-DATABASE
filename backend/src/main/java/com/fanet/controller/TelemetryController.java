package com.fanet.controller;

import com.fanet.model.TelemetryRecord;
import com.fanet.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ResponseEntity<?> ingest(@RequestBody List<TelemetryRecord> records) {
        if (records == null || records.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "空的遥测数据"));
        }
        telemetryService.ingest(records);
        return ResponseEntity.ok(Map.of("ingested", records.size()));
    }
}
