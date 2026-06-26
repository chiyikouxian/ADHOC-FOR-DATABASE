-- PostgreSQL seed data for FANET

INSERT INTO drone_models (model_id, model_name, max_flight_minutes, max_speed) VALUES
    (1, 'Recon-X1', 35, 18.00),
    (2, 'Relay-R2', 50, 12.50),
    (3, 'Heavy-H3', 25, 22.00);

INSERT INTO drones (drone_id, model_id, serial_no, status) VALUES
    (0, NULL, 'GS-0000', 'idle'),
    (1, 1, 'SN-0001', 'idle'),
    (2, 1, 'SN-0002', 'flying'),
    (3, 2, 'SN-0003', 'flying'),
    (4, 2, 'SN-0004', 'assigned'),
    (5, 3, 'SN-0005', 'idle'),
    (6, 3, 'SN-0006', 'offline'),
    (7, 1, 'SN-0007', 'maintenance');

INSERT INTO users (username, password_hash, role) VALUES
    ('admin', '$2a$10$nu16Ys/pLFiKCKnniRSeJO9LOw1GKTP2q1rrUdPjoCxqxOdEIZJ4.', 'admin'),
    ('operator1', '$2a$10$nu16Ys/pLFiKCKnniRSeJO9LOw1GKTP2q1rrUdPjoCxqxOdEIZJ4.', 'operator'),
    ('operator2', '$2a$10$nu16Ys/pLFiKCKnniRSeJO9LOw1GKTP2q1rrUdPjoCxqxOdEIZJ4.', 'operator');

INSERT INTO missions (mission_id, creator_id, title, status, planned_start) VALUES
    (1, 1, convert_from(decode('e59f8ee58cbae8beb9e7958ce5b7a1e6a380', 'hex'), 'UTF8'), 'completed', now() - interval '3 day'),
    (2, 2, convert_from(decode('e5a49ce997b4e4bea6e5af9fe4bbbbe58aa1', 'hex'), 'UTF8'), 'running', now() - interval '2 hour'),
    (3, 2, convert_from(decode('e4b8ade7bba7e993bee8b7afe58e8be58a9be6b58be8af95', 'hex'), 'UTF8'), 'scheduled', now() + interval '1 day'),
    (4, 1, convert_from(decode('e5ba94e680a5e6909ce5afbbe6bc94e7bb83', 'hex'), 'UTF8'), 'draft', NULL);

INSERT INTO mission_assignments (mission_id, drone_id, status, assigned_at, completed_at) VALUES
    (1, 1, 'done', now() - interval '3 day', now() - interval '3 day' + interval '40 min'),
    (1, 5, 'done', now() - interval '3 day', now() - interval '3 day' + interval '38 min'),
    (2, 2, 'executing', now() - interval '2 hour', NULL),
    (2, 3, 'executing', now() - interval '2 hour', NULL),
    (3, 4, 'assigned', now() - interval '10 min', NULL);

INSERT INTO waypoints (assign_id, seq, lat, lon, alt) VALUES
    (1, 1, 31.230000, 121.470000, 120.0),
    (1, 2, 31.235000, 121.475000, 130.0),
    (1, 3, 31.240000, 121.470000, 125.0),
    (1, 4, 31.235000, 121.465000, 120.0),
    (3, 1, 31.200000, 121.440000, 150.0),
    (3, 2, 31.205000, 121.450000, 160.0);

INSERT INTO drone_latest (drone_id, ts, lat, lon, alt, battery_pct, rssi) VALUES
    (0, now(), 31.225000, 121.460000, 0.0, 100.0, -40),
    (1, now(), 31.230000, 121.470000, 120.5, 87.0, -62),
    (2, now(), 31.232000, 121.472000, 135.0, 64.0, -70),
    (3, now(), 31.205000, 121.450000, 160.0, 45.0, -78),
    (4, now(), 31.210000, 121.455000, 110.0, 92.0, -55),
    (5, now(), 31.228000, 121.468000, 0.0, 78.0, -60),
    (6, now() - interval '5 min', 31.240000, 121.480000, 0.0, 12.0, -95),
    (7, now() - interval '1 hour', 31.226000, 121.461000, 0.0, 100.0, -50);

INSERT INTO network_links_snapshot (src_drone_id, dst_drone_id, link_quality, is_active) VALUES
    (1, 0, 90, TRUE),
    (2, 1, 75, TRUE),
    (3, 1, 60, TRUE),
    (4, 3, 55, TRUE),
    (5, 0, 88, TRUE),
    (6, 2, 20, FALSE);

INSERT INTO alerts (drone_id, alert_type, severity, detail, resolved) VALUES
    (6, 'battery_drop', 'critical', convert_from(decode('e794b5e9878fe9998de887b3203132efbc85efbc8ce4bd8ee4ba8ee5ae89e585a8e99888e580bc', 'hex'), 'UTF8'), FALSE),
    (6, 'link_loss', 'critical', convert_from(decode('e4b88ee4b88ae6b8b8e88a82e782b9e993bee8b7afe5a4b1e6b4bb', 'hex'), 'UTF8'), FALSE),
    (3, 'battery_drop', 'warning', convert_from(decode('e794b5e9878f203435efbc85efbc8ce5bbbae8aeaee8bf94e888aa', 'hex'), 'UTF8'), FALSE),
    (2, 'gps_drift', 'info', convert_from(decode('47505320e8bdbbe5beaee6bc82e7a7bbefbc8ce5b7b2e887aae58aa8e6a0a1e6ada3', 'hex'), 'UTF8'), TRUE);
