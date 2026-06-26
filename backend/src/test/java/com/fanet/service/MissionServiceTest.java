package com.fanet.service;

import com.fanet.mapper.pg.MissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock
    private MissionMapper missionMapper;

    @InjectMocks
    private MissionService missionService;

    @Test
    void rejectsAssignmentWhenDroneCannotBeLocked() {
        when(missionMapper.lockDroneForAssign(anyInt())).thenReturn(null);

        Map<String, Object> result = missionService.assignDroneToMission(1L, 7);

        assertFalse((Boolean) result.get("success"));
        assertTrue(String.valueOf(result.get("reason")).contains("不存在") || String.valueOf(result.get("reason")).contains("非空闲"));
        verify(missionMapper, never()).assignDrone(anyInt());
        verify(missionMapper, never()).insertAssignment(anyLong(), anyInt());
    }

    @Test
    void rejectsAssignmentWhenBatteryIsBelowThreshold() {
        when(missionMapper.lockDroneForAssign(anyInt())).thenReturn(Map.of(
                "drone_id", 2,
                "status", "idle",
                "battery", 15.5
        ));

        Map<String, Object> result = missionService.assignDroneToMission(2L, 2);

        assertFalse((Boolean) result.get("success"));
        assertTrue(String.valueOf(result.get("reason")).contains("电量不足"));
        verify(missionMapper, never()).assignDrone(anyInt());
        verify(missionMapper, never()).insertAssignment(anyLong(), anyInt());
    }

    @Test
    void assignsDroneWhenBatteryAndStatusAreValid() {
        when(missionMapper.lockDroneForAssign(anyInt())).thenReturn(Map.of(
                "drone_id", 3,
                "status", "idle",
                "battery", 68.0
        ));

        Map<String, Object> result = missionService.assignDroneToMission(9L, 3);

        assertTrue((Boolean) result.get("success"));
        assertEquals(3, result.get("droneId"));
        assertEquals(68.0, result.get("battery"));
        verify(missionMapper).assignDrone(3);
        verify(missionMapper).insertAssignment(9L, 3);
    }

    @Test
    void delegatesRouteRankingAndMissionListQueries() {
        List<Map<String, Object>> routes = List.of(Map.of("path", "4->3->1->0", "hops", 3));
        List<Map<String, Object>> ranking = List.of(Map.of("drone_id", 1, "rank", 1));
        List<Map<String, Object>> missions = List.of(Map.of("mission_id", 1, "title", "城区边界巡检"));

        when(missionMapper.findRoute(4)).thenReturn(routes);
        when(missionMapper.missionRanking()).thenReturn(ranking);
        when(missionMapper.listMissions()).thenReturn(missions);

        assertSame(routes, missionService.findRoute(4));
        assertSame(ranking, missionService.missionRanking());
        assertSame(missions, missionService.listMissions());
    }
}
