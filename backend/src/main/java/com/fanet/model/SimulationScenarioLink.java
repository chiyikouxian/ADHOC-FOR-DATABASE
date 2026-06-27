package com.fanet.model;

public class SimulationScenarioLink {

    private Long scenarioLinkId;
    private Long scenarioId;
    private Integer srcDroneNo;
    private Integer dstDroneNo;
    private Integer initialQuality;
    private Boolean isEnabled;

    public Long getScenarioLinkId() {
        return scenarioLinkId;
    }

    public void setScenarioLinkId(Long scenarioLinkId) {
        this.scenarioLinkId = scenarioLinkId;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(Long scenarioId) {
        this.scenarioId = scenarioId;
    }

    public Integer getSrcDroneNo() {
        return srcDroneNo;
    }

    public void setSrcDroneNo(Integer srcDroneNo) {
        this.srcDroneNo = srcDroneNo;
    }

    public Integer getDstDroneNo() {
        return dstDroneNo;
    }

    public void setDstDroneNo(Integer dstDroneNo) {
        this.dstDroneNo = dstDroneNo;
    }

    public Integer getInitialQuality() {
        return initialQuality;
    }

    public void setInitialQuality(Integer initialQuality) {
        this.initialQuality = initialQuality;
    }

    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Boolean enabled) {
        isEnabled = enabled;
    }
}
