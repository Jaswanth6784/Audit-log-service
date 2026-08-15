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
