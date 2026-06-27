package com.fanet.service;

import com.fanet.model.SimulationScenario;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SimulationBootstrapService implements ApplicationRunner {

    private final SimulationScenarioService scenarioService;
    private final SimulationRuntimeService runtimeService;

    public SimulationBootstrapService(SimulationScenarioService scenarioService,
                                      SimulationRuntimeService runtimeService) {
        this.scenarioService = scenarioService;
        this.runtimeService = runtimeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            SimulationScenario scenario = scenarioService.ensureDefaultScenario();
            if (!runtimeService.isRunning()) {
                runtimeService.start(scenario.getScenarioId(), java.util.Map.of());
            }
        } catch (Exception e) {
            System.err.println("[SimulationBootstrap] failed: " + e.getMessage());
        }
    }
}
