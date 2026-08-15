# Requirements and Traceability

## Goal and constraints

Build a reviewable, production-oriented prototype that records append-only audit events, detects tampering through a hash chain, and demonstrates disciplined AI-assisted engineering with human approval.

- Java 21, Spring Boot 4.1.0, Maven, JPA, Flyway, Swagger/OpenAPI, JUnit 5, and JaCoCo.
- H2 is the active development and test database; PostgreSQL is configured as the production target but is not claimed as tested yet.
- The repository must remain private, show incremental milestone history, and include an accurate attestation and AI-use trace.
- Every implemented capability must be tied to a requirement, automated validation, and an explicit production boundary.

## Scenario A - core service

| Requirement | Implementation evidence | Validation evidence |
| --- | --- | --- |
| Append event type, actor, resource, structured payload, and timestamp | `POST /audit/events`; `AppendAuditEventService`; Flyway-managed `audit_event` table | `AuditEventAppendIntegrationTest`; hash golden vectors; concurrent append test |
| Append-only API | Only POST and GET mappings exist for audit events; no update or delete endpoint | OpenAPI contract inspection and controller mapping review |
| Query by actor, resource, event type, and time range in any combination | `GET /audit/events`; JPA Specifications; inclusive `from` and exclusive `to` | `AuditEventQueryIntegrationTest` |
| Stable pagination and incremental synchronization | Immutable sequence keyset using `afterSequence`; bounded `limit`; `nextAfterSequence` | Multi-page and empty-poll query tests |
| Tamper-evident record linked to predecessor | Global chain head; canonical content hash; `previousHash`; versioned record hash | `AuditHashServiceTest`; concurrent append verification |
| Verify the full chain and identify the first inconsistency/type | `GET /audit/verify` and compatibility alias `/audit/verification` | `AuditChainVerifierTest`; `AuditVerificationIntegrationTest` |
| Detect direct database tampering | Verifier recomputes content, record, predecessor, sequence, and head evidence | Integration test changes stored event content with SQL and expects `CONTENT_HASH_MISMATCH` |

## Scenario B - extension

| Requirement | Implementation evidence | Validation evidence |
| --- | --- | --- |
| Configurable retention without false chain failures | Bounded soft archive, optional scheduler, manual run endpoint; verification includes archived events | `RetentionIntegrationTest`; scheduler and property tests |
| Structured redaction without breaking tamper evidence | Hash version 2 salted leaf commitments; RFC 6901 leaf selection; salt destruction; immutable receipt | `PayloadCommitmentServiceTest`; `RedactionIntegrationTest` |
| Bulk export by actor or resource | `GET /audit/exports` with exactly one actor or resource scope | `AuditExportIntegrationTest`; scope tests |
| Self-contained independent verification | Full/bridge global-chain proof, canonical manifest, Ed25519 signature, trusted key ID; `POST /audit/exports/verification` | Signature, scope, payload, link, count, kind, and head tampering tests |

## Scenario C - compliance reporting

The original product statement is intentionally ambiguous. Questions, provisional assumptions, normalized requirements, risks, and scope decisions are recorded in [Scenario C: Compliance Reporting](scenario-c-compliance-reporting.md).

| Clarified capability | Status | Evidence / boundary |
| --- | --- | --- |
| Controlled client-account access evidence | Implemented | `POST /compliance/access-events`; actor/source derived from authenticated identity; controlled taxonomies; no raw client values |
| Bounded, minimized report | Implemented | `GET /compliance/access-reports`; exact account-or-actor scope, mandatory purpose/time bounds, captured head, keyset pages, access receipt |
| Signed criteria-bound report export | Implemented | `GET /compliance/access-exports` and verification POST; full/bridge proof; exporter receipt |
| Authenticated authorization separation | Implemented | H2 Basic demonstration users; PostgreSQL JWT issuer/JWK/audience/roles; least-privilege authorities |
| Source-system emission completeness | Deferred | Requires source inventory, atomic capture/outbox, idempotency, delivery monitoring, and reconciliation outside this service |
| Direct regulator portal and scheduled delivery | Out of scope | Prototype supports authorized internal report production only |
| Jurisdiction-specific retention and identifier policy | Decision pending | Compliance, Legal, Privacy, and IAM approval required |

## Cross-cutting production evidence

| Concern | Evidence | Remaining boundary |
| --- | --- | --- |
| Schema ownership | Flyway migrations; Hibernate `validate` | PostgreSQL migration/locking behavior still needs Testcontainers validation |
| Security | Stateless fail-closed rules, strict authorities, JSON 401/403, authenticated administrative attribution, externalized production credentials/keys | TLS, IdP, KMS/HSM, database roles, and key rotation are deployment responsibilities |
| Logging and monitoring | Structured JSON logs; health/readiness/liveness; protected Prometheus endpoint | Alert rules and centralized telemetry backend are environment-specific |
| Quality | Maven Enforcer, JUnit 5, real-HTTP integration tests, JaCoCo 85% line/75% branch gates, GitHub Actions clean verification | Dedicated load testing and a PostgreSQL integration suite remain pre-production work |
| Containers | Multi-stage non-root application image; PostgreSQL Compose setup and health check | Docker was not available for local execution |
| Documentation and ownership | README runbook/Bruno guidance, architecture, ADRs, AI log, final summary, attestation, incremental commits | Candidate must personally verify attestation and repository access before submission |

## Accepted architectural assumptions

- One globally ordered chain is appropriate for the prototype and favors simple verification over horizontal append throughput.
- Caller occurrence time is distinct from server-controlled ingestion time; both are normalized to microseconds.
- Soft archival is the safe first retention behavior because physical deletion would break a genesis-to-head proof.
- Hash-only bridge records minimize unrelated disclosure, while their signed selection remains a trusted-signer attestation.
- External anchoring, production key custody, asynchronous export, source reconciliation, and regulator identity federation are explicit extensions rather than hidden claims.
