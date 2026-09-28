# ADR-010 — Idempotency and retry policy

**Status:** Accepted

## Context

Retries are safe for some operations (for example setting Wi-Fi ON) but unsafe
for others (for example sending an SMS).

## Decision

Every atomic command declares idempotency and retry safety:

- IDEMPOTENT
- NON_IDEMPOTENT
- CONDITIONAL

Retry policy is derived from this declaration plus operation-specific metadata.

## Invariants

- NON_IDEMPOTENT commands are not automatically retried.
- CONDITIONAL commands require explicit conditions before retry.
- Retry limits and backoff are bounded.
- Cancellation interrupts retry loops.
- Planner/runtime tests cover retry classification.

## Consequences

Transient recovery improves without duplicating destructive or external side
effects.
