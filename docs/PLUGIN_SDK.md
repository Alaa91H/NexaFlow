# NexaFlow Plugin SDK (Experimental)

> **Status:** ✅ Implemented — `core/plugin-sdk` is merged and the host integration (builder
> picker + execution + plugin manager) is live. The wire protocol itself is frozen by the
> ecosystem; the SDK API surface may still evolve before v1.0 of the SDK. Feedback from early
> developers is welcome.
> Companion to [`ARCHITECTURE.md`](ARCHITECTURE.md) and the root `README.md`.

NexaFlow speaks the **Android Locale plugin protocol** — the same open, battle-tested
inter-app contract used by Tasker, MacroDroid, Automate and Locale. Any developer can ship a
standalone APK that NexaFlow (and every other Locale-compatible host) can discover and invoke
as a first-class action. This document is the complete developer contract plus the blueprint
for the first-party SDK module that removes the boilerplate.

> **Want working code instead of prose?** The [`sample-plugins/`](../sample-plugins/) folder is a
> complete, buildable reference plugin (NFC toggle) with **zero external libraries** — copy it,
> swap the logic, ship. It implements the raw protocol this document specifies end-to-end.

---

## 1. Why the Locale protocol?

| Requirement | Locale protocol answer |
|---|---|
| Third parties extend the engine **without our help** | A plugin is just an exported `Activity` + `BroadcastReceiver` in their own APK |
| Hosts must not trust plugin code | Plugins only receive intents; they never touch NexaFlow data or processes |
| One plugin, many hosts | Tasker, MacroDroid, Automate and NexaFlow all speak the same intents |
| Configuration survives process death | Config is a plain `Bundle` (primitive types only) under a well-known extra |
| Developer ergonomics | A tiny SDK module provides base classes + a JSON round-trip so plugins are ~100 lines |

---

## 2. The protocol specification

### 2.1 Intents

| Role | Intent action (constant) | Delivered to |
|---|---|---|
| Configure a setting action | `com.twofortyfouram.locale.intent.action.EDIT_SETTING` | exported `Activity` |
| Execute a setting action | `com.twofortyfouram.locale.intent.action.FIRE_SETTING` | exported `BroadcastReceiver` |
| Configure a condition (state) | `com.twofortyfouram.locale.intent.action.EDIT_CONDITION` | exported `Activity` |
| Query a condition | `com.twofortyfouram.locale.intent.action.QUERY_CONDITION` | exported `BroadcastReceiver` (ordered) |
| Tasker event (extension) | `net.dinglisch.android.tasker.ACTION_EDIT_EVENT` | exported `Activity` |

**v1 of the NexaFlow SDK covers the *setting* pair (`EDIT_SETTING` / `FIRE_SETTING`).**
Condition and event plugins are follow-ups (see §7).

### 2.2 Extras (namespace `com.twofortyfouram.locale.intent.extra.*`)

| Key | Type | Direction | Meaning |
|---|---|---|---|
| `BUNDLE` | `android.os.Bundle` | edit → host (result), host → fire | Persisted configuration. **Must be < 25 KB serialized, primitives/Strings/arrays only — no custom `Parcelable`/`Serializable` classes.** |
| `STRING_BLURB` | `String` | edit → host (result), host → fire | Concise human-readable summary, e.g. `"Wi-Fi: toggle"`. Legacy hosts also accept the old key `BLURB`. |
| `STRING_BREADCRUMB` | `String` | host → edit | Title hierarchy shown while configuring (e.g. `"NexaFlow ▸ My Plugin"`). |

### 2.3 Edit → Host result contract

The edit `Activity` must finish with:

```kotlin
RESULT_OK -> resultIntent.putExtra(EXTRA_BUNDLE, bundle)      // config
             resultIntent.putExtra(EXTRA_STRING_BLURB, blurb)  // summary
             setResult(RESULT_OK, resultIntent)
RESULT_CANCELED -> setResult(RESULT_CANCELED, null)           // user aborted
```

The host stores the returned bundle verbatim and re-sends it in `FIRE_SETTING`.

### 2.4 Fire (execution) contract

The host sends an **explicit** `FIRE_SETTING` broadcast to the plugin's `BroadcastReceiver`:

- `Intent.setPackage(...)` + component targeting the receiver class the host discovered via
  `PackageManager.queryBroadcastReceivers(...)`.
- Flags the host must set: `FLAG_INCLUDE_STOPPED_PACKAGES` (wake force-stopped apps) and, for
  automatic runs, `FLAG_FROM_BACKGROUND`. Never `FLAG_RECEIVER_FOREGROUND`.
- The receiver must **return quickly**: schedule real work on a worker thread and finish
  `onReceive` promptly (or use `goAsync()`).

### 2.5 Ordered broadcast + result codes (Tasker extension, recommended)

If the host fires an **ordered** broadcast, the receiver can report outcome and locals:

| Result code | Meaning |
|---|---|
| `RESULT_CODE_OK = 0` | Executed successfully |
| `RESULT_CODE_PENDING = 1` | Work started; will finish asynchronously (use `goAsync()`) |
| `RESULT_CODE_CANCELED = 2` | User canceled |
| `RESULT_CODE_FAILED = -1` | Execution failed; set `%err` (`net.dinglisch.android.tasker.extras.ERR`, 0–999) and `%errmsg` (`...extras.ERRMSG`) for the error UI |

### 2.6 Variable replacement (Tasker extension)

`net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS` is a `Bundle` of
`key -> String[]` (Bundle of String arrays) telling the host which extras contain
`%var` tokens to substitute before delivery (e.g. `BUNDLE -> ["config"]`).
NexaFlow v1 ships its own `%var` engine (§6) and will support this hand-off in a later phase.

### 2.7 Manifest requirements (hard rules for every plugin)

```xml
<manifest ... android:installLocation="internalOnly">   <!-- required -->
  <application>
    <activity
        android:name=".EditActivity"
        android:exported="true"                        <!-- required -->
        android:label="@string/plugin_name">
      <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.EDIT_SETTING" />
      </intent-filter>
    </activity>

    <receiver
        android:name=".FireReceiver"
        android:exported="true">                       <!-- required -->
      <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.FIRE_SETTING" />
      </intent-filter>
    </receiver>
  </application>
</manifest>
```

- `exported="true"` and **enabled** on both components, or hosts skip the plugin.
- `android:installLocation="internalOnly"` — hosts reject plugins on external storage.
- Plugins live in **their own APK/process**. No shared UID, no library dependency on NexaFlow.

---

## 3. Bundle JSON convention

The raw protocol is primitive-typed, but plugins need structured config. The NexaFlow SDK
serializes each plugin's config as a **JSON object string** stored in the bundle:

```
Bundle
└── "config" : String   // JSON, e.g. {"enabled":true,"stream":"alarm","level":7}
└── "sdkVersion" : Int  // = 1, opt-in marker for future protocol bumps
```

Rules:

1. **Keys must be primitive + String + arrays only.** The bundle itself never carries custom
   objects — only the JSON string does. This keeps every host compatible.
2. **Keep the serialized bundle < 25 KB.** NexaFlow's SDK enforces this with a hard guard
   (`BundleJsonException` when exceeded) at save time, so plugins fail fast in dev, not on
   a user's device.
3. **JSON schema is owned by the plugin.** The SDK is schema-agnostic: it round-trips
   `Map<String, Any?>` ⇄ JSON ⇄ Bundle. Document your schema in your plugin's README.
4. Unknown JSON fields must be ignored, so old plugin APKs keep working with new configs
   and vice-versa (forward/backward tolerance).

Example round-trip:

```kotlin
val config: Map<String, Any?> = mapOf("enabled" to true, "level" to 7)
val bundle = PluginConfigParser.toBundle(config)     // JSON-encoded under "config"
val back: Map<String, Any?> = PluginConfigParser.fromBundle(bundle)
```

---

## 4. Blurb guidelines

The **blurb** is what the user reads in the action list — make it a complete, short sentence:

| ✔ Good | ✘ Bad |
|---|---|
| `Set ring volume to 7` | `7` |
| `Flashlight: toggle` | `flash` |
| `Open Profile: Sleep (21:00–07:00)` | `sleep` |

- Keep it under ~60 characters; hosts truncate.
- Localize it via your app's resources when the locale changes (the SDK base classes
  recompute the blurb from the bundle in `onResume`/edit).
- Never embed secrets or device-specific data in the blurb.

---

## 5. Blueprint: `core/plugin-sdk` module (experimental)

A dependency-light Android library that turns the raw protocol into base classes, so a new
plugin is one class each for edit + fire.

### 5.1 Registration

```kotlin
// settings.gradle.kts
include(":core:plugin-sdk")
```

### 5.2 Proposed `build.gradle.kts`

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nexaflow.core.pluginsdk"
    compileSdk = 37
    defaultConfig { minSdk = 26 }   // matches all core/* modules
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }

dependencies {
    api("androidx.core:core-ktx:1.19.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
```

### 5.3 Package layout

```
core/plugin-sdk/src/main/java/com/nexaflow/core/pluginsdk/
├── LocaleContract.kt          // action strings, extra keys, result codes (single source of truth)
├── PluginFireReceiver.kt      // abstract BroadcastReceiver — template method + ordered-result support
├── PluginEditActivity.kt      // abstract Activity — breadcrumb, blurb recompute, RESULT_OK packing
├── PluginConfigParser.kt      // Bundle <-> JSON <-> Map<String,Any?> + 25 KB guard
├── PluginResult.kt            // sealed: Ok / Pending / Canceled / Failed(code, message)
└── PluginRegistry.kt          // optional host-side helper: enumerate installed plugins
core/plugin-sdk/src/test/java/com/nexaflow/core/pluginsdk/
├── PluginConfigParserTest.kt  // round-trip, size guard, forward/backward tolerance
└── LocaleContractTest.kt      // constants match the published protocol
```

### 5.4 Key API sketches

```kotlin
// LocaleContract.kt — keep these in ONE place so hosts and plugins never drift
object LocaleContract {
    const val ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val EXTRA_BUNDLE        = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_STRING_BLURB  = "com.twofortyfouram.locale.intent.extra.STRING_BLURB"
    const val EXTRA_BREADCRUMB    = "com.twofortyfouram.locale.intent.extra.STRING_BREADCRUMB"
    const val RESULT_CODE_OK      = 0
    const val RESULT_CODE_PENDING = 1
    const val RESULT_CODE_CANCELED = 2
    const val RESULT_CODE_FAILED  = -1
    const val MAX_BUNDLE_BYTES    = 25_000
}

// PluginFireReceiver.kt — plugin subclasses override onFire only
abstract class PluginFireReceiver : BroadcastReceiver() {
    final override fun onReceive(context: Context, intent: Intent) {
        val config = PluginConfigParser.fromBundle(
            intent.getBundleExtra(LocaleContract.EXTRA_BUNDLE))
        val result = onFire(context, config)          // suspend-friendly: use goAsync()
        if (receiver.isOrderedBroadcast) {
            resultCode = result.toResultCode()
            if (result is Failed) {
                // %err / %errmsg extras for Tasker-compatible error UI
                // (the data field is left untouched — errors ride in extras)
            }
        }
    }

    /** Pure plugin logic: return a [PluginResult]. Keep it fast. */
    abstract fun onFire(context: Context, config: Map<String, Any?>): PluginResult
}

// PluginEditActivity.kt — plugin subclasses provide the config UI + blurb
abstract class PluginEditActivity : Activity() {
    final override fun onCreate(savedInstanceState: Bundle?) {
        // read breadcrumb from EXTRA_BREADCRUMB, render plugin config UI
    }
    protected fun save(config: Map<String, Any?>, blurb: String) {
        val out = Intent().apply {
            putExtra(LocaleContract.EXTRA_BUNDLE, PluginConfigParser.toBundle(config))
            putExtra(LocaleContract.EXTRA_STRING_BLURB, blurb)
        }
        setResult(RESULT_OK, out)
        finish()
    }
    // Back is NOT overridden: finishing without save() already delivers
    // RESULT_CANCELED to the host, on every API level incl. 33+ predictive back.
}
```

### 5.5 Minimal end-to-end plugin (~100 lines with the SDK)

```kotlin
// AndroidManifest.xml: activity + receiver exactly as §2.7
class FlashlightEditActivity : PluginEditActivity() {
    private var enabled = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)   // a Switch bound to `enabled`
        findViewById<Switch>(R.id.toggle).setOnCheckedChangeListener { _, b -> enabled = b }
        saveButton.setOnClickListener {
            save(mapOf("enabled" to enabled), getString(if (enabled) R.string.blurb_on
                                                          else R.string.blurb_off))
        }
    }
}

class FlashlightFireReceiver : PluginFireReceiver() {
    override fun onFire(context: Context, config: Map<String, Any?>): PluginResult {
        val enabled = config["enabled"] as? Boolean ?: return PluginResult.Failed(-1, "missing flag")
        val ok = FlashlightController.toggle(context, enabled)   // your real logic
        return if (ok) PluginResult.Ok else PluginResult.Failed(2, "camera in use")
    }
}
```

---

## 6. NexaFlow host-side integration (roadmap)

| Phase | Scope | Status |
|---|---|---|
| **H1** | Protocol spec + SDK module (`core/plugin-sdk`). | ✅ Done |
| **H2** | Builder: `ActionType.PLUGIN_FIRE`, `PluginRepository` discovery via `queryBroadcastReceivers(FIRE_SETTING)`, plugin picker dialog, `EDIT_SETTING` activity-result flow persisting `(package, receiverClass, bundleJson, blurb)`. | ✅ Done |
| **H3** | Engine: `PluginFireClient` (explicit ordered broadcast + `FLAG_INCLUDE_STOPPED_PACKAGES` + timeout) + `PluginFireHandler` in the registry; result codes surfaced as execution messages. Plugin manager screen (list / test fire / app info) in Settings. | ✅ Done |
| **H4** | Tasker variable hand-off (`VARIABLE_REPLACE_KEYS`) so plugins can read `%var`; then condition plugins (`EDIT_CONDITION`/`QUERY_CONDITION`) and, optionally, event plugins. | 🚧 Planned |

Acceptance gate for H2+: `PluginFireClientRoundTripTest` (Robolectric) drives the client's
explicit ordered FIRE_SETTING broadcast against a fake plugin receiver declared in
`core/execution/src/test/AndroidManifest.xml`, covering OK/Pending/Failed end-to-end;
`PluginFireClientTest` covers absent-package / blank-receiver non-success paths; and full
`test assembleDebug assembleRelease lintDebug` runs on every PR.

---

## 7. Developer checklist

Before publishing a plugin (and if you want a copy-paste starting point, see
[`sample-plugins/`](../sample-plugins/)):

- [ ] `EDIT_SETTING` + `FIRE_SETTING` declared with `exported="true"`; `installLocation="internalOnly"`.
- [ ] Result bundle < 25 KB, primitives/Strings/arrays only; JSON under `config` key.
- [ ] `RESULT_OK` always carries `EXTRA_BUNDLE` **and** `EXTRA_STRING_BLURB`.
- [ ] `onFire` returns immediately; heavy work on a thread/`goAsync()`; no blocking calls.
- [ ] Blurb is a short, complete, localized sentence.
- [ ] Tested in a Locale-compatible host: Tasker (`Plugin` → `Locale Plugin`) and
      MacroDroid (`Plugins`) both act as free test harnesses — no NexaFlow build needed.
- [ ] Behavior verified after force-stop (hosts use `FLAG_INCLUDE_STOPPED_PACKAGES`; your
      receiver must still fire).

---

## 8. Versioning and stability promise

- `sdkVersion` lives in the bundle and will only grow on **breaking** changes; v1 hosts
  ignore unknown `sdkVersion` values and plugins keep working.
- The protocol itself (intent actions, extras, result codes) is frozen by the ecosystem —
  NexaFlow's SDK may evolve, the wire format will not.
- The `core/plugin-sdk` module is **experimental**: APIs may change until H2 lands, at which
  point the module is promoted to stable with semantic versioning and a `CHANGELOG`.

**Ready to build?** Open an issue tagged `plugin-sdk` with your plugin idea, or start from the
blueprint above and share your feedback on the API shapes before the contract freezes.
