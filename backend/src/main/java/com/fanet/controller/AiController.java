package com.fanet.controller;

import com.fanet.service.EndurancePredictor;
import com.fanet.service.MissionReportService;
import com.fanet.service.Nl2SqlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final Nl2SqlService nl2SqlService;
    private final EndurancePredictor endurancePredictor;
    private final MissionReportService missionReportService;

    public AiController(Nl2SqlService nl2SqlService,
                        EndurancePredictor endurancePredictor,
                        MissionReportService missionReportService) {
        this.nl2SqlService = nl2SqlService;
        this.endurancePredictor = endurancePredictor;
        this.missionReportService = missionReportService;
    }

    /** NL2SQL 自然语言查询 */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, String> req) {
        String question = req.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "问题不能为空"));
        }
        return ResponseEntity.ok(nl2SqlService.ask(question));
    }

    /**
     * 续航预测
     * POST /api/ai/endurance
     * Body: { "droneId": 1, "historyMinutes": 60, "useAI": true }
     */
    @PostMapping("/endurance")
    public ResponseEntity<Map<String, Object>> predictEndurance(@RequestBody Map<String, Object> req) {
        Integer droneId = req.get("droneId") instanceof Number n ? n.intValue() : null;
        if (droneId == null || droneId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "有效的 droneId 必填"));
        }
        int historyMinutes = req.get("historyMinutes") instanceof Number n ? n.intValue() : 60;
        boolean useAI = req.get("useAI") instanceof Boolean b ? b : false;

        Map<String, Object> result = endurancePredictor.predict(droneId, historyMinutes, useAI);
        return ResponseEntity.ok(result);
    }

    /**
     * 飞行复盘报告
     * POST /api/ai/report
     * Body: { "missionId": 1, "useAI": true }
     */
    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> generateReport(@RequestBody Map<String, Object> req) {
        Long missionId = req.get("missionId") instanceof Number n ? n.longValue() : null;
        if (missionId == null || missionId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "有效的 missionId 必填"));
        }
        boolean useAI = req.get("useAI") instanceof Boolean b ? b : false;

        Map<String, Object> report = missionReportService.generateReport(missionId, useAI);
        return ResponseEntity.ok(report);
    }
}
