package com.fanet.model;

import java.time.OffsetDateTime;

public class Drone {
    private Integer droneId;
    private Integer modelId;
    private String serialNo;
    private String status;
    private OffsetDateTime registeredAt;

    // 关联字段(来自 v_drone_latest 视图)
    private String modelName;
    private Integer maxFlightMinutes;
    private OffsetDateTime lastSeen;
    private Double lat;
    private Double lon;
    private Double alt;
    private Double batteryPct;
    private Integer rssi;

    public Integer getDroneId() { return droneId; }
    public void setDroneId(Integer droneId) { this.droneId = droneId; }
    public Integer getModelId() { return modelId; }
    public void setModelId(Integer modelId) { this.modelId = modelId; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Integer getMaxFlightMinutes() { return maxFlightMinutes; }
    public void setMaxFlightMinutes(Integer v) { this.maxFlightMinutes = v; }
    public OffsetDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(OffsetDateTime lastSeen) { this.lastSeen = lastSeen; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }
    public Double getAlt() { return alt; }
    public void setAlt(Double alt) { this.alt = alt; }
    public Double getBatteryPct() { return batteryPct; }
    public void setBatteryPct(Double batteryPct) { this.batteryPct = batteryPct; }
    public Integer getRssi() { return rssi; }
    public void setRssi(Integer rssi) { this.rssi = rssi; }
}
