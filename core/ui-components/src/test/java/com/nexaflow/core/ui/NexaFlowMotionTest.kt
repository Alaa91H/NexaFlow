package com.nexaflow.core.ui

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the Google-2026 motion helpers ([NexaFlowSprings], [nexaFlowSpatialSpec],
 * [nexaFlowEffectsSpec]) to the active [MaterialTheme.motionScheme]:
 *  - every helper must return the same spec type and values the scheme itself
 *    provides (delegation, no drifted hand-copied constants);
 *  - under reduced-motion (ANIMATOR_DURATION_SCALE = 0) every helper must
 *    collapse to an instant `tween(0)` — no spring, no animation;
 *  - [nexaFlowEntrance] shows content statically and immediately under
 *    reduced motion.
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

    // --- reduce-motion detection -----------------------------------------

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

    // --- helpers delegate to the active MotionScheme ---------------------

    @Test
    fun defaultSpring_matchesSchemeDefaultSpatial() {
        var helper: FiniteAnimationSpec<Float>? = null
        var direct: FiniteAnimationSpec<Float>? = null
        composeRule.setContent {
            helper = NexaFlowSprings.default()
            direct = MaterialTheme.motionScheme.defaultSpatialSpec()
        }
        composeRule.waitForIdle()
        assertSameSpec(helper!!, direct!!)
    }

    @Test
    fun fastSpring_matchesSchemeFastSpatial() {
        var helper: FiniteAnimationSpec<Float>? = null
        var direct: FiniteAnimationSpec<Float>? = null
        composeRule.setContent {
            helper = NexaFlowSprings.fast()
            direct = MaterialTheme.motionScheme.fastSpatialSpec()
        }
        composeRule.waitForIdle()
        assertSameSpec(helper!!, direct!!)
    }

    @Test
    fun spatialSpec_matchesSchemeDefaultSpatial() {
        var helper: FiniteAnimationSpec<Float>? = null
        var direct: FiniteAnimationSpec<Float>? = null
        composeRule.setContent {
            helper = nexaFlowSpatialSpec<Float>()
            direct = MaterialTheme.motionScheme.defaultSpatialSpec()
        }
        composeRule.waitForIdle()
        assertSameSpec(helper!!, direct!!)
    }

    @Test
    fun spatialSpecSlow_matchesSchemeSlowSpatial() {
        var helper: FiniteAnimationSpec<Float>? = null
        var direct: FiniteAnimationSpec<Float>? = null
        composeRule.setContent {
            helper = nexaFlowSpatialSpec<Float>(slow = true)
            direct = MaterialTheme.motionScheme.slowSpatialSpec()
        }
        composeRule.waitForIdle()
        assertSameSpec(helper!!, direct!!)
    }

    @Test
    fun effectsSpec_matchesSchemeDefaultEffects() {
        var helper: FiniteAnimationSpec<Float>? = null
        var direct: FiniteAnimationSpec<Float>? = null
        composeRule.setContent {
            helper = nexaFlowEffectsSpec<Float>()
            direct = MaterialTheme.motionScheme.defaultEffectsSpec()
        }
        composeRule.waitForIdle()
        assertSameSpec(helper!!, direct!!)
    }

    @Test
    fun effectsSpecSlow_matchesSchemeSlowEffects() {
        var helper: FiniteAnimationSpec<Float>? = null
        var direct: FiniteAnimationSpec<Float>? = null
        composeRule.setContent {
            helper = nexaFlowEffectsSpec<Float>(slow = true)
            direct = MaterialTheme.motionScheme.slowEffectsSpec()
        }
        composeRule.waitForIdle()
        assertSameSpec(helper!!, direct!!)
    }

    // --- reduced motion: every helper collapses to an instant tween ------

    @Test
    fun allSpecs_collapseToInstantTweenUnderReducedMotion() {
        setAnimatorDurationScale(0f)
        val specs = mutableListOf<FiniteAnimationSpec<Float>>()
        composeRule.setContent {
            specs += NexaFlowSprings.default()
            specs += NexaFlowSprings.fast()
            specs += nexaFlowSpatialSpec<Float>()
            specs += nexaFlowSpatialSpec<Float>(slow = true)
            specs += nexaFlowEffectsSpec<Float>()
            specs += nexaFlowEffectsSpec<Float>(slow = true)
        }
        composeRule.waitForIdle()
        assertEquals("all six helpers must resolve", 6, specs.size)
        specs.forEach { spec ->
            val tween = spec as? TweenSpec
            assertNotNull("reduced motion must collapse springs to a tween: $spec", tween)
            assertEquals("reduced-motion tween must be instant", 0, tween!!.durationMillis)
        }
    }

    // --- entrance under reduced motion ------------------------------------

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

    // --- helpers -----------------------------------------------------------

    /** Asserts the helper returned the same spec type AND values as the scheme. */
    private fun assertSameSpec(
        helper: FiniteAnimationSpec<Float>,
        direct: FiniteAnimationSpec<Float>
    ) {
        assertEquals("spec type must match the scheme", direct::class, helper::class)
        when (helper) {
            is SpringSpec -> {
                val expected = direct as SpringSpec
                assertEquals(expected.dampingRatio, helper.dampingRatio, 0.0001f)
                assertEquals(expected.stiffness, helper.stiffness, 0.0001f)
            }
            is TweenSpec -> {
                val expected = direct as TweenSpec
                assertEquals(expected.durationMillis, helper.durationMillis)
            }
            else -> fail("unexpected spec type ${helper::class}")
        }
    }
}
