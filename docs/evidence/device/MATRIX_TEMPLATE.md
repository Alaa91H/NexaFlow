# Device acceptance matrix — TEMPLATE

Fill one copy per build. Do not reuse a matrix across builds or devices.

## Build under test

| Field | Value |
|---|---|
| Version name / code | `<e.g. 3.60.0 / 3060000>` |
| Commit | `<40-char sha>` |
| Variant | `release` / `debug` |
| Signing cert SHA-256 | `<sha256>` |
| Mapping artifact | `<release asset name or n/a>` |

## Devices

| # | Model | OEM | Android / API | ABI | Root | Shizuku | Notes |
|---|---|---|---|---|---|---|---|
| 1 | `<reference device>` | `<oem>` | 15 / 35 | arm64-v8a | no | granted | reference device |
| 2 | `<oem-a model>` | `<oem-a>` | `<ver>` | arm64-v8a | no | no | vendor skin restrictions |
| 3 | `<oem-b model>` | `<oem-b>` | `<ver>` | arm64-v8a | no | granted | aggressive battery saver |
| 4 | `<API 26 device>` | `<oem>` | 8.0 / 26 | arm64-v8a | no | no | minSdk floor |

## Scenario matrix

Every cell must be `PASS`, `FAIL` or `BLOCKED` with a log reference.

| Scenario | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| Fresh install + onboarding completes | | | | |
| Exact alarm permission denied -> degraded schedule still fires | | | | |
| Exact alarm permission granted -> schedule fires on time | | | | |
| Boot (`BOOT_COMPLETED`) restores enabled tasks | | | | |
| Force-stop then reopen restores the schedule | | | | |
| Doze: task due inside maintenance window runs | | | | |
| Doze: task outside window is deferred, never silently dropped | | | | |
| Timezone change re-bases the next run | | | | |
| Manual clock change does not duplicate or skip a run | | | | |
| Foreground service survives 30 minutes of backgrounding | | | | |
| Shizuku granted: privileged action succeeds | | | | |
| Shizuku revoked mid-run: failure is reported, no retry storm | | | | |
| Root granted: privileged action succeeds | | | | |
| Root denied: task reports a clear, non-crashing failure | | | | |
| Notification permission denied: task still runs, no crash | | | | |
| Accessibility/SMS-dependent task without the permission is blocked explicitly | | | | |
| Airplane-mode condition trigger fires and clears | | | | |
| Kill during execution -> recovery review item on next launch | | | | |
| Backup export -> reinstall -> import restores tasks disabled | | | | |
| Arabic RTL + TalkBack: main screens usable | | | | |

## Evidence per run

- `logcat-<device>.txt` (filtered to `com.nexaflow`, sanitized)
- `dumpsys-alarm-<device>.txt`
- `dumpsys-deviceidle-<device>.txt` (Doze rows)
- screenshots for RTL/TalkBack rows
- diagnostic bundle JSON for every FAIL row

## Sign-off

| Role | Name | Date | Result |
|---|---|---|---|
| Device runner | | | |
| Reviewer | | | |
