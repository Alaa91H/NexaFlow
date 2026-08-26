# NexaFlow v3.41.5

NexaFlow v3.41.5 is a reliability patch focused on protecting routine data during JSON backup import. It follows a competitive and Android-platform review that prioritized safe portability and predictable automation behavior over speculative feature expansion.

## Fixed

Backup files containing duplicate automation IDs are now rejected before any data is written. Previously, duplicate IDs inside a malformed or hand-edited file could make ID remapping ambiguous and allow the repository’s replacement semantics to discard one imported automation silently. The import remains all-or-nothing for this validation path, so no automation is saved when the file is rejected.

## Added

This release adds a regression test that verifies duplicate automation IDs fail safely without persisting data. It also adds a documented 2026 research record that captures the competitive and platform evidence used to prioritize reliability, portability, permission-aware scheduling, and release quality.

## Verification

The release candidate passed the complete GitHub Actions pipeline: resource hygiene checks, locale parity checks, Python resource-gate tests, Detekt, Android Lint, Android unit tests, debug and release APK builds, release AAB build, dependency-verification checks, APK permission checks, APK signature checks, zip alignment, 16 KB native-library alignment, and bundle validation.

> Android automation still depends on the permissions available on the device, Android version, OEM behavior, and any explicitly enabled Root or Shizuku capabilities. This release strengthens backup safety; it does not bypass platform restrictions.

## Research references

The supporting research record is available at [`docs/RESEARCH_2026.md`](docs/RESEARCH_2026.md). It links the official Android scheduling guidance and comparable automation products that informed this release scope.
