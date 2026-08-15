# ADR 0010: Use a separate signed compliance export manifest

## Status

Accepted for Milestone 12.

## Context

The generic version-1 audit export signs actor/resource scope, a captured global-chain head, matching count, and full/bridge records. Compliance evidence must additionally bind controlled purpose, half-open time bounds, optional access filters, and an explicit criteria fingerprint. Adding fields to the existing canonical manifest would change its signed bytes and break compatibility with previously issued bundles.

Exporting only matching events would break global-chain continuity. Exporting every event body would expose unrelated identities and payloads. A compliance export also needs attributable creation evidence without recursively including its own receipt.

## Decision

Introduce a separate version-1 `COMPLIANCE_ACCESS_REPORT` manifest while reusing the existing canonical serializer, Ed25519 signer/trust configuration, export record model, synchronous chain limit, and extracted chain/content verifier.

The manifest signs normalized account-or-actor scope, controlled report purpose, half-open time range, optional filters, a domain-separated SHA-256 criteria fingerprint, bundle identity/time, captured chain head, matching count, and all chain positions. Matching access events are `FULL`; unrelated positions are hash-only `BRIDGE` records. Archived matches remain evidentiary and are included.

Require `COMPLIANCE_REPORT_EXPORT` to create a bundle and the existing verification authority to verify it. After signing, detach read entities and atomically append a `COMPLIANCE_ACCESS_REPORT_EXPORTED` receipt referencing bundle ID, criteria fingerprint, purpose, signer key, count, and captured head without raw scope or returned metadata.

## Consequences

- Existing generic export canonical bytes and signatures remain backward compatible, including the original typed signer API.
- A recipient can verify trusted signature, criteria fingerprint, disclosed commitments, sequence links, record hashes, full-record criteria membership, count, and captured head without database access.
- Bridge records hide unrelated identities and bodies while retaining global-chain proof.
- Because a bridge hides selection fields, the recipient cannot prove it was not a match; trusted signer governance attests selection completeness.
- The creation receipt is immediately after, not inside, the captured bundle and links back through immutable identifiers.
- Generation and verification remain O(n) and bounded; large delivery requires a later asynchronous design.
