# ADR 0005: Use signed scoped exports with hash-only bridge records

## Status

Accepted for Milestone 7.

## Context

The assignment requires actor- or resource-scoped exports that a recipient can verify independently. Events belong to one global chain, so matching events may be separated by unrelated records. Exporting only matches breaks chain continuity, while exporting all event bodies violates scope privacy.

## Decision

Export a versioned manifest from genesis through a repeatable-read chain-head snapshot. Matching events are `FULL` records; unrelated events are `BRIDGE` records exposing only their chain hash metadata. Sign the canonical manifest with Ed25519 and identify the trusted signing key by a stable key ID. Verification requires both a valid trusted signature and successful recomputation of all disclosed commitments and chain links.

## Consequences

- A recipient can verify selected records against the captured global chain head without database access.
- Unrelated event bodies and identifiers are omitted, although sequence positions and hash commitments remain visible.
- Trust in the signing public key must be established independently of the bundle.
- Export size and work are O(n) in total chain length, not the number of matches.
- Synchronous generation is bounded; larger production exports require an asynchronous delivery design.
- Key rotation requires retaining trusted historical public keys if old bundles must remain verifiable.
