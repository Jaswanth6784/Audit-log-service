# Architecture Overview

The service authenticates every non-probe route, separates authorities by capability, validates inputs at typed trust boundaries, keeps secrets externalized, and keeps operational logs free of audit payload data.

## Style

The service is a modular monolith organized by feature. Each feature separates HTTP adapters, application orchestration, domain rules, and infrastructure. This keeps cryptographic rules testable without Spring or a database while avoiding premature distributed-system complexity.

## Feature modules

- `audit`: append, query, canonicalization, hash chaining, and verification.
- `retention`: configurable soft archival and scheduling.
- `redaction`: payload commitments and removal of disclosure material.
- `export`: self-contained signed export bundles.
- `compliance`: controlled access-event ingestion, bounded reporting, and signed criteria-bound export.
- `shared`: configuration, errors, security, and observability.

## Data flow

1. The API validates an append request.
2. The application layer obtains the chain head under a database lock.
3. The domain layer canonicalizes the event and calculates content and record hashes.
4. JPA persists the immutable event and advances the chain head atomically.
5. Query paths return immutable projections using keyset pagination.
6. Verification recomputes hashes in sequence order and stops at the first inconsistency.

## Compliance ingestion path

`POST /compliance/access-events` is a narrow adapter over the existing append application service. Security first requires `COMPLIANCE_ACCESS_WRITE`. The adapter derives the initiating actor from the authenticated principal and derives the emitting source from the JWT `client_id` claim; local H2 uses a fixed demonstration source. Neither value can be supplied in the request body.

The request accepts only enumerated action, outcome, data category, purpose, and reason values plus an opaque account ID, correlation UUID, and optional occurrence time. Unknown JSON properties fail deserialization. The application fixes `eventType` to `CLIENT_ACCOUNT_DATA_ACCESS` and `resourceType` to `CLIENT_ACCOUNT`, de-duplicates and sorts category codes, builds a minimized structured payload, then delegates to the same locked hash-chain append transaction used by generic events. This reuses integrity machinery without allowing a source to control the audit envelope or inject raw client data.

The current correlation ID is evidence, not an idempotency constraint. Production source integration must define retry semantics and source-scoped uniqueness before claiming exactly-once acceptance. Delegated/effective actor representation remains a separate reviewed change.

## Compliance reporting path

`GET /compliance/access-reports` requires `COMPLIANCE_REPORT_READ`, exactly one account-or-actor scope, a controlled report purpose, and a finite half-open occurrence-time range. Action, outcome, source, and data-category filters are controlled values. Base scope/time/type predicates execute in the database; portable payload filters execute while scanning bounded sequence-ordered candidates. A 10,000-candidate ceiling prevents an unbounded application scan until measured workloads justify promoted columns or database-specific JSON indexes.

The transaction uses repeatable-read isolation and captures the global chain-head sequence before querying. Every candidate predicate includes that upper boundary, making the page snapshot explicit. Results expose the controlled access fields, redaction state, event ID, sequence, predecessor hash, and record hash, but not generic payload maps, content hashes, commitment proofs, or salts.

After constructing the detached read snapshot, the same transaction appends a `COMPLIANCE_ACCESS_REPORT_VIEWED` receipt. Detaching is essential because Hibernate otherwise dirty-checks mutable JSON maps during the later write and can issue updates against read evidence. The receipt binds a domain-separated SHA-256 criteria fingerprint, controlled report purpose, scope type, consumer identity, captured boundary, request cursor/limit, returned range, and continuation outcome without copying the raw scope or returned metadata.

## Compliance export path

`GET /compliance/access-exports` requires `COMPLIANCE_REPORT_EXPORT` and the same bounded scope, purpose, time, and optional filters as a report. Under repeatable-read isolation it captures the chain head, maps every position through that head to either a content-bearing `FULL` match or privacy-preserving hash-only `BRIDGE`, and rejects chains above the configured synchronous export limit.

The compliance manifest is separate from the established version-1 generic audit manifest so canonical signed bytes remain backward compatible. Both formats reuse the same record representation, canonical serializer, Ed25519 signer/trust configuration, and extracted record-chain verifier. The compliance signature additionally covers a domain-separated criteria fingerprint, normalized criteria, bundle type/ID, generation time, captured head, matching count, and records.

After signing, queried entities are detached and the transaction appends a `COMPLIANCE_ACCESS_REPORT_EXPORTED` receipt. It identifies the authenticated exporter, bundle, criteria fingerprint, controlled purpose, signer key, count, and captured boundary without raw scope or client metadata. The receipt follows the captured boundary and is therefore linked by reference rather than included in its own export.

Verification can independently establish signer trust, criteria-fingerprint consistency, disclosed payload commitments, record hashes, sequence continuity, full-record criteria membership, declared count, and captured head. It cannot evaluate whether a hash-only bridge should have matched because the fields needed for selection are intentionally undisclosed; completeness of selection is an attestation by the trusted signer, while end-to-end event completeness remains dependent on source controls.

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

The payload update and an `AUDIT_PAYLOAD_REDACTED` receipt append occur in one transaction. The receipt discloses no removed values and records the target event, paths, commitments, authenticated privacy administrator, and reason. The API does not accept an actor override. Database backups, replicas, and logs require separate erasure governance because transactional redaction cannot remove historical copies outside the active database.

## Export path

An export accepts exactly one actor scope or one resource scope. Inside a repeatable-read transaction it captures the global chain head and streams every stored event, including soft-archived records. Matching rows become `FULL` records. Non-matching rows become `BRIDGE` records containing only sequence, hash version, content hash, previous hash, and record hash. This preserves global-chain continuity without exporting unrelated event identity or payload fields.

The service signs a recursively key-sorted canonical JSON manifest with Ed25519. The signature covers the bundle version and ID, generation time, declared scope, captured chain head, matching count, and every full/bridge record. Verification first checks the signature against the configured key ID and public key, then independently checks scope membership, payload commitments, content hashes, sequence continuity, predecessor links, record hashes, match count, and final head. The embedded public key is descriptive; trust must be established outside the bundle.

The current synchronous bundle contains a proof from genesis through the captured head and is therefore O(n) in the global chain size. A configurable limit fails with HTTP 413 before scanning oversized chains. Production-scale generation should be asynchronous and streamed to protected object storage; externally anchored checkpoints or a Merkle-based structure are future alternatives if proof size becomes a primary requirement.

## Compliance reporting design

Scenario C is clarified in [Scenario C: Compliance Reporting](scenario-c-compliance-reporting.md). The design models client-account data access as a controlled profile of the existing audit event rather than maintaining a second ledger. Compliance reports are minimized projections over that source of truth, scoped by actor or account and a bounded UTC interval, with signed evidence for external verification.

The integrity boundary is deliberate: the chain proves accepted records were not altered relative to the captured head. It cannot prove every source system emitted every required event. Production completeness requires registered-source inventory, atomic source capture, durable idempotent delivery, monitoring, and reconciliation. Authenticated principal attribution and role-separated report access are implemented; production identity governance and source-completeness controls remain prerequisites for compliance-grade claims.

## Database profiles

H2 in PostgreSQL compatibility mode is the default for rapid local development. PostgreSQL is the intended production database. Flyway is the only schema owner; Hibernate validates rather than creates schema. PostgreSQL-specific locking and type behavior must be tested with Testcontainers before production claims are made.

## Observability

Actuator exposes health, readiness/liveness probes, application information, and Prometheus metrics. Console logs use structured JSON. Sensitive audit payloads must never be written to application logs.

## Security posture

All routes are governed by one stateless, fail-closed authorization matrix. Health and application-info probes are public; append, read, verify, export, redaction, retention, documentation, and metrics operations require separate authorities. Security failures return Problem Details JSON with HTTP 401 for missing/invalid authentication and HTTP 403 for insufficient authority.

The H2 profile uses encoded in-memory HTTP Basic users with known development-only passwords. The PostgreSQL profile is an OAuth2 resource server: JWT signature, issuer, lifetime, and audience are validated using externally configured issuer/JWK metadata, and the explicit `roles` claim maps to audit authorities without an implicit prefix. Supplying both issuer and JWK Set locations avoids authorization-server discovery at startup while retaining issuer checks.

CSRF is disabled because the production API is stateless and bearer-token authenticated; cookie or session authentication would require a new CSRF decision. Deployed traffic requires TLS. Endpoint authentication does not by itself make the generic event's caller-supplied `actorId` an authenticated subject. Typed compliance ingestion binds initiating actor and source to authenticated credentials; effective/delegated actor representation remains deferred until IAM approves that model.

The H2 profile also contains a known public demonstration signing key. PostgreSQL requires external Ed25519 key material and fails fast when it is absent. Production should place signing behind a KMS/HSM or secrets-managed signer, distribute trust roots independently, support key rotation by key ID, and record export access without logging exported payloads.
