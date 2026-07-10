package com.fanet.service;

import com.fanet.mapper.pg.MissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class MissionService {

    private final MissionMapper missionMapper;

    public MissionService(MissionMapper missionMapper) {
        this.missionMapper = missionMapper;
    }

    @Transactional(transactionManager = "pgTransactionManager")
    public Map<String, Object> assignDroneToMission(Long missionId, Integer droneId) {
        // 行锁：SELECT ... FOR UPDATE 锁定该无人机行
        Map<String, Object> locked = missionMapper.lockDroneForAssign(droneId);
        if (locked == null) {
            return Map.of("success", false, "reason", "无人机不存在或非空闲状态");
        }
        if (!"idle".equalsIgnoreCase(String.valueOf(locked.get("status")))) {
            return Map.of("success", false, "reason", "无人机不存在或非空闲状态");
        }

        Double battery = ((Number) locked.get("battery")).doubleValue();
        if (battery < 20.0) {
            return Map.of("success", false, "reason", "电量不足(当前 " + battery + "%)");
        }

        // 行锁保护下更新状态 + 插入分配
        missionMapper.assignDrone(droneId);
        missionMapper.insertAssignment(missionId, droneId);

        return Map.of("success", true, "droneId", droneId, "battery", battery);
    }

    public List<Map<String, Object>> findRoute(Integer srcDroneId) {
        return missionMapper.findRoute(srcDroneId);
    }

    public List<Map<String, Object>> missionRanking() {
        return missionMapper.missionRanking();
    }

    public List<Map<String, Object>> listMissions() {
        return missionMapper.listMissions();
    }
}
