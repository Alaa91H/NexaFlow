# T03 DataStore Windows evidence

- Baseline commit: `d2d810395acf0bf7b33ed6701ee08c39a515a4a9` (T02 merge on `main`)
- Captured: `2026-10-08 11:53:16 +02:00`
- Host: Windows 11, x64; Gradle 9.6.1; Microsoft JDK 17.0.20.1; Android SDK configured at `C:\Users\Alaa\AppData\Local\Android\Sdk`.
- Command: `.\gradlew.bat :core:datastore:testDebugUnitTest --no-configuration-cache --no-parallel --max-workers=1 --no-daemon --console=plain`
- Result: PASS; `46` tests, 0 failures, 0 errors. Per-suite JUnit XML files are archived beside this report.
- Quality gates: `detekt lintDebug assembleDebug` PASS (same Gradle process); final output `BUILD SUCCESSFUL in 1h 20m 9s`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `be38d4deb17df6b009e57adfd0fb3139c0f1964b8b6f24e800af7940c73261c7`. Build artifact only; NOT installed or device-tested.
- Lint XML SHA-256: `f5da016043a3ff21328c3807e0b7d799125738ded705e8fda1c320131c59eed8`.

## Root cause

The five failures were platform-specific replacement behavior, not parallel scheduling, stale fixtures, or multiple DataStore instances. Direct Windows/JDK 17 diagnostic reproduced `File.renameTo` returning `false` when the destination already exists (target stayed `old`); `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` replaced it successfully. The affected tests initialize Robolectric at its default API below the app's `minSdk = 26`; DataStore's Android implementation uses the legacy rename branch below API 26 and `Files.move` on API 26+. Pinning these Android-backed tests to Robolectric API 26 makes test runtime match the app's supported floor. AndroidX release notes also identify the legacy non-Android JVM rename bug as fixed in DataStore 1.1.0; the repo's Android runtime resolution is `datastore-core-android:1.2.1`.

## Former failures and resolution

- `AgentNetworkPreferencesTest.explicitLanChoicePersists` — PASS under API 26.
- `AiProviderPreferencesTest.v1MigrationPreservesValidProfilesWhenOneEntryIsCorrupt` — PASS under API 26.
- `SmsDeliveryStoreTest.cooldownIsDurableAcrossDifferentMessages` — PASS under API 26.
- `SmsDeliveryStoreTest.samePhysicalMessageCanBeClaimedByDifferentAutomations` — PASS under API 26.
- `SmsDeliveryStoreTest.duplicateFingerprintIsRejectedAcrossClaims` — PASS under API 26.

Before the change: full suite reproduced 5 failures/46 tests even with `--no-parallel --max-workers=1`; a single isolated AgentNetwork test also failed. This excludes test-task parallelism and fixture collisions.

## Ubuntu / CI

Ubuntu baseline PASS: run 37744647225, head `2233746099118635c358dfafca7a08cb0bd0ef66`, build job step `Run unit tests` ran `./gradlew testDebugUnitTest` successfully on Ubuntu 26.04 / Temurin JDK 21. This is pre-fix evidence on the same DataStore dependency and source baseline; exact proposed-commit Ubuntu evidence is pending PR CI. The build job now names both `:core:datastore:testDebugUnitTest` and the full `testDebugUnitTest` task in the same Gradle invocation, so the module test task is explicit without executing its task twice.

## Scope and impact

- Only Robolectric test SDK configuration and explicit PR CI coverage changed.
- No production persistence code, dependency versions, migrations, data schema, secrets, or signing behavior changed.
- Emulator/device runtime: NOT TESTED.


- Original failing Windows JUnit XML reports for AgentNetworkPreferences, AiProviderPreferences, and SmsDeliveryStore are archived in this directory; the three files preserve all five individual failure stack traces. The original full failure log is `docs/audit/evidence/t02-datastore-windows-2026-10-08.log` (SHA-256 `e7680956adcdc61c5ea16ba863f3a2fb22f451909add712b88e554f14d7508d2`).
- Ubuntu baseline CI: https://github.com/Alaa91H/NexaFlow/actions/runs/37744647225. The exact proposed commit's Ubuntu run is intentionally pending this PR's required checks.
