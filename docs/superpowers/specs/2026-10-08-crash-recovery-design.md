# Crash recovery and UNKNOWN outcomes

## Decision

Keep action side effects at-most-once across uncertain process boundaries: persist an action-start marker first, and never replay `ACTION_STARTED` or `ACTION_UNKNOWN` without external verification. Only a workflow at a durable action boundary can be considered for resumption, and only after matching its exact saved automation revision, workflow schema version, enabled state, and action topology. This release classifies such runs but does not launch an automatic resumer.

Corrupt individual checkpoint payloads must remain reviewable and reserve admission until a user explicitly acknowledges their removal. Existing full-file DataStore corruption replacement remains a documented recovery limit.

## Scope

- Harden corrupt-record retention and execution admission.
- Add diagnostics for quarantined records using the existing read-only recovery projection and explicit acknowledgement flow.
- Require enabled and exact-revision workflow state for safe-boundary classification.
- Preserve the existing immutable per-run workflow metadata and history-before-terminal-removal ordering.
- Document device-only verification gaps rather than infer evidence.

## Acceptance criteria

1. Malformed checkpoint text remains in durable storage, appears in recovery diagnostics, and cannot be overwritten by new admission.
2. Explicit recovery acknowledgement clears corrupt records and reopens admission.
3. Disabled, missing, changed, and legacy workflows cannot become safe resume candidates.
4. `ACTION_STARTED` and `ACTION_UNKNOWN` never become replayable without verification/compensation.
5. Tests and exact-head CI pass; physical low-storage, abrupt-kill, reboot, and OEM checks are reported separately as `NOT TESTED` when unavailable.
