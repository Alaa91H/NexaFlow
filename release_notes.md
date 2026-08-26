# NexaFlow v3.42.0

NexaFlow v3.42.0 makes the app’s existing local starter routines discoverable while preserving a deliberate review-before-activation workflow. This release is informed by a new comparative review of Samsung Modes and Routines, MacroDroid, TaskerNet, vFlow, and current Android platform guidance.

## Added

The New Task builder now exposes **Starter routines** when compatible bundled templates are available on the current device. Each starter routine is local, editable, and filtered through NexaFlow’s existing capability model before it is offered. The chooser pre-fills a localized routine name and opens the full review stage, so the user can inspect and adjust the exact triggers and actions before saving.

Starter-routine labels and review guidance are localized across every language shipped by NexaFlow. The release also adds regression coverage for the template title mapping and the activation policy.

## Changed

A routine created from a starter template is saved **disabled by default**. This adds a clear user-controlled review point: the user must explicitly enable it from the dashboard after reviewing the generated configuration. Existing routines retain their current enabled state, and manually created routines preserve their existing first-save behavior.

## Fixed

French starter-routine strings now escape apostrophes correctly for Android resource compilation. This restores successful resource merging and Android Lint for the French locale.

## Verification

The final `main` candidate passed the complete GitHub Actions pipeline: resource hygiene checks, locale-parity checks, Python resource-gate tests, Detekt, Android Lint, Android unit tests, debug and release APK builds, release AAB build, dependency-verification checks, APK permission checks, APK signature checks, zip alignment, 16 KB native-library alignment, and bundle validation.

> Starter routines are local templates, not downloaded automation code. This release does not add cloud storage, remote sharing, webhooks, scripting, AI generation, accessibility-driven screen control, or elevated process capabilities.

## Research record

The comparative evidence, selected scope, acceptance criteria, and explicitly deferred work are documented in [`docs/RESEARCH_2026.md`](docs/RESEARCH_2026.md).
