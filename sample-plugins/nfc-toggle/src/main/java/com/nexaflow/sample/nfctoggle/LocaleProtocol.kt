package com.nexaflow.sample.nfctoggle

/**
 * The Android Locale plugin protocol, inlined so this sample is fully
 * self-contained (zero dependencies). This is the SAME frozen wire contract
 * used by Tasker, MacroDroid, Automate, Locale and NexaFlow — the canonical
 * copy lives in `docs/PLUGIN_SDK.md` and `core/plugin-sdk`.
 *
 * Keep these values byte-for-byte identical to the published protocol:
 * hosts match on the exact action strings and read the exact extra keys.
 */
object LocaleProtocol {

    // ── Intent actions ────────────────────────────────────────────────────────

    /** Host → plugin: launch the configuration [android.app.Activity]. */
    const val ACTION_EDIT_SETTING =
        "com.twofortyfouram.locale.intent.action.EDIT_SETTING"

    /** Host → plugin: execute the configured action in the plugin's receiver. */
    const val ACTION_FIRE_SETTING =
        "com.twofortyfouram.locale.intent.action.FIRE_SETTING"

    // ── Extras (namespace com.twofortyfouram.locale.intent.extra.*) ──────────

    /** The persisted configuration bundle (< 25 KB, primitives/Strings only). */
    const val EXTRA_BUNDLE =
        "com.twofortyfouram.locale.intent.extra.BUNDLE"

    /** Concise human-readable summary shown in the host's action list. */
    const val EXTRA_STRING_BLURB =
        "com.twofortyfouram.locale.intent.extra.STRING_BLURB"

    /** Title hierarchy shown in the plugin's edit activity (host → edit). */
    const val EXTRA_STRING_BREADCRUMB =
        "com.twofortyfouram.locale.intent.extra.STRING_BREADCRUMB"

    /** Legacy extra key still accepted by older hosts. */
    const val EXTRA_BLURB =
        "com.twofortyfouram.locale.intent.extra.BLURB"

    // ── Ordered-broadcast result codes (fire) ─────────────────────────────────

    const val RESULT_CODE_OK = 0
    const val RESULT_CODE_PENDING = 1
    const val RESULT_CODE_CANCELED = 2
    const val RESULT_CODE_FAILED = -1

    // ── Tasker-compatible error extras (net.dinglisch.android.tasker.extras) ─

    const val EXTRA_TASKER_ERR = "net.dinglisch.android.tasker.extras.ERR"
    const val EXTRA_TASKER_ERRMSG = "net.dinglisch.android.tasker.extras.ERRMSG"

    // ── NexaFlow bundle JSON convention (docs/PLUGIN_SDK.md §3) ──────────────

    /** Structured config is serialized as a JSON string under this bundle key. */
    const val KEY_CONFIG = "config"

    /** Opt-in marker for future protocol bumps; v1 hosts ignore unknown values. */
    const val KEY_SDK_VERSION = "sdkVersion"
    const val SDK_VERSION = 1

    /** The serialized bundle must stay under this size (base-10 bytes). */
    const val MAX_BUNDLE_BYTES = 25_000
}
