package com.nexaflow.sample.nfctoggle

import android.content.Context
import android.nfc.NfcAdapter
import java.util.concurrent.TimeUnit

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
     *
     * Call this from a worker thread: state settlement and the bounded shell
     * fallback are synchronous by design.
     */
    fun setNfcEnabled(context: Context, enabled: Boolean): String? {
        val adapter = NfcAdapter.getDefaultAdapter(context)
            ?: return "No NFC hardware on this device"
        val manager = context.getSystemService(Context.NFC_SERVICE)
        if (manager != null) {
            val reflectionError = toggleViaReflection(manager, enabled)
            if (reflectionError == null && adapter.isEnabled == enabled) return null
            if (reflectionError == null) {
                if (waitForAdapterState(adapter, enabled)) return null
                return "NFC state did not change (ROM blocked it)"
            }
        }
        return toggleViaShell(enabled)
            .takeIf { it == null } ?: "NFC toggle failed: try again from the system settings"
    }

    /** Hidden NfcManager.setNfcEnabled/setNfcDisabled via reflection. */
    private fun toggleViaReflection(manager: Any, enabled: Boolean): String? {
        val methodName = if (enabled) "setNfcEnabled" else "setNfcDisabled"
        return try {
            val method = runCatching {
                manager.javaClass.getMethod(methodName, Boolean::class.javaObjectType)
            }.getOrElse {
                manager.javaClass.getMethod(methodName, Boolean::class.javaPrimitiveType)
            }
            method.invoke(manager, enabled)
            null
        } catch (t: Throwable) {
            t.message ?: "Reflection failed"
        }
    }

    /** Shell fallback: `svc nfc enable|disable`, with a hard timeout. */
    private fun toggleViaShell(enabled: Boolean): String? {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("svc", "nfc", if (enabled) "enable" else "disable")
            )
            val finished = process.waitFor(SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                "Shell command timed out"
            } else if (process.exitValue() == 0) {
                null
            } else {
                "Shell command failed"
            }
        } catch (t: Throwable) {
            t.message ?: "Shell unavailable"
        }
    }

    /**
     * NFC state changes are asynchronous on many ROMs. Poll briefly on the
     * background plugin worker instead of blocking the receiver's main thread.
     */
    private fun waitForAdapterState(adapter: NfcAdapter, enabled: Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(STATE_SETTLE_TIMEOUT_MS)
        while (System.nanoTime() < deadline) {
            if (runCatching { adapter.isEnabled == enabled }.getOrDefault(false)) return true
            try {
                Thread.sleep(STATE_POLL_MS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return runCatching { adapter.isEnabled == enabled }.getOrDefault(false)
    }

    private const val STATE_SETTLE_TIMEOUT_MS = 500L
    private const val STATE_POLL_MS = 50L
    private const val SHELL_TIMEOUT_SECONDS = 2L
}
