# NexaFlow v3.50.0 — Root Network Diagnostics and Dedicated Hotspot Trigger

**Release date:** 2026-08-27
**Release scope:** Root-assisted cellular capability diagnostics, a dedicated Hotspot trigger, and a simplified new-task connectivity picker.

## Overview

v3.50.0 resolves a recurring usability failure in which the cellular network-mode editor could only report that supported modes were unavailable, even after the user had granted elevated access. The release does not invent device capabilities or declare a radio-mode write successful solely because a root command launched. Instead, it makes the privileged read path diagnosable, refreshes the capability card after the targeted root permission flow completes, and preserves same-subscription read-back verification for every network-mode write.

The release also separates **Hotspot** into its own `ON` / `OFF` trigger. The legacy combined **Connectivity** trigger is intentionally hidden from the picker for new tasks because its former menu duplicated dedicated Hotspot and Cellular Network functionality. Existing saved Connectivity automations remain compatible and are neither migrated nor rewritten.

## Delivered changes

| Area | Delivered behavior |
|---|---|
| Root network-mode capability read | `NetworkModeSnapshot` carries a bounded, local-only diagnostic when the app cannot derive a confirmed mode mask. The editor distinguishes missing `READ_PHONE_STATE`, unavailable elevated access, a failed privileged read, and an unparseable returned cellular mask. |
| Root permission refresh | When Shizuku is not the live path, the cellular-mode editor invokes the targeted root prompt and runtime-phone-state permission flow. It then reloads its capability state instead of retaining a stale unreadable snapshot. |
| Write verification | The existing privileged write → read-back flow remains mandatory and subscription-scoped. A non-confirmed radio mode remains an observable failure; it is not shown as applied. |
| Hotspot trigger | New `TriggerType.HOTSPOT` supports `ON` / `OFF`, maps to the existing connectivity source, participates in monitor and manual evaluation, and is rendered in the builder, dashboard, and routine details. |
| Simplified picker | The legacy combined Connectivity item is no longer addable for new tasks. Dedicated Hotspot and Cellular Network choices remain available. Legacy Connectivity records can still be viewed, edited, and executed. |
| Unknown-safe handling | A failed manual `tether_on` read stays `UNKNOWN`, not `OFF`, preventing a read failure from matching a negative state or synthesizing an end path. |
| Localization | Added dashboard labels for Hotspot across every shipped locale and corrected Android escaping for the French string. |

## Verification

The implementation commit was accepted by the complete remote GitHub Actions pipeline: [run 33061943012](https://github.com/Alaa91H/NexaFlow/actions/runs/33061943012). The successful workflow ran resource hygiene and locale-parity checks, resource-gate tests, Detekt, Android Lint, the Android unit-test suite, debug/release APK builds, release AAB build, dependency verification, packaged-permission verification, APK signing verification, native-library 16 KB alignment checks, zip alignment, and bundle validation.

> No local Gradle build, lint task, or unit test was run. All Gradle acceptance was performed remotely in GitHub Actions.

## Compatibility and operational limits

Android exposes public subscription-scoped allowed-network-type APIs from API 33, while privileged shell access depends on the ROM's telephony service. AOSP accepts `root` or `shell` for its `cmd phone` allowed-network-type commands, but availability, output format, carrier restrictions, radio persistence, and resulting mode support can still vary by OEM, modem, SIM, and carrier. NexaFlow therefore presents actual local diagnostic evidence and keeps unavailable/unparseable results unavailable; it does not fall back to an unsafe global setting or claim universal baseband control. [1] [2]

No database migration, new runtime permission declaration, telemetry, cloud service, or user-data collection is introduced. Granting root remains entirely under the device's root manager. The application only requests the already-declared `READ_PHONE_STATE` grant when the user explicitly starts the elevated network-mode recovery flow.

## Upgrade guidance

After installing v3.50.0, open a Cellular Network action and tap the elevated-access control if the card reports that `READ_PHONE_STATE` is not granted. Once the root manager returns, the capability card refreshes automatically. If the result reports a failed privileged read or an unreadable mask, retain the diagnostic when comparing the device's ROM, modem, SIM, and carrier configuration; do not treat the presence of root alone as a guarantee that the telephony service exposes a supported control path.

Create new tethering automations through **Hotspot** under Connectivity and choose **On** or **Off**. Existing combined Connectivity rules continue to load for compatibility; users can replace them intentionally with dedicated rules when appropriate.

## References

[1]: https://developer.android.com/reference/android/telephony/TelephonyManager#setAllowedNetworkTypesForReason(int,long) "Android Developers: setAllowedNetworkTypesForReason"
[2]: https://android.googlesource.com/platform/packages/services/Telephony/+/master/src/com/android/phone/TelephonyShellCommand.java "AOSP: TelephonyShellCommand"
[3]: https://github.com/Dhangofa/NetToggle "NetToggle: comparable Root/Shizuku diagnostic approach"
[4]: https://github.com/aunchagaonkar/NetworkSwitch "NetworkSwitch: comparable Root/Shizuku control approach"
