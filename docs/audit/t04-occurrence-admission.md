# T04 — Duplicate event admission and repeated side effects

## Guarantee and bounds

An event is eligible for durable occurrence protection only when its source supplies a concrete stable event identity. `ExecutionEngine` hashes the automation id, source id, and event id; it never persists the trigger payload or event data. The hash receipt and execution checkpoint are committed in the same DataStore transaction before snapshots, lifecycle ownership, or action dispatch. Same-process duplicate suppression remains a short 30-second optimization; it is not the durable guarantee.

Receipts are retained for 45 days and capped at 8,192 entries. Expired entries are pruned on admission. When the ledger is full, identified events fail closed before any action starts. Deleting an automation clears its receipts with its other durable execution state. Missing identities are intentionally not synthesized from event type or mutable payload, so those sources retain their own narrower protection and do not gain a cross-process exactly-once claim.

## Source identity inventory

| Source | Identity supplied to execution | Coverage |
| --- | --- | --- |
| Scheduled time / boot | Scheduler occurrence id | Durable duplicate admission |
| Calendar | Calendar event id plus start/type | Durable for concrete event; legacy leave transitions may not provide one |
| SMS | SHA-256 sender/body/timestamp fingerprint; a separate durable delivery claim guards receivers | Duplicate delivery claim lasts 24 hours; event receipt lasts 45 days |
| Explicit plugin event | Plugin event id, only when caller supplies it | Durable; requery-only synthetic ids stay process-local |
| Battery, location, notification | No concrete per-event id | Existing lifecycle/state gates only; no cross-process per-occurrence guarantee |
| Device state/event, sensor | No concrete physical event id | State/source gating only; no cross-process per-occurrence guarantee |
| Package, incoming call, webhook | No concrete event id in these ingress paths | Existing cooldown/policy only; no cross-process per-occurrence guarantee |

These distinctions are deliberate: repeated state snapshots are not equivalent to stable event receipts, and a guessed fingerprint can suppress legitimate distinct events.

## Side-effect limits

The checkpoint records action start and reserves a per-action idempotency key before dispatch. A known outcome is committed afterward. An uncertain outcome is persisted as `ACTION_UNKNOWN`; the recovery coordinator requires review/reconciliation and does not blindly replay it. This protects local orchestration from automatic duplicate dispatch after a crash but cannot make an external effect transactional with DataStore.

HTTP retries use one `Idempotency-Key` for attempts within one logical action. A remote server must honor that header for retry deduplication; a later independent execution intentionally receives another key. There is no end-to-end exactly-once guarantee for HTTP, SMS sending, calls, package changes, or destructive effects. If process death occurs after a provider applies an effect but before the local result is committed, the outcome may require review and may remain unknown. Source-side SMS receive claims do not make outgoing SMS exactly-once.

No event payload or secrets are added to the durable receipt or emitted in receipt diagnostics. Existing action configuration and provider-level logging remain separately governed by their current code paths.

## Verification

- `ActiveExecutionStoreCheckpointTest`: same receipt across recreated store, concurrent same-identity admission (one winner), distinct event acceptance, automation-scoped cleanup, and receipt capacity behavior.
- `ExecutionEngineConcurrentAdmissionTest`: replay is rejected across engine recreation; a different occurrence still runs.
- `TriggerOccurrenceDeduplicatorTest`: hash uses source/event identity and automation only; missing identity yields no durable key.
- `PluginEventIngressTest`: requery hint identity remains non-durable.
- Focused JVM unit tasks are run as part of T04 before merge. Physical-device, process-kill, real provider, and OEM behavior remain `NOT TESTED` by those tests.
