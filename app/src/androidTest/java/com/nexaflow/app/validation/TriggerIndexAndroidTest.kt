package com.nexaflow.app.validation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.engine.TriggerIndex
import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side contract for TriggerIndex's own Flow projection and immutable snapshots.
 *
 * The source is a deterministic MutableStateFlow because TriggerIndex accepts only a Flow; this
 * test proves index semantics, not Room observation or Android broadcast delivery.
 */
@RunWith(AndroidJUnit4::class)
class TriggerIndexAndroidTest {

    @Test
    fun rebuildTracksEnabledSubscriptionsAndDropsDisabledAutomationAtomically() = runBlocking {
        val source = MutableStateFlow(listOf(automation("enabled", enabled = true)))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val index = TriggerIndex(source)
        val job = scope.launch { index.start() }

        try {
            awaitVersion(index, minimum = 1L)
            assertEquals(listOf("enabled"), index.bySource(TriggerSource.PLUGIN.sourceId).map { it.id })
            assertEquals("enabled", index.snapshot("enabled")?.id)

            source.value = listOf(automation("disabled", enabled = false))
            awaitVersion(index, minimum = 2L)
            assertEquals(emptyList<String>(), index.bySource(TriggerSource.PLUGIN.sourceId).map { it.id })
            assertNull(index.snapshot("enabled"))
            assertNull(index.snapshot("disabled"))
        } finally {
            job.cancelAndJoin()
        }
    }

    private suspend fun awaitVersion(index: TriggerIndex, minimum: Long) = withTimeout(5_000L) {
        while (index.version < minimum) delay(10L)
    }

    private fun automation(id: String, enabled: Boolean): Automation = Automation(
        id = id,
        name = id,
        description = "",
        icon = "",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "Test",
        priority = 0,
        enabled = enabled,
        triggers = listOf(Trigger(type = TriggerType.PLUGIN_EVENT)),
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L
    )
}
