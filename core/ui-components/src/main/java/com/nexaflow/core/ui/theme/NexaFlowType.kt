package com.nexaflow.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Ink Glass type scale. These are the only text styles feature code should
 * use: they carry the reference's exact sizes, weights and line heights, and
 * they are built for whichever font family the app theme selected for the
 * current locale (Plus Jakarta Sans, IBM Plex Sans Arabic, or the system font).
 *
 * Material 3 roles remain available for built-in components, but new UI must
 * consume `NexaFlowTheme.type`.
 */
@Immutable
data class NexaFlowType(
    val screenTitle: TextStyle,
    val subScreenTitle: TextStyle,
    val bentoValue: TextStyle,
    val cardTitle: TextStyle,
    val pickerRowTitle: TextStyle,
    val meta: TextStyle,
    val sectionLabel: TextStyle,
    val button: TextStyle,
    val chip: TextStyle,
    val askPill: TextStyle,
    val statusPill: TextStyle,
    /** 11sp/800, uppercase + 0.08em tracking for Latin scripts only. */
    val eyebrow: TextStyle,
    /** 10.5sp/800, the WHEN / THEN / EXIT label. */
    val flowLabel: TextStyle,
    val flowChip: TextStyle
)

/** Builds the Ink type scale on top of [family]. */
fun nexaFlowType(family: FontFamily = FontFamily.Default): NexaFlowType = NexaFlowType(
    screenTitle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    subScreenTitle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    bentoValue = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    cardTitle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(700),
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    pickerRowTitle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(700),
        fontSize = 15.5.sp,
        lineHeight = 20.sp
    ),
    meta = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(500),
        fontSize = 12.5.sp,
        lineHeight = 16.sp
    ),
    sectionLabel = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(700),
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    button = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    chip = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(600),
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    askPill = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(600),
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    statusPill = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(700),
        fontSize = 11.5.sp,
        lineHeight = 14.sp
    ),
    eyebrow = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.88.sp
    ),
    flowLabel = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(800),
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.84.sp
    ),
    flowChip = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(600),
        fontSize = 12.5.sp,
        lineHeight = 16.sp
    )
)

val LocalNexaFlowType = staticCompositionLocalOf { nexaFlowType() }

// Java's Pattern rejects a bare script name (`\p{Arabic}`), so the Unicode
// script property must be spelled `IsArabic`.
private val arabicScript = Regex("\\p{IsArabic}")

/** True when [text] contains Arabic script anywhere. */
fun containsArabicScript(text: String): Boolean = arabicScript.containsMatchIn(text)

/**
 * Arabic must never receive letter-spacing: tracking breaks the joining
 * behaviour of the script. Gate on the string's own script — not on the app
 * locale — so "تشغيل GPS" inside an English list stays intact.
 */
fun TextStyle.forText(text: String): TextStyle =
    if (containsArabicScript(text)) copy(letterSpacing = 0.sp) else this

/**
 * Uppercase is a Latin-only affordance for eyebrows. Arabic has no case and
 * uppercasing would be a no-op at best, a broken shaping at worst.
 */
fun eyebrowText(text: String): String =
    if (containsArabicScript(text)) text else text.uppercase()
