# Trigger Expression v2

Trigger Expression v2 is an explicit opt-in on an automation. A missing expression keeps the existing `ANY`/`ALL` implementation and historical `workflowVersion` semantics unchanged. The expression has its own schema version (`2`) and is validated before builder save, import, and execution. Invalid or unsupported persisted data is represented as invalid and fails closed; it never falls back to legacy matching.

## Operators and bounds

The serializable AST supports `State(index)`, `Event(index)`, `AllOf`, `AnyOf`, `NotState(index)`, ordered `Sequence(first, second, withinMs)`, and `Count(index, minimumCount, withinMs)`. `NOT` is restricted to live state triggers. Temporal operators require a source with a stable occurrence identity. Validation caps the tree at 64 nodes and depth 8, windows at 1–604,800,000 ms, and counts at 1–1,000. References must be in range and unique.

The domain evaluator is shared by runtime and builder preview and uses tri-state results. `Event` represents only the current occurrence. `Sequence` uses history ordinals to establish order and monotonic elapsed time for the window. `Count` counts unique admitted occurrences inside that monotonic window. State read failures remain unknown/unavailable and cannot satisfy a gate.

## Persistence, identity, and invalidation

Room schema 28 adds an optional expression and a positive `workflowRevision`. Transactional definition upserts increment the revision only when definition content changes; status toggles preserve it. Canonical workflow schema 5 adds optional fields with defaults so schemas 3 and 4 still decode as legacy workflows. The Room migration leaves existing rows with revision 1 and no expression.

Temporal history is stored in Preferences DataStore with Android Keystore HMAC-SHA256 digests. It stores only automation ID, workflow revision, trigger index, elapsed realtime, ordinal, and a digest; event IDs, source IDs, and payloads are never persisted. Limits are 128 workflows, 4,096 records, 256 KiB serialized data, and a seven-day TTL. Oversized, malformed, or clock-regressed state is cleared and the current evaluation is blocked. A definition revision change isolates and discards old history. Disable, delete, import replacement, and detected state-source permission uncertainty clear affected history.

## Builder and recovery behavior

The builder preserves legacy matching until the user enables v2. Trigger reorder remaps references to preserve the referenced trigger; removing a referenced trigger invalidates the draft and requires repair. The preview synthesizes a bounded sample and calls the same pure evaluator; it does not call providers or execute actions. Backup/import validation rejects invalid v2 expressions before mutating installed automations.

## Verification limits

JVM tests cover schema bounds, truth tables, temporal ordering/expiry, duplicate admission, persisted history, revision changes, migration defaults, and preview/runtime evaluator parity. Emulator integration, physical-device behavior, OEM behavior, permission denial/regrant, reboot recovery on device, and live provider source identity are **NOT TESTED** by these JVM results and require separate evidence before making those claims.
