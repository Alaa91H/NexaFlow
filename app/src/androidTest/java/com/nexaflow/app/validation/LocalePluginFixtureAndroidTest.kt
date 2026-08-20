package com.nexaflow.app.validation

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.execution.plugin.PluginConditionClient
import com.nexaflow.core.execution.plugin.PluginFireClient
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginCompatibilityStatus
import com.nexaflow.core.pluginsdk.PluginDiscoveryRegistry
import com.nexaflow.core.pluginsdk.PluginType
import com.nexaflow.domain.models.ConditionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * External-APK validation for the dedicated Locale fixture.
 *
 * Preparation is explicit: build and install :test-fixtures:locale-plugin-fixture before this
 * class. If it is missing, JUnit marks the class skipped; a skipped test is never evidence of
 * plugin integration. The fixture returns deterministic protocol results and performs no Android
 * privileged operation.
 */
@RunWith(AndroidJUnit4::class)
class LocalePluginFixtureAndroidTest {

    private lateinit var context: Context

    @Before
    fun requireInstalledFixture() {
        context = ApplicationProvider.getApplicationContext()
        val installed = runCatching {
            context.packageManager.getApplicationInfo(FIXTURE_PACKAGE, 0)
        }.isSuccess
        assumeTrue("MISSING_DEPENDENCY: install the dedicated Locale fixture APK before running this class", installed)
    }

    @Test
    fun discoveryFindsCompatibleSettingAndConditionComponents() = runBlocking {
        val descriptors = PluginDiscoveryRegistry(context).refresh().descriptors

        val setting = descriptors.singleOrNull {
            it.packageName == FIXTURE_PACKAGE && it.type == PluginType.SETTING
        }
        val condition = descriptors.singleOrNull {
            it.packageName == FIXTURE_PACKAGE && it.type == PluginType.CONDITION
        }

        assertEquals(PluginCompatibilityStatus.COMPATIBLE, setting?.compatibility)
        assertEquals(FIXTURE_EDIT_ACTIVITY, setting?.editActivity?.className)
        assertEquals(FIXTURE_FIRE_RECEIVER, setting?.receiver?.className)
        assertEquals(PluginCompatibilityStatus.COMPATIBLE, condition?.compatibility)
        assertEquals(FIXTURE_CONDITION_RECEIVER, condition?.receiver?.className)
    }

    @Test
    fun explicitFireReportsFixtureSuccessFailurePendingCancellationAndTimeout() = runBlocking {
        val client = PluginFireClient(timeoutMs = 1_000L)

        val success = client.fire(context, FIXTURE_PACKAGE, FIXTURE_FIRE_RECEIVER, configuration("success"))
        val failure = client.fire(context, FIXTURE_PACKAGE, FIXTURE_FIRE_RECEIVER, configuration("failure"))
        val pending = client.fire(context, FIXTURE_PACKAGE, FIXTURE_FIRE_RECEIVER, configuration("pending"))
        val cancelled = client.fire(context, FIXTURE_PACKAGE, FIXTURE_FIRE_RECEIVER, configuration("cancelled"))
        val timedOut = PluginFireClient(timeoutMs = 300L)
            .fire(context, FIXTURE_PACKAGE, FIXTURE_FIRE_RECEIVER, configuration("timeout"))

        assertTrue(success.isSuccess)
        assertEquals(LocaleContract.RESULT_CODE_FAILED, failure.resultCode)
        assertEquals(LocaleContract.RESULT_CODE_PENDING, pending.resultCode)
        assertEquals(LocaleContract.RESULT_CODE_CANCELED, cancelled.resultCode)
        assertFalse(timedOut.isSuccess)
        assertTrue(timedOut.timedOut)
    }

    @Test
    fun explicitConditionQueryPreservesThreeStateProtocol() = runBlocking {
        val client = PluginConditionClient(timeoutMs = 1_000L)
        val satisfied = client.query(context, FIXTURE_PACKAGE, FIXTURE_CONDITION_RECEIVER, condition("satisfied"))
        val unsatisfied = client.query(context, FIXTURE_PACKAGE, FIXTURE_CONDITION_RECEIVER, condition("unsatisfied"))
        val unknown = client.query(context, FIXTURE_PACKAGE, FIXTURE_CONDITION_RECEIVER, condition("unknown"))

        assertEquals(ConditionResult.Satisfied, satisfied.condition)
        assertEquals(ConditionResult.Unsatisfied, unsatisfied.condition)
        assertEquals(ConditionResult.Unknown, unknown.condition)
        assertEquals(LocaleContract.RESULT_CONDITION_SATISFIED, satisfied.resultCode)
        assertEquals(LocaleContract.RESULT_CONDITION_UNSATISFIED, unsatisfied.resultCode)
        assertEquals(LocaleContract.RESULT_CONDITION_UNKNOWN, unknown.resultCode)
    }

    private fun configuration(outcome: String): Bundle = Bundle().apply {
        putString("fixtureOutcome", outcome)
    }

    private fun condition(state: String): Bundle = Bundle().apply {
        putString("fixtureConditionState", state)
    }

    private companion object {
        const val FIXTURE_PACKAGE = "com.nexaflow.testfixture.locale"
        const val FIXTURE_EDIT_ACTIVITY = "$FIXTURE_PACKAGE.LocalePluginEditActivity"
        const val FIXTURE_FIRE_RECEIVER = "$FIXTURE_PACKAGE.LocalePluginFireReceiver"
        const val FIXTURE_CONDITION_RECEIVER = "$FIXTURE_PACKAGE.LocalePluginConditionReceiver"
    }
}
