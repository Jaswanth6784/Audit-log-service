# ADR 0002: Use One Global, Versioned Hash Chain

- Status: Accepted for Scenario A
- Date: 2026-08-15

## Context

The assignment describes every event as linking to the immediately preceding record. A global chain follows that wording directly but requires append serialization.

## Decision

Assign a monotonic sequence and store `content_hash`, `previous_hash`, and `record_hash`. The chain head will be updated in the same transaction as the event while protected by a database lock. Hash inputs will use a versioned canonical representation and SHA-256.

## Consequences

- Verification order and the first inconsistent record are unambiguous.
- Concurrent appends contend on a small critical section.
- Very high write-volume deployments may later require partitioned chains and signed aggregate checkpoints.
- A database administrator able to rewrite all events could recreate an internally consistent chain; external anchoring is required to protect against that threat.
