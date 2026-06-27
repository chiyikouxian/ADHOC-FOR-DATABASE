package com.fanet.mapper.pg;

import com.fanet.model.SimulationScenario;
import com.fanet.model.SimulationScenarioDrone;
import com.fanet.model.SimulationScenarioLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SimulationScenarioMapper {

    List<Map<String, Object>> listScenarios();

    SimulationScenario findScenarioByName(@Param("name") String name);

    SimulationScenario findScenarioById(@Param("scenarioId") Long scenarioId);

    List<SimulationScenarioDrone> findScenarioDronesByScenarioId(@Param("scenarioId") Long scenarioId);

    List<SimulationScenarioLink> findScenarioLinksByScenarioId(@Param("scenarioId") Long scenarioId);

    int insertScenario(SimulationScenario scenario);

    int updateScenario(SimulationScenario scenario);

    int deleteScenario(@Param("scenarioId") Long scenarioId);

    int deleteScenarioDrones(@Param("scenarioId") Long scenarioId);

    int deleteScenarioLinks(@Param("scenarioId") Long scenarioId);

    int insertScenarioDrone(SimulationScenarioDrone drone);

    int insertScenarioLink(SimulationScenarioLink link);
}
