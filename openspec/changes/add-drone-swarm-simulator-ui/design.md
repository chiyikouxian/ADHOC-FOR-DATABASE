## Context
The current simulator exists as `simulator/simulator-mqtt.js`, with parameters passed from the command line. It produces telemetry and link data but has no visual management interface, no persistent scene configuration, and no CRUD-oriented workflow. The project now needs a frontend-facing simulation page that feels like a product capability rather than a developer-only script.

This change should also strengthen the course-project narrative by adding visible Create/Read/Update/Delete interactions in a way that still fits the FANET domain.

## Goals / Non-Goals

- Goals:
  - Add a dedicated simulation page in the frontend
  - Support scenario CRUD with persistent storage
  - Support live simulator start/stop/apply controls from the platform
  - Visualize drones, links, runtime metrics, and parameter changes
  - Preserve the existing telemetry/topology data pipeline where possible

- Non-Goals:
  - Build a full physics engine
  - Replace the current telemetry/topology pages
  - Implement multi-user collaborative editing
  - Add a separate microservice just for simulation control

## Decisions

### Decision: Split persistent configuration from live runtime state
Persistent simulator data should be stored in PostgreSQL, while the live running state should be held in memory and optionally mirrored to Redis for fast polling/push.

Reason:
- Scenario definitions need CRUD and reportability
- Runtime state changes too frequently to belong entirely in relational storage

### Decision: Add a simulator control service in the backend
The backend should own scenario CRUD and expose runtime control APIs such as:
- create scenario
- update scenario
- delete scenario
- start simulation
- stop simulation
- apply live parameters

Reason:
- Keeps frontend simple
- Makes permissions and validation consistent
- Allows reuse of existing WebSocket / telemetry pipelines

### Decision: Introduce a UI-first simulation workspace
The new page should combine:
- scenario list
- scenario editor form
- live map/canvas view
- topology overlay
- parameter controls
- runtime telemetry summary

Reason:
- It becomes both a demonstration page and a CRUD page
- It reduces the need to jump between CLI, topology page, and telemetry page during demos

### Decision: Use CRUD on simulation scenarios rather than on raw telemetry
The new CRUD surface should target:
- simulation scenarios
- drone group templates
- link presets

Reason:
- Raw telemetry should remain append-oriented and not be edited/deleted casually
- Scenario management is a more natural domain object for course demonstration

## Data Model

### Persistent tables in PostgreSQL

#### `sim_scenarios`
Stores one reusable simulation scene definition.

Suggested fields:
- `scenario_id BIGSERIAL PRIMARY KEY`
- `name VARCHAR(64) NOT NULL UNIQUE`
- `description VARCHAR(255)`
- `status VARCHAR(16) NOT NULL DEFAULT 'draft'`
- `drone_count INT NOT NULL`
- `publish_interval_ms INT NOT NULL`
- `area_center_lat NUMERIC(9,6) NOT NULL`
- `area_center_lon NUMERIC(9,6) NOT NULL`
- `area_radius_m INT NOT NULL`
- `battery_min NUMERIC(5,2) NOT NULL`
- `battery_max NUMERIC(5,2) NOT NULL`
- `rssi_min INT NOT NULL`
- `rssi_max INT NOT NULL`
- `alt_min NUMERIC(7,2) NOT NULL`
- `alt_max NUMERIC(7,2) NOT NULL`
- `topology_mode VARCHAR(24) NOT NULL`
- `motion_mode VARCHAR(24) NOT NULL`
- `created_by BIGINT REFERENCES users(user_id)`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`

Recommended constraints:
- `status IN ('draft', 'ready', 'archived')`
- `topology_mode IN ('chain', 'star', 'mesh', 'custom')`
- `motion_mode IN ('random-walk', 'patrol', 'orbit', 'hover')`
- `drone_count > 0`
- `battery_min <= battery_max`
- `rssi_min <= rssi_max`

#### `sim_scenario_drones`
Stores optional per-drone overrides inside a scenario.

Suggested fields:
- `scenario_drone_id BIGSERIAL PRIMARY KEY`
- `scenario_id BIGINT NOT NULL REFERENCES sim_scenarios(scenario_id) ON DELETE CASCADE`
- `drone_no INT NOT NULL`
- `model_id INT REFERENCES drone_models(model_id)`
- `serial_no VARCHAR(64)`
- `initial_lat NUMERIC(9,6)`
- `initial_lon NUMERIC(9,6)`
- `initial_alt NUMERIC(7,2)`
- `initial_battery_pct NUMERIC(5,2)`
- `initial_rssi INT`
- `role_tag VARCHAR(24)`

Recommended constraints:
- unique `(scenario_id, drone_no)`

#### `sim_scenario_links`
Stores custom link topology edges for scenarios using `custom` topology mode.

Suggested fields:
- `scenario_link_id BIGSERIAL PRIMARY KEY`
- `scenario_id BIGINT NOT NULL REFERENCES sim_scenarios(scenario_id) ON DELETE CASCADE`
- `src_drone_no INT NOT NULL`
- `dst_drone_no INT NOT NULL`
- `initial_quality INT`
- `is_enabled BOOLEAN NOT NULL DEFAULT TRUE`

Recommended constraints:
- unique `(scenario_id, src_drone_no, dst_drone_no)`
- `src_drone_no <> dst_drone_no`

### Suggested PostgreSQL DDL draft

```sql
CREATE TABLE sim_scenarios (
    scenario_id         BIGSERIAL    PRIMARY KEY,
    name                VARCHAR(64)  NOT NULL UNIQUE,
    description         VARCHAR(255),
    status              VARCHAR(16)  NOT NULL DEFAULT 'draft',
    drone_count         INT          NOT NULL,
    publish_interval_ms INT          NOT NULL,
    area_center_lat     NUMERIC(9,6) NOT NULL,
    area_center_lon     NUMERIC(9,6) NOT NULL,
    area_radius_m       INT          NOT NULL,
    battery_min         NUMERIC(5,2) NOT NULL,
    battery_max         NUMERIC(5,2) NOT NULL,
    rssi_min            INT          NOT NULL,
    rssi_max            INT          NOT NULL,
    alt_min             NUMERIC(7,2) NOT NULL,
    alt_max             NUMERIC(7,2) NOT NULL,
    topology_mode       VARCHAR(24)  NOT NULL,
    motion_mode         VARCHAR(24)  NOT NULL,
    created_by          BIGINT       REFERENCES users(user_id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_sim_scenarios_status
        CHECK (status IN ('draft', 'ready', 'archived')),
    CONSTRAINT ck_sim_scenarios_topology
        CHECK (topology_mode IN ('chain', 'star', 'mesh', 'custom')),
    CONSTRAINT ck_sim_scenarios_motion
        CHECK (motion_mode IN ('random-walk', 'patrol', 'orbit', 'hover')),
    CONSTRAINT ck_sim_scenarios_drone_count
        CHECK (drone_count > 0),
    CONSTRAINT ck_sim_scenarios_publish_interval
        CHECK (publish_interval_ms >= 100),
    CONSTRAINT ck_sim_scenarios_radius
        CHECK (area_radius_m > 0),
    CONSTRAINT ck_sim_scenarios_battery_range
        CHECK (battery_min >= 0 AND battery_max <= 100 AND battery_min <= battery_max),
    CONSTRAINT ck_sim_scenarios_rssi_range
        CHECK (rssi_min <= rssi_max),
    CONSTRAINT ck_sim_scenarios_alt_range
        CHECK (alt_min >= 0 AND alt_min <= alt_max)
);

CREATE TABLE sim_scenario_drones (
    scenario_drone_id    BIGSERIAL    PRIMARY KEY,
    scenario_id          BIGINT       NOT NULL REFERENCES sim_scenarios(scenario_id) ON DELETE CASCADE,
    drone_no             INT          NOT NULL,
    model_id             INT          REFERENCES drone_models(model_id),
    serial_no            VARCHAR(64),
    initial_lat          NUMERIC(9,6),
    initial_lon          NUMERIC(9,6),
    initial_alt          NUMERIC(7,2),
    initial_battery_pct  NUMERIC(5,2),
    initial_rssi         INT,
    role_tag             VARCHAR(24),
    CONSTRAINT uq_sim_scenario_drones UNIQUE (scenario_id, drone_no),
    CONSTRAINT ck_sim_scenario_drones_no CHECK (drone_no > 0),
    CONSTRAINT ck_sim_scenario_drones_battery
        CHECK (initial_battery_pct IS NULL OR (initial_battery_pct >= 0 AND initial_battery_pct <= 100))
);

CREATE TABLE sim_scenario_links (
    scenario_link_id BIGSERIAL PRIMARY KEY,
    scenario_id      BIGINT    NOT NULL REFERENCES sim_scenarios(scenario_id) ON DELETE CASCADE,
    src_drone_no     INT       NOT NULL,
    dst_drone_no     INT       NOT NULL,
    initial_quality  INT,
    is_enabled       BOOLEAN   NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_sim_scenario_links UNIQUE (scenario_id, src_drone_no, dst_drone_no),
    CONSTRAINT ck_sim_scenario_links_nodes
        CHECK (src_drone_no > 0 AND dst_drone_no >= 0 AND src_drone_no <> dst_drone_no),
    CONSTRAINT ck_sim_scenario_links_quality
        CHECK (initial_quality IS NULL OR (initial_quality >= 0 AND initial_quality <= 100))
);

CREATE INDEX idx_sim_scenarios_status ON sim_scenarios (status);
CREATE INDEX idx_sim_scenarios_updated_at ON sim_scenarios (updated_at DESC);
CREATE INDEX idx_sim_scenarios_created_by ON sim_scenarios (created_by);
CREATE INDEX idx_sim_scenario_drones_scenario ON sim_scenario_drones (scenario_id);
CREATE INDEX idx_sim_scenario_links_scenario ON sim_scenario_links (scenario_id);
```

### Recommended trigger behavior

To keep editing metadata fresh, the implementation should add an `updated_at` maintenance trigger for `sim_scenarios`.

Suggested behavior:
- update `updated_at = now()` on every update
- optionally bump parent `sim_scenarios.updated_at` when child drone or link records change

This is recommended because the frontend scenario list will likely sort by latest modification time.

### Runtime-only state

The following should remain runtime-managed instead of fully persisted:
- current active session id
- current simulator process status
- current tick count
- live aggregate metrics
- latest runtime parameter overrides not yet saved as a scenario version

These can live in:
- backend in-memory state for first version
- optional Redis hash for quick status reads

### Recommended runtime Redis keys

These are optional but useful if the simulation workspace needs fast status polling or WebSocket fan-out:

- `sim:runtime:active`
  - active scenario/session summary
- `sim:runtime:metrics`
  - aggregate metrics such as tick count, avg battery, active links
- `sim:runtime:preview:nodes`
  - current drone preview payload
- `sim:runtime:preview:edges`
  - current link preview payload

## API Design

### Scenario CRUD APIs

#### `GET /api/simulation/scenarios`
Returns the scenario list for the workspace.

Response focus:
- `scenarioId`
- `name`
- `status`
- `droneCount`
- `topologyMode`
- `motionMode`
- `updatedAt`

Suggested response example:

```json
[
  {
    "scenarioId": 1,
    "name": "城区巡检-10机链式拓扑",
    "status": "ready",
    "droneCount": 10,
    "topologyMode": "chain",
    "motionMode": "random-walk",
    "publishIntervalMs": 1000,
    "updatedAt": "2026-06-27T08:00:00Z"
  }
]
```

#### `GET /api/simulation/scenarios/{scenarioId}`
Returns the full scenario definition including drone and link sub-records.

Suggested response example:

```json
{
  "scenarioId": 1,
  "name": "城区巡检-10机链式拓扑",
  "description": "用于演示链式中继与低速巡航",
  "status": "ready",
  "droneCount": 10,
  "publishIntervalMs": 1000,
  "areaCenterLat": 31.230000,
  "areaCenterLon": 121.470000,
  "areaRadiusM": 800,
  "batteryMin": 75.0,
  "batteryMax": 100.0,
  "rssiMin": -85,
  "rssiMax": -45,
  "altMin": 100.0,
  "altMax": 220.0,
  "topologyMode": "custom",
  "motionMode": "random-walk",
  "drones": [
    {
      "droneNo": 1,
      "modelId": 1,
      "serialNo": "SIM-0001",
      "initialLat": 31.230100,
      "initialLon": 121.470100,
      "initialAlt": 120.0,
      "initialBatteryPct": 95.0,
      "initialRssi": -60,
      "roleTag": "relay"
    }
  ],
  "links": [
    {
      "srcDroneNo": 1,
      "dstDroneNo": 0,
      "initialQuality": 88,
      "isEnabled": true
    }
  ],
  "createdAt": "2026-06-27T08:00:00Z",
  "updatedAt": "2026-06-27T08:10:00Z"
}
```

#### `POST /api/simulation/scenarios`
Creates a scenario.

Body should support:
- basic scenario metadata
- runtime defaults
- optional drone overrides
- optional custom links

Suggested request example:

```json
{
  "name": "山地搜救-6机星型拓扑",
  "description": "用于演示中心中继与高空覆盖",
  "status": "draft",
  "droneCount": 6,
  "publishIntervalMs": 1500,
  "areaCenterLat": 31.228000,
  "areaCenterLon": 121.468000,
  "areaRadiusM": 1200,
  "batteryMin": 70.0,
  "batteryMax": 100.0,
  "rssiMin": -90,
  "rssiMax": -50,
  "altMin": 120.0,
  "altMax": 260.0,
  "topologyMode": "star",
  "motionMode": "orbit",
  "drones": [],
  "links": []
}
```

Suggested response example:

```json
{
  "scenarioId": 2,
  "message": "scenario created"
}
```

#### `PUT /api/simulation/scenarios/{scenarioId}`
Updates an existing scenario.

Expected behavior:
- replace editable base fields
- upsert nested drone overrides
- replace or upsert custom links

Suggested request example:

```json
{
  "name": "山地搜救-6机自定义拓扑",
  "status": "ready",
  "publishIntervalMs": 1000,
  "topologyMode": "custom",
  "motionMode": "patrol",
  "drones": [
    {
      "droneNo": 1,
      "modelId": 2,
      "serialNo": "SIM-0001",
      "roleTag": "leader"
    }
  ],
  "links": [
    {
      "srcDroneNo": 1,
      "dstDroneNo": 0,
      "initialQuality": 92,
      "isEnabled": true
    },
    {
      "srcDroneNo": 2,
      "dstDroneNo": 1,
      "initialQuality": 84,
      "isEnabled": true
    }
  ]
}
```

Suggested response example:

```json
{
  "scenarioId": 2,
  "message": "scenario updated"
}
```

#### `DELETE /api/simulation/scenarios/{scenarioId}`
Deletes a stored scenario after confirmation rules pass.

Suggested response example:

```json
{
  "scenarioId": 2,
  "message": "scenario deleted"
}
```

### Runtime control APIs

#### `POST /api/simulation/runtime/start`
Starts a simulation session using a selected scenario.

Body:
- `scenarioId`
- optional temporary overrides such as `droneCount`, `publishIntervalMs`, `motionMode`

Suggested request example:

```json
{
  "scenarioId": 1,
  "overrides": {
    "droneCount": 8,
    "publishIntervalMs": 800,
    "motionMode": "orbit"
  }
}
```

Suggested response example:

```json
{
  "running": true,
  "scenarioId": 1,
  "startedAt": "2026-06-27T08:30:00Z",
  "message": "simulation started"
}
```

#### `POST /api/simulation/runtime/stop`
Stops the active simulation session.

Suggested response example:

```json
{
  "running": false,
  "message": "simulation stopped"
}
```

#### `POST /api/simulation/runtime/apply`
Applies permitted live parameter changes to the running simulation.

Body examples:
- `publishIntervalMs`
- `batteryDrainFactor`
- `rssiNoiseFactor`
- `motionMode`

Suggested request example:

```json
{
  "publishIntervalMs": 500,
  "batteryDrainFactor": 1.2,
  "rssiNoiseFactor": 0.8,
  "motionMode": "patrol"
}
```

Suggested response example:

```json
{
  "running": true,
  "message": "runtime parameters applied"
}
```

#### `GET /api/simulation/runtime/status`
Returns current runtime status.

Response focus:
- `running`
- `scenarioId`
- `startedAt`
- `droneCount`
- `publishIntervalMs`
- `tickCount`
- `avgBattery`
- `activeLinks`

Suggested response example:

```json
{
  "running": true,
  "scenarioId": 1,
  "scenarioName": "城区巡检-10机链式拓扑",
  "startedAt": "2026-06-27T08:30:00Z",
  "droneCount": 8,
  "publishIntervalMs": 800,
  "motionMode": "orbit",
  "tickCount": 146,
  "avgBattery": 86.4,
  "activeLinks": 6
}
```

### Visualization data APIs

#### `GET /api/simulation/runtime/preview`
Returns a workspace-focused live preview payload.

Response focus:
- drone nodes with current coordinates and battery
- link edges with current quality and state
- summary metrics

This endpoint can internally reuse existing telemetry/topology state if convenient.

Suggested response example:

```json
{
  "nodes": [
    {
      "droneNo": 1,
      "name": "SIM-0001",
      "lat": 31.230100,
      "lon": 121.470100,
      "alt": 120.0,
      "batteryPct": 93.2,
      "rssi": -61
    }
  ],
  "edges": [
    {
      "source": 1,
      "target": 0,
      "quality": 88,
      "active": true
    }
  ],
  "metrics": {
    "running": true,
    "droneCount": 8,
    "avgBattery": 86.4,
    "activeLinks": 6,
    "publishIntervalMs": 800
  }
}
```

## DTO Suggestions

### Backend request DTOs
- `SimulationScenarioCreateRequest`
- `SimulationScenarioUpdateRequest`
- `SimulationScenarioDroneRequest`
- `SimulationScenarioLinkRequest`
- `SimulationRuntimeStartRequest`
- `SimulationRuntimeApplyRequest`

### Backend response DTOs
- `SimulationScenarioSummaryResponse`
- `SimulationScenarioDetailResponse`
- `SimulationRuntimeStatusResponse`
- `SimulationRuntimePreviewResponse`
- `SimulationActionResponse`

## Validation Rules

### Scenario create/update validation
- `name` required, length `1-64`
- `droneCount` must be positive
- `publishIntervalMs` should be `>= 100`
- `batteryMin` and `batteryMax` must remain within `0-100`
- `altMin <= altMax`
- `rssiMin <= rssiMax`
- `custom` topology mode may require at least one link when `droneCount > 1`

### Runtime control validation
- only one simulation may run at a time in the first version
- `start` must fail if selected scenario does not exist
- `apply` must fail if no simulation is active
- unsupported live fields must be rejected explicitly

## UI Design

### Left panel: scenario management
- scenario list
- create button
- duplicate button
- delete button
- quick filter by status or topology mode

### Center panel: live scene visualization
- top-down map/canvas
- drone markers
- link overlays
- optional topology mini-mode switch

### Right panel: editing and runtime control
- scenario form editor
- start / stop controls
- parameter sliders and numeric inputs
- runtime summary cards

## Recommended implementation slices

### Slice 1: CRUD-first version
- tables
- scenario CRUD endpoints
- scenario page with list + form

Value:
- closes the “basic CRUD” gap quickly

### Slice 2: runtime control version
- runtime start/stop/apply endpoints
- simulator backend bridge

Value:
- turns the page into an actual simulation workspace

### Slice 3: polished visualization version
- richer visualization
- runtime metrics
- better scene editing affordances

Value:
- strengthens demo quality and presentation impact

## Risks / Trade-offs

- Runtime control over a Node.js simulator adds coordination complexity
  - Mitigation: begin with a single active simulation session model

- Frontend scope may grow too large if it tries to replace existing pages
  - Mitigation: position this page as a control and orchestration workspace, not a replacement dashboard

- CRUD can become fake if it is only cosmetic
  - Mitigation: persist scenario records in PostgreSQL and make them reusable across sessions

## Migration Plan

1. Add PostgreSQL tables for simulator scenarios and scenario drones/links
2. Add backend CRUD endpoints and runtime control endpoints
3. Extend the frontend with a new `/simulation` route and page
4. Refactor the simulator runtime so it can be controlled by backend-issued parameters
5. Add real-time runtime status updates

## Open Questions

- Whether scenario templates and live sessions should be separate tables from the first version
- Whether live control should use REST polling only or REST + WebSocket updates from day one
