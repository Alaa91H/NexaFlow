package com.nexaflow.core.execution

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies that a task lifecycle survives a fresh store instance and authorizes
 * its configured end behavior exactly once.
 */
@RunWith(RobolectricTestRunner::class)
class ActiveExecutionStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun freshStore() = ActiveExecutionStore(context)

    @Before
    fun clearLedger() = runBlocking {
        freshStore().clear("active-execution-test")
    }

    @Test
    fun startedTask_survivesAFreshStoreInstance() = runBlocking {
        freshStore().markStarted("active-execution-test")

        assertTrue(freshStore().consumeStarted("active-execution-test"))
    }

    @Test
    fun consumeStarted_isOneShot() = runBlocking {
        val writer = freshStore()
        writer.markStarted("active-execution-test")

        assertTrue(freshStore().consumeStarted("active-execution-test"))
        assertFalse(freshStore().consumeStarted("active-execution-test"))
    }
}
