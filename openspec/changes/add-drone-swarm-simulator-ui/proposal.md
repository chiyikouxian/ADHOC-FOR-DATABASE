# Change: Add drone swarm simulator UI

## Why
The project already has a simulator script, but it is currently command-line driven and invisible to end users. This creates two gaps:

1. The simulator cannot be demonstrated as a first-class product feature because users cannot visually control or inspect the simulation state from the frontend.
2. The project’s basic CRUD coverage is still weak from a course-deliverable perspective, especially in user-facing create/update/delete operations.

Adding a dedicated "drone swarm simulator" page addresses both gaps. It turns the existing simulator into a visual scene editor and control panel, while also introducing a clear CRUD-oriented management surface for simulation scenarios and drone groups.

## What Changes
- Add a new frontend page for drone swarm simulation visualization and control
- Add scenario configuration CRUD for simulator scenes, drone groups, and link presets
- Add backend APIs for simulator scenario management and runtime control
- Add a runtime bridge so the simulator can accept parameter changes without requiring manual terminal edits
- Add real-time visualization for drone positions, link topology, and simulation metrics

## Impact
- Affected specs: `drone-swarm-simulator-ui`
- Affected code:
  - frontend routing, layout navigation, and new simulation view
  - backend controllers/services for scenario CRUD and runtime control
  - simulator runtime control layer
  - PostgreSQL schema for persistent simulator scenarios
- Risks:
  - Cross-cutting frontend/backend/simulator change
  - Need to separate "persistent scenario configuration" from "live runtime state"
