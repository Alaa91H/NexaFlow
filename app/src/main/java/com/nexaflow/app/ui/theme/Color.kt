package com.nexaflow.app.ui.theme

import androidx.compose.material3.ColorScheme
import com.nexaflow.core.ui.theme.AccentSeed
import com.nexaflow.core.ui.theme.DefaultAccentId
import com.nexaflow.core.ui.theme.NexaFlowAccentSeeds
import com.nexaflow.core.ui.theme.accentSeedFor
import com.nexaflow.core.ui.theme.nexaFlowColorScheme

/**
 * The app's color entry point.
 *
 * The palette itself lives in `core/ui-components` — every module, the Wear
 * app and the widgets read it from there, so there is exactly one place a
 * color is defined. This file only re-exports it under the names the app's
 * own code already uses.
 *
 * The brand is original: an indigo → violet → aqua flow gradient. The
 * previous Google Blue seed set is gone, and with it the "this looks like a
 * Google app" reading the old palette produced.
 */

/** Every accent the appearance screen offers, keyed by its stored id. */
internal val accentSeeds: Map<String, AccentSeed> = NexaFlowAccentSeeds

/** The accent used when nothing has been chosen yet. */
internal const val defaultAccentId: String = DefaultAccentId

/** The accent seed for [accentId], falling back to the default. */
internal fun accentSeed(accentId: String?): AccentSeed = accentSeedFor(accentId)

/** The Material 3 scheme for [seed] on the given canvas. */
internal fun googleColorScheme(seed: AccentSeed, dark: Boolean): ColorScheme =
    nexaFlowColorScheme(seed, dark)
