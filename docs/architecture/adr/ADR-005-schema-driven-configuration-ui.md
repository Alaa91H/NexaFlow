# ADR-005: Schema-driven Configuration UI

**Status:** Accepted  
**Date:** 2026-09-28

## Context

Large per-type Compose branches create duplicated defaults, validation and
visibility rules. Consolidated families require one coherent configurator
without moving runtime rules into UI code.

## Decision

Canonical configuration is rendered from a typed schema describing sections,
fields, types, defaults, allowed values, cardinality, visibility/requirement
rules, conflict rules, capability requirements, sensitivity and summary
metadata.

A shared `NodeConfiguratorSheet` provides Basic / Advanced / Expert progressive
disclosure, search, lazy large-list rendering, selected-only views and
multi-selection controls. Phone-sized screens may use a full-screen sheet.

The schema is the source for presentation and pre-save validation; semantic and
capability validators remain authoritative domain/runtime boundaries.

## Invariants

- No new family-specific mega-`when` recreates the old duplication.
- Runtime never imports Compose.
- UI cannot advertise unsupported selection semantics.
- Secret fields are visibly classified and never surfaced in summaries/logs.
- Search/select-all preserve deterministic selection order where ORDERED matters.
- RTL, TalkBack, focus, dynamic type and localization are first-class acceptance
  criteria.
- Defaults shown in UI match declared schema defaults.

## Consequences

Families can become dramatically simpler in the picker while advanced control
remains available inside one consistent surface. UI changes no longer require
duplicating runtime configuration contracts.
