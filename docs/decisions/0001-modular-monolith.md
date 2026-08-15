# ADR 0001: Use a Modular Monolith

- Status: Accepted
- Date: 2026-08-15

## Context

The assignment requires one independently runnable service and values understandable engineering decisions. Splitting early into distributed services would introduce failure modes and infrastructure unrelated to the required behavior.

## Decision

Use one Spring Boot deployment organized into feature modules with domain, application, API, and infrastructure boundaries.

## Consequences

- Transactions and global chain ordering remain straightforward.
- Features can be tested independently without network calls.
- Module boundaries require discipline because they are not enforced by deployment boundaries.
- A future extraction remains possible if scale or ownership requirements justify it.
