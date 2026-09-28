# ADR-010: Idempotency, Retry, Verification and Compensation

**Status:** Accepted  
**Date:** 2026-09-28

## Context

Retries can duplicate irreversible/external effects. The existing capability
model already defines `CapabilityIdempotency`, `CapabilityRetrySafety`,
verification modes and compensation support.

## Decision

Reuse those existing contracts for canonical operations. Every side-effecting
operation declares idempotency, retry safety, verification policy and
compensation/restore capability.

- IDEMPOTENT + SAFE operations may retry within bounded policy.
- NON_IDEMPOTENT/UNSAFE operations are never blindly repeated.
- CONDITIONAL operations require operation-specific proof/policy.
- UNKNOWN after a possible effect is verified/reconciled before any retry.
- Compensation is invoked only when explicitly supported and never advertised
  as a transaction guarantee for inherently irreversible Android operations.

## Invariants

- Retry count/backoff are bounded.
- Verification success is not inferred solely from transport/exit success.
- A destructive operation cannot claim reversible compensation.
- Recovery uses durable identity/idempotency keys where applicable.
- Batch rollback only includes commands with honest compensation support.

## Consequences

The canonical engine can be resilient to transient failures without duplicating
SMS, HTTP side effects, installs, destructive package actions or other unsafe
operations.
