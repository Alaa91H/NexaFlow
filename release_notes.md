# NexaFlow v3.46.0

NexaFlow v3.46.0 completes the local outcome-filtering path for execution history. Users can now isolate **Skipped runs** alongside all runs and failures, making it easier to understand intentional non-execution without mistaking it for a completed action or a failure.

## Added

Execution History now includes a **Skipped runs** filter in both the global view and routine-scoped history. The selection is backed by a dedicated Room Paging query that applies routine scope, `success = 1`, and the established `Skipped:%` record pattern before results are mapped to the UI. Results remain newest first.

The routine **Execution health** card now provides **View skipped runs** when that routine has recorded skips. It opens the same evidence view with `outcome=skipped` already selected. This release adds localized skipped-state labels and empty-state guidance across every shipped locale, plus domain, Room DAO, navigation, and Compose screen regression coverage.

## Changed

History now uses an explicit shared execution-outcome model rather than a binary failure-only UI state. Route parsing, Paging selection, health aggregation, summary presentation, and the History status pill use the same classification: a skipped run is a successful stored record whose message begins with `Skipped:`.

The three filter chips wrap on compact screens and with longer localized text, rather than requiring one fixed-width row.

## Fixed

Skipped records were previously presented with the same History status pill as ordinary completed runs because both are persisted with `success = true`. They are now explicitly labeled **Skipped**, while preserving their non-failure semantics.

## Privacy and scope

This is a **read-only, on-device diagnostic improvement** over existing execution records. It adds no account, network request, telemetry, permission, foreground service, background task, schema migration, export path, raw-log mutation, automatic retry, or automated remediation.

> A skipped run records that NexaFlow deliberately performed no action, such as when a condition is not met or a maintenance occurrence is not ready. It is not an error prediction, and it does not guarantee or alter subsequent Android background behavior.

## Verification

The final release candidate passed the complete GitHub Actions pipeline: resource hygiene and locale-parity checks, Python resource-gate tests, Detekt, Android Lint, Android unit tests, debug and release APK builds, release AAB build, dependency verification, APK permission and signature checks, zip alignment, 16 KB native-library alignment, and bundle validation.

## Research record

The current review of Tasker, MacroDroid, Automate, and Android platform guidance; the resulting scope decision; acceptance criteria; and deferred work are recorded in [`docs/RESEARCH_2026.md`](docs/RESEARCH_2026.md).
