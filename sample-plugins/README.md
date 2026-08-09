# NexaFlow Sample Plugins

A collection of **reference Locale-protocol plugins** — complete, buildable,
dependency-free templates that show exactly how to extend NexaFlow (and every
other Locale-compatible host: Tasker, MacroDroid, Automate, Locale) with your
own actions.

## Why a sample folder?

The full developer contract lives in [`docs/PLUGIN_SDK.md`](../docs/PLUGIN_SDK.md).
This folder is the *running code* version of that contract: a real plugin that
does real work with **zero external libraries** (only the Android framework +
`org.json`, which ships inside the OS). It is the fastest way to start a new
plugin — copy, rename, swap the logic, ship.

## Layout

| Path | What it is |
|---|---|
| `nfc-toggle/` | Reference plugin: **NFC on/off** via the `EDIT_SETTING` / `FIRE_SETTING` pair |

### `nfc-toggle/`

| File | Role |
|---|---|
| `LocaleProtocol.kt` | The frozen wire protocol (actions, extras, result codes) inlined so the sample is self-contained |
| `PluginConfig.kt` | Bundle ⇄ JSON config convention using only `org.json` |
| `NfcToggleEditActivity.kt` | `EDIT_SETTING` screen: pick the NFC state, Save returns the config bundle + blurb |
| `NfcToggleFireReceiver.kt` | `FIRE_SETTING` receiver: applies the state, reports OK / `%err`+`%errmsg` |
| `NfcController.kt` | The real work (hidden-API reflection + shell fallback) — swap this for your logic |
| `AndroidManifest.xml` | The protocol's hard manifest rules, annotated line by line |
| `src/test/…` | Robolectric tests for the config round-trip, the receiver's result contract, and the edit flow |

## Build it

The sample is wired into the root build, so the standard gate covers it:

```bash
# Build just the sample APK
./gradlew :sample-plugins:nfc-toggle:assembleDebug

# APK lands at:
#   sample-plugins/nfc-toggle/build/outputs/apk/debug/nfc-toggle-debug.apk
```

Or open the repo in Android Studio and run the `sample-plugins:nfc-toggle`
configuration on a device.

## Try it in NexaFlow

1. Install the APK on your device.
2. Open **NexaFlow → Settings → Plugins** → tap **Refresh** — the plugin is
   discovered through `PackageManager.queryBroadcastReceivers(FIRE_SETTING)`.
3. Or build a task in the **automation builder** and pick **Plugins** in the
   action list, then choose *NFC Toggle*.
4. Configure the NFC state, save, and run the task. The host fires the plugin,
   which toggles NFC and reports the outcome.

You can also test it with Tasker (`Plugin → Locale Plugin`) or MacroDroid
(`Plugins`) — both are free host test harnesses.

## Write your own plugin (template recipe)

1. Copy `nfc-toggle/` and rename the package + `applicationId`.
2. Keep `LocaleProtocol.kt` **byte-for-byte identical** (it is the frozen wire).
3. Replace `NfcController` with your logic — the protocol only cares that you
   return quickly and report a result.
4. Keep the `AndroidManifest.xml` rules unchanged: `installLocation="internalOnly"`,
   `exported="true"`, the two intent-filters.
5. Follow the checklist in [`docs/PLUGIN_SDK.md`](../docs/PLUGIN_SDK.md) §7
   before publishing.

## Notes

- **No external libraries by design.** The template proves the raw protocol
  works without the SDK module (`core/plugin-sdk` is the *convenience* layer;
  this sample shows the underlying contract it automates).
- **NFC needs privileged access.** Toggling NFC is a system-level operation.
  The sample tries the hidden `NfcManager` API first and falls back to the
  shell; on unrooted stock ROMs it reports a clear error instead of failing
  silently. Your plugin can do anything your app can do — the protocol is
  transport-agnostic.
