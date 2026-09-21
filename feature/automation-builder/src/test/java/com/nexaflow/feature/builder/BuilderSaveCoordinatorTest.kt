package com.nexaflow.feature.builder

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuilderSaveCoordinatorTest {

    @Test
    fun postSaveFlow_doesNotRunAfterCancelledSave() = runBlocking {
        val saveJob = launch {
            error("simulated save failure")
        }

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
}
