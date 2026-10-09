package com.nexaflow.core.execution

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.nexaflow.core.datastore.TriggerExpressionHistoryStore
import com.nexaflow.core.security.OccurrenceIdentityHmac
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.workflow.TriggerExpressionDefinitionV2
import com.nexaflow.domain.workflow.TriggerExpressionNodeV2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerExpressionRuntimeEvaluatorTest {
    private val context: Context = ContextWrapper(null)

    private fun automation(root: TriggerExpressionNodeV2) = Automation(
        id = "runtime-expression",
        name = "Temporal test",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 1,
        enabled = true,
        triggers = listOf(
            Trigger(TriggerType.SMS, emptyMap()),
            Trigger(TriggerType.CALENDAR, emptyMap()),
        ),
        actions = emptyList(),
        triggerExpressionV2 = TriggerExpressionDefinitionV2(root = root),
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun sequencePersistsAcrossEvaluationsAndRequiresOrder() = runTest {
        val state = MemoryPreferencesDataStore()
        val hmac = OccurrenceIdentityHmac { source, id ->
            java.security.MessageDigest.getInstance("SHA-256")
                .digest("$source:$id".toByteArray()).joinToString("") { "%02x".format(it) }
        }
        val evaluator = TriggerExpressionRuntimeEvaluator(TriggerExpressionHistoryStore(state, hmac, { 0L }))
        val workflow = automation(TriggerExpressionNodeV2.Sequence(0, 1, 1_000L))

        val first = evaluator.evaluate(
            context, workflow, TriggerOccurrence(setOf(0), 100L, "sms", "event-a"), 100L
        )
        assertEquals(ConditionResult.Unsatisfied, first.result)
        val second = evaluator.evaluate(
            context, workflow, TriggerOccurrence(setOf(1), 200L, "calendar", "event-b"), 200L
        )
        assertEquals(ConditionResult.Satisfied, second.result)

        val simultaneous = TriggerExpressionRuntimeEvaluator(
            TriggerExpressionHistoryStore(MemoryPreferencesDataStore(), hmac, { 0L })
        ).evaluate(
            context,
            workflow.copy(triggers = listOf(
                Trigger(TriggerType.SMS, emptyMap()), Trigger(TriggerType.SMS, emptyMap())
            )),
            TriggerOccurrence(setOf(0, 1), 250L, "sms", "same-occurrence"),
            250L,
        )
        assertEquals(ConditionResult.Unsatisfied, simultaneous.result)

        val reversedState = MemoryPreferencesDataStore()
        val reversed = TriggerExpressionRuntimeEvaluator(TriggerExpressionHistoryStore(reversedState, hmac, { 0L }))
        reversed.evaluate(context, workflow, TriggerOccurrence(setOf(1), 300L, "calendar", "event-c"), 300L)
        val reverseResult = reversed.evaluate(
            context, workflow, TriggerOccurrence(setOf(0), 400L, "sms", "event-d"), 400L
        )
        assertEquals(ConditionResult.Unsatisfied, reverseResult.result)
    }

    @Test
    fun malformedOptInIsUnavailableAndMarkedInvalid() = runTest {
        val workflow = automation(TriggerExpressionNodeV2.Sequence(0, 1, 1_000L)).copy(
            triggerExpressionV2 = TriggerExpressionDefinitionV2.invalidSentinel()
        )
        val result = TriggerExpressionRuntimeEvaluator(null).evaluate(context, workflow, null, 1L)
        assertEquals(ConditionResult.Unavailable, result.result)
        assertTrue(result.invalid)
    }

    private class MemoryPreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(emptyPreferences())
        override val data: StateFlow<Preferences> = state.asStateFlow()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }
}
