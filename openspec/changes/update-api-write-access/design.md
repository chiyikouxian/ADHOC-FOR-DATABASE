## Context
The platform is a classroom project that runs locally, but its current JWT implementation does not protect business APIs. The change must preserve unauthenticated device telemetry ingestion while securing operator and administrator workflows.

## Decisions
- Anonymous access is limited to `POST /api/auth/login`, `GET /api/health`, and `POST /api/telemetry`.
- Authenticated operators can read platform state, use AI analysis, acknowledge alerts, and assign missions.
- Administrator-only access covers scenario CRUD/runtime control, mission reset, benchmark writes, explain-plan endpoints, and telemetry ingestion by the manual API.
- `JWT_SECRET` must be supplied through the environment and contain at least 32 bytes. The backend startup script loads a local `.env` file when present.

## Risks / Trade-offs
- Existing manual API calls without a token will receive `401` or `403`. The frontend already sends JWTs through its Axios interceptor.
- MQTT telemetry remains independent of HTTP authorization. The HTTP ingestion endpoint is administrator-only to avoid an anonymous write path.
