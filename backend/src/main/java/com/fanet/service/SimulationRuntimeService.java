package com.fanet.service;

import com.fanet.model.SimulationScenario;
import com.fanet.model.SimulationScenarioLink;
import com.fanet.model.TelemetryRecord;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class SimulationRuntimeService {

    private final SimulationScenarioService scenarioService;
    private final TelemetryService telemetryService;
    private final DataSource tdDataSource;
    private final DataSource pgDataSource;
    private final TopologySyncService topologySyncService;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    private ScheduledFuture<?> activeTask;
    private RuntimeSession activeSession;

    public SimulationRuntimeService(SimulationScenarioService scenarioService,
                                    TelemetryService telemetryService,
                                    @Qualifier("tdDataSource") DataSource tdDataSource,
                                    @Qualifier("pgDataSource") DataSource pgDataSource,
                                    TopologySyncService topologySyncService) {
        this.scenarioService = scenarioService;
        this.telemetryService = telemetryService;
        this.tdDataSource = tdDataSource;
        this.pgDataSource = pgDataSource;
        this.topologySyncService = topologySyncService;
    }

    public synchronized Map<String, Object> start(Long scenarioId, Map<String, Object> overrides) {
        if (isRunning()) {
            stop();
        }

        SimulationScenario scenario = scenarioService.getScenario(scenarioId);
        int droneCount = getOverrideInt(overrides, "droneCount", scenario.getDroneCount());
        int publishIntervalMs = getOverrideInt(overrides, "publishIntervalMs", scenario.getPublishIntervalMs());
        String motionMode = getOverrideString(overrides, "motionMode", scenario.getMotionMode());

        RuntimeSession session = new RuntimeSession();
        session.scenarioId = scenario.getScenarioId();
        session.scenarioName = scenario.getName();
        session.startedAt = Instant.now();
        session.publishIntervalMs = Math.max(100, publishIntervalMs);
        session.motionMode = motionMode;
        session.topologyMode = scenario.getTopologyMode();
        session.areaCenterLat = scenario.getAreaCenterLat();
        session.areaCenterLon = scenario.getAreaCenterLon();
        session.areaRadiusM = scenario.getAreaRadiusM();
        session.runtimeDrones = buildRuntimeDrones(scenario, droneCount);
        session.customLinks = scenario.getLinks() != null ? scenario.getLinks() : List.of();
        syncDroneCatalog(session);

        activeSession = session;
        activeTask = executor.scheduleAtFixedRate(this::tickSafely, 0, session.publishIntervalMs, TimeUnit.MILLISECONDS);

        return Map.of(
                "running", true,
                "scenarioId", scenario.getScenarioId(),
                "startedAt", session.startedAt.toString(),
                "message", "simulation started"
        );
    }

    public synchronized Map<String, Object> stop() {
        if (activeTask != null) {
            activeTask.cancel(false);
            activeTask = null;
        }
        if (activeSession != null) {
            syncIdleStatuses(activeSession);
        }
        activeSession = null;
        return Map.of(
                "running", false,
                "message", "simulation stopped"
        );
    }

    public synchronized Map<String, Object> apply(Map<String, Object> params) {
        if (!isRunning()) {
            throw new IllegalStateException("当前没有运行中的仿真会话");
        }
        if (params.containsKey("publishIntervalMs")) {
            int nextInterval = toInt(params.get("publishIntervalMs"), activeSession.publishIntervalMs);
            if (nextInterval < 100) {
                throw new IllegalArgumentException("publishIntervalMs must be >= 100");
            }
            activeSession.publishIntervalMs = nextInterval;
            rescheduleActiveTask();
        }
        if (params.containsKey("motionMode")) {
            activeSession.motionMode = String.valueOf(params.get("motionMode"));
        }
        if (params.containsKey("batteryDrainFactor")) {
            activeSession.batteryDrainFactor = Math.max(0.1, toDouble(params.get("batteryDrainFactor"), 1.0));
        }
        if (params.containsKey("rssiNoiseFactor")) {
            activeSession.rssiNoiseFactor = Math.max(0.1, toDouble(params.get("rssiNoiseFactor"), 1.0));
        }
        return Map.of(
                "running", true,
                "message", "runtime parameters applied"
        );
    }

    public synchronized Map<String, Object> status() {
        if (!isRunning()) {
            return Map.of("running", false);
        }
        return buildStatus(activeSession);
    }

    public synchronized Map<String, Object> preview() {
        if (!isRunning()) {
            return Map.of(
                    "nodes", List.of(),
                    "edges", List.of(),
                    "metrics", Map.of("running", false)
            );
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (RuntimeDrone drone : activeSession.runtimeDrones) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("droneNo", drone.droneNo);
            node.put("name", drone.serialNo);
            node.put("lat", round(drone.lat, 6));
            node.put("lon", round(drone.lon, 6));
            node.put("alt", round(drone.alt, 2));
            node.put("batteryPct", round(drone.batteryPct, 2));
            node.put("rssi", drone.rssi);
            nodes.add(node);
        }

        List<Map<String, Object>> edges = buildPreviewEdges(activeSession);
        boolean hasGroundStation = edges.stream().anyMatch(edge -> Integer.valueOf(0).equals(edge.get("target")));
        if (hasGroundStation) {
            Map<String, Object> groundStation = new LinkedHashMap<>();
            groundStation.put("droneNo", 0);
            groundStation.put("name", "地面站");
            groundStation.put("lat", round(activeSession.areaCenterLat, 6));
            groundStation.put("lon", round(activeSession.areaCenterLon, 6));
            groundStation.put("alt", 0);
            groundStation.put("batteryPct", 100);
            groundStation.put("rssi", 0);
            nodes.add(groundStation);
        }
        Map<String, Object> metrics = new LinkedHashMap<>(buildStatus(activeSession));

        return Map.of(
                "nodes", nodes,
                "edges", edges,
                "metrics", metrics
        );
    }

    public synchronized boolean isRunning() {
        return activeTask != null && !activeTask.isCancelled() && activeSession != null;
    }

    @PreDestroy
    public void shutdown() {
        if (activeTask != null) {
            activeTask.cancel(true);
        }
        executor.shutdownNow();
    }

    private void tickSafely() {
        try {
            tick();
        } catch (Exception e) {
            System.err.println("[SimulationRuntime] tick failed: " + e.getMessage());
        }
    }

    private synchronized void tick() {
        if (!isRunning()) {
            return;
        }
        RuntimeSession session = activeSession;
        Instant now = Instant.now();
        List<TelemetryRecord> records = new ArrayList<>();
        double batterySum = 0;

        for (RuntimeDrone drone : session.runtimeDrones) {
            moveDrone(session, drone);
            drone.batteryPct = Math.max(0, drone.batteryPct - (0.08 * session.batteryDrainFactor) - random.nextDouble() * 0.03);
            drone.rssi = clamp((int) Math.round(drone.rssi + (random.nextDouble() - 0.5) * 4 * session.rssiNoiseFactor), -95, -40);
            batterySum += drone.batteryPct;

            TelemetryRecord record = new TelemetryRecord();
            record.setDroneId(drone.droneNo);
            record.setModelId(drone.modelId);
            record.setTs(now);
            record.setLat(round(drone.lat, 6));
            record.setLon(round(drone.lon, 6));
            record.setAlt(round(drone.alt, 2));
            record.setBatteryPct(round(drone.batteryPct, 2));
            record.setRssi(drone.rssi);
            records.add(record);
        }

        telemetryService.ingest(records);
        List<RuntimeLink> links = buildRuntimeLinks(session);
        ingestLinks(links);
        topologySyncService.syncLinkSnapshot();

        session.tickCount++;
        session.avgBattery = records.isEmpty() ? 0 : round(batterySum / records.size(), 2);
        session.activeLinks = (int) links.stream().filter(link -> link.active).count();
        session.lastEdges = buildEdgeMaps(links);
    }

    private void moveDrone(RuntimeSession session, RuntimeDrone drone) {
        double latStep = (random.nextDouble() - 0.5) * 0.0004;
        double lonStep = (random.nextDouble() - 0.5) * 0.0004;
        double altStep = (random.nextDouble() - 0.5) * 1.8;

        if ("orbit".equals(session.motionMode)) {
            double angle = ((session.tickCount + drone.droneNo) % 360) * Math.PI / 180.0;
            double radius = Math.max(0.001, session.areaRadiusM / 111000.0 / 3.5);
            drone.lat = session.areaCenterLat + Math.cos(angle) * radius;
            drone.lon = session.areaCenterLon + Math.sin(angle) * radius;
        } else if ("hover".equals(session.motionMode)) {
            latStep *= 0.1;
            lonStep *= 0.1;
        } else if ("patrol".equals(session.motionMode)) {
            latStep *= 0.6;
            lonStep *= 1.2;
        }

        if (!"orbit".equals(session.motionMode)) {
            drone.lat += latStep;
            drone.lon += lonStep;
        }
        drone.alt = clamp(round(drone.alt + altStep, 2), drone.altMin, drone.altMax);
    }

    private List<RuntimeLink> buildRuntimeLinks(RuntimeSession session) {
        List<RuntimeLink> links = new ArrayList<>();
        if (!session.customLinks.isEmpty()) {
            for (SimulationScenarioLink link : session.customLinks) {
                if (link.getSrcDroneNo() == null || link.getDstDroneNo() == null
                        || link.getSrcDroneNo() <= 0 || link.getDstDroneNo() < 0) {
                    continue;
                }
                RuntimeLink edge = new RuntimeLink();
                edge.src = link.getSrcDroneNo();
                edge.dst = link.getDstDroneNo();
                edge.quality = clamp((link.getInitialQuality() != null ? link.getInitialQuality() : 70) + random.nextInt(11) - 5, 0, 100);
                edge.active = Boolean.TRUE.equals(link.getIsEnabled()) && edge.quality > 20;
                links.add(edge);
            }
            return links;
        }

        if ("star".equals(session.topologyMode)) {
            for (int i = 1; i < session.runtimeDrones.size(); i++) {
                links.add(runtimeLink(session.runtimeDrones.get(i).droneNo, session.runtimeDrones.get(0).droneNo, 78));
            }
            return links;
        }

        if ("mesh".equals(session.topologyMode)) {
            for (int i = 0; i < session.runtimeDrones.size(); i++) {
                for (int j = i + 1; j < session.runtimeDrones.size() && j < i + 3; j++) {
                    links.add(runtimeLink(session.runtimeDrones.get(i).droneNo, session.runtimeDrones.get(j).droneNo, 72));
                }
            }
            return links;
        }

        if (!session.runtimeDrones.isEmpty()) {
            for (int i = 1; i < session.runtimeDrones.size(); i++) {
                links.add(runtimeLink(session.runtimeDrones.get(i).droneNo, session.runtimeDrones.get(i - 1).droneNo, 76));
            }
        }
        return links;
    }

    private RuntimeLink runtimeLink(int src, int dst, int baseQuality) {
        RuntimeLink link = new RuntimeLink();
        link.src = src;
        link.dst = dst;
        link.quality = clamp(baseQuality + random.nextInt(13) - 6, 0, 100);
        link.active = link.quality > 20;
        return link;
    }

    private void ingestLinks(List<RuntimeLink> links) {
        if (links.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        for (int i = 0; i < links.size(); i++) {
            RuntimeLink link = links.get(i);
            if (i > 0) {
                sql.append(' ');
            }
            sql.append("l_link_").append(link.src).append('_').append(link.dst)
                    .append(" USING network_links TAGS(").append(link.src).append(',').append(link.dst).append(')')
                    .append(" VALUES(NOW,").append(link.quality).append(',').append(link.active).append(')');
        }
        try (Connection conn = tdDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        } catch (SQLException e) {
            throw new IllegalStateException("TDengine link ingest failed: " + e.getMessage(), e);
        }
    }

    private void syncDroneCatalog(RuntimeSession session) {
        String upsertDroneSql = "INSERT INTO drones (drone_id, model_id, serial_no, status) "
                + "VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (drone_id) DO UPDATE SET model_id = EXCLUDED.model_id, serial_no = EXCLUDED.serial_no, status = EXCLUDED.status";
        String updateUnusedSql = "UPDATE drones SET status = 'offline' WHERE drone_id <> 0";
        String setGroundSql = "UPDATE drones SET status = 'idle' WHERE drone_id = 0";
        try (Connection conn = pgDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(updateUnusedSql);
                stmt.executeUpdate(setGroundSql);
            }
            try (PreparedStatement ps = conn.prepareStatement(upsertDroneSql)) {
                for (RuntimeDrone drone : session.runtimeDrones) {
                    ps.setInt(1, drone.droneNo);
                    ps.setInt(2, drone.modelId);
                    ps.setString(3, drone.serialNo);
                    ps.setString(4, "flying");
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("simulation drone catalog sync failed: " + e.getMessage(), e);
        }
    }

    private void syncIdleStatuses(RuntimeSession session) {
        Set<Integer> activeIds = new HashSet<>();
        for (RuntimeDrone drone : session.runtimeDrones) {
            activeIds.add(drone.droneNo);
        }

        try (Connection conn = pgDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE drones SET status = 'offline' WHERE drone_id <> 0");
                stmt.executeUpdate("UPDATE drones SET status = 'idle' WHERE drone_id = 0");
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE drones SET status = 'idle' WHERE drone_id = ?")) {
                for (Integer droneId : activeIds) {
                    ps.setInt(1, droneId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("simulation idle status sync failed: " + e.getMessage(), e);
        }
    }

    private void rescheduleActiveTask() {
        if (activeTask != null) {
            activeTask.cancel(false);
        }
        activeTask = executor.scheduleAtFixedRate(this::tickSafely, 0, activeSession.publishIntervalMs, TimeUnit.MILLISECONDS);
    }

    private List<RuntimeDrone> buildRuntimeDrones(SimulationScenario scenario, int droneCount) {
        List<RuntimeDrone> drones = new ArrayList<>();
        Map<Integer, RuntimeDrone> overrides = new LinkedHashMap<>();
        if (scenario.getDrones() != null) {
            scenario.getDrones().forEach(item -> {
                RuntimeDrone drone = new RuntimeDrone();
                drone.droneNo = item.getDroneNo();
                drone.modelId = item.getModelId() != null ? item.getModelId() : 1;
                drone.serialNo = item.getSerialNo() != null ? item.getSerialNo() : String.format("SIM-%04d", item.getDroneNo());
                drone.lat = item.getInitialLat() != null ? item.getInitialLat() : scenario.getAreaCenterLat();
                drone.lon = item.getInitialLon() != null ? item.getInitialLon() : scenario.getAreaCenterLon();
                drone.alt = item.getInitialAlt() != null ? item.getInitialAlt() : scenario.getAltMin() + random.nextDouble() * (scenario.getAltMax() - scenario.getAltMin());
                drone.batteryPct = item.getInitialBatteryPct() != null ? item.getInitialBatteryPct() : scenario.getBatteryMin() + random.nextDouble() * (scenario.getBatteryMax() - scenario.getBatteryMin());
                drone.rssi = item.getInitialRssi() != null ? item.getInitialRssi() : clamp(scenario.getRssiMin() + random.nextInt(Math.max(1, scenario.getRssiMax() - scenario.getRssiMin() + 1)), -95, -40);
                drone.altMin = scenario.getAltMin();
                drone.altMax = scenario.getAltMax();
                overrides.put(drone.droneNo, drone);
            });
        }

        for (int i = 1; i <= droneCount; i++) {
            RuntimeDrone base = overrides.get(i);
            if (base == null) {
                base = new RuntimeDrone();
                base.droneNo = i;
                base.modelId = ((i - 1) % 3) + 1;
                base.serialNo = String.format("SIM-%04d", i);
                base.lat = scenario.getAreaCenterLat() + (random.nextDouble() - 0.5) * 0.02;
                base.lon = scenario.getAreaCenterLon() + (random.nextDouble() - 0.5) * 0.02;
                base.alt = scenario.getAltMin() + random.nextDouble() * (scenario.getAltMax() - scenario.getAltMin());
                base.batteryPct = scenario.getBatteryMin() + random.nextDouble() * (scenario.getBatteryMax() - scenario.getBatteryMin());
                base.rssi = clamp(scenario.getRssiMin() + random.nextInt(Math.max(1, scenario.getRssiMax() - scenario.getRssiMin() + 1)), -95, -40);
                base.altMin = scenario.getAltMin();
                base.altMax = scenario.getAltMax();
            }
            drones.add(base);
        }
        return drones;
    }

    private Map<String, Object> buildStatus(RuntimeSession session) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", true);
        status.put("scenarioId", session.scenarioId);
        status.put("scenarioName", session.scenarioName);
        status.put("startedAt", session.startedAt.toString());
        status.put("droneCount", session.runtimeDrones.size());
        status.put("publishIntervalMs", session.publishIntervalMs);
        status.put("motionMode", session.motionMode);
        status.put("tickCount", session.tickCount);
        status.put("avgBattery", session.avgBattery);
        status.put("activeLinks", session.activeLinks);
        return status;
    }

    private List<Map<String, Object>> buildPreviewEdges(RuntimeSession session) {
        return session.lastEdges != null ? session.lastEdges : List.of();
    }

    private List<Map<String, Object>> buildEdgeMaps(List<RuntimeLink> links) {
        List<Map<String, Object>> edges = new ArrayList<>();
        for (RuntimeLink link : links) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", link.src);
            edge.put("target", link.dst);
            edge.put("quality", link.quality);
            edge.put("active", link.active);
            edges.add(edge);
        }
        return edges;
    }

    private int getOverrideInt(Map<String, Object> overrides, String key, int defaultValue) {
        if (overrides == null || !overrides.containsKey(key)) {
            return defaultValue;
        }
        return toInt(overrides.get(key), defaultValue);
    }

    private String getOverrideString(Map<String, Object> overrides, String key, String defaultValue) {
        if (overrides == null || !overrides.containsKey(key) || overrides.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(overrides.get(key));
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(String.valueOf(value)) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private static class RuntimeSession {
        Long scenarioId;
        String scenarioName;
        Instant startedAt;
        int publishIntervalMs;
        String motionMode;
        String topologyMode;
        double areaCenterLat;
        double areaCenterLon;
        int areaRadiusM;
        double batteryDrainFactor = 1.0;
        double rssiNoiseFactor = 1.0;
        long tickCount;
        double avgBattery;
        int activeLinks;
        List<RuntimeDrone> runtimeDrones = List.of();
        List<SimulationScenarioLink> customLinks = List.of();
        List<Map<String, Object>> lastEdges = List.of();
    }

    private static class RuntimeDrone {
        int droneNo;
        int modelId;
        String serialNo;
        double lat;
        double lon;
        double alt;
        double batteryPct;
        int rssi;
        double altMin;
        double altMax;
    }

    private static class RuntimeLink {
        int src;
        int dst;
        int quality;
        boolean active;
    }
}
