package com.nexaflow.feature.builder

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.models.ConstraintType
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression tests for the responsive constraint editor layout. In particular,
 * configuration chips must wrap instead of sharing an undersized Row when the
 * device uses RTL and an accessibility font scale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ConstraintEditorCardLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun disableAnimations() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    @Test
    fun chargingStateChoices_wrapWithoutTextOverlap_onNarrowRtlLargeFontLayout() {
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = 1f, fontScale = 1.8f)
            ) {
                MaterialTheme {
                    LazyColumn(
                        modifier = Modifier
                            .width(200.dp)
                            .height(360.dp)
                    ) {
                        item {
                            Box(modifier = Modifier.width(200.dp)) {
                                ConstraintEditorCard(
                                    draft = ConstraintDraft(
                                        type = ConstraintType.CHARGING,
                                        config = mapOf("state" to "CHARGING")
                                    ),
                                    index = 0,
                                    total = 1,
                                    initiallyExpanded = true,
                                    onConfigChange = {},
                                    onRemove = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scroll once to the last option, then measure both chips in the
        // same viewport. Measuring after two independent scroll operations
        // would incorrectly compare two different scroll offsets.
        composeRule
            .onNodeWithTag("constraint_charging_no")
            .performScrollTo()
            .assertIsDisplayed()

        val chargingYesBounds = composeRule
            .onNodeWithTag("constraint_charging_yes")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val chargingNoBounds = composeRule
            .onNodeWithTag("constraint_charging_no")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        val labelsDoNotIntersect =
            chargingYesBounds.right <= chargingNoBounds.left ||
                chargingNoBounds.right <= chargingYesBounds.left ||
                chargingYesBounds.bottom <= chargingNoBounds.top ||
                chargingNoBounds.bottom <= chargingYesBounds.top

        assertTrue(
            "State choice labels must not overlap on a narrow RTL layout: " +
                "charging=$chargingYesBounds, notCharging=$chargingNoBounds",
            labelsDoNotIntersect
        )
    }
}
