package com.nexaflow.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.nexaflow.core.ui.theme.nexaFlowTypography
import com.nexaflow.app.R

/**
 * The app's font families.
 *
 * Roboto Flex is the variable font behind Google's current Material
 * products, and Noto Sans Arabic is the Arabic fallback, so the Arabic UI
 * renders with a matching weight and rhythm instead of the default
 * substitution.
 *
 * The *scale* — sizes, weights, letter spacing — belongs to the shared
 * design system, not to this module: the Wear app and the widgets read the
 * same decisions from `core/ui-components`. Only the font assets live here,
 * because only this module ships them.
 */
@OptIn(ExperimentalTextApi::class)
private fun googleFamily(weight: Int): FontFamily = FontFamily(
    Font(
        R.font.roboto_flex,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight))
    ),
    Font(
        R.font.noto_sans_arabic,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight))
    )
)

private val googleNormal = googleFamily(FontWeight.Normal.weight)
private val googleMedium = googleFamily(FontWeight.Medium.weight)

/** The app's type scale, built from the shared design system's scale. */
val Typography: Typography = nexaFlowTypography(normal = googleNormal, medium = googleMedium)
