## ADDED Requirements
### Requirement: Platform APIs Require JWT Authentication
The system SHALL require a valid JWT for all platform APIs except login and health checks.

#### Scenario: Anonymous platform read is rejected
- **WHEN** an unauthenticated client requests a business API
- **THEN** the system SHALL return `401 Unauthorized`

### Requirement: Telemetry Ingestion Requires Administrator Authority
The system SHALL require the administrator role for HTTP telemetry ingestion and all operational write APIs.

#### Scenario: Operator attempts privileged write
- **WHEN** an operator sends a request to a simulation control or benchmark write endpoint
- **THEN** the system SHALL return `403 Forbidden`

#### Scenario: Administrator writes telemetry
- **WHEN** an administrator sends a valid telemetry ingestion request
- **THEN** the system SHALL accept the request for processing

### Requirement: JWT Secret Must Be Supplied Securely
The system SHALL reject startup when `JWT_SECRET` is absent or shorter than 32 bytes.

#### Scenario: Missing JWT secret
- **WHEN** the backend starts without a valid `JWT_SECRET`
- **THEN** startup SHALL fail with an actionable configuration error
