# Crash recovery policy

## Durable admission and action boundaries

An automation execution must persist its bounded checkpoint before invoking any side effect. Before each action, the store records `ACTION_STARTED`, the stable workflow identity, schema version, persisted revision, node identity, backend, and idempotency key. After a verified action it records `ACTION_COMPLETED` with the result hash and verification state. Cancellation or process interruption during an action leaves `ACTION_UNKNOWN`; that outcome is never retried automatically.

History is written before a terminal checkpoint is removed. If history or checkpoint persistence fails, the unresolved checkpoint remains available for startup recovery and review. A terminal checkpoint may be compacted to satisfy capacity limits; an unresolved checkpoint may not be evicted to admit another run.

## Startup classification

Startup atomically claims interrupted checkpoints. An `ACTION_STARTED` or `ACTION_UNKNOWN` record requires explicit verification or compensation. An `EXIT_PENDING` record requires exit-lifecycle reconciliation. A run at a validated action boundary is only a resume candidate when the automation still exists, is enabled, and its exact persisted revision, schema version, and action topology match the checkpoint. Missing, changed, disabled, legacy, or otherwise unprovable definitions require manual diagnostics. This release does not implement an automatic workflow resumer.

## Corrupt evidence and user acknowledgement

Unparseable checkpoint strings are quarantined as bounded raw evidence. New execution admission is rejected while corrupt checkpoint evidence remains. Recovery review displays a diagnostic item; an explicit acknowledgement can clear the quarantined entries after the existing confirmation flow. Reading recovery state does not mutate it. A full DataStore file corruption is handled by the configured replacement handler and cannot retain bytes that DataStore itself has discarded; device backup/forensic recovery remains outside this store's guarantees.

## Validation limits

Unit tests cover corrupt checkpoint quarantine/admission, explicit clearing, revision mismatch, disabled automation, and uncertain-action no-replay classification. Low-storage write failures, abrupt process death on physical hardware, reboot behavior, OEM background limits, and live recovery UI behavior have not been exercised on a connected device in this environment (`NOT TESTED`).
