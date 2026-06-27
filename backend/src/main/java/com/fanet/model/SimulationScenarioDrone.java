package com.fanet.model;

public class SimulationScenarioDrone {

    private Long scenarioDroneId;
    private Long scenarioId;
    private Integer droneNo;
    private Integer modelId;
    private String serialNo;
    private Double initialLat;
    private Double initialLon;
    private Double initialAlt;
    private Double initialBatteryPct;
    private Integer initialRssi;
    private String roleTag;

    public Long getScenarioDroneId() {
        return scenarioDroneId;
    }

    public void setScenarioDroneId(Long scenarioDroneId) {
        this.scenarioDroneId = scenarioDroneId;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(Long scenarioId) {
        this.scenarioId = scenarioId;
    }

    public Integer getDroneNo() {
        return droneNo;
    }

    public void setDroneNo(Integer droneNo) {
        this.droneNo = droneNo;
    }

    public Integer getModelId() {
        return modelId;
    }

    public void setModelId(Integer modelId) {
        this.modelId = modelId;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public Double getInitialLat() {
        return initialLat;
    }

    public void setInitialLat(Double initialLat) {
        this.initialLat = initialLat;
    }

    public Double getInitialLon() {
        return initialLon;
    }

    public void setInitialLon(Double initialLon) {
        this.initialLon = initialLon;
    }

    public Double getInitialAlt() {
        return initialAlt;
    }

    public void setInitialAlt(Double initialAlt) {
        this.initialAlt = initialAlt;
    }

    public Double getInitialBatteryPct() {
        return initialBatteryPct;
    }

    public void setInitialBatteryPct(Double initialBatteryPct) {
        this.initialBatteryPct = initialBatteryPct;
    }

    public Integer getInitialRssi() {
        return initialRssi;
    }

    public void setInitialRssi(Integer initialRssi) {
        this.initialRssi = initialRssi;
    }

    public String getRoleTag() {
        return roleTag;
    }

    public void setRoleTag(String roleTag) {
        this.roleTag = roleTag;
    }
}
