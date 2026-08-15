# ADR 0009: Use bounded compliance report snapshots with access receipts

## Status

Accepted for Milestone 11.

## Context

Internal compliance users need a purpose-built view of client-account-data access evidence. Reusing the generic audit query would disclose payload and proof details beyond the report need, allow unbounded criteria, and leave report access itself unaudited. Copying access events into a reporting ledger would introduce consistency and integrity ambiguity.

Controlled compliance attributes currently live in JSON so H2 and PostgreSQL share one schema. Database-specific JSON filtering or speculative promoted columns would be premature without volume and query-plan measurements.

## Decision

Expose `GET /compliance/access-reports` with a dedicated `COMPLIANCE_REPORT_READ` authority. Require exactly one account-or-actor scope, a controlled report purpose, inclusive `from`, exclusive `to`, and sequence-keyset pagination. Permit controlled action, outcome, source, and data-category filters.

Query only typed access events from the existing global chain. Capture the chain-head sequence and execute under repeatable-read isolation, applying the captured sequence as an upper bound. Run scope, type, time, archive, and sequence predicates in the database; evaluate JSON filters during a sequence-ordered scan capped at 10,000 candidates. Return a minimized projection without generic payload maps or commitment proofs.

Append a `COMPLIANCE_ACCESS_REPORT_VIEWED` event in the same transaction after detaching queried entities from the persistence context. The receipt stores the authenticated consumer, controlled report purpose, a domain-separated SHA-256 fingerprint of complete request criteria, scope type, captured boundary, pagination request, returned sequence range, and continuation outcome. It excludes the raw account/actor scope and returned access metadata.

## Consequences

- Every successful page is attributable and linked to immutable chain evidence.
- The captured boundary prevents concurrent writes from drifting into one logical page.
- The criteria fingerprint binds known criteria without placing the raw investigated scope in administrative receipts.
- Detaching the read snapshot prevents Hibernate JSON dirty checking from issuing update statements against immutable evidence when the receipt is appended.
- Portable payload filtering remains safe only within the scan cap; HTTP 413 requires narrower criteria.
- Large reports, signed compliance-specific manifests, asynchronous delivery, and measured indexing remain later decisions.
