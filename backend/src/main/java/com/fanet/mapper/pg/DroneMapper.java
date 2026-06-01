package com.fanet.mapper.pg;

import com.fanet.model.Drone;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DroneMapper {

    List<Drone> findAll();

    Drone findById(@Param("droneId") Integer droneId);

    List<Drone> findByStatus(@Param("status") String status);

    int updateStatus(@Param("droneId") Integer droneId, @Param("status") String status);
}
