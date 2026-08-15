# ADR 0008: Use a controlled compliance access-ingestion boundary

## Status

Accepted for Milestone 10.

## Context

The generic audit append API deliberately accepts many event shapes and a reported `actorId`. That flexibility is unsuitable for compliance evidence about client-account-data access: a caller could choose its identity, event envelope, or arbitrary payload and could accidentally submit raw client data. Building a second ledger would duplicate the established hash-chain, retention, redaction, verification, and export controls.

## Decision

Expose `POST /compliance/access-events` as a typed adapter over the existing append service. Require the dedicated `COMPLIANCE_ACCESS_WRITE` authority. Derive the actor from the authenticated principal and the production source from the validated JWT `client_id` claim; use a fixed source only for the H2 demonstration profile.

Fix the audit event and resource types, accept only controlled enums and identifiers, reject unknown JSON fields, de-duplicate and sort data categories, and construct the minimized payload inside the application. Continue to store the evidence in the global version-2 commitment/hash chain.

Do not enforce correlation-ID uniqueness yet. Exactly-once behavior depends on an agreed source retry and idempotency contract, including the scope of uniqueness. Defer delegated actor semantics and reporting APIs until their identity and product requirements are approved.

## Consequences

- A request body cannot override the authenticated actor or credential-bound source.
- Arbitrary payloads and raw client values are excluded by construction at this endpoint.
- Compliance evidence immediately inherits existing append atomicity, tamper verification, retention, redaction, and export behavior.
- Taxonomy changes are API-contract changes and require controlled evolution rather than free-form strings.
- Hash-chain integrity still proves only accepted-event integrity, not source emission completeness.
- Retried requests can currently produce multiple valid events with the same correlation ID; source delivery controls must address that before production completeness claims.
