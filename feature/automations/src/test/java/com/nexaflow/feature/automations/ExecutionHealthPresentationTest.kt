package com.nexaflow.feature.automations

import com.nexaflow.domain.models.AutomationHealthStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutionHealthPresentationTest {

    @Test
    fun `no execution state uses the no runs title and explanation`() {
        assertEquals(R.string.execution_health_no_runs, executionHealthTitleRes(AutomationHealthStatus.NO_EXECUTIONS))
        assertEquals(R.string.execution_health_no_runs_sub, executionHealthSubtitleRes(AutomationHealthStatus.NO_EXECUTIONS))
    }

    @Test
    fun `healthy state uses activity wording rather than a success claim`() {
        assertEquals(R.string.execution_health_activity, executionHealthTitleRes(AutomationHealthStatus.HEALTHY))
        assertEquals(R.string.execution_health_activity_sub, executionHealthSubtitleRes(AutomationHealthStatus.HEALTHY))
    }

    @Test
    fun `repeated failures use attention wording`() {
        assertEquals(R.string.execution_health_attention, executionHealthTitleRes(AutomationHealthStatus.NEEDS_ATTENTION))
        assertEquals(R.string.execution_health_attention_sub, executionHealthSubtitleRes(AutomationHealthStatus.NEEDS_ATTENTION))
    }
}
