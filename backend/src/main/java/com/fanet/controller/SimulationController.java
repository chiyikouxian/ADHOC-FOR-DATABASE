package com.fanet.controller;

import com.fanet.model.SimulationScenario;
import com.fanet.service.SimulationScenarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationScenarioService scenarioService;
    private final com.fanet.service.SimulationRuntimeService runtimeService;

    public SimulationController(SimulationScenarioService scenarioService,
                                com.fanet.service.SimulationRuntimeService runtimeService) {
        this.scenarioService = scenarioService;
        this.runtimeService = runtimeService;
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<Map<String, Object>>> listScenarios() {
        return ResponseEntity.ok(scenarioService.listScenarios());
    }

    @GetMapping("/scenarios/{scenarioId}")
    public ResponseEntity<SimulationScenario> getScenario(@PathVariable Long scenarioId) {
        return ResponseEntity.ok(scenarioService.getScenario(scenarioId));
    }

    @PostMapping("/scenarios")
    public ResponseEntity<Map<String, Object>> createScenario(@RequestBody SimulationScenario scenario) {
        return ResponseEntity.ok(scenarioService.createScenario(scenario));
    }

    @PutMapping("/scenarios/{scenarioId}")
    public ResponseEntity<Map<String, Object>> updateScenario(@PathVariable Long scenarioId,
                                                              @RequestBody SimulationScenario scenario) {
        return ResponseEntity.ok(scenarioService.updateScenario(scenarioId, scenario));
    }

    @DeleteMapping("/scenarios/{scenarioId}")
    public ResponseEntity<Map<String, Object>> deleteScenario(@PathVariable Long scenarioId) {
        return ResponseEntity.ok(scenarioService.deleteScenario(scenarioId));
    }

    @PostMapping("/runtime/start")
    public ResponseEntity<Map<String, Object>> startRuntime(@RequestBody Map<String, Object> request) {
        Long scenarioId = request.get("scenarioId") instanceof Number n ? n.longValue() : null;
        if (scenarioId == null || scenarioId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "有效的 scenarioId 必填"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides = request.get("overrides") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        return ResponseEntity.ok(runtimeService.start(scenarioId, overrides));
    }

    @PostMapping("/runtime/stop")
    public ResponseEntity<Map<String, Object>> stopRuntime() {
        return ResponseEntity.ok(runtimeService.stop());
    }

    @PostMapping("/runtime/apply")
    public ResponseEntity<Map<String, Object>> applyRuntime(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(runtimeService.apply(request));
    }

    @GetMapping("/runtime/status")
    public ResponseEntity<Map<String, Object>> runtimeStatus() {
        return ResponseEntity.ok(runtimeService.status());
    }

    @GetMapping("/runtime/preview")
    public ResponseEntity<Map<String, Object>> runtimePreview() {
        return ResponseEntity.ok(runtimeService.preview());
    }
}
