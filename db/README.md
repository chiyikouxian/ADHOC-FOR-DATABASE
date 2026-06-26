# Database Notes

This project uses two databases:

- PostgreSQL 16 for relational and transactional data
- TDengine 3.x for telemetry time-series data

## Files

- `schema_pg.sql`: PostgreSQL schema, indexes, views, triggers
- `seed_pg.sql`: PostgreSQL seed data
- `schema_tdengine.sql`: TDengine super tables and sample setup

## Initialization

### PostgreSQL

When you start the stack with `docker compose up -d`, PostgreSQL loads:

- `db/schema_pg.sql`
- `db/seed_pg.sql`

on first boot automatically.

### TDengine

Run once after containers are up:

```powershell
docker exec -i fanet-tdengine taos -f /tmp/schema_tdengine.sql
```

Verify:

```powershell
docker exec -i fanet-tdengine taos -s "show fanet.stables;"
```

## Core PostgreSQL objects

- `users`
- `drone_models`
- `drones`
- `missions`
- `mission_assignments`
- `waypoints`
- `alerts`
- `audit_log`
- `drone_latest`
- `network_links_snapshot`
- `v_drone_latest`

## Core TDengine objects

- `telemetry`
- `network_links`

## Important API endpoints

### Relational / operational

- `POST /api/auth/login`
- `GET /api/drones`
- `GET /api/drones/{id}`
- `POST /api/missions/{id}/assign/{droneId}`
- `GET /api/missions`
- `GET /api/missions/ranking`
- `GET /api/missions/route/{droneId}`
- `GET /api/alerts`
- `POST /api/alerts/{alertId}/resolve`
- `GET /api/topology`
- `GET /api/topology/links`

### Telemetry / TDengine

- `POST /api/telemetry`
- `GET /api/telemetry/drone/{id}/series`
- `GET /api/telemetry/cluster/aggregate`
- `GET /api/telemetry/drone/{id}/battery`
- `GET /api/telemetry/drone/{id}/rssi`
- `GET /api/telemetry/links/snapshot`

### AI / analysis

- `POST /api/ai/ask`
- `POST /api/ai/endurance`
- `POST /api/ai/report`

### Explain endpoints

- `GET /api/explain/schedule`
- `GET /api/explain/recursive`
- `GET /api/explain/ranking`
- `GET /api/explain/drones`

## Query and benchmark scripts

```powershell
node simulator/load-test.js
node simulator/load-test.js --mqtt
node simulator/load-test.js --compare
node simulator/concurrency-test.js 10 1
node simulator/query-bench.js
```

## Latest benchmark snapshot

Environment used in the latest verification:

- Backend port: `18080`
- Compare mode: PostgreSQL-only path vs TDengine-only path
- Duration per stage: `10s`
- Stages: `10 -> 50 -> 100 -> 200 -> 500` drones

Observed compare results:

| Drones | PG TPS | TD TPS | TD/PG | PG P95 | TD P95 |
|---|---:|---:|---:|---:|---:|
| 10 | 126.3 | 160.7 | 127% | 10ms | 7ms |
| 50 | 173.9 | 175.1 | 101% | 7ms | 7ms |
| 100 | 143.8 | 169.1 | 118% | 9ms | 7ms |
| 200 | 103.5 | 151.7 | 147% | 12ms | 8ms |
| 500 | 52.5 | 106.4 | 203% | 24ms | 12ms |

Conclusion:

- TDengine-only ingestion outperformed PostgreSQL-only ingestion across most stages.
- The gap widened as load increased, especially at `500` drones.
- At `500` drones, TDengine reached `106.4 TPS` while PostgreSQL reached `52.5 TPS`.
- P95 latency at `500` drones was `12ms` for TDengine and `24ms` for PostgreSQL.
- These results support the project's split-storage design:
  PostgreSQL handles transactional and relational workloads, while TDengine is the better fit for high-frequency telemetry writes.

## Known data flow

1. Telemetry is written into TDengine
2. Latest drone snapshot is upserted into PostgreSQL `drone_latest`
3. Link snapshots are synced from TDengine into PostgreSQL `network_links_snapshot`
4. Frontend pages read:
   - PostgreSQL for mission, alert, topology, and dashboard views
   - TDengine-backed APIs for time-series and prediction inputs

## Current runtime defaults

- Backend port: `18080`
- Frontend proxy target: `http://127.0.0.1:18080`
- MQTT topic telemetry: `fanet/telemetry`
- MQTT topic links: `fanet/network_links`
