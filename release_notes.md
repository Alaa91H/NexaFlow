# NexaFlow v3.44.0

NexaFlow v3.44.0 completes the path from a routine health signal to the execution evidence behind it. Users can now open a focused, paged run history for the routine they are inspecting, without losing the existing global history view.

## Added

The **Execution health** card in routine details now includes **View routine history**. It opens a history screen that is filtered at the database layer to the selected routine and ordered newest first. The history destination clearly identifies routine-scoped history, while the Settings entry continues to show all routines.

The release adds a Room Paging query for `automationId`, localized history-navigation labels in every shipped language, a DAO regression test proving that only the selected routine’s records are returned in newest-first order, and a route regression test.

## Changed

The History navigation route now accepts an optional routine identifier. When it is missing or blank, NexaFlow preserves the original global-history behaviour; when it is present, the ViewModel uses the filtered Paging source before records are mapped to the UI.

## Fixed

Release validation identified two implementation issues: Kotlin could not infer the optional navigation argument type, and the first route test depended on an Android API in a plain JVM test. The argument is now retrieved with an explicit type and route encoding is JVM-safe. The final main candidate passed CI after both corrections.

## Privacy and scope

Routine history remains a local, read-only view over existing `ExecutionRecord` data. This release adds no network request, telemetry, account, permission, background service, database schema migration, export capability, raw log mutation, or automatic remediation.

> The filtered view shows recorded evidence, not a prediction of future execution. Device settings, permissions, constraints, OEM behavior, and Android background limits can still affect subsequent runs.

## Verification

The final `main` candidate passed the complete GitHub Actions pipeline: resource hygiene and locale-parity checks, Python resource-gate tests, Detekt, Android Lint, Android unit tests, debug and release APK builds, release AAB build, dependency verification, APK permission and signature checks, zip alignment, 16 KB native-library alignment, and bundle validation.

## Research record

The competitive research, database review, acceptance criteria, safety constraints, and deferred scope are documented in [`docs/RESEARCH_2026.md`](docs/RESEARCH_2026.md).
