package com.nexaflow.core.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/** Adaptive navigation style chosen from the M3 window width size class. */
enum class NavigationStyle {
    /** Compact/medium: bottom navigation bar. */
    BOTTOM_BAR,

    /** Expanded/extra-large: navigation rail on the start edge. */
    RAIL,

    /** Non-top-level destination: no persistent navigation chrome. */
    NONE
}

/**
 * Pure decision for adaptive navigation chrome (F2 in the 2026 audit).
 *
 * Compact and medium windows get the bottom bar; expanded and extra-large
 * windows get a navigation rail; non-top-level destinations (builder,
 * details, pickers) never show persistent chrome. Kept as a pure function so
 * it is unit-testable and the NavHost graph stays independent of it — only
 * the chrome changes, so back-stack state survives window resizes.
 */
fun navigationStyleFor(widthSizeClass: WindowWidthSizeClass, isTopLevel: Boolean): NavigationStyle {
    if (!isTopLevel) return NavigationStyle.NONE
    return if (widthSizeClass >= WindowWidthSizeClass.Expanded) {
        NavigationStyle.RAIL
    } else {
        NavigationStyle.BOTTOM_BAR
    }
}
