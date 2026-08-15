# Scenario C: Compliance Reporting Clarification and Design

## Product statement

Regulators need to be able to audit access to client account data.

This statement is not implementation-ready. It does not define access, client data, evidence completeness, report consumers, authorization, timeliness, retention, or the regulator delivery channel. This document records the questions, provisional assumptions, normalized requirement, and prototype boundary that must exist before code is written.

## Stakeholders and decision owners

| Stakeholder | Decision responsibility |
| --- | --- |
| Compliance and Legal | Applicable obligations, report contents, retention, jurisdictions, and regulator expectations |
| Product owner | Supported workflows, service-level objectives, and prioritization |
| Data owner / Privacy | Client-data classification, minimization, redaction, and permitted report disclosure |
| IAM / Security | Actor identity, delegation, service principals, authorization, and regulator access |
| Source-system owners | Which operations emit events and how delivery completeness is guaranteed |
| Audit platform owner | Event schema, integrity, reporting API, monitoring, and operational support |

## Ambiguities, questions, and provisional assumptions

Assumptions below allow a bounded prototype design; they are not statements of law or approved enterprise policy.

| Area | Question requiring confirmation | Provisional assumption | Risk if wrong |
| --- | --- | --- | --- |
| Meaning of access | Are reads, searches, downloads, exports, prints, writes, and denied attempts in scope? | Record read-like actions and denied attempts. Writes remain ordinary audit events unless Compliance adds them to the access taxonomy. | Missing a regulated action or producing excessive noise |
| Data scope | Which systems, accounts, and data categories are client account data? | Account-scoped data in registered source systems is in scope; events name approved data categories, never raw client values. | Incomplete coverage or sensitive report payloads |
| Actor identity | Are employees, clients, delegates, vendors, batch jobs, and services included? | Human and service principals are included. Delegation records both effective and originating identity. | Misattribution and weak non-repudiation |
| Outcome | Are failed or unauthorized attempts reportable? | Record `ALLOWED` and `DENIED`; system failures use a separate outcome only if the source can distinguish them reliably. | Concealed suspicious attempts or misleading failures |
| Purpose | Must business purpose, case, or consent be recorded? | A controlled `purposeCode` is required; optional case references must not expose client data. | Inability to demonstrate legitimate purpose |
| Consumers | Do regulators query directly, or do internal compliance staff produce submissions? | Only authorized internal compliance users access the prototype. Direct regulator identity federation and portal access are deferred. | Wrong authentication and delivery architecture |
| Timeliness | On demand, scheduled, near-real-time, or periodic? | Evidence is available after ingestion; reports are on demand. Scheduled submissions are deferred. | Missed reporting deadlines |
| Report scope | Which filters and grouping are required? | A bounded UTC time range plus at least one account or actor scope is required. Action, outcome, source, and data category are optional filters. | Unbounded scans or unusable reports |
| Retention | How long must access evidence and generated reports be retained? | Reuse configured soft archival until Compliance supplies jurisdiction-specific schedules; do not physically delete chain evidence. | Over-retention, under-retention, or legal conflict |
| Evidence | Is integrity enough, or must completeness and source delivery also be proven? | Hash-chain and signature evidence proves received records were not altered. Source completeness requires transactional outbox/acknowledgement controls outside this prototype. | False claim that the report covers events never emitted |
| Corrections | Can an incorrect access event be changed? | Never update it. Append a correction event referencing the original event ID and reason. | Loss of historical truth |
| Redaction | Can account identifiers or access details be redacted? | Raw sensitive values are excluded. Any approved payload redaction retains commitments and a receipt; identifiers needed for evidence require policy approval before removal. | Evidence unusability or privacy violation |
| Availability and scale | What volume, report window, latency, and concurrency are expected? | Use keyset pagination for interactive reads and asynchronous signed export for large reports. Exact limits require volume data. | Resource exhaustion or missed service levels |

## Definitions used by the prototype

- **Client account data**: classified information associated with an account in a registered source system. Events contain category codes, not the accessed values.
- **Access**: an attempt to view, search, download, export, or print client account data, whether allowed or denied.
- **Actor**: the authenticated human or workload principal that initiated the access. When delegated, both effective and originating principals are retained.
- **Source system**: the registered application that enforced the access decision and emitted the evidence.
- **Compliance report**: a filtered, sequence-ordered view of recorded access events plus integrity metadata and generation criteria.
- **Complete report**: complete only for events successfully accepted from declared source systems within the captured sequence/time boundary. End-to-end completeness is not claimed without source delivery controls.

## Normalized requirement

Authorized internal compliance users shall be able to retrieve a complete, time-bounded, sequence-ordered report of recorded attempts to access client account data for a specified account or actor. Each entry shall identify the authenticated actor, effective actor when delegated, account, action, outcome, classified data categories, controlled purpose, source system, correlation identifier, occurrence time, ingestion time, and tamper-evidence metadata. The report criteria, generation time, and captured chain head shall be recorded, and exportable evidence shall be signed so an authorized recipient can detect alteration independently.

The system shall minimize disclosed client information, audit report access, use UTC and half-open time intervals, reject unbounded requests, preserve append-only corrections, and distinguish integrity of received records from completeness of source-system emission.

## Acceptance criteria

1. A typed access event uses a controlled event type and validates action, outcome, data-category, purpose, source-system, correlation, account, and actor fields.
2. Actor identity comes from an authenticated principal; a request body cannot silently override it. Delegation is explicit.
3. Access-event payloads contain category and decision metadata but no raw client account values.
4. A report requires `from`, `to`, and exactly one primary account or actor scope; `from` is inclusive and `to` is exclusive.
5. Optional filters include action, outcome, source system, and data category. Results use immutable sequence keyset pagination.
6. Normal reads exclude archived events according to policy, while evidentiary export explicitly declares whether archived records are included. The prototype design includes them for regulatory evidence.
7. Report entries expose event and chain references sufficient to locate and verify evidence without exposing commitment salts unnecessarily.
8. A signed export binds the normalized criteria, generation timestamp, captured chain head, matching count, and exported records.
9. Viewing or exporting a report appends an administrative audit event without placing returned client data in logs or receipt payloads.
10. Unauthorized report creation is denied and produces security telemetry; authorization roles distinguish reading, exporting, verifying, and administering retention/redaction.
11. Invalid or corrected source evidence is represented by a new linked event, never an update or delete.
12. Tests demonstrate filter correctness, pagination, authorization, privacy minimization, archived evidence handling, signing, tamper detection, and the documented completeness boundary.

## Technical design

### Evidence model

Reuse the global `audit_event` chain rather than copying evidence into a reporting table. A controlled `CLIENT_ACCOUNT_DATA_ACCESS` event uses:

- `actorId`: authenticated initiating principal.
- `resourceType`: `CLIENT_ACCOUNT`.
- `resourceId`: stable opaque account identifier used by existing resource queries and exports.
- `eventType`: `CLIENT_ACCOUNT_DATA_ACCESS`.
- `timestamp`: source occurrence time; `recordedAt` remains server ingestion time.
- `payload.action`: controlled value such as `VIEW`, `SEARCH`, `DOWNLOAD`, `EXPORT`, or `PRINT`.
- `payload.outcome`: `ALLOWED` or `DENIED`.
- `payload.dataCategories`: controlled classification codes.
- `payload.purposeCode`: controlled business-purpose code.
- `payload.sourceSystem`: registered source identifier.
- `payload.correlationId`: request trace identifier without client data.
- `payload.effectiveActorId`: present only for approved delegation/impersonation workflows.
- `payload.reasonCode`: optional controlled decision or denial reason.

The hash chain protects the event fields, and version-2 leaf commitments allow later approved payload redaction. Existing resource and actor indexes support the primary report scopes. JSON payload filters may require promoted columns or PostgreSQL JSON indexes after volume measurements; no speculative index is added in this milestone.

### Proposed API flow

1. A registered source or gateway submits a typed access event after its authorization decision.
2. Security derives actor and source identity from credentials; the application validates controlled codes and appends the canonical event.
3. An authorized compliance user requests a bounded report using account or actor scope and a UTC time range.
4. The query reuses global sequence keyset pagination and returns a compliance-specific projection that excludes proof salts and raw payload details not required by the report.
5. A report-view receipt is appended with criteria fingerprint, consumer principal, result boundary, and purpose, but not returned data.
6. Large or externally delivered evidence uses the signed export mechanism with a compliance manifest binding report criteria to the captured head.

Candidate API names for the later implementation are `POST /compliance/access-events`, `GET /compliance/access-reports`, and an asynchronous export job resource. Names and synchronous limits remain subject to API review. The assignment-required chain endpoint will also be exposed as `GET /audit/verify`; the existing `/audit/verification` path can remain as a compatibility alias.

### Authorization model

The design separates these authorities:

- `AUDIT_EVENT_WRITE`: registered source ingestion.
- `COMPLIANCE_REPORT_READ`: bounded internal reports.
- `COMPLIANCE_REPORT_EXPORT`: signed evidence generation/download.
- `AUDIT_VERIFY`: chain or bundle verification.
- `AUDIT_PRIVACY_ADMIN`: redaction.
- `AUDIT_RETENTION_ADMIN`: retention execution/configuration.

Production identity must come from the enterprise identity provider or workload identity. The current caller-supplied `actorId` is suitable only for prototype data and cannot support a compliance-grade attribution claim.

### Completeness and trust boundary

Hash chaining answers: “Were accepted events altered or removed relative to the stored chain?” It does not answer: “Did every source emit every required event?” Production completeness requires registered-source inventory, transactional outbox or equivalent atomic capture, durable delivery with idempotency, lag/dead-letter monitoring, reconciliation, and source coverage attestations. Report metadata must identify covered sources and watermark boundaries rather than claiming universal completeness.

## Current implementation and scope boundary

| Capability | Current status | Boundary |
| --- | --- | --- |
| Append access-shaped events | Partially implemented through generic `POST /audit/events` | No controlled schema or authenticated identity yet |
| Filter by actor/account/type/time | Implemented through `GET /audit/events` | Generic response exposes more audit detail than a minimized compliance projection |
| Stable incremental reads | Implemented with sequence keyset pagination | Consumer authorization is not implemented |
| Tamper verification | Implemented at `/audit/verification` | Assignment path `/audit/verify` still needs an alias |
| Retention and archived verification | Implemented | Retention duration lacks Compliance approval |
| Structured redaction | Implemented with salted commitments | Identifier-redaction policy is unresolved |
| Actor/account signed export | Implemented | Export authorization and compliance-specific criteria manifest are not implemented |
| Identity and role separation | Deferred | Required before compliance-grade claims |
| Source delivery completeness | Deferred | Requires integration with source systems and operational reconciliation |
| Direct regulator portal/submission | Out of scope | Internal authorized report production is the provisional workflow |

Milestone 8 intentionally changes documentation only. It demonstrates clarification before implementation and treats the existing service as a well-reasoned partial implementation. A later approved milestone may implement the typed/minimized slice after security and API choices are accepted.

## Validation strategy for a later implementation

- Contract tests for controlled values, required scope, time bounds, and rejected raw sensitive fields.
- Security tests for unauthenticated, wrong-role, delegated, and service-principal access.
- Repository/integration tests for combined filters, archived rows, pagination, and concurrent appends.
- End-to-end tests proving report-view receipts do not leak returned data.
- Signed-export tests binding criteria, watermark/head, counts, and records.
- Tampering tests for event content, chain links, report criteria, and signatures.
- Load tests using expected source volume and report windows before setting operational limits or indexes.
- Reconciliation tests once a source outbox/delivery protocol exists.

## Risks and limitations

- Caller-supplied identities are spoofable until authentication is implemented.
- A global chain serializes appends and makes full proofs O(n); scale tests may drive partitioning or checkpoint design.
- Soft archival preserves evidence but does not establish a legally approved retention schedule.
- Redaction can conflict with evidentiary needs and requires policy-driven field classification.
- Report access itself exposes sensitive metadata and needs least privilege, purpose capture, monitoring, and audit receipts.
- An independently valid signature proves issuer and integrity only when the recipient obtains trusted public keys out of band.
- No implementation can claim source completeness without controls beyond this service boundary.

## Decisions required before implementation

1. Compliance approves the access-action taxonomy, outcomes, required data categories, and purpose codes.
2. Privacy approves identifiers and metadata permitted in reports and receipts.
3. IAM approves principal, delegation, workload identity, and role mappings.
4. Product approves report consumers, synchronous window/limits, timeliness, and delivery workflow.
5. Source owners agree on atomic capture, idempotent delivery, reconciliation, and coverage ownership.
6. Compliance and Legal approve retention and correction policies by jurisdiction.
