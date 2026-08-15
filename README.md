# Audit Log Service

Production-oriented prototype of an append-only, tamper-evident audit log service built with Java 21 and Spring Boot 4.1.0.

## Current milestone

Milestone 1 provides the application foundation only. The audit APIs and hash-chain behavior are intentionally deferred to later reviewed milestones.

## Prerequisites

- Java 21 or newer (the build emits Java 21 bytecode)
- Docker only when using the optional PostgreSQL profile

The Maven Wrapper is the supported build entry point, so a global Maven installation is not required.

## Run with H2 (default)

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Useful endpoints:

- Health: `http://localhost:8080/actuator/health`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## Append an audit event

`POST /audit/events` is the only audit-data mutation endpoint currently exposed. There are deliberately no update or delete operations.

```bash
curl --request POST http://localhost:8080/audit/events \
  --header "Content-Type: application/json" \
  --data '{
    "eventType": "RECORD_UPDATED",
    "actorId": "user-123",
    "resourceType": "ACCOUNT",
    "resourceId": "account-456",
    "payload": {"field": "mailingAddress"},
    "timestamp": "2026-08-15T10:15:30.123456Z"
  }'
```

`timestamp` is optional. When omitted, the server assigns the current UTC time. The service always adds a separate server-controlled `recordedAt` value. Both values are normalized to microsecond precision before hashing so H2 and PostgreSQL round-trips remain consistent.

## Optional PostgreSQL setup

PostgreSQL is configured but is not the default database during the current milestone.

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
```

Override credentials with `AUDIT_DB_URL`, `AUDIT_DB_USERNAME`, and `AUDIT_DB_PASSWORD`. Never use the compose development password in a shared or production environment.

## Quality gate

```bash
./mvnw clean verify
```

The build runs JUnit 5 tests and fails below 85% line coverage or 75% branch coverage. The HTML report is generated at `target/site/jacoco/index.html`.

## Documentation

- [Requirements](docs/requirements.md)
- [Architecture](docs/architecture.md)
- [AI usage log](docs/ai-usage-log.md)
- [Architecture decisions](docs/decisions/)
