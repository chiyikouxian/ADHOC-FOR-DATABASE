-- FANET 平台 — TDengine 3.x 遥测时序 schema
-- 配套关系库见 schema_pg.sql；不设外键，drone_id 标签与 PG drones.drone_id 同值
-- 用法: docker exec -i fanet-tdengine taos -f /tmp/schema_tdengine.sql

CREATE DATABASE IF NOT EXISTS fanet DURATION 10 KEEP 365 PRECISION 'ms';

USE fanet;

CREATE STABLE IF NOT EXISTS telemetry (ts TIMESTAMP, lat DOUBLE, lon DOUBLE, alt FLOAT, battery_pct FLOAT, rssi INT) TAGS (drone_id INT, model_id INT);

CREATE STABLE IF NOT EXISTS network_links (ts TIMESTAMP, link_quality INT, is_active BOOL) TAGS (src_drone_id INT, dst_drone_id INT);

-- 写入示例（自动建子表）
INSERT INTO d_telemetry_1 USING telemetry TAGS (1, 2) VALUES (NOW, 31.230000, 121.470000, 120.5, 87.0, -62);

INSERT INTO d_telemetry_1 USING telemetry TAGS (1, 2) VALUES (NOW+1s, 31.230100, 121.470100, 121.0, 86.5, -63);

INSERT INTO l_link_1_0 USING network_links TAGS (1, 0) VALUES (NOW, 88, true);
