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

## Database profiles

H2 in PostgreSQL compatibility mode is the default for rapid local development. PostgreSQL is the intended production database. Flyway is the only schema owner; Hibernate validates rather than creates schema. PostgreSQL-specific locking and type behavior must be tested with Testcontainers before production claims are made.

## Observability

Actuator exposes health, readiness/liveness probes, application information, and Prometheus metrics. Console logs use structured JSON. Sensitive audit payloads must never be written to application logs.

## Security posture

Security will be added as a dedicated milestone once API behavior exists. The eventual design will authenticate callers, separate append/read/verify/export authorities, validate inputs, keep secrets externalized, and keep operational logs free of audit payload data.
