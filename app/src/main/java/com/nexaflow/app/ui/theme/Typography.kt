package com.nexaflow.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nexaflow.app.R

/**
 * Ink Glass typography (§5.5).
 *
 * Plus Jakarta Sans (OFL) carries Latin, IBM Plex Sans Arabic (OFL) carries
 * Arabic. Compose does not fall back per glyph across `Font` entries, so the
 * family is chosen per locale by [contentFontFamily]: Arabic locales
 * get Plex, Cyrillic/Devanagari/CJK locales keep Roboto Flex (Plus Jakarta has
 * no coverage there), everything else gets Jakarta.
 */

/** Plus Jakarta Sans is a variable font; each weight is a variation instance. */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, weights: List<Int>): FontFamily = FontFamily(
    weights.map { weight ->
        Font(
            resId = resId,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight))
        )
    }
)

private val JakartaFamily = variableFamily(
    R.font.plus_jakarta_sans,
    listOf(400, 500, 600, 700, 800)
)

private val RobotoFlexFamily = variableFamily(
    R.font.roboto_flex,
    listOf(400, 500, 600, 700, 800)
)

/** IBM Plex Sans Arabic ships static weights; 800 maps to Bold. */
private val PlexArabicFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold)
)

/**
 * The family for the current locale. Pure and testable in spirit: `ar` → Plex,
 * `ru`/`hi`/`ja`/`zh` → Roboto Flex, everything else → Jakarta.
 */
fun contentFontFamily(languageTag: String): FontFamily = when (languageTag) {
    "ar" -> PlexArabicFamily
    "ru", "hi", "ja", "zh" -> RobotoFlexFamily
    else -> JakartaFamily
}


/**
 * Material 3 roles re-cut on the Ink scale so built-in components (menus,
 * dialogs, switches, text fields) inherit the same voice as the custom UI.
 */
fun nexaFlowM3Typography(family: FontFamily): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 40.sp,
        lineHeight = 48.sp
    ),
    displaySmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 34.sp,
        lineHeight = 42.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(700),
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(700),
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(500),
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(500),
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(500),
        fontSize = 12.5.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(700),
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(600),
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(700),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    )
)
