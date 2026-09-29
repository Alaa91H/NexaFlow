package com.nexaflow.macrobenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.nexaflow.domain.canonical.BooleanValue
import com.nexaflow.domain.canonical.CanonicalExecutionPlanner
import com.nexaflow.domain.canonical.CanonicalNodeId
import com.nexaflow.domain.canonical.FailurePolicy
import com.nexaflow.domain.canonical.PlanExecutionPolicy
import com.nexaflow.domain.canonical.SequenceNode
import com.nexaflow.domain.canonical.SetStateNode
import com.nexaflow.domain.canonical.TargetId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T33 device-side latency evidence for the canonical planner.
 *
 * The JVM suite proves structural bounds deterministically; this benchmark
 * records real device timing for the maximum single-sequence workload the AST
 * accepts (1,024 commands) without turning wall-clock variance into a normal
 * unit-test failure.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CanonicalPlannerBenchmarks {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val maxSequence = SequenceNode(
        id = CanonicalNodeId("benchmark-root"),
        children = (0 until 1_024).map { index ->
            SetStateNode(
                id = CanonicalNodeId("benchmark-$index"),
                target = TargetId("core.benchmark.target.$index"),
                state = BooleanValue(index % 2 == 0),
            )
        },
    )

    @Test
    fun plan1024Commands() = benchmarkRule.measureRepeated {
        CanonicalExecutionPlanner.default().plan(
            root = maxSequence,
            executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
            failurePolicy = FailurePolicy.FAIL_FAST,
        )
    }
}
