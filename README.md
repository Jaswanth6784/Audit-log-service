# Audit Log Service

Production-oriented prototype of an append-only, tamper-evident audit log service built with Java 21 and Spring Boot 4.1.0.

## Current milestone

Milestone 6 provides immutable event append, filtered sequence-ordered reads, complete hash-chain verification, configurable soft-archive retention, and commitment-based payload redaction. Verifiable export remains intentionally deferred to a later reviewed milestone.

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

## Query audit events

`GET /audit/events` accepts any combination of `actorId`, `resourceType`, `resourceId`, `eventType`, `from`, and `to`. String filters are exact matches. `from` is inclusive, `to` is exclusive, and both use ISO-8601 instants. Results are always ordered by the immutable `sequenceNumber`.

In Bruno, create a GET request to:

```text
http://localhost:8080/audit/events?actorId=user-123&resourceType=ACCOUNT&from=2026-08-15T00:00:00Z&to=2026-08-16T00:00:00Z&afterSequence=0&limit=2
```

Send the request, then copy `nextAfterSequence` from the response into the next request's `afterSequence`. Continue while `hasMore` is `true`. For incremental synchronization, persist that cursor and poll again later with the same filters. An empty page deliberately returns the input cursor unchanged.

Pagination rules:

- `afterSequence` defaults to `0` and must be non-negative.
- `limit` defaults to `50` and must be between `1` and `200`.
- The service reads one extra row to calculate `hasMore` without running a separate count query.
- Archived rows are excluded from normal queries so the contract remains compatible with the later retention milestone.

Useful Bruno validation checks are `limit=201`, `afterSequence=-1`, a blank string filter, and a `from` instant equal to or later than `to`; each must return HTTP 400.

## Verify the audit chain

Create a Bruno GET request to:

```text
http://localhost:8080/audit/verification
```

A clean chain returns HTTP 200 with `valid: true`, the number of verified events, and the current chain-head sequence. A broken chain also returns HTTP 200 because the verification operation completed successfully; inspect `valid`, `firstInvalidSequence`, `violationType`, and `detail` for the integrity result.

To demonstrate tamper detection locally, append at least two events and directly alter an assignment event field in the H2 console or database client—for example, change the second row's `actor_id`. The next verification should return `CONTENT_HASH_MISMATCH` at sequence 2. Direct database modification is deliberately used only as a verification test; the application exposes no update or delete audit-event API.

Violation types distinguish a missing chain head, sequence gap, unsupported hash version, predecessor-link mismatch, content-hash mismatch, record-hash mismatch, and chain-head sequence/hash mismatch.

## Apply retention

Retention uses server-controlled `recordedAt`, not caller-controlled event time. It marks expired records with `archivedAt`; it never physically deletes them, so complete chain verification remains possible. Normal event queries omit archived records.

Run one bounded batch manually in Bruno:

```text
POST http://localhost:8080/audit/retention/runs
```

The response reports `archivedCount` and `hasMoreEligibleEvents`. Repeat the request while that flag is true. This administrative endpoint is intentionally available for assignment demonstration and must be authorization-protected in the security milestone.

Default configuration:

```yaml
audit:
  retention:
    enabled: false
    max-age: 365d
    batch-size: 500
    fixed-delay: 1h
```

Set `audit.retention.enabled=true` to enable the scheduler. Each invocation processes at most one batch, preventing a large backlog from creating an unbounded database transaction. Environment variables such as `AUDIT_RETENTION_ENABLED`, `AUDIT_RETENTION_MAX_AGE`, `AUDIT_RETENTION_BATCH_SIZE`, and `AUDIT_RETENTION_FIXED_DELAY` can override these settings.

## Redact sensitive payload leaves

New events use hash version 2 and create a salted commitment for every JSON payload leaf. Redaction replaces selected values with commitment markers and destroys their 256-bit salts, while verification reconstructs the same committed payload tree. Hash-version-1 events remain verifiable but cannot be safely redacted and return HTTP 409.

Use the event UUID returned by the append request and RFC 6901 JSON Pointer paths:

```text
POST http://localhost:8080/audit/events/{eventId}/redactions
Content-Type: application/json
```

```json
{
  "actorId": "privacy-officer-1",
  "reason": "approved data-subject request",
  "paths": ["/customer/ssn", "/payment/cardNumber"]
}
```

Only leaf paths can be redacted. The operation uses a database lock so concurrent requests cannot lose changes. It also atomically appends an `AUDIT_PAYLOAD_REDACTED` receipt containing the target, paths, commitments, actor, and reason; the response returns that receipt's event ID. Until authentication is implemented, `actorId` is caller supplied and must not be treated as verified identity.

The original value and salt are not recoverable through the service after a successful commit. Back up and restore policies must account for privacy erasure requirements because an older database backup can still contain pre-redaction material.

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
