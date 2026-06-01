package com.fanet.model;

import java.time.Instant;

public class TelemetryRecord {
    private Integer droneId;
    private Integer modelId;
    private Instant ts;
    private Double lat;
    private Double lon;
    private Double alt;
    private Double batteryPct;
    private Integer rssi;

    public Integer getDroneId() { return droneId; }
    public void setDroneId(Integer droneId) { this.droneId = droneId; }
    public Integer getModelId() { return modelId; }
    public void setModelId(Integer modelId) { this.modelId = modelId; }
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
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
