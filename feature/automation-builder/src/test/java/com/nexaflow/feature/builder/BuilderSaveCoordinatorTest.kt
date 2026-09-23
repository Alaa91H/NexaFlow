package com.nexaflow.feature.builder

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuilderSaveCoordinatorTest {

    @Test
    fun postSaveFlow_doesNotRunAfterCancelledSave() = runBlocking {
        val saveJob = Job().apply { cancel() }

        var postSaveFlowRan = false
        val postSaveJob = launchAfterSave(saveJob) {
            postSaveFlowRan = true
        }

        postSaveJob.join()

        assertFalse(postSaveFlowRan)
    }

    @Test
    fun postSaveFlow_waitsForPersistenceJob() = runBlocking {
        val allowSaveToFinish = CompletableDeferred<Unit>()
        val saveJob = launch {
            allowSaveToFinish.await()
        }

        var postSaveFlowRan = false
        val postSaveJob = launchAfterSave(saveJob) {
            postSaveFlowRan = true
        }

        yield()
        assertFalse(postSaveFlowRan)

        allowSaveToFinish.complete(Unit)
        postSaveJob.join()

        assertTrue(postSaveFlowRan)
    }

    @Test
    fun postSaveFlow_readsTheCommittedEditSnapshot() = runBlocking {
        data class EditSnapshot(
            val triggers: List<String>,
            val triggerMatch: String
        )

        var persisted = EditSnapshot(
            triggers = listOf("charger", "night-range"),
            triggerMatch = "ANY"
        )
        val writeStarted = CompletableDeferred<Unit>()
        val allowWriteToFinish = CompletableDeferred<Unit>()
        val saveJob = launch {
            writeStarted.complete(Unit)
            allowWriteToFinish.await()
            persisted = EditSnapshot(
                triggers = listOf("night-range", "wifi"),
                triggerMatch = "ALL"
            )
        }

        writeStarted.await()

        var observedAfterSave: EditSnapshot? = null
        val postSaveJob = launchAfterSave(saveJob) {
            observedAfterSave = persisted
        }

        yield()
        assertNull("post-save flow must not observe the stale edit snapshot", observedAfterSave)

        allowWriteToFinish.complete(Unit)
        postSaveJob.join()

        assertEquals(
            EditSnapshot(listOf("night-range", "wifi"), "ALL"),
            observedAfterSave
        )
    }
}
