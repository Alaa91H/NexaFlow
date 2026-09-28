# ADR-011: Provider Architecture

**Status:** Accepted  
**Date:** 2026-09-28

## Context

Core Android APIs, settings/user hand-offs, Shizuku, Root and plugins can
provide equivalent or partial capabilities. Binding workflows to mechanisms
would leak platform details and grow the core.

## Decision

Providers implement typed operation/capability contracts behind the existing
router. Core targets/operations are mechanism-neutral. Android API, privileged
and plugin implementations register bounded provider descriptors and must pass
a common conformance test kit.

Plugin/provider registration includes stable namespace, supported operation
versions, parameter schema, capability requirements, risk, cancellation,
timeout, idempotency/retry declarations and error mapping. Registration never
implicitly grants privilege or user approval.

## Invariants

- Providers cannot redefine existing `core.*` semantics.
- Vendor/product names do not become core semantic targets.
- A provider cannot bypass capability/security policy.
- Raw privileged commands remain implementation details behind typed operations.
- Provider disappearance/death yields structured availability/outcome changes.
- Conformance tests are mandatory before discoverability.

## Consequences

NexaFlow can grow through providers without making the canonical workflow model
or core runtime vendor-specific.
