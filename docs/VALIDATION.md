# Validation record

## Current checkout — v3.91.10 baseline revalidation, 2026-10-05

The P0 evidence refresh for `66f0dbd1327365b770901b2fa7d8d528a531ae9b` is recorded in [`evidence/baseline/p0-01-current-main-2026-10-05.md`](evidence/baseline/p0-01-current-main-2026-10-05.md). The local Android SDK was available and `detekt`, all debug lint tasks, and `assembleDebug assembleRelease -PallowDebugSigning=true` completed. The combined Windows Gradle invocation still exited nonzero: five DataStore tests failed when `FileStorageConnection.writeScope` could not replace a target file with its temporary file. The exact commit's Ubuntu tagged CI run [37258291959](https://github.com/Alaa91H/NexaFlow/actions/runs/37258291959) passed, including unit tests and production release checks. All four local app/Wear debug/release APKs passed v2 signature and version metadata inspection, but were signed with the Android Debug certificate and are disposable validation outputs. No Android device was connected. These results do not certify device behavior or make the local full Gradle command green.

## Security scan follow-up — 2026-10-05

At `7b600dff98e510255f93aa028409fd262f31d1ee`, official Gitleaks v8.30.1 was downloaded with its published SHA-256 verified and run against all local Git refs. It scanned 2,221 commits and initially reported five generic-key matches; review found two synthetic test fixtures and three non-secret comment/release-text matches. Their exact fingerprints are recorded in `.gitleaksignore`; the repeat scan exited 0 with no remaining findings. Details and scan limitations are in [`evidence/baseline/gitleaks-2026-10-05.md`](evidence/baseline/gitleaks-2026-10-05.md).

A checksum-pinned Gitleaks job in the single GitHub Actions workflow passed with lint, coverage, unit tests, and APK assembly in [run 37254899900](https://github.com/Alaa91H/NexaFlow/actions/runs/37254899900). Release publication was skipped on the ordinary `main` push. GitHub's repository security settings report native secret scanning disabled. No physical device was connected, so OEM/device validation remains `NOT TESTED`.

## Current checkout — v3.91.8 baseline audit, 2026-10-05

The source audit and current local validation status are recorded in [`AUDIT.md`](AUDIT.md) and [`MASTER_PLAN_STATUS_2026-10-05.md`](MASTER_PLAN_STATUS_2026-10-05.md). Repository catalog/locale/suppression/README checks and the release-signing guard unittest passed. `detekt` completed. The combined `lintDebug` run did not finish because its Gradle daemon stopped advancing at `automation-builder` after reaching approximately 4.3 GB resident memory. A broad `testDebugUnitTest --continue` run executed the datastore module and reported 5/46 failures: DataStore could not rename a temporary file over an existing file. A Java probe confirmed this host's `File.renameTo(existingTarget)` returns `false`. The same baseline passed the `testDebugUnitTest` workflow gate on Ubuntu in [GitHub Actions run 37227693119](https://github.com/Alaa91H/NexaFlow/actions/runs/37227693119), confirming the Windows-local failures are host-specific. The broad local run continued into execution test compilation, then was interrupted after no visible progress for several minutes; the local test task is **not passed**.

`app-debug.apk` and `wear-debug.apk` were built locally and their V2 signatures verified; the app signer is `Android Debug`. The merged release manifest audit passed. A release packaging task without production credentials failed closed as expected. GitHub Actions run [37245345750](https://github.com/Alaa91H/NexaFlow/actions/runs/37245345750) on commit `d0e39a38` passed lint, coverage, unit tests, untagged APK assembly, and packaged-APK checks. Untagged CI APKs used explicit debug signing and were not published as a GitHub Release. Tag run [37247996319](https://github.com/Alaa91H/NexaFlow/actions/runs/37247996319) passed production signing, APK integrity checks, and publication; [release v3.91.9](https://github.com/Alaa91H/NexaFlow/releases/tag/v3.91.9) contains only the phone and Wear APKs. At the time of the original baseline entry no full-history scan tool was installed; see the scan follow-up above. Device and OEM checks remain `NOT TESTED`.

## Historical validation record — v3.74 candidate

Status: local integration checks passed. CI and publication are still pending until the commit, push, tag and release workflow complete.

## Canonical platform gates (local, 2026-09-29)

The canonical automation platform (T05–T41) is verified by deterministic gates, run locally on the closing commits of each phase and re-runnable at any time:

- `python3 scripts/check_canonical_final_audit.py` — the closing audit: every canonical gate ships a unittest, stays wired into CI and passes on the current tree; the inventory closes every phase T05–T39 with the retirement ledger. Passed with all 40 gates green.
- `python3 scripts/check_canonical_release_readiness.py` — every canonical gate passes, the T26+ statuses are recorded, the changelog carries an Unreleased section and the working tree is clean. Passed on the closing commits.
- `./gradlew :domain:testDebugUnitTest --tests "com.nexaflow.domain.canonical.*" --console=plain` and `./gradlew :core:plugin-sdk:testDebugUnitTest --tests "com.nexaflow.core.pluginsdk.*" --console=plain` — the canonical model and plugin SDK suites. Passed (plugin-sdk: 33 tests including the 13 T41 condition-contract tests).

These are JVM-local results. Device/OEM behavior remains governed by the standing rule: do not infer device results from JVM tests; physical-device evidence is recorded in the dated sections below.

The candidate combines published v3.73 main with preserved local work, then applies the supplied external-ingress audit and data/sensor/HTTP improvements. Original local work is retained in the `codex/security-audit-snapshot` branch; the release integration is in `codex/security-capability-release`.

Local checks run on 2026-09-17:

- `./gradlew :core:datastore:testDebugUnitTest --no-daemon --max-workers=1` — passed.
- `./gradlew :core:execution:testDebugUnitTest --no-daemon --max-workers=1` — passed after making the queued-deadline test use an injected clock.
- `./gradlew detekt lintDebug --continue --max-workers=2` — passed.
- `./gradlew testDebugUnitTest :app:assembleDebug :app:processReleaseManifest --continue --max-workers=2` — passed.

The final broad unit/build run reported `BUILD SUCCESSFUL in 3m 12s` with 844 actionable tasks, 15 executed and 829 up-to-date. The quality run reported `BUILD SUCCESSFUL in 1m 24s` with 1009 actionable tasks, 44 executed and 965 up-to-date.

CI commit/run links, release artifact URLs and tag status must be recorded after the push and release workflow complete. Device/OEM testing and the framework-permission instrumentation suite have not been run in this session. Do not infer those results from JVM tests.


## USB diagnosis, 2026-09-18

A connected 23049PCD8G running Android 17/API 37 and NexaFlow v3.74.3 reported Root available and Shizuku not granted. Process-scoped logcat contained repeated TelephonyCallback RejectedExecutionException messages against shutting-down or terminated executors, with thousands of completed callback tasks. Source review found unconditional listener re-registration on active-data-subscription delivery, including repeated initial values. The correction suppresses unchanged subscription delivery and tolerates callback submission after shutdown. This is device evidence of the original failure; installation and physical-device retesting of the correction are still pending. Raw device logs are not included in the repository.

Recovery-health domain tests and execution-history DAO regression tests passed locally. Automation-details and history Kotlin compilation passed. Recovery warnings now remain visible independently of failed-run counts, and legacy deferrals are consistently filtered and localized in history.

## USB diagnosis follow-up — v3.74.5 candidate

The v3.74.4 installation was verified on the USB device. Android 17 accepted exact, idle-safe time alarms for the configured 22:00 start and 06:00 end window, so the scheduler permission and Doze delivery path are operational. The historical execution record showed a removed hidden hotspot API (`WifiManager#setWifiApEnabled`) under Shizuku; v3.74.5 replaces it with the typed WifiShell Soft AP operation. A previous process instance was also killed by Android after excessive Binder traffic; the telephony re-registration correction is present in v3.74.4, and ongoing device observation remains required to establish long-running stability.

The v3.74.5 CI build exposed a module-boundary error before packaging: the automation-details UI attempted to inject the DataStore-backed execution ledger directly. v3.74.6 routes the explicit recovery-backlog reset through `ExecutionEngine`, the established owner of that ledger. The fix restores the compile path without changing reset semantics: records are discarded only after user confirmation, never replayed or reported as successful.

The recovery ledger remains fail-safe: unresolved side effects are never replayed implicitly. v3.74.5 supplies an explicit, confirmed, per-routine reset so a legacy recovery backlog can be acknowledged without blocking future scheduled runs for that routine. The device was unavailable for final on-device hotspot command validation after the source change; CI and the focused unit tests remain the release gates.
