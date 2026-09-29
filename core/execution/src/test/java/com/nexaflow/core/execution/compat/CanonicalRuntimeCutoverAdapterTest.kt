package com.nexaflow.core.execution.compat

import com.nexaflow.domain.canonical.CanonicalFieldId
import com.nexaflow.domain.canonical.DurationValue
import com.nexaflow.domain.canonical.EnumTokenValue
import com.nexaflow.domain.canonical.ExpressionValue
import com.nexaflow.domain.canonical.IntegerValue
import com.nexaflow.domain.canonical.TextValue
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalRuntimeCutoverAdapterTest {

    private val cutover = CanonicalRuntimeCutoverAdapter()

    @Test
    fun ringerModeUsesRealModeKeyAndDeclaredDefault() {
        val explicit = cutover.prepareAction(
            Action(ActionType.SYSTEM_RINGER_MODE, mapOf("mode" to "VIBRATE")),
            runId = "run-ringer-explicit",
            instanceId = "v3.action.0",
        )
        assertEquals(
            EnumTokenValue("compat.system_ringer_mode.mode", "VIBRATE"),
            explicit.command.arguments[CanonicalFieldId("mode")],
        )

        val defaulted = cutover.prepareAction(
            Action(ActionType.SYSTEM_RINGER_MODE, emptyMap()),
            runId = "run-ringer-default",
            instanceId = "v3.action.0",
        )
        assertEquals(
            EnumTokenValue("compat.system_ringer_mode.mode", "NORMAL"),
            defaulted.command.arguments[CanonicalFieldId("mode")],
        )
    }

    @Test
    fun alarmUsesHourMinuteInsteadOfInventedTimeKey() {
        val prepared = cutover.prepareAction(
            Action(
                ActionType.SYSTEM_SET_ALARM,
                mapOf("hour" to "7", "minute" to "30"),
            ),
            runId = "run-alarm",
            instanceId = "v3.action.0",
        )
        assertEquals(
            IntegerValue(7),
            prepared.command.arguments[CanonicalFieldId("hour")],
        )
        assertEquals(
            IntegerValue(30),
            prepared.command.arguments[CanonicalFieldId("minute")],
        )
    }

    @Test
    fun waitUsesCatalogSecondsDefaultAsTypedDuration() {
        val prepared = cutover.prepareAction(
            Action(ActionType.SYSTEM_WAIT, emptyMap()),
            runId = "run-wait",
            instanceId = "v3.action.0",
        )
        assertEquals(
            DurationValue(5_000),
            prepared.command.arguments[CanonicalFieldId("seconds")],
        )
    }

    @Test
    fun installApkUsesPathContract() {
        val prepared = cutover.prepareAction(
            Action(
                ActionType.SYSTEM_INSTALL_APK,
                mapOf("path" to "/data/local/tmp/app.apk"),
            ),
            runId = "run-install",
            instanceId = "v3.action.0",
        )
        assertEquals(
            TextValue("/data/local/tmp/app.apk"),
            prepared.command.arguments[CanonicalFieldId("path")],
        )
    }

    @Test
    fun mediaTransportIdentitySurvivesIntoAtomicCommand() {
        val prepared = cutover.prepareAction(
            Action(ActionType.SYSTEM_MEDIA_NEXT, emptyMap()),
            runId = "run-media",
            instanceId = "v3.action.0",
        )
        assertEquals(
            EnumTokenValue("core.media.command", "NEXT"),
            prepared.command.arguments[CanonicalFieldId("command")],
        )
    }

    @Test
    fun expressionCapableBrightnessPromotesTypedExpressionIntoPayload() {
        val prepared = cutover.prepareAction(
            Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "%BATTERY")),
            runId = "run-expression",
            instanceId = "v3.action.0",
        )
        val payload = prepared.command.payload as ExpressionValue
        assertEquals("%BATTERY", payload.source)
        assertEquals(com.nexaflow.domain.canonical.CanonicalValueKind.INTEGER, payload.resultKind)
    }

    @Test
    fun invalidBrightnessIsRejectedBeforePlanning() {
        try {
            cutover.prepareAction(
                Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "999")),
                runId = "run-invalid",
                instanceId = "v3.action.0",
            )
            throw AssertionError("Expected canonical validation to reject brightness 999")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("cutover refused"))
        }
    }
}
