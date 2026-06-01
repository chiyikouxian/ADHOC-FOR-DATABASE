package com.fanet.mapper.td;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TelemetryMapper {

    void insertOne(@Param("droneId") Integer droneId,
                   @Param("modelId") Integer modelId,
                   @Param("ts") Long ts,
                   @Param("lat") Double lat,
                   @Param("lon") Double lon,
                   @Param("alt") Double alt,
                   @Param("batteryPct") Double batteryPct,
                   @Param("rssi") Integer rssi);

    List<Map<String, Object>> queryLatestByDrone(@Param("droneId") Integer droneId,
                                                 @Param("limit") Integer limit);
}
