# ADR 0007: Use profile-specific authentication with one authorization model

## Status

Accepted for Milestone 9.

## Context

The assignment must remain easy to demonstrate locally with H2 and Bruno, while the PostgreSQL target needs externally governed authentication. Leaving administrative, export, and evidence endpoints anonymous would make actor attribution and production-readiness claims indefensible. Running a local authorization server would add unrelated infrastructure to the assignment.

## Decision

Use Spring Security with one stateless endpoint-authorization matrix. The H2 profile provides encoded in-memory HTTP Basic users with public development-only credentials. The PostgreSQL profile is an OAuth2 JWT resource server configured through external issuer, JWK Set, and audience values. JWT authorities come from an explicit `roles` claim and use the same authority names as local users.

Health and application-info probes remain public. Prometheus, API documentation, audit reads/writes, verification, export, retention, and redaction are role protected. Unmatched routes deny access. Authentication and authorization failures use JSON Problem Details.

## Consequences

- Local reviewers can exercise every role without an external identity provider.
- Production identity, token issuance, expiry, revocation policy, and MFA remain owned by the enterprise authorization system.
- Issuer and audience validation prevent accepting a correctly signed token intended for another issuer or service.
- Basic credentials are restricted to H2 and are unsuitable for deployment.
- Disabling CSRF is valid only while the API remains stateless and does not authenticate with cookies or sessions.
- Securing the generic append API authenticates the writer but does not prove that its `actorId` field represents the authenticated principal.
