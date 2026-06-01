-- =============================================================
-- FANET 平台 — PostgreSQL 16 关系/事务核心 schema
-- 配套时序库见 schema_tdengine.sql；初始化顺序见 db/README.md
-- 约定：drone_id = 0 视为地面站（递归拓扑终点）
-- =============================================================

-- ---------- 1.1 基础实体：用户、型号、无人机 ----------

-- 操作员/管理员账号
CREATE TABLE users (
    user_id       BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,                 -- bcrypt，登录逻辑见 S2
    role          VARCHAR(16)  NOT NULL DEFAULT 'operator',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN ('admin', 'operator'))
);

-- 无人机型号字典（续航/速度等参数）
CREATE TABLE drone_models (
    model_id           SERIAL       PRIMARY KEY,
    model_name         VARCHAR(64)  NOT NULL UNIQUE,
    max_flight_minutes INT          NOT NULL,
    max_speed          NUMERIC(6,2) NOT NULL,            -- m/s
    CONSTRAINT ck_models_flight CHECK (max_flight_minutes > 0),
    CONSTRAINT ck_models_speed  CHECK (max_speed > 0)
);

-- 无人机实例（状态机字段）
CREATE TABLE drones (
    drone_id      INT          PRIMARY KEY,              -- 与 TDengine 标签同值；0 = 地面站
    model_id      INT          REFERENCES drone_models(model_id),
    serial_no     VARCHAR(64)  NOT NULL UNIQUE,
    status        VARCHAR(16)  NOT NULL DEFAULT 'idle',
    registered_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_drones_status
        CHECK (status IN ('idle', 'assigned', 'flying', 'offline', 'maintenance'))
);

-- ---------- 1.2 任务、分配桥表、航点 ----------

-- 任务（一次集群作业）
CREATE TABLE missions (
    mission_id    BIGSERIAL    PRIMARY KEY,
    creator_id    BIGINT       NOT NULL REFERENCES users(user_id),
    title         VARCHAR(128) NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'draft',
    planned_start TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_missions_status
        CHECK (status IN ('draft', 'scheduled', 'running', 'completed', 'aborted'))
);

-- 任务-无人机分配（多对多桥表，事务行锁的主战场）
CREATE TABLE mission_assignments (
    assign_id   BIGSERIAL   PRIMARY KEY,
    mission_id  BIGINT      NOT NULL REFERENCES missions(mission_id),
    drone_id    INT         NOT NULL REFERENCES drones(drone_id),
    status      VARCHAR(16) NOT NULL DEFAULT 'assigned',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_assign_status
        CHECK (status IN ('assigned', 'executing', 'done', 'failed', 'released')),
    -- 同一任务内一架机只占一行（防重复分配）
    CONSTRAINT uq_assign_mission_drone UNIQUE (mission_id, drone_id)
);

-- 航点路径（一个分配对应多个航点，按 seq 排序）
CREATE TABLE waypoints (
    wp_id     BIGSERIAL     PRIMARY KEY,
    assign_id BIGINT        NOT NULL REFERENCES mission_assignments(assign_id) ON DELETE CASCADE,
    seq       INT           NOT NULL,
    lat       NUMERIC(9,6)  NOT NULL,
    lon       NUMERIC(9,6)  NOT NULL,
    alt       NUMERIC(7,2)  NOT NULL,
    CONSTRAINT uq_waypoint_seq UNIQUE (assign_id, seq)
);

-- ---------- 1.3 告警、审计日志 ----------

-- 告警（异常检测产物，AI/规则写入）
CREATE TABLE alerts (
    alert_id   BIGSERIAL    PRIMARY KEY,
    drone_id   INT          NOT NULL REFERENCES drones(drone_id),
    alert_type VARCHAR(32)  NOT NULL,                    -- battery_drop / gps_drift / link_loss ...
    severity   VARCHAR(16)  NOT NULL DEFAULT 'warning',
    detail     TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved   BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_alerts_severity CHECK (severity IN ('info', 'warning', 'critical'))
);

-- 审计日志（触发器自动写入）
CREATE TABLE audit_log (
    log_id     BIGSERIAL    PRIMARY KEY,
    table_name VARCHAR(64)  NOT NULL,
    row_pk     VARCHAR(64)  NOT NULL,
    action     VARCHAR(16)  NOT NULL,                    -- INSERT / UPDATE / DELETE
    old_val    JSONB,
    new_val    JSONB,
    changed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- 1.4 跨库快照表（桥接 TDengine，避免分布式事务）----------

-- 每架机最新状态快照：接入服务写遥测时同步回写，供调度事务单库内取电量
CREATE TABLE drone_latest (
    drone_id    INT          PRIMARY KEY REFERENCES drones(drone_id),
    ts          TIMESTAMPTZ  NOT NULL,
    lat         NUMERIC(9,6),
    lon         NUMERIC(9,6),
    alt         NUMERIC(7,2),
    battery_pct NUMERIC(5,2),
    rssi        INT
);

-- 最近一窗活跃链路快照：拓扑分析前从 TDengine 导入，供 PG 递归 CTE
CREATE TABLE network_links_snapshot (
    src_drone_id INT     NOT NULL,
    dst_drone_id INT     NOT NULL,
    link_quality INT,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    snapshot_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (src_drone_id, dst_drone_id)
);

-- ---------- 1.5 索引（覆盖调度/告警/报表高频查询）----------

CREATE INDEX idx_drones_status        ON drones (status);
CREATE INDEX idx_assign_drone         ON mission_assignments (drone_id);
CREATE INDEX idx_assign_mission       ON mission_assignments (mission_id);
CREATE INDEX idx_assign_status        ON mission_assignments (status);
CREATE INDEX idx_missions_creator     ON missions (creator_id);
CREATE INDEX idx_missions_status      ON missions (status);
CREATE INDEX idx_alerts_drone_time    ON alerts (drone_id, created_at DESC);
CREATE INDEX idx_alerts_unresolved    ON alerts (resolved) WHERE resolved = FALSE;
CREATE INDEX idx_audit_table_time     ON audit_log (table_name, changed_at DESC);

-- ---------- 1.6 视图：每架机最新状态 ----------

-- 关系侧维度 + 最新遥测快照（drone_latest），供大屏与调度展示
CREATE VIEW v_drone_latest AS
SELECT d.drone_id,
       d.serial_no,
       d.status,
       dm.model_name,
       dm.max_flight_minutes,
       dl.ts          AS last_seen,
       dl.lat,
       dl.lon,
       dl.alt,
       dl.battery_pct,
       dl.rssi
FROM drones d
LEFT JOIN drone_models dm ON dm.model_id = d.model_id
LEFT JOIN drone_latest  dl ON dl.drone_id = d.drone_id;

-- ---------- 1.7 审计触发器（drones / missions 写 audit_log）----------

-- 通用审计函数：把行变更写入 audit_log（to_jsonb 捕获新旧值）
CREATE OR REPLACE FUNCTION fn_audit() RETURNS TRIGGER AS $$
DECLARE
    pk_text VARCHAR(64);
BEGIN
    IF TG_TABLE_NAME = 'drones' THEN
        pk_text := COALESCE(NEW.drone_id, OLD.drone_id)::text;
    ELSIF TG_TABLE_NAME = 'missions' THEN
        pk_text := COALESCE(NEW.mission_id, OLD.mission_id)::text;
    ELSE
        pk_text := '';
    END IF;

    INSERT INTO audit_log(table_name, row_pk, action, old_val, new_val)
    VALUES (
        TG_TABLE_NAME,
        pk_text,
        TG_OP,
        CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE to_jsonb(OLD) END,
        CASE WHEN TG_OP = 'DELETE' THEN NULL ELSE to_jsonb(NEW) END
    );
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_drones
    AFTER INSERT OR UPDATE OR DELETE ON drones
    FOR EACH ROW EXECUTE FUNCTION fn_audit();

CREATE TRIGGER trg_audit_missions
    AFTER INSERT OR UPDATE OR DELETE ON missions
    FOR EACH ROW EXECUTE FUNCTION fn_audit();

-- 告警去重：同一架机同类型的未解决告警只保留一条（部分唯一索引）
CREATE UNIQUE INDEX uq_alert_open
    ON alerts (drone_id, alert_type)
    WHERE resolved = FALSE;

-- =============================================================
-- schema_pg.sql 结束
-- =============================================================
