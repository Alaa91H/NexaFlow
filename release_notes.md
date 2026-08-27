# NexaFlow v3.45.0

NexaFlow v3.45.0 makes recorded execution problems faster to investigate. Execution history now offers a local **All runs / Failures** filter, so users can focus on persisted failed runs without exporting data, enabling telemetry, or scanning successful activity.

## Added

The History screen now provides an **All runs / Failures** filter in both global history and routine-scoped history. Selecting **Failures** switches the Paging source to a Room query that filters `ExecutionRecord.success = false` before records reach the UI, while preserving newest-first ordering.

The **Execution health** card now shows **View failures** only when its routine has at least one recorded failed run. The action opens the same routine-scoped history screen with the failure filter already selected. The release also adds localized filter and empty-state text in every shipped locale, database coverage for the failure-only Paging query, and navigation coverage for the direct failure route.

## Changed

The History destination accepts an optional typed `outcome` argument. `outcome=failed` initializes the local failure filter; when no outcome is supplied, NexaFlow retains the established all-runs behavior. Changing the filter reactively recreates the appropriate Paging flow without changing historical records.

## Fixed

Release validation exposed two regressions in the initial implementation: the Compose delegated-state import was absent from the filter UI, and the initial English filter label duplicated the existing failed-status text used by a screen test. Both issues were corrected before release approval, and the final candidate passed CI.

## Privacy and scope

This release is a **read-only, on-device view** over existing execution records. It adds no account, network request, telemetry, permission, background service, schema migration, export path, raw-log mutation, automated remediation, or prediction. A skipped execution remains distinct from a failure: skips are stored as successful records with a `Skipped:` message and are therefore not included in the failure-only filter.

> The filter helps inspect evidence that has already been recorded. It does not predict future execution outcomes; Android permissions, device state, OEM behavior, and background limits may still affect later runs.

## Verification

The final release candidate passed the complete GitHub Actions pipeline: resource hygiene and locale-parity checks, Python resource-gate tests, Detekt, Android Lint, Android unit tests, debug and release APK builds, release AAB build, dependency verification, APK permission and signature checks, zip alignment, 16 KB native-library alignment, and bundle validation.

## Research record

The competitive review, Android-platform constraints, selected failure-filter scope, acceptance criteria, and deferred work are documented in [`docs/RESEARCH_2026.md`](docs/RESEARCH_2026.md).
