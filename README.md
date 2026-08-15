# Audit Log Service

Production-oriented prototype of an append-only, tamper-evident audit log service built with Java 21 and Spring Boot 4.1.0.

## Current milestone

Milestone 11 adds bounded internal compliance reports with account-or-actor scope, mandatory half-open time ranges, controlled optional filters, stable sequence cursors, minimized projections, captured chain boundaries, and privacy-minimized report-access receipts.

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
- Prometheus metrics (monitor authority required): `http://localhost:8080/actuator/prometheus`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## H2 development authentication

The H2 profile uses three in-memory HTTP Basic users. These credentials are public development fixtures and must never be used outside local H2 execution.

| Username | Password | Authorities |
| --- | --- | --- |
| `audit-admin` | `audit-admin-dev-only` | All audit authorities |
| `audit-writer` | `audit-writer-dev-only` | Append audit and compliance access events |
| `audit-reader` | `audit-reader-dev-only` | Query and verify only |

In Bruno, open the request's **Auth** tab, select **Basic Auth**, and enter the appropriate username and password. Override the passwords with `AUDIT_LOCAL_ADMIN_PASSWORD`, `AUDIT_LOCAL_WRITER_PASSWORD`, and `AUDIT_LOCAL_READER_PASSWORD` when needed.

The authorization matrix is:

| Operation | Required authority |
| --- | --- |
| Append events | `AUDIT_WRITER` |
| Append typed compliance access events | `COMPLIANCE_ACCESS_WRITE` |
| Read bounded compliance access reports | `COMPLIANCE_REPORT_READ` |
| Query events and view API documentation | `AUDIT_READER` |
| Verify chains or export bundles | `AUDIT_VERIFIER` |
| Create signed exports | `AUDIT_EXPORTER` |
| Redact payload leaves | `AUDIT_PRIVACY_ADMIN` |
| Execute retention | `AUDIT_RETENTION_ADMIN` |
| Read Prometheus metrics | `AUDIT_MONITOR` |

Health and application-info probes are public. All other unmatched routes fail closed. Missing or invalid credentials return HTTP 401; valid credentials without the required authority return HTTP 403.

## Append an audit event

`POST /audit/events` is the only audit-data mutation endpoint currently exposed. There are deliberately no update or delete operations.

```bash
curl --request POST http://localhost:8080/audit/events \
  --user audit-writer:audit-writer-dev-only \
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

## Record a compliance access event

In Bruno, create a `POST` request to `http://localhost:8080/compliance/access-events`, select **Basic Auth**, and use `audit-writer` / `audit-writer-dev-only`. Set `Content-Type: application/json` and use:

```json
{
  "accountId": "account-123",
  "action": "VIEW",
  "outcome": "ALLOWED",
  "dataCategories": ["BALANCES", "TRANSACTIONS"],
  "purposeCode": "CUSTOMER_SERVICE",
  "correlationId": "d1d109d1-7e82-45e8-95b8-0503416c0f38",
  "reasonCode": "POLICY_ALLOWED",
  "timestamp": "2026-08-15T10:15:30.123456Z"
}
```

A successful request returns HTTP 201. The response contains the chain sequence, event ID, normalized evidence, timestamps, hash version, and record hash. `timestamp` and `reasonCode` are optional. Duplicate data categories are removed and the remaining values are sorted before hashing.

Controlled values are:

- `action`: `VIEW`, `SEARCH`, `DOWNLOAD`, `EXPORT`, `PRINT`
- `outcome`: `ALLOWED`, `DENIED`
- `dataCategories`: `ACCOUNT_IDENTIFIERS`, `PROFILE`, `CONTACT`, `BALANCES`, `POSITIONS`, `TRANSACTIONS`
- `purposeCode`: `CUSTOMER_SERVICE`, `FRAUD_REVIEW`, `REGULATORY`, `OPERATIONS`, `SECURITY_INVESTIGATION`
- `reasonCode`: `POLICY_ALLOWED`, `ROLE_NOT_PERMITTED`, `PURPOSE_NOT_PERMITTED`, `CLIENT_RESTRICTION`, `AUTHORIZATION_FAILURE`

Do not send `actorId`, `sourceSystem`, or a generic `payload`: unknown fields return HTTP 400. In H2, the authenticated username becomes `actorId` and the trusted source is `LOCAL_H2_DEMO`. In the PostgreSQL JWT profile, `actorId` comes from the authenticated principal and `sourceSystem` from the required `client_id` claim.

Bruno checks worth demonstrating:

- no credentials returns 401;
- `audit-reader` credentials return 403;
- an unknown field such as `"actorId": "spoofed"` returns 400;
- an unsupported action such as `DELETE` returns 400;
- `GET /audit/verify` with reader credentials remains valid after ingestion.

`correlationId` is recorded but is not yet a database uniqueness key. A source retry can therefore create another valid event; production delivery needs an agreed idempotency key and source-scoped uniqueness policy.

## Query a compliance access report

In Bruno, create a GET request and use `audit-reader` / `audit-reader-dev-only` with Basic Auth. Exactly one of `accountId` or `actorId` is required, and both `from` and `to` are mandatory:

```text
http://localhost:8080/compliance/access-reports?accountId=account-123&from=2026-08-15T00:00:00Z&to=2026-08-16T00:00:00Z&reportPurpose=REGULATORY&afterSequence=0&limit=50
```

`from` is inclusive and `to` is exclusive. `reportPurpose` is mandatory and uses the same controlled purpose taxonomy as ingestion. Optional controlled filters are `action`, `outcome`, `sourceSystem`, and `dataCategory`. Pagination uses the immutable global sequence: send the response's `nextAfterSequence` in the next request while `hasMore` is `true`.

Each page captures `capturedThroughSequence` before querying, so later concurrent appends cannot drift into that page. The response exposes compliance fields and the minimum chain references needed to locate and verify evidence; it excludes the generic payload object, commitment proofs, salts, and content hash.

Every successful page appends a `COMPLIANCE_ACCESS_REPORT_VIEWED` receipt. The receipt records the authenticated consumer, criteria fingerprint, scope type, captured boundary, cursor, limit, returned sequence range, and page outcome. It deliberately excludes the raw account or actor scope and returned client metadata. The response's `accessReceiptEventId` links the page to this receipt.

Candidate scanning is capped at 10,000 events per page because optional compliance fields currently live in portable JSON payloads rather than speculative database-specific indexes. HTTP 413 asks the caller to narrow the time range. Production volume measurements can justify promoted columns or PostgreSQL JSON indexes later.

Useful Bruno checks:

- omit both scopes or supply both scopes: HTTP 400;
- omit either time bound or `reportPurpose`, or use `from >= to`: HTTP 400;
- use writer credentials: HTTP 403;
- set `to` equal to an event timestamp to demonstrate the exclusive upper bound;
- verify `/audit/verify` remains valid after report receipts are appended.

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

Create a Bruno GET request using the `audit-reader` credentials:

```text
http://localhost:8080/audit/verify
```

`/audit/verification` remains available as a compatibility alias.

A clean chain returns HTTP 200 with `valid: true`, the number of verified events, and the current chain-head sequence. A broken chain also returns HTTP 200 because the verification operation completed successfully; inspect `valid`, `firstInvalidSequence`, `violationType`, and `detail` for the integrity result.

To demonstrate tamper detection locally, append at least two events and directly alter an assignment event field in the H2 console or database client—for example, change the second row's `actor_id`. The next verification should return `CONTENT_HASH_MISMATCH` at sequence 2. Direct database modification is deliberately used only as a verification test; the application exposes no update or delete audit-event API.

Violation types distinguish a missing chain head, sequence gap, unsupported hash version, predecessor-link mismatch, content-hash mismatch, record-hash mismatch, and chain-head sequence/hash mismatch.

## Apply retention

Retention uses server-controlled `recordedAt`, not caller-controlled event time. It marks expired records with `archivedAt`; it never physically deletes them, so complete chain verification remains possible. Normal event queries omit archived records.

Run one bounded batch manually in Bruno using the `audit-admin` credentials:

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

Use the `audit-admin` credentials, the event UUID returned by the append request, and RFC 6901 JSON Pointer paths:

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

## Create and verify a scoped export

The export is scoped to exactly one actor or one resource. In Bruno, use the `audit-admin` credentials and create one of these GET requests:

```text
GET http://localhost:8080/audit/exports?actorId=user-123
GET http://localhost:8080/audit/exports?resourceType=ACCOUNT&resourceId=account-456
```

The response downloads a JSON bundle. `FULL` records contain matching event content and commitment proofs. `BRIDGE` records contain only sequence and hash metadata for unrelated events, allowing the recipient to reproduce the global chain without disclosing unrelated payloads. Archived matching records are included, and redacted values remain redacted.

To verify in Bruno, save the complete GET response body, create a second request, and paste it as the JSON body:

```text
POST http://localhost:8080/audit/exports/verification
Content-Type: application/json
```

A valid bundle returns `valid: true`. Change any disclosed value without changing the signature and it returns `SIGNATURE_INVALID`. The verifier also recomputes the scope, payload commitments, sequence links, record hashes, match count, and captured chain head after signature validation.

The H2 profile uses a public RFC 8032 test-vector key for local demonstration only. The PostgreSQL profile requires `AUDIT_EXPORT_KEY_ID`, `AUDIT_EXPORT_PRIVATE_KEY`, and `AUDIT_EXPORT_PUBLIC_KEY`; keys are Base64-encoded PKCS#8 private and X.509 public Ed25519 keys. In production, keep the private key in a secrets manager or KMS/HSM-backed signer, publish trusted public keys out of band, rotate using stable key IDs, and never reuse the checked-in development key.

Synchronous export is capped by `audit.export.max-chain-events` (10,000 by default) because a self-contained global-chain proof is O(n). A production extension should generate large bundles asynchronously, stream them to encrypted object storage, apply expiry/download authorization, and retain job/audit metadata.

## Optional PostgreSQL setup

PostgreSQL is configured but is not the default database during the current milestone.

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
```

Override credentials with `AUDIT_DB_URL`, `AUDIT_DB_USERNAME`, and `AUDIT_DB_PASSWORD`. Never use the compose development password in a shared or production environment.

The PostgreSQL profile also refuses to start without explicit export signing-key environment variables. This fail-fast behavior prevents an accidental production deployment from using the local demonstration identity.

PostgreSQL uses OAuth2 JWT bearer tokens instead of local users. Configure `AUDIT_SECURITY_ISSUER_URI`, `AUDIT_SECURITY_JWK_SET_URI`, and `AUDIT_SECURITY_AUDIENCE`. Tokens must pass signature, issuer, lifetime, and audience validation and provide a `roles` claim containing the exact authorities listed above. Supplying the JWK Set URI separately allows startup without authorization-server discovery while issuer validation remains enabled.

TLS termination is mandatory in a deployed environment. HTTP Basic is restricted to the H2 profile, and CSRF is disabled because the service is a stateless token-authenticated API; do not add cookie/session authentication without revisiting that decision.

## Quality gate

```bash
./mvnw clean verify
```

The build runs JUnit 5 tests and fails below 85% line coverage or 75% branch coverage. The HTML report is generated at `target/site/jacoco/index.html`.

## Security interview questions

- Why Basic locally and JWT for PostgreSQL? Basic keeps Bruno-based H2 demonstration self-contained, while JWT delegates production authentication, token lifecycle, and identity governance to an authorization server.
- Why validate issuer and audience? Signature validation establishes who signed a token; issuer and audience validation also establish who issued it and that it was intended for this service.
- Why separate authorities? Least privilege prevents a reader from appending evidence or an ordinary writer from executing privacy and retention administration.
- Why is health public but Prometheus protected? Orchestrators need probes without credentials, while metrics can reveal operational information useful to an attacker.
- Why 401 versus 403? HTTP 401 means usable authentication is absent; HTTP 403 means an authenticated principal lacks authority.
- Does authentication make every request `actorId` trustworthy? No. The generic append API may represent an upstream subject. The typed compliance endpoint is narrower: it derives its initiating actor from the authenticated principal and does not accept an actor override.
- Why use a separate typed compliance endpoint? It prevents sources from selecting the evidence type, resource type, identity, or arbitrary payload shape, making validation and data minimization enforceable at the trust boundary.
- Why derive `sourceSystem` from JWT `client_id`? A request body is attacker-controlled; a validated credential claim binds the emitting workload identity to the accepted evidence.
- Why normalize category ordering? Semantically equivalent requests should produce one deterministic payload representation before commitment and hashing.
- Does hash chaining prove every access was recorded? No. It proves integrity of accepted events. Completeness also requires atomic source capture, idempotent delivery, monitoring, and reconciliation.
- Why capture a chain boundary? It makes the page's evidence horizon explicit and prevents concurrent appends from changing the logical snapshot during pagination.
- Why audit report reads? Compliance metadata is sensitive; recording who viewed which criteria and result boundary provides oversight without duplicating returned data.
- Why fingerprint the raw scope in the receipt? It binds the receipt to known criteria while avoiding direct disclosure of an account or investigated actor in administrative evidence.
- Why cap the candidate scan? Portable in-memory filtering is acceptable only when bounded; the cap fails safely until measured volume justifies indexed promoted fields.
- Why is CSRF disabled? The production API accepts bearer tokens rather than browser cookies. The local Basic mode is development-only; introducing session/cookie authentication requires enabling an appropriate CSRF strategy.

## Documentation

- [Requirements](docs/requirements.md)
- [Architecture](docs/architecture.md)
- [Scenario C compliance reporting](docs/scenario-c-compliance-reporting.md)
- [AI usage log](docs/ai-usage-log.md)
- [Architecture decisions](docs/decisions/)
