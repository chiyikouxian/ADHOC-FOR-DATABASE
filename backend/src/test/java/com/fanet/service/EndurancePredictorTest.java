package com.fanet.service;

import com.fanet.mapper.td.TelemetryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EndurancePredictorTest {

    @Mock
    private TelemetryMapper telemetryMapper;

    @Mock
    private Nl2SqlService nl2SqlService;

    @InjectMocks
    private EndurancePredictor predictor;

    @Test
    void returnsErrorWhenBatterySeriesHasFewerThanThreePoints() {
        when(telemetryMapper.batterySeries(anyInt(), anyString())).thenReturn(List.of(
                point("2026-06-27T00:00:00Z", 80.0),
                point("2026-06-27T00:01:00Z", 79.0)
        ));

        Map<String, Object> result = predictor.predict(1, 60, false);

        assertEquals(2, result.get("dataPoints"));
        assertEquals(-1, result.get("estimatedMinutes"));
        assertTrue(String.valueOf(result.get("error")).contains("至少需要 3 个数据点"));
        verify(nl2SqlService, never()).askRaw(anyString());
    }

    @Test
    void predictsEnduranceFromLinearBatterySeries() {
        when(telemetryMapper.batterySeries(anyInt(), anyString())).thenReturn(List.of(
                point("2026-06-27T00:00:00Z", 90.0),
                point("2026-06-27T00:10:00Z", 80.0),
                point("2026-06-27T00:20:00Z", 70.0),
                point("2026-06-27T00:30:00Z", 60.0)
        ));

        Map<String, Object> result = predictor.predict(1, 60, false);

        assertEquals(4, result.get("dataPoints"));
        assertEquals(60.0, result.get("currentBatteryPct"));
        assertEquals(1.0, result.get("dischargeRatePctPerMin"));
        assertEquals(50.0, result.get("estimatedMinutes"));
        assertEquals(1.0, result.get("rSquared"));
        assertEquals("high", result.get("confidence"));
    }

    @Test
    void returnsWarningWhenBatteryIsCriticallyLow() {
        when(telemetryMapper.batterySeries(anyInt(), anyString())).thenReturn(List.of(
                point("2026-06-27T00:00:00Z", 19.0),
                point("2026-06-27T00:05:00Z", 18.0),
                point("2026-06-27T00:10:00Z", 17.0)
        ));

        Map<String, Object> result = predictor.predict(2, 30, false);

        assertEquals(17.0, result.get("currentBatteryPct"));
        assertTrue(String.valueOf(result.get("warning")).contains("低于 20%"));
    }

    @Test
    void includesAiAnalysisWhenRequested() {
        when(telemetryMapper.batterySeries(anyInt(), anyString())).thenReturn(List.of(
                point("2026-06-27T00:00:00Z", 88.0),
                point("2026-06-27T00:10:00Z", 80.0),
                point("2026-06-27T00:20:00Z", 72.0)
        ));
        when(nl2SqlService.askRaw(anyString())).thenReturn("建议 40 分钟内返航");

        Map<String, Object> result = predictor.predict(3, 60, true);

        assertEquals("建议 40 分钟内返航", result.get("aiAnalysis"));
        assertFalse(result.containsKey("aiError"));
        verify(nl2SqlService).askRaw(anyString());
    }

    private Map<String, Object> point(String ts, double batteryPct) {
        return Map.of(
                "ts", Timestamp.from(Instant.parse(ts)),
                "battery_pct", batteryPct
        );
    }
}
