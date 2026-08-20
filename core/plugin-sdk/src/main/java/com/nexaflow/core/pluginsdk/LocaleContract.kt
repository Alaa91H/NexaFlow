package com.nexaflow.core.pluginsdk

/**
 * The Android Locale plugin protocol — the same open, inter-app contract used
 * by Tasker, MacroDroid, Automate and Locale. Keeping every action/extra/result
 * constant in ONE place lets hosts and plugins never drift apart.
 *
 * See `docs/PLUGIN_SDK.md` for the full developer contract.
 */
object LocaleContract {

    // ── Intent actions ────────────────────────────────────────────────────────

    /** Host → plugin: launch the plugin's configuration [android.app.Activity]. */
    const val ACTION_EDIT_SETTING =
        "com.twofortyfouram.locale.intent.action.EDIT_SETTING"

    /** Host → plugin: execute the configured action in the plugin's receiver. */
    const val ACTION_FIRE_SETTING =
        "com.twofortyfouram.locale.intent.action.FIRE_SETTING"

    /** Host → plugin: launch the configuration activity of a condition plugin. */
    const val ACTION_EDIT_CONDITION =
        "com.twofortyfouram.locale.intent.action.EDIT_CONDITION"

    /** Host → plugin: ordered broadcast asking whether a condition is true. */
    const val ACTION_QUERY_CONDITION =
        "com.twofortyfouram.locale.intent.action.QUERY_CONDITION"

    /** Plugin → host: asks the host to re-query one configured condition/event. */
    const val ACTION_REQUEST_QUERY =
        "com.twofortyfouram.locale.intent.action.REQUEST_QUERY"

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

    /** Legacy extra key still accepted by older hosts/plugins. */
    const val EXTRA_BLURB =
        "com.twofortyfouram.locale.intent.extra.BLURB"

    /** Required by Locale request-query broadcasts to identify the edit component. */
    const val EXTRA_STRING_ACTIVITY_CLASS_NAME =
        "com.twofortyfouram.locale.intent.extra.ACTIVITY"

    // ── Ordered-broadcast result codes (fire + query) ────────────────────────

    const val RESULT_CODE_OK = 0
    const val RESULT_CODE_PENDING = 1
    const val RESULT_CODE_CANCELED = 2
    const val RESULT_CODE_FAILED = -1

    // ── Ordered condition result codes (Locale Plugin API) ─────────────────────

    /** The condition receiver evaluated the configured state as true. */
    const val RESULT_CONDITION_SATISFIED = 16

    /** The condition receiver evaluated the configured state as false. */
    const val RESULT_CONDITION_UNSATISFIED = 17

    /** The receiver cannot currently determine state; this is not false. */
    const val RESULT_CONDITION_UNKNOWN = 18

    // ── Hard protocol limits ──────────────────────────────────────────────────

    /** The serialized bundle must stay under this size (base-10 bytes). */
    const val MAX_BUNDLE_BYTES = 25_000
}
