# ADR-012 — Diagnostics and execution journal

**Status:** Accepted

## Context

Users need to know why an automation ran, did not run, or partially failed.
Unstructured logs are insufficient and may leak sensitive data.

## Decision

Maintain a structured execution journal for each run:

`TriggerEvaluation -> Validation -> CapabilityResolution -> Planning ->
CommandExecution[] -> FinalResult`

Record timestamps, durations, status, stable error code, selected provider, and
sanitized metadata.

## Invariants

- Secrets and sensitive payloads are redacted or omitted.
- A skipped run records the exact blocking condition.
- Journal writing must not change workflow semantics.
- Diagnostic events use stable machine-readable identifiers.
- Retention/storage limits are explicit.

## Consequences

The app can provide a reliable “Why didn't it run?” view and developers can
debug failures without depending on raw logcat output.
