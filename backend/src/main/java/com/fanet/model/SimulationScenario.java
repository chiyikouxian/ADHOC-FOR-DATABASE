package com.fanet.model;

import java.time.OffsetDateTime;
import java.util.List;

public class SimulationScenario {

    private Long scenarioId;
    private String name;
    private String description;
    private String status;
    private Integer droneCount;
    private Integer publishIntervalMs;
    private Double areaCenterLat;
    private Double areaCenterLon;
    private Integer areaRadiusM;
    private Double batteryMin;
    private Double batteryMax;
    private Integer rssiMin;
    private Integer rssiMax;
    private Double altMin;
    private Double altMax;
    private String topologyMode;
    private String motionMode;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<SimulationScenarioDrone> drones;
    private List<SimulationScenarioLink> links;

    public Long getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(Long scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDroneCount() {
        return droneCount;
    }

    public void setDroneCount(Integer droneCount) {
        this.droneCount = droneCount;
    }

    public Integer getPublishIntervalMs() {
        return publishIntervalMs;
    }

    public void setPublishIntervalMs(Integer publishIntervalMs) {
        this.publishIntervalMs = publishIntervalMs;
    }

    public Double getAreaCenterLat() {
        return areaCenterLat;
    }

    public void setAreaCenterLat(Double areaCenterLat) {
        this.areaCenterLat = areaCenterLat;
    }

    public Double getAreaCenterLon() {
        return areaCenterLon;
    }

    public void setAreaCenterLon(Double areaCenterLon) {
        this.areaCenterLon = areaCenterLon;
    }

    public Integer getAreaRadiusM() {
        return areaRadiusM;
    }

    public void setAreaRadiusM(Integer areaRadiusM) {
        this.areaRadiusM = areaRadiusM;
    }

    public Double getBatteryMin() {
        return batteryMin;
    }

    public void setBatteryMin(Double batteryMin) {
        this.batteryMin = batteryMin;
    }

    public Double getBatteryMax() {
        return batteryMax;
    }

    public void setBatteryMax(Double batteryMax) {
        this.batteryMax = batteryMax;
    }

    public Integer getRssiMin() {
        return rssiMin;
    }

    public void setRssiMin(Integer rssiMin) {
        this.rssiMin = rssiMin;
    }

    public Integer getRssiMax() {
        return rssiMax;
    }

    public void setRssiMax(Integer rssiMax) {
        this.rssiMax = rssiMax;
    }

    public Double getAltMin() {
        return altMin;
    }

    public void setAltMin(Double altMin) {
        this.altMin = altMin;
    }

    public Double getAltMax() {
        return altMax;
    }

    public void setAltMax(Double altMax) {
        this.altMax = altMax;
    }

    public String getTopologyMode() {
        return topologyMode;
    }

    public void setTopologyMode(String topologyMode) {
        this.topologyMode = topologyMode;
    }

    public String getMotionMode() {
        return motionMode;
    }

    public void setMotionMode(String motionMode) {
        this.motionMode = motionMode;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<SimulationScenarioDrone> getDrones() {
        return drones;
    }

    public void setDrones(List<SimulationScenarioDrone> drones) {
        this.drones = drones;
    }

    public List<SimulationScenarioLink> getLinks() {
        return links;
    }

    public void setLinks(List<SimulationScenarioLink> links) {
        this.links = links;
    }
}
