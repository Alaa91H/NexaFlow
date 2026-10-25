package com.nexaflow.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Arabic legibility rules of the type scale.
 *
 * The app's Arabic face is Noto Sans Arabic, which stays crisp at regular
 * and medium weights but softens badly at extra bold. The scale therefore
 * reserves the heavy weights for large roles only, and never puts a bold
 * style under 14sp — a rule that is easy to break by accident when a new
 * role is added.
 */
class ArabicLegibilityTest {

    private val typography: Typography = nexaFlowTypography()

    /** Roles whose weight may reach Bold. */
    private val heavyRoles = setOf(
        "displayLarge", "displayMedium", "displaySmall",
        "headlineLarge", "headlineMedium", "headlineSmall"
    )

    @Test
    fun noBoldStyleIsUsedBelowFourteenSp() {
        val styles = listOf(
            "bodyLarge" to typography.bodyLarge,
            "bodyMedium" to typography.bodyMedium,
            "bodySmall" to typography.bodySmall,
            "labelLarge" to typography.labelLarge,
            "labelMedium" to typography.labelMedium,
            "labelSmall" to typography.labelSmall
        )
        styles.forEach { (role, style) ->
            assertTrue(
                style.fontWeight != FontWeight.Bold && style.fontWeight != FontWeight.ExtraBold,
                "$role must not be bold — Arabic text softens at heavy weights"
            )
        }
    }

    @Test
    fun heavyWeightsOnlyAppearOnLargeRoles() {
        val styles = listOf(
            "displayLarge" to typography.displayLarge,
            "displayMedium" to typography.displayMedium,
            "displaySmall" to typography.displaySmall,
            "headlineLarge" to typography.headlineLarge,
            "headlineMedium" to typography.headlineMedium,
            "headlineSmall" to typography.headlineSmall,
            "titleLarge" to typography.titleLarge,
            "titleMedium" to typography.titleMedium,
            "titleSmall" to typography.titleSmall
        )
        styles.forEach { (role, style) ->
            if (style.fontWeight == FontWeight.Bold) {
                assertTrue(
                    role in heavyRoles,
                    "$role carries a bold weight but is not a large role"
                )
            }
        }
    }

    @Test
    fun noRoleIsSmallerThanElevenSp() {
        val sizes = listOf(
            typography.bodySmall.fontSize,
            typography.labelSmall.fontSize
        )
        sizes.forEach { size ->
            assertTrue(
                size.value >= 11f,
                "no role may be smaller than 11sp — Arabic loses its diacritics below that"
            )
        }
    }

    @Test
    fun lineHeightsLeaveRoomForArabicDiacritics() {
        // Arabic ascenders and descenders need more leading than Latin: a
        // ratio under 1.3 clips the marks above and below the letters.
        val styles = listOf(
            "bodyLarge" to typography.bodyLarge,
            "bodyMedium" to typography.bodyMedium,
            "bodySmall" to typography.bodySmall,
            "labelMedium" to typography.labelMedium,
            "labelSmall" to typography.labelSmall
        )
        styles.forEach { (role, style) ->
            val ratio = style.lineHeight.value / style.fontSize.value
            assertTrue(
                ratio >= 1.3f,
                "$role has a line-height ratio of ${"%.2f".format(ratio)}, below the " +
                    "1.30 that Arabic diacritics need"
            )
        }
    }
}
