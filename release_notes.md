# NexaFlow v3.50.1 — Time-Range End Recovery and Android 17 Network-Mask Correction

**Release date:** 2026-08-27
**Release scope:** Reliable re-arming of active time-range end alarms, verified restoration of the normal ringer mode, and corrected Android cellular network-type bitmasks.

## Overview

v3.50.1 addresses a concrete overnight automation failure: a time range could set the device to silent at 22:00 yet fail to reach its configured end behavior at 06:00 after Android alarm loss and schedule reconciliation. The release preserves the immutable occurrence that owns the active range, restores only its matching future `END` alarm, and keeps the normal end dispatcher unchanged. A dedicated regression test verifies that a configured `SYSTEM_RINGER_MODE` end value of `{mode=NORMAL}` is dispatched exactly once through the same `ActionRegistry` route used by ordinary actions.

The release also corrects the one-bit offset in NexaFlow's `TelephonyManager.NetworkTypeBitMask` family constants. The defect could turn a valid privileged Android 17 LTE/NR read into an empty selectable mask and display the diagnostic that no supported cellular mask was returned. The corrected mapping follows AOSP's `1 << (NETWORK_TYPE - 1)` rule for GSM/GPRS/EDGE, UMTS/HSPA, CDMA/EVDO, TD-SCDMA, LTE/LTE-CA, NR, and IWLAN. [1]

## Delivered changes

| Area | Delivered behavior |
|---|---|
| Active time-range END alarm | During `scheduleFresh`, NexaFlow now re-arms the existing future `END` alarm only when the durable `ACTIVE` lifecycle and stored schedule occurrence match on automation id, occurrence id, generation, and expected end time. The original `PendingIntent` identity is reused; no new occurrence is generated and no exit is executed while re-arming. |
| Overnight regression coverage | Tests cover the retained 22:00–06:00-style occurrence and explicitly reject elapsed, exiting, or generation-mismatched records. |
| Unified end-action dispatch | A new coordinator test proves that `TIME_WINDOW_ENDED` dispatches `SYSTEM_RINGER_MODE` with the configured normal-mode payload once through `ExecutionEngine.runExit`, `executeAction`, and `ActionRegistry`. |
| Ringer restoration | `SystemController` verifies the framework ringer mode. When Android rejects a return from `SILENT` or `VIBRATE` to `NORMAL`, the elevated path requests only NexaFlow's notification-policy special access with AOSP `cmd notification allow_dnd`, verifies `NotificationManager.isNotificationPolicyAccessGranted`, and retries `AudioManager`. It does not alter the interruption filter or notification policy. [2] |
| Permission grant flow | The root/Shizuku all-permissions flow now uses the same notification-policy command instead of an app-op that can appear successful while leaving Android notification-policy access unavailable. |
| Android 17 cellular masks | Corrected all local `NetworkTypeBitMask` positions and added tests for named (`LTE`, `LTE|LTE_CA`, `NR`), decimal, and binary AOSP read-back values. |
| Dynamic-profile compatibility | New dynamic cellular profiles store an explicit `aosp-network-type-bitmask-v1` marker. A legacy dynamic `network_mask` without that marker is rejected safely until the user reselects the profile; it is never silently remapped to a potentially different radio restriction. |

## Verification

The final main-branch candidate was accepted by the complete remote GitHub Actions pipeline: [run 33069758773](https://github.com/Alaa91H/NexaFlow/actions/runs/33069758773). The successful workflow completed resource hygiene, locale parity, resource-gate tests, Detekt, Android Lint, Android unit tests, debug and release APK builds, release AAB build, dependency verification, packaged-permission verification, APK signing verification, native-library 16 KB alignment checks, zip alignment, and bundle validation.

> No Gradle build, lint task, or unit test was run locally. All Gradle acceptance for this release was performed remotely in GitHub Actions.

## Compatibility and operational limits

Android cancels alarms at shutdown, so the release repairs the specific retained-range re-arm path required after boot or schedule reconciliation. Precise delivery still depends on Android exact-alarm access; where that access is absent, NexaFlow retains its existing inexact idle-safe fallback. [3]

An exit action that returns a failure remains visible as `EXIT_FAILED`. NexaFlow intentionally does not blindly replay a lifecycle already in `EXITING` after process interruption: arbitrary exit effects such as an external command or notification can be uncertain, and a normal service start does not prove a previous coroutine has died. A future automatic `EXITING` recovery requires a durable per-exit action checkpoint and explicit ownership epoch. This release therefore fixes the lost-END path without making an unsafe exactly-once claim across process death.

Root or Shizuku access enables the reviewed operations but does not override device-specific telephony limits. Android/OEM telephony services, modem firmware, carrier policy, SIM configuration, and command/output availability can still prevent a readable or persistently writable allowed-network-types mask. NexaFlow preserves the selected-SIM write-and-read-back contract and keeps an unconfirmed value unavailable rather than reporting success. It does not use the obsolete global `preferred_network_mode` setting as a modern write fallback. [1] [4] [5]

## Upgrade guidance

After installing v3.50.1, keep the desired 22:00–06:00 range enabled and ensure NexaFlow retains exact-alarm access if punctual delivery is required. When a normal-ringer end action runs, the app will verify its result. On a rooted device where Android has not yet approved notification-policy access, the app requests only that app-specific access through the reviewed elevated route; a ROM rejection remains a visible failed action rather than a false success.

For every saved **dynamic** Cellular Network action created before v3.50.1, open the action and select its intended profile again after the capability card has loaded. This writes the new schema marker and avoids ambiguously reinterpreting an old shifted mask. Traditional `2G`, `3G`, `4G`, `5G`, or `AUTO` actions without a stored dynamic mask continue to use the corrected mapping. If Android still reports a failed privileged read or unsupported cellular mask, retain the local diagnostic when checking the ROM, modem, SIM, and carrier path; root alone is not a universal radio-control guarantee.

## References

[1]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/android/telephony/TelephonyManager.java "AOSP: TelephonyManager NetworkTypeBitMask"
[2]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationShellCmd.java "AOSP: NotificationShellCmd allow_dnd"
[3]: https://developer.android.com/develop/background-work/services/alarms "Android Developers: Schedule alarms"
[4]: https://developer.android.com/reference/android/telephony/TelephonyManager#setAllowedNetworkTypesForReason(int,long) "Android Developers: setAllowedNetworkTypesForReason"
[5]: https://android.googlesource.com/platform/packages/services/Telephony/+/refs/heads/main/src/com/android/phone/TelephonyShellCommand.java "AOSP: TelephonyShellCommand"
