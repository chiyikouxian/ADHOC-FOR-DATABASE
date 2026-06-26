package com.fanet.controller;

import com.fanet.service.MissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionService missionService;
    private final com.fanet.mapper.pg.DroneMapper droneMapper;

    public MissionController(MissionService missionService, com.fanet.mapper.pg.DroneMapper droneMapper) {
        this.missionService = missionService;
        this.droneMapper = droneMapper;
    }

    @PostMapping("/{missionId}/assign/{droneId}")
    public ResponseEntity<?> assign(@PathVariable Long missionId,
                                    @PathVariable Integer droneId) {
        Map<String, Object> result = missionService.assignDroneToMission(missionId, droneId);
        boolean success = (boolean) result.get("success");
        return success ? ResponseEntity.ok(result) : ResponseEntity.status(409).body(result);
    }

    @PostMapping("/reset-drone/{droneId}")
    public ResponseEntity<?> resetDrone(@PathVariable Integer droneId) {
        droneMapper.updateStatus(droneId, "idle");
        return ResponseEntity.ok(Map.of("reset", droneId));
    }

    @GetMapping("/route/{droneId}")
    public ResponseEntity<List<Map<String, Object>>> route(@PathVariable Integer droneId) {
        return ResponseEntity.ok(missionService.findRoute(droneId));
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<Map<String, Object>>> ranking() {
        return ResponseEntity.ok(missionService.missionRanking());
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(missionService.listMissions());
    }
}
