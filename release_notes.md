# NexaFlow v3.43.0

NexaFlow v3.43.0 makes routine behaviour easier to understand at the point where users inspect and manage a routine. The release turns existing local execution-history analysis into a concise, read-only health summary while keeping the diagnostic surface private, local, and non-invasive.

## Added

Routine details now include an **Execution health** card. It presents one clear, evidence-based state: **No recorded runs**, **Activity recorded**, or **Needs attention** when repeated failures are detected. The card also shows completed, skipped, and failed run counts, plus the latest recorded failure message when one exists.

The health summary is localized across every language shipped by NexaFlow. The release adds regression coverage for every health-status presentation mapping.

## Changed

Routine details now observe NexaFlow’s existing `HealthRepository` flow. The card updates reactively from the execution records already stored locally; it does not add a permission, network request, telemetry stream, database schema change, new background service, or a user-writable raw log.

## Design and safety

The interaction follows a review of Tasker Run Log, MacroDroid System Log, and Automate Flow Logs. The implementation deliberately favors a low-noise, read-only summary over a mutable raw-log feature. This helps users understand repeated failures and skipped work without letting workflows alter diagnostic evidence.

> Execution health reports facts from already-recorded runs. Android and device-specific conditions can still affect future execution, so users should inspect the routine’s trigger, constraints, permissions, and device settings when a failure is reported.

## Verification

The final `main` candidate passed the complete GitHub Actions pipeline: resource hygiene and locale-parity checks, Python resource-gate tests, Detekt, Android Lint, Android unit tests, debug and release APK builds, release AAB build, dependency verification, APK permission and signature checks, zip alignment, 16 KB native-library alignment, and bundle validation.

## Research record

The market comparison, observability-security rationale, acceptance criteria, and deferred scope are documented in [`docs/RESEARCH_2026.md`](docs/RESEARCH_2026.md).
