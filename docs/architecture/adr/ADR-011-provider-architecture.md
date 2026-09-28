# ADR-011 — Provider architecture

**Status:** Accepted

## Context

Long-term extensibility requires new platform/OEM/plugin capabilities without
continually expanding the core runtime.

## Decision

Providers register supported targets/operations, capability requirements,
schemas/executors, and compatibility metadata through a controlled provider
contract.

Plugins are providers but remain behind permission/identity/high-risk approval
boundaries.

## Invariants

- Providers cannot redefine the semantic meaning of an existing stable ID.
- Every provider passes a conformance test kit.
- Provider disappearance yields a structured failure.
- Core canonical models do not import provider implementation classes.
- High-risk providers require explicit authorization.

## Consequences

Core remains smaller and more stable while device/vendor/plugin functionality
can grow independently.
