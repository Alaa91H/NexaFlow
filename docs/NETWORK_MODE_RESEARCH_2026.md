# Network Mode Root Research — 2026-08-27

## Official API baseline

The Android `TelephonyManager` reference lists `createForSubscriptionId`, `getAllowedNetworkTypesForReason`, and `setAllowedNetworkTypesForReason` as public methods, alongside separate USER and CARRIER allowed-network-type reasons. This confirms that network-mode state is subscription-scoped and reason-scoped; it does **not** establish that an ordinary application, including one whose process can execute root commands, has permission to invoke the binder API directly.

The implementation must therefore retain a layered design: attempt a subscription-specific public read/write when it is actually allowed; use a root backend only through closed typed commands; read back the same subscription after a write; and report an explicit unavailable/unconfirmed result rather than showing a fabricated preferred-network choice. A shell command or Android manifest declaration alone is not proof that a particular OEM modem/RIL accepts or persists a requested network mask.

## Source

[1]: https://developer.android.com/reference/android/telephony/TelephonyManager "Android Developers — TelephonyManager"

## API-level verification

The official Android reference identifies `TelephonyManager.setAllowedNetworkTypesForReason(int, long)` as **added in API level 33**. The existing Android 13 (`TIRAMISU`) guard around the direct public setter is therefore correct and must not be widened to Android 12. The same reference states that callers must pin a `TelephonyManager` to a specific subscription using `createForSubscriptionId` for subscription-specific calls.

The direct setter is not a universal root substitute: Android permission enforcement occurs in the target telephony service, and app-process root does not transform the app's normal binder identity. The reliable fallback must remain a root/privileged command that targets the correct modem/slot and then obtains an independent read-back for the same subscription.

[2]: https://developer.android.com/reference/android/telephony/TelephonyManager#setAllowedNetworkTypesForReason(int,long) "Android Developers — setAllowedNetworkTypesForReason"
[3]: https://developer.android.com/reference/android/telephony/TelephonyManager#createForSubscriptionId(int) "Android Developers — createForSubscriptionId"

## AOSP shell-command verification

AOSP `TelephonyShellCommand` declares and dispatches the exact `cmd phone get-allowed-network-types-for-users` and `cmd phone set-allowed-network-types-for-users` subcommands. Thus NexaFlow's closed command names are aligned with AOSP; a generic replacement with `settings put global preferred_network_mode` would be incorrect for current Android and multi-SIM operation.

However, the command belongs to the device's TelephonyShell implementation. Its presence, option support, permission behavior, and output format can differ on OEM builds. NexaFlow must surface the actual failed command/read-back diagnostic and keep a result unconfirmed when the same selected subscription cannot be parsed and verified. This is safer than declaring root capability solely because `su` succeeds.

[4]: https://android.googlesource.com/platform/packages/services/Telephony/+/master/src/com/android/phone/TelephonyShellCommand.java "AOSP — TelephonyShellCommand"

## Comparable implementation review

Two open-source network-mode applications corroborate the engineering boundary. `NetworkSwitch` uses separate Root and Shizuku control paths, while `NetToggle` combines privileged execution, SIM/slot targeting, capability filtering, and user-visible diagnostics. `NetToggle` explicitly states that results remain dependent on the device, ROM, modem, SIM setup, and carrier configuration. These projects support adding clearer local diagnostics and maintaining per-SIM read-back; they do **not** justify pretending that there is a universally successful command or that a root grant removes all carrier/RIL restrictions.

[5]: https://github.com/aunchagaonkar/NetworkSwitch "NetworkSwitch — root/Shizuku network-mode control"
[6]: https://github.com/Dhangofa/NetToggle "NetToggle — root/Shizuku network-mode control and diagnostics"

## Android 17 follow-up: unreadable-mask root cause and compatibility boundary

The Android 17 device returned the local diagnostic: `SIM 1: The privileged read returned no supported cellular mask`. The privileged route can validly return a binary, decimal, or pipe-separated AOSP RAT value such as `LTE|NR`. NexaFlow already parsed all three formats, but its selectable `NetworkTypeBitMask` constants used `1 << NETWORK_TYPE` rather than AOSP's `1 << (NETWORK_TYPE - 1)`. Consequently, a valid LTE/NR mask was intersected with a differently shifted selectable mask and could become zero, producing the reported diagnostic. This is a local mapping defect; it neither proves nor disproves root, `READ_PHONE_STATE`, modem, SIM, or carrier capability. [7] [10]

The correction aligns GSM/GPRS/EDGE, UMTS/HSPA, CDMA/EVDO, TD-SCDMA, LTE/LTE-CA, NR, and IWLAN positions with the AOSP definition. Regression tests pin AOSP LTE (`1 << 12`), LTE-CA (`1 << 18`), NR (`1 << 19`), pipe-separated names, decimal output, and binary output. The existing subscription-scoped public and closed elevated read/write paths keep their same-subscription read-back requirement; `settings put global preferred_network_mode` remains excluded from the modern write path. [7] [10]

Dynamic `network_mask` values written before this correction are deliberately **not** reinterpreted. A legacy shifted LTE profile can overlap a corrected LTE-plus-NR profile, so an automatic data migration could change the user's intended radio restriction. New dynamic selections carry an explicit `aosp-network-type-bitmask-v1` marker. A stored dynamic value without that marker fails safely with a request to reopen the task and select its profile again. Traditional `mode` actions with no dynamic mask continue to use the corrected named profile mapping, and raw per-subscription restore snapshots remain untouched because they capture masks read from the device rather than locally generated family constants.

A source comparison confirms that practical root/Shizuku projects use per-SIM targeting and fallbacks, but none establishes a universal OEM guarantee. NetworkSwitch uses reflective `getAllowedNetworkTypesForReason` and falls back to reflective `getPreferredNetworkType`; NetToggle reads OEM `preferred_network_mode<subId>` values as a compatibility signal. NexaFlow retains its closed, per-subscription, read-verified semantics and keeps unknown output unavailable. A legacy `getPreferredNetworkType` fallback is not added in this release because a RIL mode is a different integer domain and requires a separately reviewed closed mapping before it can safely become a displayed or writable bitmask. [8] [9]

[7]: https://android.googlesource.com/platform/packages/services/Telephony/+/refs/heads/main/src/com/android/phone/TelephonyShellCommand.java "AOSP TelephonyShellCommand"
[8]: https://github.com/aunchagaonkar/NetworkSwitch "NetworkSwitch source reference"
[9]: https://github.com/Dhangofa/NetToggle "NetToggle source reference"
[10]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/android/telephony/TelephonyManager.java "AOSP TelephonyManager NetworkTypeBitMask"
