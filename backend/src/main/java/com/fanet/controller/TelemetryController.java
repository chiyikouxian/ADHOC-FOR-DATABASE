package com.fanet.controller;

import com.fanet.model.TelemetryRecord;
import com.fanet.service.TelemetryService;
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

    // ==================== 写入 ====================

    @PostMapping
    public ResponseEntity<?> ingest(@RequestBody List<TelemetryRecord> records) {
        if (records == null || records.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "空的遥测数据"));
        }
        telemetryService.ingest(records);
        return ResponseEntity.ok(Map.of("ingested", records.size()));
    }

    // ==================== 时序查询 ====================

    /**
     * 单机遥测时序（TDengine INTERVAL 降采样）
     * GET /api/telemetry/drone/1/series?window=1m&minutes=60
     */
    @GetMapping("/drone/{droneId}/series")
    public ResponseEntity<?> getDroneSeries(
            @PathVariable Integer droneId,
            @RequestParam(defaultValue = "1m") String window,
            @RequestParam(defaultValue = "60") int minutes) {
        List<Map<String, Object>> data = telemetryService.getDroneTelemetrySeries(droneId, window, minutes);
        return ResponseEntity.ok(data);
    }

    /**
     * 全集群遥测聚合（大屏概览）
     * GET /api/telemetry/cluster/aggregate?window=5m&minutes=30
     */
    @GetMapping("/cluster/aggregate")
    public ResponseEntity<?> getClusterAggregate(
            @RequestParam(defaultValue = "5m") String window,
            @RequestParam(defaultValue = "30") int minutes) {
        List<Map<String, Object>> data = telemetryService.getAllDronesAggregated(window, minutes);
        return ResponseEntity.ok(data);
    }

    /**
     * 单机电量原始序列（续航预测用）
     * GET /api/telemetry/drone/1/battery?minutes=120
     */
    @GetMapping("/drone/{droneId}/battery")
    public ResponseEntity<?> getBatterySeries(
            @PathVariable Integer droneId,
            @RequestParam(defaultValue = "120") int minutes) {
        List<Map<String, Object>> data = telemetryService.getBatterySeries(droneId, minutes);
        return ResponseEntity.ok(data);
    }

    /**
     * 单机信号原始序列
     * GET /api/telemetry/drone/1/rssi?minutes=60
     */
    @GetMapping("/drone/{droneId}/rssi")
    public ResponseEntity<?> getRssiSeries(
            @PathVariable Integer droneId,
            @RequestParam(defaultValue = "60") int minutes) {
        List<Map<String, Object>> data = telemetryService.getRssiSeries(droneId, minutes);
        return ResponseEntity.ok(data);
    }

    /**
     * 最新链路快照（供拓扑分析）
     * GET /api/telemetry/links/snapshot?seconds=30
     */
    @GetMapping("/links/snapshot")
    public ResponseEntity<?> getLinkSnapshot(
            @RequestParam(defaultValue = "30") int seconds) {
        List<Map<String, Object>> data = telemetryService.getLatestLinkSnapshot(seconds);
        return ResponseEntity.ok(data);
    }
}
