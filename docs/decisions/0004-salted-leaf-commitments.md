# ADR 0004: Use salted leaf commitments for payload redaction

## Status

Accepted for Milestone 6.

## Context

Replacing a value inside a version-1 payload changes the event content hash and invalidates every later record. Keeping the original value in a hidden table would preserve verification but would not remove the sensitive disclosure material.

## Decision

Hash version 2 commits independently to every canonical JSON leaf using a random 256-bit salt. The event content hash covers the resulting commitment tree. A redaction replaces an exact leaf with its commitment marker and removes its salt. The same transaction appends a non-sensitive redaction receipt to the global audit chain. Version-1 events are not eligible for redaction.

## Consequences

- Remaining visible values and the payload structure stay independently verifiable.
- Removed low-entropy values cannot be tested against their commitments without the destroyed salts.
- Redaction is irreversible in the active database.
- Proof metadata increases storage and verification cost in proportion to payload leaf count.
- Only exact leaf JSON Pointers are supported; subtree redaction requires enumerating descendant leaves.
- Backups and replicas need separate lifecycle controls.
- The stated redaction actor is not trustworthy until endpoint authentication is implemented.
