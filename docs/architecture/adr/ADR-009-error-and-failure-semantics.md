# ADR-009: Error and Failure Semantics

**Status:** Accepted  
**Date:** 2026-09-28

## Context

Legacy handlers often return human strings while the capability layer already
has structured outcomes and error codes. Canonical execution needs stable
machine-readable failure semantics for UI, history, recovery and tests.

## Decision

New canonical paths use a typed error/outcome taxonomy and adapt existing
`CapabilityErrorCode` / operation outcomes rather than inventing unrelated
strings. Required categories include invalid configuration, unsupported,
permission/capability missing, security rejection, target not found, timeout,
transient provider failure, provider unavailable, cancellation, conflict,
migration failure, pending user action and uncertain/UNKNOWN outcome.

Human-facing localized messages are presentation data mapped from codes.
Legacy `SystemControlResult` remains an adapter boundary during migration.

## Invariants

- A raw exception/string is not the canonical API contract.
- UNKNOWN is distinct from FAILED.
- PENDING_USER_ACTION is not SUCCESS.
- Cancellation is not reported as generic failure.
- Sensitive parameter values never enter error text.
- Error codes are stable enough for diagnostics/tests.

## Consequences

History and "why didn't it run?" UI can explain failures consistently across
Android API, Shizuku, Root and plugin providers.
