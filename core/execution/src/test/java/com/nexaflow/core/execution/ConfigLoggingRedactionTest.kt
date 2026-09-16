package com.nexaflow.core.execution

import android.util.Log
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * P0.1 trust-boundary regression: the elevated-action diagnostic path must not
 * leak raw `action.config` values into logcat. Config values can carry shell
 * commands, tokens, package names, or anything a workflow author stored; only
 * the action type and config KEY NAMES may be logged.
 *
 * The log line is built by [ElevatedDiagnostics.failureLine] — the same function
 * the engine calls — so this test pins the real production format, not a copy.
 */
@RunWith(RobolectricTestRunner::class)
class ConfigLoggingRedactionTest {

    @After
    fun tearDown() {
        ShadowLog.reset()
    }

    private fun runElevatedFailurePath(config: Map<String, String>) {
        val action = Action(
            type = ActionType.ADVANCED_SHIZUKU,
            config = config
        )
        val message = "No elevated runtime available (Shizuku or root)"
        Log.w("ExecutionEngine", ElevatedDiagnostics.failureLine(action, message))
    }

    @Test
    fun elevatedFailureLog_neverContainsConfigValues() {
        val secrets = listOf(
            "rm -rf /data/local/tmp",
            "S3CR3T-TOKEN-VALUE",
            "vault:password#entry-42",
            "com.private.target.package"
        )
        val config = linkedMapOf(
            "command" to secrets[0],
            "authorization" to "Bearer ${secrets[1]}",
            "vaultRef" to secrets[2],
            "packageName" to secrets[3]
        )

        runElevatedFailurePath(config)

        val logs = ShadowLog.getLogsForTag("ExecutionEngine")
        assertTrue("expected the diagnostic log to be emitted", logs.isNotEmpty())
        val joined = logs.joinToString("\n") { it.msg }
        for (secret in secrets) {
            assertFalse(
                "logcat leaked config value: $secret\n---\n$joined",
                joined.contains(secret)
            )
        }
    }

    @Test
    fun elevatedFailureLog_containsOnlyConfigKeyNames() {
        val config = mapOf(
            "command" to "super-secret-value",
            "timeoutMs" to "30000"
        )

        runElevatedFailurePath(config)

        val joined = ShadowLog.getLogsForTag("ExecutionEngine").joinToString("\n") { it.msg }
        assertTrue(joined.contains("configKeys=command,timeoutMs"))
        assertFalse(joined.contains("super-secret-value"))
    }
}
