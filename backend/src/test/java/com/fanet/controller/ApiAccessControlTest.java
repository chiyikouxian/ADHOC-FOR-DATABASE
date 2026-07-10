package com.fanet.controller;

import com.fanet.config.SecurityConfig;
import com.fanet.security.JwtAuthenticationEntryPoint;
import com.fanet.security.JwtFilter;
import com.fanet.security.JwtUtil;
import com.fanet.service.SimulationRuntimeService;
import com.fanet.service.SimulationScenarioService;
import com.fanet.service.TelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HealthController.class, SimulationController.class, TelemetryController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=test-security-secret-that-is-longer-than-thirty-two-bytes",
        "jwt.expiration-ms=3600000"
})
class ApiAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private SimulationScenarioService scenarioService;

    @MockBean
    private SimulationRuntimeService runtimeService;

    @MockBean
    private TelemetryService telemetryService;

    @Test
    void allowsAnonymousHealthButRejectsAnonymousBusinessRead() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/simulation/scenarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsOperatorReadButRejectsOperatorSimulationControl() throws Exception {
        when(scenarioService.listScenarios()).thenReturn(List.of(Map.of("scenarioId", 1L, "name", "巡检")));

        mockMvc.perform(get("/api/simulation/scenarios").header("Authorization", bearer("operator", "operator")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/simulation/runtime/start")
                        .header("Authorization", bearer("operator", "operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdministratorSimulationControlAndTelemetryIngestion() throws Exception {
        when(runtimeService.start(any(), any())).thenReturn(Map.of("running", true, "scenarioId", 1L));

        mockMvc.perform(post("/api/simulation/runtime/start")
                        .header("Authorization", bearer("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));

        mockMvc.perform(post("/api/telemetry")
                        .header("Authorization", bearer("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"droneId\":1,\"modelId\":1,\"lat\":31.23,\"lon\":121.47,\"alt\":100,\"batteryPct\":90,\"rssi\":-60}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingested").value(1));

        verify(telemetryService).ingest(any());
    }

    private String bearer(String username, String role) {
        return "Bearer " + jwtUtil.generateToken(username, role);
    }
}
