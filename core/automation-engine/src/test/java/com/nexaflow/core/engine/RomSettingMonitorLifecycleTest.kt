package com.nexaflow.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationRuntimeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Lifecycle regression tests for [RomSettingMonitor]. A stopped monitoring
 * service must cancel the ROM polling job; otherwise a subsequent service start
 * creates a second polling loop and can execute an automation twice.
 */
@RunWith(RobolectricTestRunner::class)
class RomSettingMonitorLifecycleTest {

    @Test
    fun `stop cancels polling and a later initialize starts one fresh job`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = FakeRepository(emptyList())
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val runtimeStore = AutomationRuntimeStore(context)
        val monitor = RomSettingMonitor(
            context = context,
            repository = repository,
            executionEngine = engine,
            activeStore = ActiveTriggerStore(context),
            runtimeStore = runtimeStore,
            exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history),
            scope = scope
        )

        monitor.initialize()
        assertTrue("initialize must retain one active polling job", monitor.isPollingForTest())

        monitor.stop()
        assertFalse("stop must cancel the active polling job", monitor.isPollingForTest())

        monitor.initialize()
        assertTrue("a later service start must create one fresh polling job", monitor.isPollingForTest())

        monitor.stop()
        scope.cancel()
    }
}
