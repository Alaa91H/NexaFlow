package com.nexaflow.feature.builder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T35 product accessibility regression coverage for the unified configurator.
 * It validates semantics/touch geometry on the actual Compose sheet, not only
 * the pure accessibility presentation model.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NodeConfiguratorAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unifiedConfiguratorKeepsReadableSemanticsAndTouchTargetInRtlLargeFont() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = 1f, fontScale = 2f),
            ) {
                MaterialTheme {
                    Box(modifier = Modifier.width(220.dp)) {
                        NodeConfiguratorSheet(
                            title = "إعداد التنفيذ",
                            selectedCount = null,
                            confirmLabel = "تطبيق",
                            confirmEnabled = true,
                            onConfirm = {},
                            onDismiss = {},
                        ) {
                            androidx.compose.material3.Text(
                                "قيمة مهمة كاملة لا يجب أن تختفي عن قارئ الشاشة",
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNode(hasText("إعداد التنفيذ")).assertIsDisplayed()
        composeRule.onNode(
            hasText("قيمة مهمة كاملة لا يجب أن تختفي عن قارئ الشاشة"),
        ).assertIsDisplayed()

        val confirmNode = composeRule.onNode(hasText("تطبيق") and hasClickAction())
            .assertIsDisplayed()
            .assertIsEnabled()
            .fetchSemanticsNode()

        assertTrue(
            "Confirm touch target must remain at least 48dp under RTL/2x font: ${confirmNode.boundsInRoot}",
            confirmNode.boundsInRoot.height >= 48f,
        )
    }

    @Test
    fun longLocalizedTitleAndValueRemainInSemanticsWithoutEllipsisReplacement() {
        val title = "إعدادات التشغيل المتقدمة جدًا للأتمتة"
        val value = "https://example.com/very/important/value/that/must/remain-readable"

        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = 1f, fontScale = 1.8f),
            ) {
                MaterialTheme {
                    Box(modifier = Modifier.width(200.dp)) {
                        NodeConfiguratorSheet(
                            title = title,
                            confirmLabel = "حفظ",
                            confirmEnabled = true,
                            onConfirm = {},
                            onDismiss = {},
                        ) {
                            androidx.compose.material3.Text(value)
                        }
                    }
                }
            }
        }

        composeRule.onNode(hasText(title)).assertIsDisplayed()
        composeRule.onNode(hasText(value)).assertIsDisplayed()
    }
}
