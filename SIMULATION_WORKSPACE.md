# Simulation Workspace

## What was added

- Frontend route: `/simulation`
- Scenario CRUD
- Inline drone child editing
- Inline link child editing
- Runtime controls: start, stop, apply, status, preview
- Graph visualization for both draft mode and live runtime mode

## Why this matters

- It fills the course requirement for visible create/read/update/delete operations
- It turns the previous command-line simulator into a demo-friendly visual workspace
- It gives the project a stronger "digital twin / controllable simulation" story

## One-time database migration

If PostgreSQL was created before this feature existed, apply:

```powershell
psql -h 127.0.0.1 -U fanet_app -d fanet -f db/migrate_simulation_scenarios.sql
```

If you prefer the Docker container:

```powershell
Get-Content db\migrate_simulation_scenarios.sql | docker exec -i fanet-postgres psql -U fanet_app -d fanet
```

## Smoke test sequence

1. Start middleware with `docker compose up -d`
2. Apply `db/migrate_simulation_scenarios.sql` if this is an existing database
3. Start backend on `18080`
4. Start frontend and open `/simulation`
5. Create a scenario, add drones and links, then save
6. Start runtime and confirm the graph changes from draft preview to live preview
