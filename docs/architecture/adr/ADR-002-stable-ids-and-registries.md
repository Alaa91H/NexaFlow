# ADR-002: Stable IDs and Registries

**Status:** Accepted  
**Date:** 2026-09-28

## Context

Kotlin enum/class names are implementation details but legacy persisted types
have made them compatibility contracts. Future extensibility must not require a
new enum for every target or variant.

## Decision

Canonical targets, operations and predicates receive immutable stable IDs such
as `core.connectivity.wifi`, `core.media.control`, and
`core.system.settings`. IDs are lowercase dotted namespaces and become
append-only compatibility identifiers once released.

A registry owns each namespace and rejects duplicate IDs. Registries expose
typed descriptors including supported operations/predicates, data types,
capability requirements and providers. Plugin IDs use provider-owned namespaces
and cannot shadow `core.*`.

The existing domain `CapabilityId` and semantic `OperationRegistry` are reused
and bridged; a duplicate capability model is forbidden. Existing
`SemanticOperationId` values may map additively to stable canonical operation
IDs while their serialized compatibility contract remains untouched.

## Invariants

- Renaming a Kotlin class never changes a persisted stable ID.
- Released stable IDs are never repurposed for different semantics.
- Duplicate registry IDs fail tests/initialization.
- Registries describe semantic contracts, not UI layout.
- Provider mechanism names (Root/Shizuku/API) are not target IDs.

## Consequences

Capabilities can expand without proportional enum growth. A later plugin/provider
SDK can register extensions through bounded namespaces while core compatibility
remains deterministic.
