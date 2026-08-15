# ADR 0006: Reuse the audit chain as compliance-report evidence

## Status

Accepted as the Scenario C design direction; runtime implementation remains pending approval.

## Context

Compliance reporting needs filtered evidence of client account data access. Creating a second reporting ledger would introduce synchronization, reconciliation, duplicate retention/redaction rules, and uncertainty about which store is authoritative. The existing audit service already provides ordered events, tamper evidence, retention-aware verification, and signed scoped exports.

## Decision

Represent account-data access as a controlled audit-event profile in the existing global chain. Produce compliance-specific, minimized projections and signed evidence from that source of truth. Do not create a separate compliance evidence table. Treat source-system event emission and delivery completeness as an explicit external trust boundary.

## Consequences

- Existing chain verification, pagination, redaction, retention, and export capabilities are reused.
- Corrections remain append-only and preserve historical evidence.
- Compliance reports cannot claim completeness beyond registered events received by the service.
- Authentication and role separation are prerequisites for trustworthy actor attribution and report access.
- Payload filtering performance may later require promoted columns or measured PostgreSQL JSON indexes.
- The global chain's serialization and O(n) full-proof cost remain scalability constraints.
