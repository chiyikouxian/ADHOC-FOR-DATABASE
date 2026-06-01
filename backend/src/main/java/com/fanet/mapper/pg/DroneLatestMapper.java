package com.fanet.mapper.pg;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface DroneLatestMapper {

    int upsert(@Param("droneId") Integer droneId,
               @Param("ts") Instant ts,
               @Param("lat") Double lat,
               @Param("lon") Double lon,
               @Param("alt") Double alt,
               @Param("batteryPct") Double batteryPct,
               @Param("rssi") Integer rssi);
}
