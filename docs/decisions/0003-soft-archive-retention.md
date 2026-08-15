# ADR 0003: Use bounded soft archival for retention

## Status

Accepted for Milestone 5.

## Context

The assignment requires configurable retention without creating false hash-chain failures. Physically deleting any event from one global predecessor-linked chain would remove evidence required to recompute later links.

## Decision

Expired events are marked with `archived_at` in bounded batches based on server-controlled `recorded_at`. Ordinary queries exclude archived rows, while complete verification and future verifiable exports continue to include them. The scheduler is disabled by default and processes one batch per invocation.

## Consequences

- Chain integrity remains verifiable after retention runs.
- Query consumers no longer see records outside the active retention window.
- Storage is not reclaimed; physical purge requires a future segmented-chain design with signed checkpoints or external anchors.
- The administrative run endpoint requires authorization before production deployment.
- Database roles must protect operational retention metadata from unauthorized updates.
