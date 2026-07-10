package com.fanet.service;

import com.fanet.mapper.pg.SimulationScenarioMapper;
import com.fanet.model.SimulationScenario;
import com.fanet.model.SimulationScenarioDrone;
import com.fanet.model.SimulationScenarioLink;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SimulationScenarioService {

    public static final String DEFAULT_SCENARIO_NAME = "默认演示场景";

    private static final Set<String> ALLOWED_STATUS = Set.of("draft", "ready", "archived");
    private static final Set<String> ALLOWED_TOPOLOGY = Set.of("chain", "star", "mesh", "custom");
    private static final Set<String> ALLOWED_MOTION = Set.of("random-walk", "patrol", "orbit", "hover");

    private final SimulationScenarioMapper mapper;

    public SimulationScenarioService(SimulationScenarioMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> listScenarios() {
        return mapper.listScenarios();
    }

    @Transactional(transactionManager = "pgTransactionManager")
    public SimulationScenario ensureDefaultScenario() {
        SimulationScenario existing = mapper.findScenarioByName(DEFAULT_SCENARIO_NAME);
        if (existing != null) {
            existing.setDrones(mapper.findScenarioDronesByScenarioId(existing.getScenarioId()));
            existing.setLinks(mapper.findScenarioLinksByScenarioId(existing.getScenarioId()));
            return existing;
        }

        SimulationScenario scenario = buildDefaultScenario();
        normalizeScenario(scenario, true);
        mapper.insertScenario(scenario);
        replaceChildren(scenario);
        scenario.setDrones(mapper.findScenarioDronesByScenarioId(scenario.getScenarioId()));
        scenario.setLinks(mapper.findScenarioLinksByScenarioId(scenario.getScenarioId()));
        return scenario;
    }

    public SimulationScenario getScenario(Long scenarioId) {
        validateScenarioId(scenarioId);
        SimulationScenario scenario = mapper.findScenarioById(scenarioId);
        if (scenario == null) {
            throw new IllegalArgumentException("simulation scenario #" + scenarioId + " 不存在");
        }
        scenario.setDrones(mapper.findScenarioDronesByScenarioId(scenarioId));
        scenario.setLinks(mapper.findScenarioLinksByScenarioId(scenarioId));
        return scenario;
    }

    @Transactional(transactionManager = "pgTransactionManager")
    public Map<String, Object> createScenario(SimulationScenario scenario) {
        normalizeScenario(scenario, true);
        mapper.insertScenario(scenario);
        replaceChildren(scenario);
        return Map.of(
                "scenarioId", scenario.getScenarioId(),
                "message", "scenario created"
        );
    }

    @Transactional(transactionManager = "pgTransactionManager")
    public Map<String, Object> updateScenario(Long scenarioId, SimulationScenario scenario) {
        validateScenarioId(scenarioId);
        if (mapper.findScenarioById(scenarioId) == null) {
            throw new IllegalArgumentException("simulation scenario #" + scenarioId + " 不存在");
        }
        scenario.setScenarioId(scenarioId);
        normalizeScenario(scenario, false);
        int updated = mapper.updateScenario(scenario);
        if (updated == 0) {
            throw new IllegalArgumentException("simulation scenario #" + scenarioId + " 更新失败");
        }
        replaceChildren(scenario);
        return Map.of(
                "scenarioId", scenarioId,
                "message", "scenario updated"
        );
    }

    @Transactional(transactionManager = "pgTransactionManager")
    public Map<String, Object> deleteScenario(Long scenarioId) {
        validateScenarioId(scenarioId);
        int deleted = mapper.deleteScenario(scenarioId);
        if (deleted == 0) {
            throw new IllegalArgumentException("simulation scenario #" + scenarioId + " 不存在");
        }
        return Map.of(
                "scenarioId", scenarioId,
                "message", "scenario deleted"
        );
    }

    private void replaceChildren(SimulationScenario scenario) {
        Long scenarioId = scenario.getScenarioId();
        mapper.deleteScenarioDrones(scenarioId);
        mapper.deleteScenarioLinks(scenarioId);

        if (scenario.getDrones() != null) {
            for (SimulationScenarioDrone drone : scenario.getDrones()) {
                validateDroneOverride(drone, scenario.getDroneCount());
                drone.setScenarioId(scenarioId);
                mapper.insertScenarioDrone(drone);
            }
        }

        if (scenario.getLinks() != null) {
            for (SimulationScenarioLink link : scenario.getLinks()) {
                validateLinkOverride(link, scenario.getDroneCount());
                link.setScenarioId(scenarioId);
                if (link.getIsEnabled() == null) {
                    link.setIsEnabled(Boolean.TRUE);
                }
                mapper.insertScenarioLink(link);
            }
        }
    }

    private SimulationScenario buildDefaultScenario() {
        SimulationScenario scenario = new SimulationScenario();
        scenario.setName(DEFAULT_SCENARIO_NAME);
        scenario.setDescription("系统默认启动场景，和模拟页初始参数保持一致，用于驱动全平台联调演示");
        scenario.setStatus("ready");
        scenario.setDroneCount(7);
        scenario.setPublishIntervalMs(1000);
        scenario.setAreaCenterLat(31.23);
        scenario.setAreaCenterLon(121.47);
        scenario.setAreaRadiusM(800);
        scenario.setBatteryMin(70.0);
        scenario.setBatteryMax(100.0);
        scenario.setRssiMin(-90);
        scenario.setRssiMax(-45);
        scenario.setAltMin(100.0);
        scenario.setAltMax(220.0);
        scenario.setTopologyMode("chain");
        scenario.setMotionMode("random-walk");

        List<SimulationScenarioDrone> drones = new java.util.ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            SimulationScenarioDrone drone = new SimulationScenarioDrone();
            drone.setDroneNo(i);
            drone.setModelId(((i - 1) % 3) + 1);
            drone.setSerialNo(String.format("SN-%04d", i));
            drone.setInitialLat(31.23 + (i * 0.0012));
            drone.setInitialLon(121.47 + ((i % 2 == 0 ? -1 : 1) * i * 0.0011));
            drone.setInitialAlt(105.0 + i * 12.0);
            drone.setInitialBatteryPct(Math.max(72.0, 100.0 - i * 3.0));
            drone.setInitialRssi(-58 - i * 3);
            drone.setRoleTag(i == 1 ? "gateway" : i <= 3 ? "scout" : "relay");
            drones.add(drone);
        }
        scenario.setDrones(drones);

        List<SimulationScenarioLink> links = new java.util.ArrayList<>();
        for (int i = 2; i <= 7; i++) {
            SimulationScenarioLink link = new SimulationScenarioLink();
            link.setSrcDroneNo(i);
            link.setDstDroneNo(i - 1);
            link.setInitialQuality(Math.max(55, 88 - i * 4));
            link.setIsEnabled(Boolean.TRUE);
            links.add(link);
        }
        scenario.setLinks(links);
        return scenario;
    }

    private void normalizeScenario(SimulationScenario scenario, boolean creating) {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario 不能为空");
        }
        scenario.setName(trimToNull(scenario.getName()));
        scenario.setDescription(trimToNull(scenario.getDescription()));
        scenario.setStatus(defaultIfBlank(scenario.getStatus(), "draft"));
        scenario.setTopologyMode(defaultIfBlank(scenario.getTopologyMode(), "chain"));
        scenario.setMotionMode(defaultIfBlank(scenario.getMotionMode(), "random-walk"));

        if (scenario.getName() == null || scenario.getName().length() > 64) {
            throw new IllegalArgumentException("name 必填且长度不能超过 64");
        }
        if (!ALLOWED_STATUS.contains(scenario.getStatus())) {
            throw new IllegalArgumentException("status 仅支持: " + ALLOWED_STATUS);
        }
        if (!ALLOWED_TOPOLOGY.contains(scenario.getTopologyMode())) {
            throw new IllegalArgumentException("topologyMode 仅支持: " + ALLOWED_TOPOLOGY);
        }
        if (!ALLOWED_MOTION.contains(scenario.getMotionMode())) {
            throw new IllegalArgumentException("motionMode 仅支持: " + ALLOWED_MOTION);
        }
        requirePositive(scenario.getDroneCount(), "droneCount");
        requireMin(scenario.getPublishIntervalMs(), 100, "publishIntervalMs");
        requireNonNull(scenario.getAreaCenterLat(), "areaCenterLat");
        requireNonNull(scenario.getAreaCenterLon(), "areaCenterLon");
        requirePositive(scenario.getAreaRadiusM(), "areaRadiusM");
        requireNonNull(scenario.getBatteryMin(), "batteryMin");
        requireNonNull(scenario.getBatteryMax(), "batteryMax");
        requireNonNull(scenario.getRssiMin(), "rssiMin");
        requireNonNull(scenario.getRssiMax(), "rssiMax");
        requireNonNull(scenario.getAltMin(), "altMin");
        requireNonNull(scenario.getAltMax(), "altMax");

        if (scenario.getBatteryMin() < 0 || scenario.getBatteryMax() > 100 || scenario.getBatteryMin() > scenario.getBatteryMax()) {
            throw new IllegalArgumentException("batteryMin/batteryMax 范围非法");
        }
        if (scenario.getRssiMin() > scenario.getRssiMax()) {
            throw new IllegalArgumentException("rssiMin 不能大于 rssiMax");
        }
        if (scenario.getAltMin() < 0 || scenario.getAltMin() > scenario.getAltMax()) {
            throw new IllegalArgumentException("altMin/altMax 范围非法");
        }
        if (!creating && scenario.getScenarioId() == null) {
            throw new IllegalArgumentException("scenarioId 必填");
        }
    }

    private void validateDroneOverride(SimulationScenarioDrone drone, Integer droneCount) {
        if (drone == null) {
            throw new IllegalArgumentException("drone override 不能为空");
        }
        requirePositive(drone.getDroneNo(), "droneNo");
        if (drone.getDroneNo() > droneCount) {
            throw new IllegalArgumentException("droneNo 超出 droneCount 范围");
        }
        if (drone.getInitialBatteryPct() != null
                && (drone.getInitialBatteryPct() < 0 || drone.getInitialBatteryPct() > 100)) {
            throw new IllegalArgumentException("initialBatteryPct 范围非法");
        }
    }

    private void validateLinkOverride(SimulationScenarioLink link, Integer droneCount) {
        if (link == null) {
            throw new IllegalArgumentException("link override 不能为空");
        }
        requirePositive(link.getSrcDroneNo(), "srcDroneNo");
        requireNonNull(link.getDstDroneNo(), "dstDroneNo");
        if (link.getDstDroneNo() < 0) {
            throw new IllegalArgumentException("dstDroneNo 不能小于 0");
        }
        if (link.getSrcDroneNo() > droneCount || link.getDstDroneNo() > droneCount) {
            throw new IllegalArgumentException("link 节点编号超出 droneCount 范围");
        }
        if (link.getSrcDroneNo().equals(link.getDstDroneNo())) {
            throw new IllegalArgumentException("srcDroneNo 不能等于 dstDroneNo");
        }
        if (link.getInitialQuality() != null && (link.getInitialQuality() < 0 || link.getInitialQuality() > 100)) {
            throw new IllegalArgumentException("initialQuality 范围非法");
        }
    }

    private void validateScenarioId(Long scenarioId) {
        if (scenarioId == null || scenarioId <= 0) {
            throw new IllegalArgumentException("有效的 scenarioId 必填");
        }
    }

    private void requirePositive(Integer value, String fieldName) {
        requireNonNull(value, fieldName);
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
    }

    private void requireMin(Integer value, int min, String fieldName) {
        requireNonNull(value, fieldName);
        if (value < min) {
            throw new IllegalArgumentException(fieldName + " 必须大于等于 " + min);
        }
    }

    private void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 必填");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : defaultValue;
    }
}
