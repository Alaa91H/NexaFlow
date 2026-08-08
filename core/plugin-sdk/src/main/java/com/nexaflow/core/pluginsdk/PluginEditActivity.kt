package com.nexaflow.core.pluginsdk

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Base class for a Locale *setting* plugin's configuration activity. The host
 * launches it with [LocaleContract.ACTION_EDIT_SETTING]; the subclass renders
 * its config UI and calls [save] with the resulting config map + blurb.
 *
 * The activity must be declared exported in the manifest:
 *
 * ```xml
 * <activity android:name=".EditActivity" android:exported="true"
 *     android:label="@string/plugin_name">
 *     <intent-filter>
 *         <action android:name="com.twofortyfouram.locale.intent.action.EDIT_SETTING" />
 *     </intent-filter>
 * </activity>
 * ```
 *
 * Contract handled here:
 * - `RESULT_OK` + [LocaleContract.EXTRA_BUNDLE] + [LocaleContract.EXTRA_STRING_BLURB]
 *   when the user confirms ([save]).
 * - `RESULT_CANCELED` when the user backs out.
 *
 * When the host is reconfiguring an existing action it passes the previously
 * saved bundle in [LocaleContract.EXTRA_BUNDLE] — read it in [onCreate] to
 * pre-fill your UI (via [PluginConfigParser.fromBundle]).
 */
abstract class PluginEditActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // breadcrumb title hierarchy, if the host provided one
        val breadcrumb = intent?.getStringExtra(LocaleContract.EXTRA_STRING_BREADCRUMB)
        if (!breadcrumb.isNullOrBlank()) title = breadcrumb
    }

    /**
     * Finishes the activity with `RESULT_OK` and the protocol extras.
     *
     * @throws PluginBundleTooLargeException when the config exceeds the 25 KB
     *   protocol limit.
     */
    protected fun save(config: Map<String, Any?>, blurb: String) {
        val out = Intent().apply {
            putExtra(LocaleContract.EXTRA_BUNDLE, PluginConfigParser.toBundle(config))
            putExtra(LocaleContract.EXTRA_STRING_BLURB, blurb)
            putExtra(LocaleContract.EXTRA_BLURB, blurb) // legacy hosts
        }
        setResult(RESULT_OK, out)
        finish()
    }

    /** Rebuilds the bundle previously returned by [save] (for pre-filling). */
    protected fun savedBundle(): Bundle? =
        intent?.getBundleExtra(LocaleContract.EXTRA_BUNDLE)

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
    }
}
