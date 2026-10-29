package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.security.SafeCommandBuilder

/**
 * Shell input surface split out of [SystemController] (NFA-06): screenshots,
 * typed text, key events, taps and swipes. Every command goes through
 * [SafeCommandBuilder], so a configured value can never escape its argument.
 */
internal class SystemInputController {

    /** Captures a screenshot to the Pictures/NexaFlow folder. */
    fun screenshot(filename: String): SystemControlResult {
        val safeName = filename.trim().ifBlank { "screenshot_${System.currentTimeMillis()}.png" }
        val safe = safeName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return try {
            val shell = PrivilegedRunner.runShell(
                "mkdir -p /sdcard/Pictures/NexaFlow && " +
                    SafeCommandBuilder.build("screencap", "-p", "/sdcard/Pictures/NexaFlow/$safe")
            )
            if (shell.success) {
                SystemControlResult.ok("Screenshot saved: /sdcard/Pictures/NexaFlow/$safe")
            } else {
                shell
            }
        } catch (t: Throwable) {
            SystemControlResult.fail("Screenshot failed: ${t.message}")
        }
    }

    /** Injects text as if typed (shell `input text`; %s is converted to a space). */
    fun inputText(text: String): SystemControlResult {
        if (text.isBlank()) return SystemControlResult.fail("No text configured")
        if (!SafeCommandBuilder.isSafeCommand(text)) {
            return SystemControlResult.fail("Text rejected: unsafe characters")
        }
        return try {
            // `input text` renders %s as a space, so real spaces must be sent
            // as %s inside the quoted argument.
            val arg = text.replace(" ", "%s")
            val shell = PrivilegedRunner.runShell(SafeCommandBuilder.build("input", "text", arg))
            if (shell.success) SystemControlResult.ok("Text injected") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Text injection failed: ${t.message}")
        }
    }

    /** Injects a key event by preset name or raw KEYCODE number. */
    fun keyEvent(key: String): SystemControlResult {
        val code = key.trim().toIntOrNull() ?: KEYCODES[key.trim().uppercase()] ?: -1
        if (code < 0) return SystemControlResult.fail("Unknown key event: $key")
        return try {
            val shell = PrivilegedRunner.runShell(SafeCommandBuilder.build("input", "keyevent", code.toString()))
            if (shell.success) SystemControlResult.ok("Key event $key sent") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Key event failed: ${t.message}")
        }
    }

    /** Taps at absolute screen coordinates. */
    fun inputTap(x: Int, y: Int): SystemControlResult {
        if (x < 0 || y < 0) return SystemControlResult.fail("Invalid coordinates ($x, $y)")
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build("input", "tap", x.toString(), y.toString())
            )
            if (shell.success) SystemControlResult.ok("Tapped ($x, $y)") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Tap failed: ${t.message}")
        }
    }

    /** Swipes between two points with an optional duration in ms. */
    fun inputSwipe(
        x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int
    ): SystemControlResult {
        return try {
            val shell = PrivilegedRunner.runShell(
                SafeCommandBuilder.build(
                    "input", "swipe", x1.toString(), y1.toString(),
                    x2.toString(), y2.toString(), durationMs.toString()
                )
            )
            if (shell.success) SystemControlResult.ok("Swiped ($x1,$y1) -> ($x2,$y2)") else shell
        } catch (t: Throwable) {
            SystemControlResult.fail("Swipe failed: ${t.message}")
        }
    }

    companion object {
        /** Preset names -> android.view.KeyEvent KEYCODE values for [keyEvent]. */
        private val KEYCODES = mapOf(
            "POWER" to 26, "BACK" to 4, "HOME" to 3, "MENU" to 82, "CAMERA" to 27,
            "RECENTS" to 187, "SEARCH" to 84, "NOTIFICATIONS" to 83, "CALL" to 5,
            "ENDCALL" to 6, "VOLUME_UP" to 24, "VOLUME_DOWN" to 25, "MUTE" to 91,
            "MEDIA_PLAY_PAUSE" to 85, "MEDIA_NEXT" to 87, "MEDIA_PREVIOUS" to 88,
            "MEDIA_STOP" to 86, "MEDIA_REWIND" to 89, "MEDIA_FAST_FORWARD" to 90,
            "BRIGHTNESS_UP" to 220, "BRIGHTNESS_DOWN" to 221, "DPAD_UP" to 19,
            "DPAD_DOWN" to 20, "DPAD_LEFT" to 21, "DPAD_RIGHT" to 22, "DPAD_CENTER" to 23,
            "ENTER" to 66, "TAB" to 61, "SPACE" to 62, "DEL" to 67, "ESCAPE" to 111,
            "SCREENSHOT" to 120, "SLEEP" to 223, "WAKEUP" to 224, "APP_SWITCH" to 187
        )
    }
}
