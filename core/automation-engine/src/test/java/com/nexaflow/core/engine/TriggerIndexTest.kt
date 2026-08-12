package com.nexaflow.core.engine

import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerIndexTest {

    private fun automation(
        id: String,
        enabled: Boolean = true,
        triggerTypes: List<TriggerType> = listOf(TriggerType.BATTERY),
    ) = Automation(
        id = id,
        name = id,
        description = "",
        icon = "",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "",
        priority = 0,
        enabled = enabled,
        triggers = triggerTypes.map { Trigger(it, emptyMap()) },
        actions = emptyList(),
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun sourceOf(type: TriggerType): String = TriggerSource.forTrigger(type).sourceId

    /** Starts collecting on the test scheduler (unconfined so emissions land eagerly). */
    private fun kotlinx.coroutines.test.TestScope.startCollecting(index: TriggerIndex) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { index.start() }
    }

    @Test
    fun `empty flow keeps the index empty`() = runTest {
        val index = TriggerIndex(MutableStateFlow(emptyList()))
        startCollecting(index)
        advanceUntilIdle()
        assertTrue(index.isEmpty())
        assertEquals(emptyList<Automation>(), index.bySource(sourceOf(TriggerType.BATTERY)))
    }

    @Test
    fun `initial emission indexes enabled automations by their trigger source`() = runTest {
        val battery = automation("b1", triggerTypes = listOf(TriggerType.BATTERY))
        val location = automation("l1", triggerTypes = listOf(TriggerType.LOCATION))
        val index = TriggerIndex(MutableStateFlow(listOf(battery, location)))
        startCollecting(index)
        advanceUntilIdle()

        assertEquals(listOf("b1"), index.bySource(sourceOf(TriggerType.BATTERY)).map { it.id })
        assertEquals(listOf("l1"), index.bySource(sourceOf(TriggerType.LOCATION)).map { it.id })
        assertEquals(emptyList<Automation>(), index.bySource("time"))
    }

    @Test
    fun `disabled automations are excluded`() = runTest {
        val enabled = automation("on", enabled = true)
        val disabled = automation("off", enabled = false)
        val index = TriggerIndex(MutableStateFlow(listOf(enabled, disabled)))
        startCollecting(index)
        advanceUntilIdle()

        assertEquals(listOf("on"), index.bySource(sourceOf(TriggerType.BATTERY)).map { it.id })
        assertNull(index.snapshot("off"))
    }

    @Test
    fun `multiple triggers index under every source`() = runTest {
        val multi = automation(
            "multi",
            triggerTypes = listOf(TriggerType.BATTERY, TriggerType.LOCATION, TriggerType.WEBHOOK),
        )
        val index = TriggerIndex(MutableStateFlow(listOf(multi)))
        startCollecting(index)
        advanceUntilIdle()

        assertEquals(listOf("multi"), index.bySource(sourceOf(TriggerType.BATTERY)).map { it.id })
        assertEquals(listOf("multi"), index.bySource(sourceOf(TriggerType.LOCATION)).map { it.id })
        assertEquals(listOf("multi"), index.bySource(sourceOf(TriggerType.WEBHOOK)).map { it.id })
    }

    @Test
    fun `network mode and rom setting map to their canonical sources`() = runTest {
        val net = automation("net", triggerTypes = listOf(TriggerType.NETWORK_MODE))
        val rom = automation("rom", triggerTypes = listOf(TriggerType.ROM_SETTING))
        val index = TriggerIndex(MutableStateFlow(listOf(net, rom)))
        startCollecting(index)
        advanceUntilIdle()

        // Canonical mapping (EventSource contract): NETWORK_MODE → connectivity,
        // ROM_SETTING → device.
        assertEquals(listOf("net"), index.bySource(sourceOf(TriggerType.NETWORK_MODE)).map { it.id })
        assertEquals(listOf("rom"), index.bySource(sourceOf(TriggerType.ROM_SETTING)).map { it.id })
    }

    @Test
    fun `re-emission rebuilds the index after a save`() = runTest {
        val flow = MutableStateFlow(listOf(automation("old")))
        val index = TriggerIndex(flow)
        startCollecting(index)
        advanceUntilIdle()
        assertEquals(listOf("old"), index.bySource(sourceOf(TriggerType.BATTERY)).map { it.id })

        // Simulate a save: the old automation is replaced by a new one.
        flow.value = listOf(automation("new"))
        advanceUntilIdle()
        assertEquals(listOf("new"), index.bySource(sourceOf(TriggerType.BATTERY)).map { it.id })
        assertNull(index.snapshot("old"))
        assertTrue(index.bySource(sourceOf(TriggerType.BATTERY)).single().id == "new")
    }

    @Test
    fun `re-emission picks up enable toggles`() = runTest {
        val flow = MutableStateFlow(listOf(automation("task", enabled = false)))
        val index = TriggerIndex(flow)
        startCollecting(index)
        advanceUntilIdle()
        assertEquals(emptyList<Automation>(), index.bySource(sourceOf(TriggerType.BATTERY)))

        flow.value = listOf(automation("task", enabled = true))
        advanceUntilIdle()
        assertEquals(listOf("task"), index.bySource(sourceOf(TriggerType.BATTERY)).map { it.id })
        assertEquals("task", index.snapshot("task")?.id)
    }

    @Test
    fun `snapshot returns the latest enabled row and null for unknown ids`() = runTest {
        val index = TriggerIndex(MutableStateFlow(listOf(automation("a"))))
        startCollecting(index)
        advanceUntilIdle()

        assertEquals("a", index.snapshot("a")?.id)
        assertNull(index.snapshot("missing"))
    }

    @Test
    fun `bySource preserves database emission order and dedupes`() = runTest {
        val a = automation("a", triggerTypes = listOf(TriggerType.BATTERY))
        val b = automation("b", triggerTypes = listOf(TriggerType.BATTERY))
        val index = TriggerIndex(MutableStateFlow(listOf(a, b)))
        startCollecting(index)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), index.bySource(sourceOf(TriggerType.BATTERY)).map { it.id })
    }

    @Test
    fun `version increments on every rebuild`() = runTest {
        val flow = MutableStateFlow(listOf(automation("x")))
        val index = TriggerIndex(flow)
        startCollecting(index)
        advanceUntilIdle()
        val afterFirst = index.version

        flow.value = listOf(automation("y"))
        advanceUntilIdle()
        assertTrue(index.version > afterFirst)
    }

    @Test
    fun `index without start stays empty`() {
        val index = TriggerIndex(MutableStateFlow(listOf(automation("never"))))
        assertTrue(index.isEmpty())
        assertEquals(emptyList<Automation>(), index.bySource(sourceOf(TriggerType.BATTERY)))
        assertNull(index.snapshot("never"))
    }

    @Test
    fun `false is not treated as true for disabled rows with same id`() = runTest {
        val flow = MutableStateFlow(
            listOf(automation("dup", enabled = false), automation("dup", enabled = true)),
        )
        val index = TriggerIndex(flow)
        startCollecting(index)
        advanceUntilIdle()

        assertFalse(index.isEmpty())
        assertEquals(listOf("dup"), index.bySource(sourceOf(TriggerType.BATTERY)).map { it.id })
        assertEquals("dup", index.snapshot("dup")?.id)
    }
}
