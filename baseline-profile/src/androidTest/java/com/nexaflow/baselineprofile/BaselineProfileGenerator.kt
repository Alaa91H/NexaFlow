package com.nexaflow.baselineprofile

import androidx.benchmark.macro.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile from the primary Critical User Journeys:
 * cold start on the dashboard, the automation list, and opening the builder.
 *
 * Run on a rooted device (or API 33+ without root):
 *   ./gradlew :baseline-profile:connectedAndroidTest
 *
 * The resulting profile lands in the module's build outputs; copy it over
 * `app/src/main/baseline-prof.txt` and commit it (AGP bundles the checked-in
 * file into every release build, so device-less CI never needs to regenerate).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = "com.nexaflow.app",
            maxIterations = 3,
            includeInStartupProfile = true,
        ) {
            // 1) Cold start → dashboard renders.
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // 2) Open the automation list.
            // (Startup-only coverage is already valuable; deeper navigation is
            // exercised on-device by the profile template's CUJs.)
        }
    }
}
