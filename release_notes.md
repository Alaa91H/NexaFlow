# NexaFlow v3.47.0

NexaFlow v3.47.0 hardens the existing cellular network-mode action for real dual-SIM devices. It makes the distinction between a SIM's **configured user restriction**, its **known effective restriction**, and the device's **current radio technology** explicit, while preserving NexaFlow's conservative, device-derived mode selection.

## What changed

The Network Mode editor now identifies the active data SIM and uses it as the default for a newly configured action. A previously saved, still-active SIM remains the user's explicit choice. This avoids confusing a physical slot number with a subscription ID and improves predictable behavior when a phone has two active SIMs.

When Android exposes both the USER and CARRIER allowed-network-type reasons, NexaFlow shows their known intersection as the effective restriction. The configured USER mask and the effective mask are intentionally separate. The app does not fabricate an effective value from the currently registered RAT, because a live LTE or NR connection is not proof of the configured allowed-network-types mask.

Dynamic network-mode actions are now summarized with their device-confirmed radio families instead of being shown incorrectly as **Auto**. Applying this action is also explicitly dispatched on `Dispatchers.IO`, so telephony binder work and elevated Root/Shizuku processes cannot block the workflow caller's main thread.

## Reliability and safety

Legacy all-SIM actions no longer fall back to a synthetic subscription ID when Android cannot provide an active subscription list. NexaFlow now fails safely with a clear phone-state-permission reason rather than risking a network-mode write to an inferred SIM.

The capability reader retains its existing native-first, elevated fallback design and now also uses the reviewed Root/Shizuku USER-mask read-back when the framework exposes selectable hardware/carrier support but blocks the app-level USER getter. Stored dynamic modes remain numeric masks only at the platform boundary; the editor continues to derive choices from confirmed device data rather than from a universal 2G/3G/4G/5G list.

> Android's public `setAllowedNetworkTypesForReason` API requires `MODIFY_PHONE_STATE` or carrier privileges, and its getter requires privileged phone-state access or carrier privileges. Root or Shizuku availability therefore remains a capability to try and verify—not a promise that a carrier, modem, OEM build, or system interface will accept a requested configuration.

## Verification

The functional release candidate passed the full GitHub Actions pipeline, including resource hygiene and locale parity, Python resource-gate tests, Detekt, Android Lint, Android unit tests, debug and release APK builds, release AAB build, dependency verification, APK permission and signature checks, zip alignment, 16 KB native-library alignment, and bundle validation.

During release validation, CI identified and the release fixed an Android-resource escaping issue in the Turkish Data SIM label and made the explicit phone-state permission guard visible to Android Lint. No local Gradle build was performed; all build and test acceptance is provided by the remote pipeline.

## Scope and limitations

This release adds no account, cloud service, telemetry, new runtime permission, hidden permission, schema migration, automated retry, vendor-specific command, or user-supplied shell execution. It does not represent a current RAT as proof of a configured radio mode, nor does it claim universal unprivileged control over Android telephony.

The supporting Android/AOSP, Shizuku, and open-source compatibility research, accepted scope, and deferred device-specific work are recorded in [`docs/RESEARCH_2026.md`](docs/RESEARCH_2026.md).
