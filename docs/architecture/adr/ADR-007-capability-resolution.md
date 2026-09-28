# ADR-007: Capability Resolution

**Status:** Accepted  
**Date:** 2026-09-28

## Context

The repository already implements `OperationRegistry`, `CapabilityRouter`,
typed strategies, evidence/health tracking, verification and adaptive
Android/Shizuku/Root routing. A second resolver would create conflicting
execution decisions.

## Decision

`CapabilityRouter` remains the single semantic strategy-selection authority.
Canonical target/operation contracts map into the existing operation/capability
layer. New operations extend `OperationRegistry` and typed strategies rather
than routing directly from UI/workflow code to platform handlers.

Selection order remains compatibility/policy/availability/evidence-health/
least-privilege aware. A user-action-only strategy produces
`PENDING_USER_ACTION`, not automated success. UNKNOWN after a possible side
effect triggers reconcile/verification rules and never blind fallback.

## Invariants

- No canonical workflow contains raw shell commands for platform capabilities.
- Privileged execution requires explicit policy/authorization.
- Least privilege is preferred when semantics are equivalent.
- Unsupported capability fails explicitly; no fake success.
- Provider health/evidence contains no secrets.
- Workflow code does not select Root/Shizuku directly.

## Consequences

Canonicalization expands semantic coverage while preserving one adaptive,
explainable and tested execution route.
