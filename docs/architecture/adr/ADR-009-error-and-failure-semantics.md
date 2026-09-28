# ADR-009 — Error and failure semantics

**Status:** Accepted

## Context

Free-form handler error strings are difficult to test, localize, aggregate, and
reason about in batch execution.

## Decision

Use a structured error model with stable codes such as
`INVALID_CONFIGURATION`, `UNSUPPORTED`, `PERMISSION_MISSING`,
`CAPABILITY_MISSING`, `SECURITY_REJECTED`, `TARGET_NOT_FOUND`,
`TIMEOUT`, `TRANSIENT_FAILURE`, `PROVIDER_UNAVAILABLE`, `CANCELLED`,
and `CONFLICT`.

Failure policy is explicit at plan/batch boundaries.

## Invariants

- New canonical runtime paths return structured errors.
- User-facing text is localized outside the error code.
- Errors never expose secrets.
- Failures are not converted to success by best-effort fallbacks.
- Batch partial failure is represented explicitly.

## Consequences

Diagnostics, retries, UI messages, telemetry, and tests share one stable failure
contract.
