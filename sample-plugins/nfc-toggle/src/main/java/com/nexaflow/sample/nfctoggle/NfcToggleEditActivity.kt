package com.nexaflow.sample.nfctoggle

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Switch

/**
 * The plugin's configuration screen. The host (NexaFlow, Tasker, MacroDroid...)
 * launches it with [LocaleProtocol.ACTION_EDIT_SETTING]. The user picks the NFC
 * state to apply and taps Save — the activity finishes with `RESULT_OK` plus
 * the protocol extras ([LocaleProtocol.EXTRA_BUNDLE] + blurb).
 *
 * When the host reconfigures an existing action it passes the previously saved
 * bundle in [LocaleProtocol.EXTRA_BUNDLE]; we pre-fill the switch from it.
 *
 * Backing out WITHOUT saving already delivers `RESULT_CANCELED` to the host —
 * do not override onBackPressed (the default is correct on every API level,
 * including 33+ predictive back).
 */
class NfcToggleEditActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        // Breadcrumb title hierarchy from the host (e.g. "NexaFlow ▸ My plugin").
        val breadcrumb = intent.getStringExtra(LocaleProtocol.EXTRA_STRING_BREADCRUMB)
        if (!breadcrumb.isNullOrBlank()) title = breadcrumb

        val switch = findViewById<Switch>(R.id.switch_nfc)
        // Pre-fill from an existing configuration when reconfiguring.
        val saved = PluginConfig.fromBundle(
            intent.getBundleExtra(LocaleProtocol.EXTRA_BUNDLE)
        )
        (saved["enabled"] as? Boolean)?.let { switch.isChecked = it }

        findViewById<Button>(R.id.button_save).setOnClickListener {
            val config = mapOf("enabled" to switch.isChecked)
            val blurb = if (switch.isChecked) {
                getString(R.string.blurb_enable)
            } else {
                getString(R.string.blurb_disable)
            }
            save(config, blurb)
        }
    }

    /** Finishes with `RESULT_OK` + the protocol extras (config bundle + blurb). */
    private fun save(config: Map<String, Any?>, blurb: String) {
        val out = Intent().apply {
            putExtra(LocaleProtocol.EXTRA_BUNDLE, PluginConfig.toBundle(config))
            putExtra(LocaleProtocol.EXTRA_STRING_BLURB, blurb)
            putExtra(LocaleProtocol.EXTRA_BLURB, blurb) // legacy hosts
        }
        setResult(RESULT_OK, out)
        finish()
    }
}
