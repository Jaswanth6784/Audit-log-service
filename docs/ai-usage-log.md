# AI Usage Log

This log records material AI assistance and the engineer's decision for traceability. The engineer remains responsible for validating every accepted change.

## 2026-08-15 - requirements and architecture

- Intent: Extract assignment requirements and propose a milestone-based architecture.
- AI contribution: Summarized Scenarios A-C; proposed a modular monolith, global hash chain, keyset pagination, soft archival, commitment-based redaction, and signed exports.
- Engineer decision: Accepted after review and explicitly approved Milestone 1.
- Modifications: H2 is the active database for now; PostgreSQL remains setup-only. A coverage gate and approval-before-commit workflow were added at the engineer's request.
- Validation: Requirements were checked against all four pages of the supplied assignment.

## 2026-08-15 - Milestone 1 foundation

- Intent: Scaffold the smallest runnable foundation without implementing audit APIs.
- AI contribution: Maven configuration, Spring profiles, Flyway baseline, OpenAPI metadata, Actuator configuration, structured logging, test and coverage setup, container setup, CI, and documentation skeleton.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Modification after validation: Replaced the direct Flyway Core dependency with Spring Boot 4.1's Flyway starter after a schema assertion showed that migrations were not auto-configured. Added direct checks for the migrated schema and genesis row.
- Validation: Maven verification passed with one integration smoke test, Flyway migration version 1 applied successfully, the coverage gate passed, the executable JAR started, and health/OpenAPI requests returned HTTP 200. Docker execution was not possible because Docker is not installed locally.

## 2026-08-15 - Milestone 2 append and hash chain

- Intent: Implement the smallest complete append path without beginning query or verification endpoints.
- AI contribution: Proposed and implemented recursive canonical JSON, SHA-256 content/record hashes, microsecond timestamp normalization, immutable JPA mappings, pessimistic chain-head locking, request validation, API documentation, and concurrent append tests.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Modifications after validation: Made `CHAR(64)` explicit in JPA after Hibernate schema validation correctly found a mismatch with Flyway's fixed-width hash columns. Replaced deprecated Jackson 3 `asText()` test calls with `asString()`.
- Validation: Six tests pass, including a golden hash vector, invalid-input HTTP response, caller/server timestamp paths, linked appends, and eight simultaneous writers. JaCoCo line and branch gates pass.

## 2026-08-15 - Milestone 3 query and incremental synchronization

- Intent: Implement assignment-required filtered reads and stable pagination without beginning chain verification or Scenario B work.
- AI contribution: Proposed and implemented composable JPA Specifications, half-open time filtering, sequence-based keyset pagination, reusable event responses, read-only transactions, request constraints, Problem Details responses, and Bruno test guidance.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Modifications after validation: Assigned the query integration test a dedicated in-memory H2 URL after Spring's test-context cache exposed cross-class fixture coupling. Added explicit handling for method-level `ConstraintViolationException` so invalid pagination parameters return HTTP 400 instead of 500.
- Validation: Eight tests pass, including combined/exact filters, time bounds, multi-page cursor traversal, empty incremental polling, and invalid query inputs. JaCoCo line and branch gates pass.

## 2026-08-15 - Milestone 4 chain verification

- Intent: Verify the complete global hash chain, identify the first inconsistency and violation type, and demonstrate detection after direct database modification.
- AI contribution: Proposed and implemented a persistence-independent verifier, explicit violation taxonomy, repeatable-read streaming service, verification HTTP contract, Swagger metadata, unit coverage for every violation branch, and an end-to-end SQL tampering test.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Design boundary: Verification includes archived rows and returns HTTP 200 for both valid and invalid integrity results because transport success is distinct from the reported verification outcome. Full-chain verification remains synchronous for this prototype; background checkpointed verification is documented as the production scaling path.
- Validation: Twelve tests pass. A three-event integration fixture verifies cleanly, then a direct SQL update to sequence 2 is reported as `CONTENT_HASH_MISMATCH` with one preceding event verified. JaCoCo line and branch gates pass.

## 2026-08-15 - Milestone 5 retention

- Intent: Apply a configurable retention policy without invalidating the global hash chain.
- AI contribution: Proposed and implemented ingestion-time eligibility, bounded soft-archive batches, an opt-in scheduler, a manual administrative endpoint, a portable retention index, structured operational logging, and an ADR documenting why physical deletion is deferred.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Design boundary: The scheduler handles one batch per invocation, ordinary queries exclude archived rows, and verification still scans them. The administrative endpoint is deliberately unsecured only until the planned security milestone.
- Validation: Retention integration coverage archives three eligible events across two bounded runs, confirms an empty repeat run is harmless, confirms archived rows disappear from reads, and verifies all four stored events still form a valid chain.

## 2026-08-15 - Milestone 6 payload redaction

- Intent: Remove selected sensitive structured payload values without invalidating tamper evidence.
- AI contribution: Proposed and implemented backward-compatible hash version 2, per-leaf salted commitments, RFC 6901 path handling, irreversible salt removal, proof-aware verification, pessimistic redaction locking, an atomic redaction receipt event, Flyway proof storage, API errors, tests, and an ADR.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Modifications after validation: Removed test-only constructor overloads after Spring correctly rejected ambiguous component construction. Added an atomic `AUDIT_PAYLOAD_REDACTED` receipt so successful privacy mutations carry target, commitment, actor, and reason evidence in the chain.
- Design boundary: Version-1 events remain verifiable but return HTTP 409 for redaction. Only exact leaf paths are supported. Caller-supplied actor identity remains untrusted until security is implemented, and historical backups require separate erasure controls.
- Validation: Unit tests cover nested objects, arrays, escaped JSON Pointer tokens, changed values, unknown paths, repeat attempts, mixed hash versions, and proof violations. End-to-end coverage confirms plaintext and salt removal, receipt creation, HTTP error behavior, and a valid chain after redaction.

## 2026-08-15 - Milestone 7 signed verifiable exports

- Intent: Produce actor- or resource-scoped evidence that can be verified without database access while avoiding disclosure of unrelated event content.
- AI contribution: Proposed and implemented repeatable-read bundle generation, full and hash-only bridge records, canonical manifests, Ed25519 signatures, configured trust roots, layered verification, bounded synchronous exports, HTTP/Swagger contracts, Bruno guidance, tests, and an ADR.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Design boundary: Bundles prove the complete global chain from genesis to the captured head and are therefore O(n). The embedded public key is not self-authenticating. The H2 key is intentionally public development material; PostgreSQL requires external key configuration.
- Validation: API coverage confirms scoped disclosure and bridge placement. Signed-tampering tests cover signatures, payload proofs, content, scope, sequence, predecessor links, record hashes, counts, kinds, and chain heads. The full Maven coverage gate passes.

## 2026-08-15 - Milestone 8 compliance-reporting clarification

- Intent: Clarify the intentionally ambiguous regulator-access statement before writing reporting code and define a defensible partial-implementation boundary.
- AI contribution: Rechecked the source assignment; proposed stakeholders, clarification questions, provisional assumptions, terminology, a normalized requirement, acceptance criteria, evidence schema, API flow, authorization roles, completeness boundary, risks, validation strategy, and an ADR.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Accepted direction: Reuse the existing audit chain as the evidence source instead of creating a second reporting ledger; distinguish integrity of accepted records from completeness of source-system emission.
- Scoped out: Runtime changes, authentication, source connectors, direct regulator access, scheduled delivery, jurisdiction-specific retention, and unmeasured database indexes.
- Validation: The documentation covers clarification, ambiguities, assumptions, normalized requirements, technical design, and implemented/deferred scope. Local Markdown targets resolve, Maven verification passes with 28 tests, and the existing JaCoCo gates remain above threshold.

## 2026-08-15 - Milestone 9 API security and contract alignment

- Intent: Protect audit capabilities with least-privilege authorities and expose the assignment-specified chain verification path before implementing typed compliance reporting.
- AI contribution: Verified current Spring Security guidance; proposed and implemented shared authorization rules, H2 Basic users, PostgreSQL JWT issuer/JWK/audience validation, explicit roles-claim mapping, stateless sessions, JSON 401/403 handling, protected metrics/documentation, Swagger schemes, `/audit/verify` compatibility, tests, Bruno guidance, and an ADR.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Modifications after validation: Replaced an unavailable Boot 3-era MockMvc auto-configuration import with real HTTP integration testing. Accounted for Spring Security 7's additional `FACTOR_BEARER` authority without weakening explicit role assertions. Permitted internal error dispatches so application failures retain their correct HTTP semantics.
- Design boundary: Local Basic credentials are public H2 fixtures. PostgreSQL relies on an external authorization server. Generic event `actorId` remains a reported subject rather than an authenticated-principal guarantee; typed compliance ingestion must resolve that distinction.
- Validation: Real HTTP tests cover public probes, 401, 403, writer/reader separation, verification aliases, export denial, and protected Prometheus access. A JWT unit test confirms exact roles-claim mapping, and the existing API tests now authenticate explicitly.

## 2026-08-15 - Milestone 10 controlled compliance access ingestion

- Intent: Accept minimized client-account-data access evidence through a controlled, authenticated boundary without implementing the later reporting slice.
- AI contribution: Proposed and implemented the typed request/response model, controlled taxonomies, authenticated actor and source resolution, dedicated authority, normalized payload construction, hash-chain reuse, strict unknown-field rejection, Swagger contract, Bruno guidance, tests, and an ADR.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Design boundary: Event and resource types are server fixed. The H2 source identity is a local fixture; PostgreSQL derives it from JWT `client_id`. Delegation, correlation-ID idempotency, source registration/reconciliation, bounded reports, report-view receipts, and compliance-specific export manifests remain deferred.
- Validation: Focused real-HTTP tests cover 401, 403, successful writer ingestion, identity derivation, controlled normalization, spoofed/unknown-field rejection, unsupported enum rejection, minimized persistence, and valid chain verification after append. The full Maven build passes with 33 tests, 94.88% line coverage, and 78.45% branch coverage.

## 2026-08-15 - Milestone 11 bounded compliance reporting

- Intent: Provide authorized internal compliance users with bounded, minimized access-evidence pages while auditing report access itself.
- AI contribution: Proposed and implemented exact account-or-actor scope, mandatory half-open time bounds, controlled filters, captured sequence boundaries, repeatable-read keyset queries, bounded portable payload scanning, minimized projections, privacy-preserving criteria fingerprints, atomic report-view receipts, authorization, Swagger/Bruno guidance, tests, and an ADR.
- Engineer decision: Reviewed and explicitly approved for commit and push on 2026-08-15.
- Modification after validation: SQL trace review found that managed JSON-backed report entities were dirty-checked when the receipt was appended. The read snapshot is now detached before the write, preventing update statements against immutable evidence.
- Design boundary: Candidate scans are capped at 10,000 until measured volume justifies promoted columns or PostgreSQL JSON indexes. Signed compliance manifests, asynchronous delivery, direct regulator access, and source-completeness controls remain deferred.
- Validation: Focused tests cover authorization, invalid scopes/ranges, mandatory purpose, account and actor reports, optional filters, half-open time behavior, keyset pagination, captured boundaries, minimized responses, receipt privacy, and valid chain verification after report access. The full Maven build passes with 36 tests, 94.58% line coverage, and 78.36% branch coverage.
