package com.nexaflow.core.ui

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the Google-2026 staggered entrance behavior in [nexaFlowEntrance]
 * and its motion helpers:
 *  - `prefers-reduced-motion` (ANIMATOR_DURATION_SCALE = 0) must be respected:
 *    content appears statically and instantly — no delay, no spring.
 *  - Without reduced motion the helpers resolve to the active MotionScheme
 *    springs (never an instant tween).
 *
 * Pinned to SDK 35 like the other Compose-UI tests: Espresso 3.7.0's
 * InputManagerEventInjectionStrategy reflectively calls the static
 * InputManager.getInstance(), removed from Robolectric's android-all for
 * SDK 37 (AOSP moved to context-based lookup).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NexaFlowMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun setAnimatorDurationScale(scale: Float) {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            scale
        )
    }

    @Test
    fun reduceMotion_disabledByDefault() {
        var reduced: Boolean? = null
        composeRule.setContent {
            reduced = isSystemReduceMotionEnabled()
        }
        composeRule.waitForIdle()
        assertFalse("animator scale defaults to 1 → reduced motion off", reduced!!)
    }

    @Test
    fun reduceMotion_enabledWhenAnimatorScaleZero() {
        setAnimatorDurationScale(0f)
        var reduced: Boolean? = null
        composeRule.setContent {
            reduced = isSystemReduceMotionEnabled()
        }
        composeRule.waitForIdle()
        assertTrue("ANIMATOR_DURATION_SCALE=0 must disable motion", reduced!!)
    }

    @Test
    fun entrance_isStaticAndImmediateUnderReducedMotion() {
        setAnimatorDurationScale(0f)
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    // Even a long delay must be skipped under reduced motion —
                    // the content is shown statically, immediately.
                    .nexaFlowEntrance(delayMillis = 5_000)
                    .testTag("card")
            ) {
                Text("card-content")
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("card-content").assertIsDisplayed()
        composeRule.onNodeWithTag("card").assertIsDisplayed()
    }

    @Test
    fun springs_resolveToInstantTweenUnderReducedMotion() {
        setAnimatorDurationScale(0f)
        var spec: FiniteAnimationSpec<Float>? = null
        composeRule.setContent {
            spec = NexaFlowSprings.default()
        }
        composeRule.waitForIdle()
        assertNotNull(spec)
        val tween = spec as? TweenSpec
        assertNotNull("reduced motion must collapse springs to a tween", tween)
        assertTrue("reduced motion tween must be instant", tween!!.durationMillis == 0)
    }

    @Test
    fun springs_resolveToSchemeSpringNormally() {
        // Default animator scale (1f) → the MotionScheme's spatial spring,
        // never an instant tween.
        var spec: FiniteAnimationSpec<Float>? = null
        composeRule.setContent {
            spec = NexaFlowSprings.default()
        }
        composeRule.waitForIdle()
        assertNotNull(spec)
        val instant = spec as? TweenSpec
        assertTrue(
            "default springs must come from the scheme (bouncy), not an instant tween",
            instant == null || instant.durationMillis != 0
        )
    }
}
