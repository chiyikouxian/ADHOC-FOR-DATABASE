-- Apply this file to an existing PostgreSQL instance that was initialized
-- before the simulation workspace feature was added.

CREATE TABLE IF NOT EXISTS sim_scenarios (
    scenario_id          BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(64)  NOT NULL,
    description          TEXT,
    status               VARCHAR(16)  NOT NULL DEFAULT 'draft',
    drone_count          INTEGER      NOT NULL,
    publish_interval_ms  INTEGER      NOT NULL DEFAULT 1000,
    area_center_lat      DOUBLE PRECISION NOT NULL,
    area_center_lon      DOUBLE PRECISION NOT NULL,
    area_radius_m        INTEGER      NOT NULL DEFAULT 1000,
    battery_min          NUMERIC(5,2) NOT NULL DEFAULT 60,
    battery_max          NUMERIC(5,2) NOT NULL DEFAULT 100,
    rssi_min             INTEGER      NOT NULL DEFAULT -90,
    rssi_max             INTEGER      NOT NULL DEFAULT -40,
    alt_min              NUMERIC(8,2) NOT NULL DEFAULT 80,
    alt_max              NUMERIC(8,2) NOT NULL DEFAULT 200,
    topology_mode        VARCHAR(16)  NOT NULL DEFAULT 'chain',
    motion_mode          VARCHAR(24)  NOT NULL DEFAULT 'random-walk',
    created_by           BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
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

CREATE TABLE IF NOT EXISTS sim_scenario_drones (
    scenario_drone_id     BIGSERIAL PRIMARY KEY,
    scenario_id           BIGINT       NOT NULL REFERENCES sim_scenarios(scenario_id) ON DELETE CASCADE,
    drone_no              INTEGER      NOT NULL,
    model_id              INTEGER      NOT NULL DEFAULT 1,
    serial_no             VARCHAR(64),
    initial_lat           DOUBLE PRECISION,
    initial_lon           DOUBLE PRECISION,
    initial_alt           NUMERIC(8,2),
    initial_battery_pct   NUMERIC(5,2),
    initial_rssi          INTEGER,
    role_tag              VARCHAR(32),
    CONSTRAINT uq_sim_scenario_drones UNIQUE (scenario_id, drone_no),
    CONSTRAINT ck_sim_scenario_drones_no CHECK (drone_no > 0),
    CONSTRAINT ck_sim_scenario_drones_battery
        CHECK (initial_battery_pct IS NULL OR (initial_battery_pct >= 0 AND initial_battery_pct <= 100))
);

CREATE TABLE IF NOT EXISTS sim_scenario_links (
    scenario_link_id   BIGSERIAL PRIMARY KEY,
    scenario_id        BIGINT    NOT NULL REFERENCES sim_scenarios(scenario_id) ON DELETE CASCADE,
    src_drone_no       INTEGER   NOT NULL,
    dst_drone_no       INTEGER   NOT NULL,
    initial_quality    INTEGER   DEFAULT 80,
    is_enabled         BOOLEAN   NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_sim_scenario_links UNIQUE (scenario_id, src_drone_no, dst_drone_no),
    CONSTRAINT ck_sim_scenario_links_nodes
        CHECK (src_drone_no > 0 AND dst_drone_no >= 0 AND src_drone_no <> dst_drone_no),
    CONSTRAINT ck_sim_scenario_links_quality
        CHECK (initial_quality IS NULL OR (initial_quality >= 0 AND initial_quality <= 100))
);

CREATE INDEX IF NOT EXISTS idx_sim_scenarios_status ON sim_scenarios (status);
CREATE INDEX IF NOT EXISTS idx_sim_scenarios_updated_at ON sim_scenarios (updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_sim_scenarios_created_by ON sim_scenarios (created_by);
CREATE INDEX IF NOT EXISTS idx_sim_scenario_drones_scenario ON sim_scenario_drones (scenario_id);
CREATE INDEX IF NOT EXISTS idx_sim_scenario_links_scenario ON sim_scenario_links (scenario_id);

CREATE OR REPLACE FUNCTION fn_touch_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_touch_sim_scenarios ON sim_scenarios;

CREATE TRIGGER trg_touch_sim_scenarios
    BEFORE UPDATE ON sim_scenarios
    FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
