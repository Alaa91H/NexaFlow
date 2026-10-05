# P0 baseline refresh — 2026-10-05

**Checkout:** `66f0dbd1327365b770901b2fa7d8d528a531ae9b` (`v3.91.10`, `main` / `origin/main`).
**Purpose:** refresh the supplied Master Execution Plan's P0 evidence on the current checkout.
**Scope:** repository checks, source inventory, local Android build/test, current CI evidence, and device availability. This report does not claim device certification.

## Results

| Check | Command / evidence | Result |
|---|---|---|
| Resource hygiene | `python scripts/auto_fix.py --check` | PASS — no orphaned, duplicate, or unbalanced strings. |
| Locale parity | `python scripts/check_strings_parity.py` | PASS — `PARITY_PROBLEMS: 0`. |
| Trigger/action catalog | `python scripts/audit_catalog_and_releases.py catalog` | PASS — 57 triggers (55 exposed) and 180 actions, builder parity OK. |
| Suppression budget | `python scripts/check_suppression_budget.py` | PASS — 83 `Suppress`, 42 `SuppressLint`. |
| README count drift | `python scripts/check_readme_stale_counts.py` | PASS — catalog/schema totals are not hard-coded. |
| Gradle project inventory | `./gradlew projects --console=plain` | PASS — 49 seconds, 36 included Gradle projects. Fresh Kotlin/Java main-source LOC matched `module-inventory.md`. |
| Device availability | `adb devices -l` | NOT TESTED — device list was empty. |
| Detekt, Lint, JVM tests | `./gradlew detekt lintDebug testDebugUnitTest --console=plain` with `ANDROID_HOME=C:\Users\Alaa\AppData\Local\Android\Sdk` | PARTIAL — Detekt and all debug Lint tasks completed; overall command failed at `:core:datastore:testDebugUnitTest` after 23m06s. Detekt emitted five `TooGenericExceptionCaught` warnings. |
| DataStore tests on Windows | `:core:datastore:testDebugUnitTest` in the command above | FAIL — 46 tests, 5 failures caused by DataStore failing to rename a `.tmp` file onto an existing target. Failures: `AgentNetworkPreferencesTest.explicitLanChoicePersists`; `AiProviderPreferencesTest.v1MigrationPreservesValidProfilesWhenOneEntryIsCorrupt`; `SmsDeliveryStoreTest.cooldownIsDurableAcrossDifferentMessages`; `SmsDeliveryStoreTest.samePhysicalMessageCanBeClaimedByDifferentAutomations`; `SmsDeliveryStoreTest.duplicateFingerprintIsRejectedAcrossClaims`. Full output: [`p0-01-gradle-windows-2026-10-05.log`](p0-01-gradle-windows-2026-10-05.log). |
| Same commit in hosted CI | [Tag CI run 37258291959](https://github.com/Alaa91H/NexaFlow/actions/runs/37258291959), exact commit `66f0dbd` | PASS on Ubuntu — secret scan, lint, coverage, tests, build, and tagged release all succeeded. This supports a Windows-specific filesystem incompatibility diagnosis; it does not make the local Gradle invocation green. |
| APK build | `./gradlew assembleDebug assembleRelease -PallowDebugSigning=true --console=plain` with the same SDK environment | PASS — 31m48s; full build output: [`p0-01-assemble-windows-2026-10-05.log`](p0-01-assemble-windows-2026-10-05.log). |
| APK metadata and signing | `apksigner verify --verbose --print-certs` and `aapt dump badging` on phone/Wear debug and release APKs | PASS — all four APKs verify with APK Signature Scheme v2; all report `versionName=v3.91.10`. Local artifacts use the Android Debug certificate because `-PallowDebugSigning=true` was explicit; they are disposable and not production-signed. |
| Full-history secret scan | `docs/evidence/baseline/gitleaks-2026-10-05.md`; checksum-pinned CI gate | PASS — 2,221 commits scanned, exact reviewed fingerprints only; hosted runs 37254899900, 37256522529, and 37258291959 passed. Native GitHub secret scanning remains disabled. |

## P0 status

- **P0-01 — PARTIAL:** static checks, Detekt, debug Lint, Android debug/release builds, and hosted Ubuntu CI have evidence. The complete local Windows Gradle command is nonzero due to the five DataStore rename failures, so this host's full suite is not declared green.
- **P0-02 — PARTIAL:** the current Gradle project and LOC inventory is refreshed. The action dispatch test establishes registry coverage, but the complete 57-trigger source/permission/runtime/device acceptance matrix is not established.
- **P0-03 — PARTIAL:** source and tests show both the in-app LLM/tool path and external agent gateway. Live provider calls and on-device approval/action/history round trips remain untested.
- **P0-04 — NOT TESTED:** no Android device was connected. No current macrobenchmark, Perfetto trace, battery measurement, `ApplicationExitInfo` review, or soak data was collected.
- **P0-05 — PARTIAL:** full-history Gitleaks and its CI gate passed; GitHub native secret scanning/push protection remains disabled.
- **P0-06 — NOT COMPLETE:** performance targets cannot be calibrated without P0-04 device measurements; `ROADMAP_BASELINED.md` has not been signed.

No production source, schema, permissions, or runtime behavior was changed for this baseline refresh. Remaining external dependencies are tracked in `docs/OPEN_QUESTIONS.md`.
