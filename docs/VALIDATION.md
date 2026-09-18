# Validation record — v3.74 candidate

Status: local integration checks passed. CI and publication are still pending until the commit, push, tag and release workflow complete.

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
