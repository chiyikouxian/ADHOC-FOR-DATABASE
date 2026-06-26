-- Repair mojibake mission and alert text in an existing PostgreSQL database.

UPDATE missions
SET title = CASE mission_id
    WHEN 1 THEN convert_from(decode('e59f8ee58cbae8beb9e7958ce5b7a1e6a380', 'hex'), 'UTF8')
    WHEN 2 THEN convert_from(decode('e5a49ce997b4e4bea6e5af9fe4bbbbe58aa1', 'hex'), 'UTF8')
    WHEN 3 THEN convert_from(decode('e4b8ade7bba7e993bee8b7afe58e8be58a9be6b58be8af95', 'hex'), 'UTF8')
    WHEN 4 THEN convert_from(decode('e5ba94e680a5e6909ce5afbbe6bc94e7bb83', 'hex'), 'UTF8')
    ELSE title
END
WHERE mission_id IN (1, 2, 3, 4);

UPDATE alerts
SET detail = CASE
    WHEN drone_id = 6 AND alert_type = 'battery_drop' AND severity = 'critical' THEN convert_from(decode('e794b5e9878fe9998de887b3203132efbc85efbc8ce4bd8ee4ba8ee5ae89e585a8e99888e580bc', 'hex'), 'UTF8')
    WHEN drone_id = 6 AND alert_type = 'link_loss' AND severity = 'critical' THEN convert_from(decode('e4b88ee4b88ae6b8b8e88a82e782b9e993bee8b7afe5a4b1e6b4bb', 'hex'), 'UTF8')
    WHEN drone_id = 3 AND alert_type = 'battery_drop' AND severity = 'warning' THEN convert_from(decode('e794b5e9878f203435efbc85efbc8ce5bbbae8aeaee8bf94e888aa', 'hex'), 'UTF8')
    WHEN drone_id = 2 AND alert_type = 'gps_drift' AND severity = 'info' THEN convert_from(decode('47505320e8bdbbe5beaee6bc82e7a7bbefbc8ce5b7b2e887aae58aa8e6a0a1e6ada3', 'hex'), 'UTF8')
    ELSE detail
END
WHERE (drone_id, alert_type, severity) IN (
    (6, 'battery_drop', 'critical'),
    (6, 'link_loss', 'critical'),
    (3, 'battery_drop', 'warning'),
    (2, 'gps_drift', 'info')
);
