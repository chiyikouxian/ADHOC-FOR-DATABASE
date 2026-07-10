package com.fanet.service;

import com.fanet.mapper.pg.SimulationScenarioMapper;
import com.fanet.model.SimulationScenario;
import com.fanet.model.SimulationScenarioDrone;
import com.fanet.model.SimulationScenarioLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationScenarioServiceTest {

    @Mock
    private SimulationScenarioMapper mapper;

    @InjectMocks
    private SimulationScenarioService service;

    @Test
    void createsScenarioAndPersistsNestedOverrides() {
        SimulationScenario scenario = validScenario();
        SimulationScenarioDrone drone = new SimulationScenarioDrone();
        drone.setDroneNo(1);
        drone.setInitialBatteryPct(88.0);
        scenario.setDrones(List.of(drone));
        SimulationScenarioLink link = new SimulationScenarioLink();
        link.setSrcDroneNo(1);
        link.setDstDroneNo(2);
        link.setInitialQuality(85);
        scenario.setLinks(List.of(link));

        doAnswer(invocation -> {
            invocation.getArgument(0, SimulationScenario.class).setScenarioId(42L);
            return 1;
        }).when(mapper).insertScenario(any(SimulationScenario.class));

        Map<String, Object> result = service.createScenario(scenario);

        assertEquals(42L, result.get("scenarioId"));
        assertEquals("scenario created", result.get("message"));
        assertEquals(42L, drone.getScenarioId());
        assertEquals(42L, link.getScenarioId());
        assertEquals(Boolean.TRUE, link.getIsEnabled());
        verify(mapper).insertScenario(scenario);
        verify(mapper).deleteScenarioDrones(42L);
        verify(mapper).deleteScenarioLinks(42L);
        verify(mapper).insertScenarioDrone(drone);
        verify(mapper).insertScenarioLink(link);
    }

    @Test
    void rejectsInvalidScenarioBeforeAnyWrite() {
        SimulationScenario scenario = validScenario();
        scenario.setBatteryMin(101.0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.createScenario(scenario));

        assertEquals("batteryMin/batteryMax 范围非法", error.getMessage());
        verify(mapper, never()).insertScenario(any());
    }

    @Test
    void rejectsLinkThatReferencesDroneOutsideScenario() {
        SimulationScenario scenario = validScenario();
        scenario.setScenarioId(7L);
        SimulationScenarioLink link = new SimulationScenarioLink();
        link.setSrcDroneNo(6);
        link.setDstDroneNo(1);
        scenario.setLinks(List.of(link));
        when(mapper.findScenarioById(7L)).thenReturn(validScenario());
        when(mapper.updateScenario(any())).thenReturn(1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.updateScenario(7L, scenario));

        assertEquals("link 节点编号超出 droneCount 范围", error.getMessage());
        verify(mapper, never()).insertScenarioLink(any());
    }

    @Test
    void allowsGroundStationAsExplicitLinkTarget() {
        SimulationScenario scenario = validScenario();
        SimulationScenarioLink link = new SimulationScenarioLink();
        link.setSrcDroneNo(1);
        link.setDstDroneNo(0);
        scenario.setLinks(List.of(link));

        doAnswer(invocation -> {
            invocation.getArgument(0, SimulationScenario.class).setScenarioId(43L);
            return 1;
        }).when(mapper).insertScenario(any(SimulationScenario.class));

        Map<String, Object> result = service.createScenario(scenario);

        assertEquals(43L, result.get("scenarioId"));
        assertEquals(43L, link.getScenarioId());
        verify(mapper).insertScenarioLink(link);
    }

    @Test
    void deletesExistingScenarioAndRejectsMissingOne() {
        when(mapper.deleteScenario(9L)).thenReturn(1);
        assertEquals(9L, service.deleteScenario(9L).get("scenarioId"));
        verify(mapper).deleteScenario(9L);

        when(mapper.deleteScenario(10L)).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.deleteScenario(10L));
    }

    private SimulationScenario validScenario() {
        SimulationScenario scenario = new SimulationScenario();
        scenario.setName("场景 A");
        scenario.setStatus("ready");
        scenario.setDroneCount(3);
        scenario.setPublishIntervalMs(1000);
        scenario.setAreaCenterLat(31.23);
        scenario.setAreaCenterLon(121.47);
        scenario.setAreaRadiusM(500);
        scenario.setBatteryMin(60.0);
        scenario.setBatteryMax(100.0);
        scenario.setRssiMin(-90);
        scenario.setRssiMax(-40);
        scenario.setAltMin(100.0);
        scenario.setAltMax(200.0);
        scenario.setTopologyMode("chain");
        scenario.setMotionMode("random-walk");
        return scenario;
    }
}
