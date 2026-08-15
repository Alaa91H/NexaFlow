package com.nexaflow.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
// In benchmark 1.4.x the rule moved to its own junit4 package.
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start macrobenchmarks (P0-3, "Baseline Profiles + Macrobenchmark").
 *
 * Two runs of the same journey so the baseline profile's effect is measurable:
 *  - [startup] uses the installed compilation state (the bundled baseline
 *    profile is applied by ProfileInstaller), the realistic production path;
 *  - [startupWithBaselineProfile] forces a partial recompile of exactly the
 *    profile's classes, isolating the AOT benefit from OS-side noise.
 *
 * Device-only. Run with a connected emulator/device:
 *   ./gradlew :macrobenchmark:connectedDebugAndroidTest
 *
 * Results land in macrobenchmark/build/outputs/connected_android_test_additional_output/.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.nexaflow.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = "com.nexaflow.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
    ) {
        pressHome()
        startActivityAndWait()
    }
}
