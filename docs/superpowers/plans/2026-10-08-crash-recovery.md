# Crash recovery implementation plan

> Task order follows the accepted T06 design. Validation claims require command output from the exact implementation head.

**Goal:** Preserve and expose uncertain execution evidence without replaying unknown side effects.

**Spec:** `docs/superpowers/specs/2026-10-08-crash-recovery-design.md`

## Steps

- [x] Confirm current checkpoint write boundaries and recovery classification.
- [x] Add a failing test for malformed checkpoint evidence being overwritten during new admission.
- [x] Quarantine malformed checkpoint payloads, retain them on writes, block admission, and surface them through the recovery review projection.
- [x] Require the persisted automation to remain enabled before a safe-boundary resume classification.
- [x] Add regression tests for corrupt evidence and disabled workflow.
- [x] Document recovery guarantees and physical validation limits.
- [ ] Run focused, full affected-module, static, and CI gates on the final revision.
- [ ] Open a PR, verify exact-head CI, merge, and update issue #129 and master #122 with evidence.

## Evidence log

- Baseline: `cd41390f5012bd6e01de85f01e01cff3d78a3ff7` (merged T05).
- Red test before fix: `ActiveExecutionStoreCheckpointTest.malformedCheckpointIsPreservedAndBlocksNewCheckpointAdmission` returned `ACCEPTED` for a new run despite malformed durable evidence.
- Focused combined test command passed once; rerun after final docs/code edits before PR.
- Physical low-storage/process-kill/reboot/OEM matrix: NOT TESTED (no connected device evidence collected).
