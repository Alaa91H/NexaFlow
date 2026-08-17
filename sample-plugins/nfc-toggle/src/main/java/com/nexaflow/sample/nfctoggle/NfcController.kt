package com.nexaflow.sample.nfctoggle

import android.content.Context
import android.nfc.NfcAdapter

/**
 * Turns NFC on/off with ZERO external libraries.
 *
 * Toggling NFC is a system-level operation, so this template shows the
 * realistic approach:
 *  1. Reflect the hidden `NfcManager.setNfcEnabled/setNfcDisabled(boolean)`
 *     system API (what system UIs use). Mirrors how the real NexaFlow app does
 *     it — see `RomSystemApiBridge` in `core/rom-integration`.
 *  2. Falls back to the shell (`svc nfc enable|disable`), which needs root or
 *     a system signature.
 *
 * Both paths fail gracefully: the plugin reports a clear %errmsg instead of
 * pretending success. Swap this class for your own logic in your plugin —
 * the protocol doesn't care HOW you do the work, only that you return quickly.
 */
object NfcController {

    /**
     * Attempts to set NFC to [enabled]. Returns null on success or a
     * human-readable failure reason.
     */
    fun setNfcEnabled(context: Context, enabled: Boolean): String? {
        val adapter = NfcAdapter.getDefaultAdapter(context)
            ?: return "No NFC hardware on this device"
        val manager = context.getSystemService(Context.NFC_SERVICE)
        if (manager != null) {
            val reflectionError = toggleViaReflection(manager, enabled)
            // Some ROMs return null/void but still apply the change — verify.
            // The state can flip asynchronously, so a stale read is not proof of
            // failure: only treat a settled opposite state as blocked, otherwise
            // optimistically accept the reflection (it raised no error).
            if (reflectionError == null && adapter.isEnabled == enabled) return null
            if (reflectionError == null) {
                val settled = runCatching {
                    Thread.sleep(150); adapter.isEnabled == enabled
                }.getOrDefault(false)
                if (settled) return null
                return "NFC state did not change (ROM blocked it)"
            }
        }
        // Fall back to the shell (root / system-signed only).
        return toggleViaShell(enabled)
            .takeIf { it == null } ?: "NFC toggle failed: try again from the system settings"
    }

    /** Hidden NfcManager.setNfcEnabled/setNfcDisabled via reflection. */
    private fun toggleViaReflection(manager: Any, enabled: Boolean): String? {
        val methodName = if (enabled) "setNfcEnabled" else "setNfcDisabled"
        return try {
            // Some ROMs expose the boxed Boolean signature, others the primitive
            // boolean — mirror RomSystemApiBridge and try both before giving up.
            val method = runCatching {
                manager.javaClass.getMethod(methodName, Boolean::class.javaObjectType)
            }.getOrElse {
                manager.javaClass.getMethod(methodName, Boolean::class.javaPrimitiveType)
            }
            method.invoke(manager, enabled)
            null
        } catch (t: Throwable) {
            // Hidden API blocked by the runtime, method missing on this ROM, etc.
            t.message ?: "Reflection failed"
        }
    }

    /** Shell fallback: `svc nfc enable|disable`. */
    private fun toggleViaShell(enabled: Boolean): String? {
        return try {
            val command = "svc nfc ${if (enabled) "enable" else "disable"}"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            if (process.waitFor() == 0) null else "Shell command failed"
        } catch (t: Throwable) {
            t.message ?: "Shell unavailable"
        }
    }
}
