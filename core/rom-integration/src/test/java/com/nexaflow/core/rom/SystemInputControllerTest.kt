package com.nexaflow.core.rom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.core.rom.model.SystemControlResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the extracted input surface (NFA-06) still enforces the same
 * argument validation and that [SystemController]'s public methods keep
 * delegating to it. The elevated transport is replaced by the shell probe, so
 * no Shizuku server, root binary or device is needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemInputControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun controller(): SystemController = SystemController(
        context = context,
        capabilityProvider = RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP)
    )

    @After
    fun tearDown() {
        PrivilegedRunner.shellRouteProbe = null
        PrivilegedRunner.rootProbeOverride = null
        PrivilegedRunner.suRunnerProbe = null
    }

    @Test
    fun blankTextIsRejectedBeforeAnyShellCommand() {
        assertFalse(controller().inputText("   ").success)
    }

    @Test
    fun unsafeTextIsRejected() {
        assertFalse(controller().inputText("hello\u0000world").success)
    }

    @Test
    fun unknownKeyNameIsRejected() {
        val result = controller().keyEvent("NOT_A_KEY")
        assertFalse(result.success)
        assertTrue(result.message.contains("Unknown key event"))
    }

    @Test
    fun negativeTapCoordinatesAreRejected() {
        assertFalse(controller().inputTap(-1, 10).success)
    }

    @Test
    fun screenshotAndSwipeStillReturnTypedResultsWhenNoRuntimeIsGranted() {
        PrivilegedRunner.shellRouteProbe = { Pair("none", "") }
        val controller = controller()
        assertFalse(controller.screenshot("shot").success)
        assertFalse(controller.inputSwipe(0, 0, 10, 10, 100).success)
        assertFalse(controller.keyEvent("26").success)
    }

    @Test
    fun everyInputCommandReportsSuccessWhenTheRouteSucceeds() {
        PrivilegedRunner.shellRouteProbe = { safe -> Pair("root", safe) }
        PrivilegedRunner.rootProbeOverride = { true }
        PrivilegedRunner.suRunnerProbe = { SystemControlResult.ok("ok") }
        val controller = controller()

        assertTrue(controller.screenshot("shot.png").success)
        assertTrue(controller.inputText("hello world").success)
        assertTrue(controller.keyEvent("HOME").success)
        assertTrue(controller.inputTap(10, 20).success)
        assertTrue(controller.inputSwipe(0, 0, 10, 10, 250).success)
    }

    @Test
    fun blankScreenshotNameFallsBackToATimestampedFile() {
        var command: String? = null
        PrivilegedRunner.shellRouteProbe = { safe ->
            command = safe
            Pair("none", "")
        }
        controller().screenshot("   ")
        val captured = requireNotNull(command)
        assertTrue(captured.contains("/sdcard/Pictures/NexaFlow/screenshot_"))
        assertTrue(captured.endsWith(".png'"))
    }

    @Test
    fun textAndTapWriteFailuresAreReturnedUnchanged() {
        PrivilegedRunner.shellRouteProbe = { Pair("none", "") }
        val controller = controller()
        assertFalse(controller.inputText("hello").success)
        assertFalse(controller.inputTap(1, 2).success)
    }

    @Test
    fun shellExceptionsBecomeTypedFailures() {
        PrivilegedRunner.shellRouteProbe = { throw IllegalStateException("transport died") }
        val controller = controller()
        val results = listOf(
            controller.screenshot("shot.png"),
            controller.inputText("hello"),
            controller.keyEvent("HOME"),
            controller.inputTap(1, 2),
            controller.inputSwipe(0, 0, 1, 1, 10)
        )
        results.forEach { result ->
            assertFalse(result.success)
            assertTrue(result.message.endsWith("transport died"))
        }
    }

    @Test
    fun validTextIsQuotedIntoASingleShellArgument() {
        var command: String? = null
        PrivilegedRunner.shellRouteProbe = { safe ->
            command = safe
            Pair("none", "")
        }
        controller().inputText("hello world")
        assertEquals("'input' 'text' 'hello%sworld'", command)
    }
}
