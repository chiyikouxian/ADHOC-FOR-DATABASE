# Change: Require authenticated access for platform operations

## Why
The application issues JWTs but currently exposes business reads and write operations without authentication. Development-default secrets also permit an accidental insecure startup.

## What Changes
- Require JWT authentication for platform reads and operator actions.
- Restrict simulation control, mission mutation, benchmark writes, and configuration writes to administrators.
- Keep only health checks, login, and telemetry ingestion publicly reachable.
- Require a non-default `JWT_SECRET` from the environment and document local environment setup.

## Impact
- Affected specs: `api-access-control` (new capability)
- Affected code: Spring Security configuration, JWT initialization, backend startup script, environment documentation, and API security tests
- **BREAKING**: callers must send a valid JWT for previously public business APIs
