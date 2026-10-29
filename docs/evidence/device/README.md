# Device evidence

Emulator runs (`.github/workflows/android-instrumentation.yml`) prove the suite
compiles and passes on AOSP-like images. They do **not** prove OEM behaviour,
Doze, boot persistence, foreground-service survival, Shizuku/Root grants or a
72-hour soak. Those require the physical matrix described in the remediation
plan (`docs/audit/FULL_AUDIT_REMEDIATION_PLAN_2026-10-28_AR.md`, NFA-01).

## Status

`BLOCKED` — no physical device or OEM image is available in this environment
(`adb devices` is empty). The templates below are ready; the rows stay empty
until a device is attached.

## How to record a run

1. Copy the templates into a dated directory: `docs/evidence/device/<YYYY-MM-DD>/`.
2. Fill one `matrix.md` per build under test and one `soak.md` per 72-hour run.
3. Attach `logcat` (sanitized), the build's `versionName`/`versionCode`, the
   signing certificate SHA-256 and the device fingerprint.
4. Mark every cell `PASS`, `FAIL` or `BLOCKED`; never leave a cell blank and
   never infer a result from a different device.

Sanitize before committing: remove phone numbers, SMS bodies, account names,
webhook URLs and agent tokens. The app's own diagnostic bundle
(`Settings -> Diagnostics -> Export diagnostic bundle`) already redacts
credentials and is the preferred log attachment.
