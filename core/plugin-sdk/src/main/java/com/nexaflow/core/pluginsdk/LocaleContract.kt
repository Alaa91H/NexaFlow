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

    /** Host → plugin: launch the configuration activity of a Tasker event plugin. */
    const val ACTION_EDIT_EVENT =
        "net.dinglisch.android.tasker.ACTION_EDIT_EVENT"

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

    // ── Tasker extension extras (namespace net.dinglisch.android.tasker.*) ─────

    /** Tasker extension: action/condition variables returned to the host. */
    const val EXTRA_VARIABLES_BUNDLE =
        "net.dinglisch.android.tasker.extras.VARIABLES"

    /** Tasker extension: a bit-mask advertising the features the host supports. */
    const val EXTRA_HOST_CAPABILITIES =
        "net.dinglisch.android.tasker.extras.HOST_CAPABILITIES"

    /** Tasker extension: event data passed from REQUEST_QUERY to QUERY_CONDITION. */
    const val EXTRA_REQUEST_QUERY_PASS_THROUGH_DATA =
        "net.dinglisch.android.tasker.extras.PASS_THROUGH_DATA"

    /** Key of the event message ID inside the pass-through data bundle. */
    const val PASS_THROUGH_BUNDLE_MESSAGE_ID_KEY =
        "net.dinglisch.android.tasker.MESSAGE_ID"

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

    // ── Tasker extension host-capability bit flags ─────────────────────────────

    /** Host accepts variables returned by a setting/action plugin. */
    const val HOST_CAPABILITY_SETTING_OUTPUT_VARIABLES = 2

    /** Host accepts variables returned by a condition plugin. */
    const val HOST_CAPABILITY_CONDITION_OUTPUT_VARIABLES = 4

    /** Host forwards a pass-through data Bundle for event REQUEST_QUERY calls. */
    const val HOST_CAPABILITY_REQUEST_QUERY_PASS_THROUGH_DATA = 64

    // ── Hard protocol limits ──────────────────────────────────────────────────

    /** The serialized bundle must stay under this size (base-10 bytes). */
    const val MAX_BUNDLE_BYTES = 25_000
}
