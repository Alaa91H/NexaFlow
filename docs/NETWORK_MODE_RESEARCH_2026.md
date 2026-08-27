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
