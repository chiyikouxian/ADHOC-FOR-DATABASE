package com.fanet.mapper.pg;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface MissionMapper {

    Map<String, Object> lockDroneForAssign(@Param("droneId") Integer droneId);

    int assignDrone(@Param("droneId") Integer droneId);

    int insertAssignment(@Param("missionId") Long missionId,
                         @Param("droneId") Integer droneId);

    java.util.List<Map<String, Object>> findRoute(@Param("srcDroneId") Integer srcDroneId);

    java.util.List<Map<String, Object>> missionRanking();
}
