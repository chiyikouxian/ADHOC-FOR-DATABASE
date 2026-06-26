package com.fanet.service;

import com.fanet.mapper.td.TelemetryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 续航/剩余飞行时间预测服务。
 * 
 * 基于 TDengine 历史电量放电曲线做线性拟合，估算剩余飞行分钟数。
 * 可叠加大模型 API 做更复杂的趋势分析（多项式拟合、飞行模式识别）。
 */
@Service
public class EndurancePredictor {
    private static final DateTimeFormatter TD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TelemetryMapper telemetryMapper;
    private final Nl2SqlService nl2SqlService;

    public EndurancePredictor(TelemetryMapper telemetryMapper,
                              Nl2SqlService nl2SqlService) {
        this.telemetryMapper = telemetryMapper;
        this.nl2SqlService = nl2SqlService;
    }

    /**
     * 预测单架无人机的剩余飞行时间。
     *
     * @param droneId        无人机 ID
     * @param historyMinutes 取最近多少分钟的电量历史
     * @param useAI          是否使用大模型增强分析
     * @return 预测结果 Map
     */
    public Map<String, Object> predict(Integer droneId, int historyMinutes, boolean useAI) {
        // 1) 从 TDengine 取电量时序
        List<Map<String, Object>> batteryData = telemetryMapper.batterySeries(
                droneId, tdTimestampMinutes(historyMinutes));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("droneId", droneId);
        result.put("dataPoints", batteryData != null ? batteryData.size() : 0);

        if (batteryData == null || batteryData.size() < 3) {
            result.put("error", "电量数据不足（至少需要 3 个数据点），无法预测");
            result.put("estimatedMinutes", -1);
            return result;
        }

        // 2) 提取 (时间偏移秒, 电量百分比) 数据点
        double[] x = new double[batteryData.size()];
        double[] y = new double[batteryData.size()];

        long firstTs = 0;
        for (int i = 0; i < batteryData.size(); i++) {
            Map<String, Object> pt = batteryData.get(i);
            long ts = toTimestamp(pt.get("ts"));
            if (i == 0) firstTs = ts;
            x[i] = (ts - firstTs) / 1000.0 / 60.0; // 转为分钟偏移
            y[i] = toDouble(pt.get("battery_pct"));
        }

        // 3) 简单线性回归: y = slope * x + intercept
        double slope = linearSlope(x, y);
        double intercept = mean(y) - slope * mean(x);
        double r2 = rSquared(x, y, slope, intercept);

        // 4) 当前电量（最近一个数据点）
        double currentBattery = y[y.length - 1];
        double dischargeRate = -slope; // 放电速率（%每分钟）

        // 5) 估算剩余时间（分钟）：当前电量 / 放电速率
        // 安全阈值：电量低于 10% 视为必须降落
        double safeBattery = currentBattery - 10.0;
        double estimatedMinutes = dischargeRate > 0.0001
                ? safeBattery / dischargeRate
                : Double.POSITIVE_INFINITY;

        result.put("currentBatteryPct", Math.round(currentBattery * 10.0) / 10.0);
        result.put("dischargeRatePctPerMin", Math.round(dischargeRate * 10000.0) / 10000.0);
        result.put("estimatedMinutes", estimatedMinutes < 10000 ? Math.round(estimatedMinutes * 10.0) / 10.0 : -1);
        result.put("rSquared", Math.round(r2 * 1000.0) / 1000.0);
        result.put("confidence", r2 > 0.8 ? "high" : r2 > 0.5 ? "medium" : "low");

        // 警告
        if (currentBattery < 20) {
            result.put("warning", "电量低于 20%，建议立即返航");
        } else if (estimatedMinutes > 0 && estimatedMinutes < 10) {
            result.put("warning", "预计剩余飞行时间不足 10 分钟");
        }

        // 6) 可选：大模型增强分析
        if (useAI && nl2SqlService != null) {
            try {
                String aiPrompt = buildPredictionPrompt(droneId, currentBattery,
                        dischargeRate, estimatedMinutes, batteryData.size(), r2);
                String aiResponse = nl2SqlService.askRaw(aiPrompt);
                if (aiResponse != null && !aiResponse.isBlank()) {
                    result.put("aiAnalysis", aiResponse.trim());
                }
            } catch (Exception e) {
                result.put("aiError", "AI 分析调用失败: " + e.getMessage());
            }
        }

        return result;
    }

    // ==================== 统计工具 ====================

    private double mean(double[] arr) {
        double sum = 0;
        for (double v : arr) sum += v;
        return sum / arr.length;
    }

    private double linearSlope(double[] x, double[] y) {
        int n = x.length;
        double mx = mean(x), my = mean(y);
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            num += (x[i] - mx) * (y[i] - my);
            den += (x[i] - mx) * (x[i] - mx);
        }
        return den == 0 ? 0 : num / den;
    }

    private double rSquared(double[] x, double[] y, double slope, double intercept) {
        int n = x.length;
        double my = mean(y);
        double ssRes = 0, ssTot = 0;
        for (int i = 0; i < n; i++) {
            double pred = slope * x[i] + intercept;
            ssRes += (y[i] - pred) * (y[i] - pred);
            ssTot += (y[i] - my) * (y[i] - my);
        }
        return ssTot == 0 ? 0 : 1 - (ssRes / ssTot);
    }

    private String buildPredictionPrompt(int droneId, double currentBattery,
                                          double rate, double estMin, int pts, double r2) {
        return String.format(
            "你是无人机电池分析专家。基于以下数据给出简短续航评估（50字以内）：\n" +
            "无人机ID: %d, 当前电量: %.1f%%, 放电速率: %.4f%%/分钟, " +
            "预估剩余: %.1f分钟, 数据点: %d, R²: %.3f",
            droneId, currentBattery, rate, estMin, pts, r2);
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0;
    }

    private long toTimestamp(Object v) {
        if (v instanceof java.sql.Timestamp ts) return ts.getTime();
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return java.time.Instant.parse(s).toEpochMilli(); } catch (Exception ignored) {}
        }
        return System.currentTimeMillis();
    }

    private String tdTimestampMinutes(int minutesAgo) {
        return LocalDateTime.now().minusMinutes(minutesAgo).format(TD_FMT);
    }
}
