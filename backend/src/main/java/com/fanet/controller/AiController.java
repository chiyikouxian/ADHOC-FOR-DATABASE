package com.fanet.controller;

import com.fanet.service.Nl2SqlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final Nl2SqlService nl2SqlService;

    public AiController(Nl2SqlService nl2SqlService) {
        this.nl2SqlService = nl2SqlService;
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, String> req) {
        String question = req.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "问题不能为空"));
        }
        return ResponseEntity.ok(nl2SqlService.ask(question));
    }
}
