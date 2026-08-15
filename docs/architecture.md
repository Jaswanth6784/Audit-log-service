# Architecture Overview
Security will be added as a dedicated milestone once API behavior exists. The eventual design will authenticate callers, separate append/read/verify/export authorities, validate inputs, keep secrets externalized, and keep operational logs free of audit payload data.
## Style

The service is a modular monolith organized by feature. Each feature separates HTTP adapters, application orchestration, domain rules, and infrastructure. This keeps cryptographic rules testable without Spring or a database while avoiding premature distributed-system complexity.

## Planned modules

- `audit`: append, query, canonicalization, hash chaining, and verification.
- `retention`: configurable soft archival and scheduling.
- `redaction`: payload commitments and removal of disclosure material.
- `export`: self-contained signed export bundles.
- `reporting`: the clarified compliance-reporting slice.
- `shared`: configuration, errors, security, and observability.

## Data flow

1. The API validates an append request.
2. The application layer obtains the chain head under a database lock.
3. The domain layer canonicalizes the event and calculates content and record hashes.
4. JPA persists the immutable event and advances the chain head atomically.
5. Query paths return immutable projections using keyset pagination.
6. Verification recomputes hashes in sequence order and stops at the first inconsistency.

## Append transaction

The service maintains one global chain-head row. Each append transaction obtains a pessimistic write lock on that row, normalizes timestamps to microseconds, canonicalizes the payload, calculates the hashes, inserts the immutable event, and advances the head. An optimistic version column provides an additional stale-write guard. Any failure rolls back both changes.

Hash version 1 stores:

- `content_hash = SHA-256(canonical event JSON)`
- `previous_hash = preceding record_hash`, or 64 zeroes for genesis
- `record_hash = SHA-256("audit-record-v1" + newline + previous_hash + newline + content_hash)`

Canonical event JSON contains only the assignment event fields: actor ID, event type, resource type, resource ID, payload, and event timestamp. Object keys are sorted recursively, array order is preserved, UTF-8 is used, and the exact algorithm is protected by golden-vector tests.

## Query path

The read API composes optional exact-match and time-range predicates through JPA Specifications. It returns only non-archived events, ordered by the immutable global sequence. Keyset pagination uses `sequence_number > afterSequence` rather than database offsets, preventing duplicate or skipped positions when new events are appended between requests and allowing the same cursor to drive incremental synchronization.

Each query requests `limit + 1` rows. The extra row establishes `hasMore` without a potentially expensive `COUNT(*)`; only the requested page is returned. `nextAfterSequence` is the last returned sequence, or the input cursor for an empty page. Event time uses a half-open interval (`from` inclusive, `to` exclusive), which makes adjacent windows composable without overlap.

## Database profiles

H2 in PostgreSQL compatibility mode is the default for rapid local development. PostgreSQL is the intended production database. Flyway is the only schema owner; Hibernate validates rather than creates schema. PostgreSQL-specific locking and type behavior must be tested with Testcontainers before production claims are made.

## Observability

Actuator exposes health, readiness/liveness probes, application information, and Prometheus metrics. Console logs use structured JSON. Sensitive audit payloads must never be written to application logs.

## Security posture

Security will be added as a dedicated milestone once API behavior exists. The eventual design will authenticate callers, separate append/read/verify/export authorities, validate inputs, keep secrets externalized, and keep operational logs free of audit payload data.
