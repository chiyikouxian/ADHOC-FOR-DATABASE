package com.fanet.mapper.td;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TelemetryMapper {

    // ==================== 写入 ====================
    void insertOne(@Param("droneId") Integer droneId,
                   @Param("modelId") Integer modelId,
                   @Param("ts") Long ts,
                   @Param("lat") Double lat,
                   @Param("lon") Double lon,
                   @Param("alt") Double alt,
                   @Param("batteryPct") Double batteryPct,
                   @Param("rssi") Integer rssi);

    // ==================== 基础查询 ====================
    List<Map<String, Object>> queryLatestByDrone(@Param("droneId") Integer droneId,
                                                 @Param("limit") Integer limit);

    // ==================== 时间窗聚合 ====================

    /** 单机遥测降采样（TDengine INTERVAL 时间窗聚合） */
    List<Map<String, Object>> aggregateByDrone(@Param("droneId") Integer droneId,
                                               @Param("since") String since,
                                               @Param("window") String window);

    /** 全集群遥测聚合（PARTITION BY drone_id） */
    List<Map<String, Object>> aggregateAllDrones(@Param("since") String since,
                                                  @Param("window") String window);

    /** 单机电量原始时序（续航预测用） */
    List<Map<String, Object>> batterySeries(@Param("droneId") Integer droneId,
                                            @Param("since") String since);

    /** 单机信号原始时序（异常检测用） */
    List<Map<String, Object>> rssiSeries(@Param("droneId") Integer droneId,
                                         @Param("since") String since);

    // ==================== 链路快照 ====================

    /** 最新链路快照导出（供 PG 递归 CTE） */
    List<Map<String, Object>> latestLinkSnapshot(@Param("since") String since);

    /** 单条链路历史 */
    List<Map<String, Object>> linkHistory(@Param("srcDroneId") Integer srcDroneId,
                                          @Param("dstDroneId") Integer dstDroneId,
                                          @Param("since") String since,
                                          @Param("limit") Integer limit);
}
