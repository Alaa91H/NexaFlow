# 72-hour soak — TEMPLATE

One copy per run. A soak is only evidence when the device stayed on the same
build for the full window and kept at least one enabled task with a periodic
trigger.

## Configuration

| Field | Value |
|---|---|
| Start (UTC) | |
| End (UTC) | |
| Build version / commit | |
| Device + Android version | |
| Enabled tasks | `<count>` (`<ids>`) |
| Trigger cadence | `<e.g. every 15 min>` |
| Charging policy | `<always plugged / battery only>` |
| Battery optimisation | `<exempt / not exempt>` |

## Samples (every 6 hours)

| Hour | Battery % | PSS (MB) | Run count (expected / actual) | Crashes | ANRs | Notes |
|---|---|---|---|---|---|---|
| 0 | | | | | | |
| 6 | | | | | | |
| 12 | | | | | | |
| 18 | | | | | | |
| 24 | | | | | | |
| 30 | | | | | | |
| 36 | | | | | | |
| 42 | | | | | | |
| 48 | | | | | | |
| 54 | | | | | | |
| 60 | | | | | | |
| 66 | | | | | | |
| 72 | | | | | | |

Collection commands:

```bash
adb shell dumpsys batterystats com.nexaflow.app > batterystats.txt
adb shell dumpsys meminfo com.nexaflow.app > meminfo.txt
adb shell dumpsys alarm | grep -A 5 com.nexaflow.app > alarms.txt
adb logcat -d -s NexaFlow:* AndroidRuntime:E > logcat-soak.txt
```

## Pass criteria

- expected runs within ±10% of the configured cadence (Doze windows excluded
  and listed explicitly),
- no crash and no ANR,
- PSS growth over 72h below 20% after the first hour,
- battery drain attributable to the app below the agreed budget,
- no duplicate runs and no run recorded twice after a device restart.

## Result

`PASS` / `FAIL` / `BLOCKED` — with the reason and the log references.
