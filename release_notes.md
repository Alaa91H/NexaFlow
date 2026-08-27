# NexaFlow v3.50.2 — Automatic Schedule Reconciliation and Cellular Capability Profiles

**Release date:** 2026-08-27
**Release scope:** Recovery of automatically scheduled time automations after verified exact-alarm access, explicit range-end delivery declaration, and evidence-based cellular profile discovery for Android 17 rooted devices.

## Overview

v3.50.2 addresses two connected production problems. First, user-defined wall-clock tasks could remain unscheduled after Android had canceled exact alarms and the elevated permission repair succeeded without a framework permission-grant broadcast. Second, the Cellular Network action could show **GSM** as its only choice when Android exposed a GSM-only `USER` configuration, even though the modem itself supported LTE or NR.

Android treats `SCHEDULE_EXACT_ALARM` as special access. It is denied by default for most fresh Android 13+ installations, revocation cancels future exact alarms, and applications must re-check access and rebuild required alarms after a grant. [1] [2] NexaFlow now performs that durable rebuild after its elevated permission pipeline has verified exact-alarm access. The existing immutable occurrence ledger remains the single source of truth for scheduled `START` and paired `END` work.

## Delivered changes

| Area | Delivered behavior |
|---|---|
| Verified exact-alarm recovery | After the Root/Shizuku all-permissions flow completes final platform verification, NexaFlow sends a package-scoped recheck signal on Android 12+. `AutomationAlarmReceiver` handles it through the same durable reconciliation path used for boot, clock, time-zone, package-replace, and framework exact-alarm-change events. Enabled time automations therefore rebuild their tracked `START` and `END` alarms without waiting for a process restart. |
| Scheduled range end | `END_AUTOMATION` is now declared explicitly in the application receiver intent filter beside `RUN_AUTOMATION`. The receiver keeps occurrence-id, generation, and end-time validation before `ExitCoordinator` dispatches configured end behavior through the normal `ExecutionEngine` path. |
| Cellular capability source | The dynamic picker no longer treats the current `USER` allowed-network-types restriction as the hardware menu. A GSM-only current restriction therefore does not by itself hide LTE or NR options. |
| Elevated modem read | With a live Shizuku UserService, NexaFlow attempts the AOSP binder-equivalent `ITelephony.getRadioAccessFamily(slot)` capability read. If it is unavailable, the reviewed elevated operation reads only `ro.telephony.default_network` and maps known AOSP RIL modes 0–33 through a closed table. [3] [4] |
| Safety and verification | Malformed, vendor-specific, unknown, or multi-SIM slot-ambiguous property values remain unavailable. The picker never creates a universal profile list, and network writes still require selected-SIM read-back confirmation before a success is recorded. |
| Regression coverage | Added deterministic unit coverage for exact-alarm recheck gating, the private receiver action, the closed profile-read operation, slot-aware property parsing, and AOSP RIL mappings for modes 9, 22, 23, and 24. |

## Operational behavior

When Android grants exact-alarm access, NexaFlow uses the existing `RTC_WAKEUP` and `setExactAndAllowWhileIdle` path for a user-selected wall-clock time. This is the appropriate scheduling mechanism for punctual, user-visible time automation where access is present. [1] The new recovery signal does not replace an alarm with an in-process timer, does not insert an arbitrary delay, and does not manufacture a new occurrence identity.

If exact-alarm access is denied or later revoked, Android governs delivery through its documented inexact fallback behavior. That fallback cannot truthfully promise an on-the-minute execution time; NexaFlow does not label it as such. [1] [2] The permissions screen continues to expose the system exact-alarm access route, while a successful elevated repair now triggers re-arming immediately after verification.

The cellular fallback improves discovery, not the authority of a carrier. A modem-default profile is evidence of a device radio configuration, whereas available service can still be constrained by the active SIM, carrier policy, modem firmware, OEM telephony implementation, region, or a different allowed-network-types reason. The selected subscription is preserved and post-write confirmation remains mandatory. There is no global `settings put preferred_network_mode` write path.

## Compatibility and safety

Existing saved automations are not deleted or automatically rewritten. The v3.50.1 dynamic-mask schema guard remains in effect: a pre-v3.50.1 saved dynamic `network_mask` without the AOSP marker is rejected until the user intentionally selects the desired profile again. Legacy named `2G`, `3G`, `4G`, `5G`, and `AUTO` actions continue to use the corrected public bitmasks.

> Root or Shizuku gives NexaFlow access only to reviewed operations. It cannot guarantee a readable or persistently writable network mask on every ROM, modem, carrier, or SIM. An unavailable or unverified result remains visible rather than being converted into a false success.

## Upgrade guidance

Install v3.50.2, then open **Permissions** and confirm that **Alarms & reminders** is allowed. On a rooted device, running the verified permission repair also causes all enabled time automations to be re-armed immediately. Keep the exact-alarm access enabled if a time such as 06:00 must be delivered punctually while the device is idle.

For the Cellular Network action, reopen the action configuration after the capability card has loaded. The picker now requests a capability source independent of the current GSM/LTE/NR `USER` restriction. Select only the profile you intend to use, and retain the post-change result message: a ROM/carrier refusal is correctly reported as a failed or unavailable change rather than an applied profile.

## Verification

The release candidate is accepted only after the complete remote GitHub Actions pipeline passes Android unit tests, Detekt, Android Lint, debug/release APK and AAB builds, dependency and packaging verification, signing verification, alignment checks, and bundle validation. No Gradle build, lint task, or unit test is run locally.

## References

[1]: https://developer.android.com/develop/background-work/services/alarms "Android Developers: Schedule alarms"
[2]: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms "Android Developers: Schedule exact alarms are denied by default"
[3]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/android/telephony/TelephonyManager.java "AOSP: TelephonyManager getSupportedRadioAccessFamily"
[4]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/android/telephony/RadioAccessFamily.java "AOSP: RadioAccessFamily network-mode mapping"
[5]: https://android.googlesource.com/platform/packages/services/Telephony/+/refs/heads/main/src/com/android/phone/PhoneInterfaceManager.java "AOSP: PhoneInterfaceManager getRadioAccessFamily"
