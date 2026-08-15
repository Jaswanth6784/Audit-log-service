# Normalized Requirements

## Goal

Build a reviewable, production-oriented prototype that records append-only audit events, detects tampering through a hash chain, and demonstrates disciplined AI-assisted engineering with human approval.

## Scenario A - core service

- Append events containing event type, actor, resource, structured payload, and timestamp.
- Expose no update or delete API.
- Query by actor, resource, event type, and time range in any combination.
- Paginate stable results.
- Store tamper-evidence metadata linking every record to its predecessor.
- Verify the full chain and identify the first inconsistency and violation type.
- Demonstrate detection after direct database modification.

## Scenario B - extension

- Apply a configurable retention policy without false chain failures.
- Redact sensitive structured payload fields without invalidating tamper evidence.
- Export actor- or resource-scoped records as a self-contained verifiable bundle.

## Scenario C - ambiguous compliance reporting

- Clarify what constitutes client account data access.
- Identify actors, access actions, report consumers, scope, timeliness, retention, and evidence expectations.
- Document assumptions and scope boundaries before implementation.

## Cross-cutting constraints

- Java 21, Spring Boot 4.1.0, Maven, JPA, Flyway, Swagger, JUnit 5, and JaCoCo.
- H2 is the active development database for the current stage.
- PostgreSQL remains the production target and has an opt-in configuration.
- Maintain architecture rationale, risks, validation evidence, AI traceability, and an accurate candidate attestation.

## Initial assumptions requiring validation

- The service owns one globally ordered audit chain.
- Event occurrence time may be caller supplied; the server assigns it when absent and always records ingestion time.
- Soft archival satisfies the first retention implementation while preserving complete verification.
- External anchoring, KMS integration, and regulator identity integration are production extensions, not hidden assumptions.
