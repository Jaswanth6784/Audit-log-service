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

Legacy hash version 1 stores:

- `content_hash = SHA-256(canonical event JSON)`
- `previous_hash = preceding record_hash`, or 64 zeroes for genesis
- `record_hash = SHA-256("audit-record-v1" + newline + previous_hash + newline + content_hash)`

Canonical event JSON contains only the assignment event fields: actor ID, event type, resource type, resource ID, payload, and event timestamp. Object keys are sorted recursively, array order is preserved, UTF-8 is used, and the exact algorithm is protected by golden-vector tests.

New appends use hash version 2. Each canonical payload leaf receives a random 256-bit salt and a domain-separated SHA-256 commitment. The content hash covers event metadata plus a tree of those commitments rather than plaintext leaf values. The stored proof map associates each RFC 6901 leaf path with its commitment and salt, and the record hash uses the `audit-record-v2` domain. Verification supports mixed version-1/version-2 chains.

## Query path

The read API composes optional exact-match and time-range predicates through JPA Specifications. It returns only non-archived events, ordered by the immutable global sequence. Keyset pagination uses `sequence_number > afterSequence` rather than database offsets, preventing duplicate or skipped positions when new events are appended between requests and allowing the same cursor to drive incremental synchronization.

Each query requests `limit + 1` rows. The extra row establishes `hasMore` without a potentially expensive `COUNT(*)`; only the requested page is returned. `nextAfterSequence` is the last returned sequence, or the input cursor for an empty page. Event time uses a half-open interval (`from` inclusive, `to` exclusive), which makes adjacent windows composable without overlap.

## Verification path

Verification reads the chain head and streams every event in sequence order inside a repeatable-read, read-only transaction. Unlike normal queries, the scan includes archived events because retention must not create an integrity blind spot. For each record it checks sequence continuity, supported hash version, the predecessor link, canonical content hash, and record hash, stopping at the first violation. Once all records pass, it compares the final sequence and hash with the stored chain head.

The pure domain verifier owns the violation taxonomy and has no JPA or HTTP dependencies. The application layer only supplies a consistent database snapshot and maps persistence entities into verifier inputs. Full verification is intentionally O(n); for a very large production chain, the same algorithm should run as a controlled background job with persisted progress and an externally anchored checkpoint rather than occupying a synchronous request for an unbounded duration.

## Retention path

Retention is a soft-archive state transition on operational metadata. Eligibility is based on server-controlled ingestion time (`recorded_at`) rather than caller-controlled event occurrence time. Each transaction selects at most `batch-size + 1` eligible sequence numbers, archives no more than `batch-size`, and uses the extra candidate to report whether backlog remains. The scheduler is opt-in and processes one bounded batch per invocation; the same operation is exposed for manual demonstration and future administrative orchestration.

Archived events disappear from ordinary query results but remain in the database and in complete verification scans. This preserves hash-chain evidence and avoids pretending that physical deletion is compatible with the current globally linked chain. Database privileges must prevent application users from changing `recorded_at` or `archived_at` directly; these operational fields are outside both hash versions' assignment-event content commitments.

## Redaction path

Redaction locks a version-2 event, validates requested leaf-only JSON Pointers against its proof map, replaces each value with its original commitment marker, and removes the corresponding salt. Unredacted leaves remain independently recomputable; redacted leaves reconstruct their original commitment without retaining disclosure material. Any changed value, proof, path structure, or marker is reported as `PAYLOAD_PROOF_MISMATCH` during chain verification.

The payload update and an `AUDIT_PAYLOAD_REDACTED` receipt append occur in one transaction. The receipt discloses no removed values and records the target event, paths, commitments, stated actor, and reason. Authentication will replace the caller-supplied actor with verified principal identity in the security milestone. Database backups, replicas, and logs require separate erasure governance because transactional redaction cannot remove historical copies outside the active database.

## Export path

An export accepts exactly one actor scope or one resource scope. Inside a repeatable-read transaction it captures the global chain head and streams every stored event, including soft-archived records. Matching rows become `FULL` records. Non-matching rows become `BRIDGE` records containing only sequence, hash version, content hash, previous hash, and record hash. This preserves global-chain continuity without exporting unrelated event identity or payload fields.

The service signs a recursively key-sorted canonical JSON manifest with Ed25519. The signature covers the bundle version and ID, generation time, declared scope, captured chain head, matching count, and every full/bridge record. Verification first checks the signature against the configured key ID and public key, then independently checks scope membership, payload commitments, content hashes, sequence continuity, predecessor links, record hashes, match count, and final head. The embedded public key is descriptive; trust must be established outside the bundle.

The current synchronous bundle contains a proof from genesis through the captured head and is therefore O(n) in the global chain size. A configurable limit fails with HTTP 413 before scanning oversized chains. Production-scale generation should be asynchronous and streamed to protected object storage; externally anchored checkpoints or a Merkle-based structure are future alternatives if proof size becomes a primary requirement.

## Compliance reporting design

Scenario C is clarified in [Scenario C: Compliance Reporting](scenario-c-compliance-reporting.md). The design models client-account data access as a controlled profile of the existing audit event rather than maintaining a second ledger. Compliance reports are minimized projections over that source of truth, scoped by actor or account and a bounded UTC interval, with signed evidence for external verification.

The integrity boundary is deliberate: the chain proves accepted records were not altered relative to the captured head. It cannot prove every source system emitted every required event. Production completeness requires registered-source inventory, atomic source capture, durable idempotent delivery, monitoring, and reconciliation. Authenticated principal attribution and role-separated report access are also prerequisites before the prototype can make compliance-grade claims.

## Database profiles

H2 in PostgreSQL compatibility mode is the default for rapid local development. PostgreSQL is the intended production database. Flyway is the only schema owner; Hibernate validates rather than creates schema. PostgreSQL-specific locking and type behavior must be tested with Testcontainers before production claims are made.

## Observability

Actuator exposes health, readiness/liveness probes, application information, and Prometheus metrics. Console logs use structured JSON. Sensitive audit payloads must never be written to application logs.

## Security posture

All routes are governed by one stateless, fail-closed authorization matrix. Health and application-info probes are public; append, read, verify, export, redaction, retention, documentation, and metrics operations require separate authorities. Security failures return Problem Details JSON with HTTP 401 for missing/invalid authentication and HTTP 403 for insufficient authority.

The H2 profile uses encoded in-memory HTTP Basic users with known development-only passwords. The PostgreSQL profile is an OAuth2 resource server: JWT signature, issuer, lifetime, and audience are validated using externally configured issuer/JWK metadata, and the explicit `roles` claim maps to audit authorities without an implicit prefix. Supplying both issuer and JWK Set locations avoids authorization-server discovery at startup while retaining issuer checks.

CSRF is disabled because the production API is stateless and bearer-token authenticated; cookie or session authentication would require a new CSRF decision. Deployed traffic requires TLS. Endpoint authentication does not by itself make the generic event's caller-supplied `actorId` an authenticated subject; the compliance ingestion design must bind source principal, initiating actor, and effective/delegated actor explicitly.

The H2 profile also contains a known public demonstration signing key. PostgreSQL requires external Ed25519 key material and fails fast when it is absent. Production should place signing behind a KMS/HSM or secrets-managed signer, distribute trust roots independently, support key rotation by key ID, and record export access without logging exported payloads.
