# ADR-005 — Schema-driven UI

**Status:** Accepted

## Context

Hand-written Compose branches for every trigger/action duplicate defaults,
validation, visibility rules, and selection behavior.

## Decision

The canonical schema is the source of truth for configuration UI. A unified
`NodeConfiguratorSheet` renders sections and fields dynamically.

Schemas describe types, defaults, constraints, selection mode, conditional
visibility/requirement, conflicts, capability requirements, help metadata, and
summary metadata.

## Invariants

- Runtime-required configuration must be declared by schema.
- No hidden runtime default may contradict the schema.
- UI must not invent semantics not represented in the schema/rules engine.
- Large selectors use search/lazy rendering and explicit selection counts.
- Basic/Advanced/Expert disclosure changes presentation, not semantics.

## Consequences

Families become simpler to maintain, and adding a supported option usually
requires schema/registry work instead of a new editor branch.
