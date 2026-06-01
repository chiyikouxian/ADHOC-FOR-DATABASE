package com.fanet.controller;

import com.fanet.mapper.pg.DroneMapper;
import com.fanet.model.Drone;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/drones")
public class DroneController {

    private final DroneMapper droneMapper;

    public DroneController(DroneMapper droneMapper) {
        this.droneMapper = droneMapper;
    }

    @GetMapping
    public ResponseEntity<List<Drone>> list(@RequestParam(required = false) String status) {
        List<Drone> drones = (status != null)
                ? droneMapper.findByStatus(status)
                : droneMapper.findAll();
        return ResponseEntity.ok(drones);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable("id") Integer id) {
        Drone drone = droneMapper.findById(id);
        if (drone == null) {
            return ResponseEntity.status(404).body(Map.of("error", "无人机不存在"));
        }
        return ResponseEntity.ok(drone);
    }
}
