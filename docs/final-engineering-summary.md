# Final Engineering Summary

## Outcome

The repository delivers a runnable, secured prototype for all three assignment scenarios. It records immutable audit evidence, links records through a versioned SHA-256 chain, identifies the first integrity failure, preserves verification across retention and redaction, and creates signed actor-, resource-, and compliance-scoped evidence bundles.

The implementation is production-oriented, not presented as production-complete. H2 is intentionally the active database per the development constraint. PostgreSQL, JWT, Docker, external signing keys, and operational telemetry are configured, while PostgreSQL Testcontainers validation, source-system reconciliation, production key custody, load testing, and regulator delivery remain explicit pre-production work.

## Delivery plan and execution

| Milestone | Requirement slice | Main design decision | Validation |
| --- | --- | --- | --- |
| 1 | Runnable foundation | Modular monolith, Flyway schema ownership, H2 default/PostgreSQL profile | Context, migration, chain-head, health/OpenAPI smoke |
| 2 | Append and hash chain | One locked global head; canonical JSON; separate content and record hashes | Golden vector, validation, concurrent writers |
| 3 | Filtered query and sync | Composable predicates and immutable sequence keyset pagination | Combined filters, half-open times, page traversal, empty polling |
| 4 | Chain verification | Pure verifier with explicit violation taxonomy; repeatable-read scan | Every violation branch and direct SQL tampering |
| 5 | Retention | Bounded soft archival on server ingestion time | Multiple batches, query exclusion, full-chain validity |
| 6 | Redaction | Version-2 salted leaf commitments and salt destruction | Pointer/proof cases, irreversible redaction, receipt, valid chain |
| 7 | Generic bulk export | Signed full/bridge proof through a captured chain head | Scope/privacy checks and layered tamper tests |
| 8 | Scenario C clarification | Normalize ambiguity before implementation; reuse the audit chain | Questions, assumptions, acceptance criteria, scope review |
| 9 | Security | Fail-closed, capability-specific authorities; Basic locally/JWT for PostgreSQL | Real-HTTP 401/403 and JWT claim mapping |
| 10 | Compliance ingestion | Controlled schema; authenticated actor/source; minimized payload | Spoofing rejection, taxonomy validation, chain validity |
| 11 | Compliance reporting | Bounded snapshot, minimized projection, audited access | Scope/time/filter/page/privacy tests |
| 12 | Compliance export | Separate criteria-bound signed manifest, shared proof verifier | Signature, criteria, scope, count, bridge, and receipt tampering tests |
| 13 | Assignment readiness | Traceability, clean-build audit, contract/security/doc consistency | Clean compile, full tests/coverage, link and repository review |

## Architecture and rationale

The service is a feature-oriented modular monolith. HTTP adapters validate trust-boundary contracts; application services own transactions and orchestration; domain services own canonicalization, commitments, hashing, and verification; JPA/Flyway own persistence. This keeps cryptographic behavior testable without distributing a prototype across services.

A single global chain gives unambiguous ordering and simple genesis-to-head verification. Every append locks the chain-head row, normalizes time to microseconds, commits the structured payload, hashes canonical content, inserts the immutable event, and advances the head atomically. The trade-off is serialized writes and O(n) complete verification/export; checkpoints, partitioning, or Merkle structures need measured scale evidence before adoption.

Occurrence time remains caller/source evidence. `recordedAt` is server-controlled and drives retention. Query pages use the global sequence as a durable cursor, avoiding offset drift and enabling incremental synchronization.

Redaction does not rewrite a plaintext hash. Hash version 2 commits each payload leaf with a random salt; approved redaction replaces the value with its commitment marker and removes its salt. An authenticated privacy administrator is recorded in an atomic receipt. Older version-1 records remain verifiable but cannot safely be redacted.

Exports include matching `FULL` records and unrelated hash-only `BRIDGE` records from genesis through the captured head. Ed25519 signs a canonical manifest. This proves signature trust, disclosed content, chain links, count, and head while minimizing unrelated disclosure. Because bridge metadata hides selection fields, the trusted signer attests selection completeness.

Scenario C reuses the same ledger rather than creating a second source of truth. Typed ingestion fixes the evidence envelope and derives actor/source identity from authentication. Reports require exactly one actor or account, a controlled purpose, and a finite half-open time window. Viewing and exporting reports creates privacy-minimized administrative receipts.

## Security and operations

- Every non-probe route is authenticated and authorized by capability; unmatched routes are denied.
- H2 uses known local Basic credentials only for Bruno demonstration. The PostgreSQL profile uses JWT signature, issuer, lifetime, audience, and explicit roles validation.
- Production database credentials and Ed25519 key material are externalized. The production profile fails fast without signing keys.
- Redaction and compliance attribution come from the authenticated principal rather than body-supplied identity.
- Structured JSON logging avoids audit payload logging. Actuator provides public health/readiness/liveness and protected Prometheus metrics.
- The application container uses a multi-stage Java 21 build and a non-root runtime user. Compose supplies PostgreSQL setup with a health check.
- CI runs the same Maven Enforcer, JUnit, packaging, and JaCoCo gates on every push and pull request.

## Quality evidence

The final gate is `./mvnw clean verify`. It compiles production and test sources from a clean checkout, executes JUnit 5 unit and real-HTTP integration tests, packages the executable JAR, and fails below 85% line or 75% branch coverage. The final copied-checkout run passed 40 tests with zero failures/errors/skips, 94.47% line coverage, and 76.32% branch coverage. The final audit also reviews generated OpenAPI paths, the absence of audit-event update/delete mappings, Markdown links, incremental commit authorship, repository privacy, container configuration, and submission files.

The clean-build review caught a missing model import left by the Milestone 12 verifier refactor. An incremental local build had reused compiled output, while GitHub Actions correctly failed from a clean checkout. The import was restored and clean compilation/full verification repeated. This is why clean CI is a required quality gate rather than optional duplication.

## Risks, trade-offs, and limitations

- Hash chaining proves integrity of records accepted by this service; it cannot prove a source emitted every required event. Production needs atomic capture/outbox, idempotent delivery, monitoring, reconciliation, and source coverage ownership.
- The global head serializes appends, and complete verification/export is O(n). Current synchronous scans and exports are bounded, but performance claims require workload testing.
- PostgreSQL is configured but not yet exercised with Testcontainers because H2-only execution was requested for this stage. PostgreSQL type, Flyway, isolation, and locking behavior must pass a dedicated suite before release.
- Soft archival is not legal deletion and the 365-day default is not a Compliance-approved schedule. Backup/replica erasure remains separate from active-database redaction.
- The H2 signing key and Basic passwords are public development fixtures. Production needs TLS, an enterprise IdP, secrets management or KMS/HSM signing, independent trust-root distribution, and key rotation.
- Compliance report candidate scanning is capped at 10,000. Promoted columns or PostgreSQL JSON indexes require measured query volume and execution plans.
- Docker configuration is supplied but was not executed locally because Docker was unavailable.
- Direct regulator access, scheduled submissions, jurisdiction-specific policy, delegation, source registration, and correlation-ID idempotency are deliberately scoped out.

## Human ownership and AI traceability

The engineer approved each milestone before implementation progression and separately before commit/push. [The AI usage log](ai-usage-log.md) records accepted, modified, and rejected proposals with rationale; the Git history preserves incremental authored changes. [The attestation](../ATTESTATION.md) remains the candidate's personal statement and must be checked by the candidate immediately before submission.

## Live defense focus

Be prepared to demonstrate: concurrent appends; keyset polling; a clean and then SQL-tampered chain; archived evidence remaining verifiable; commitment-based redaction; signed bundle tampering; report authorization and receipts; the boundary between integrity and source completeness; and why the current limits are safer than unbounded synchronous work.
