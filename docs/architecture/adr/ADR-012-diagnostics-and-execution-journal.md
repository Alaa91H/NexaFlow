# ADR-012: Diagnostics and Execution Journal

**Status:** Accepted  
**Date:** 2026-09-28

## Context

Stable automation requires explaining not only failures but why triggers did
not match, why a provider was unavailable, or why an action was skipped.

## Decision

Extend the existing execution journal/timeline rather than introducing a second
logging store. Canonical runs produce structured lifecycle events for trigger
evaluation, semantic validation, capability resolution, planning, command
start/completion/failure, migration and recovery.

Every event carries correlation/run/node identity, timestamps/duration, stable
status/error code and safe provider metadata. A user-facing diagnostic surface
can derive "why didn't it run?" from these structured facts.

## Invariants

- Secrets, webhook tokens, passwords, sensitive payloads and raw privileged
  commands are never persisted/logged.
- Redaction occurs before persistence/export.
- Diagnostics are observational; logging failure does not change execution.
- Run/node correlation IDs remain stable through planning and execution.
- Unknown/partial results remain distinguishable from success/failure.

## Consequences

Supportability improves without coupling workflow semantics to human log text,
and sanitized snapshots can later support deterministic diagnostic replay.
