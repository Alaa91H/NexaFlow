package com.nexaflow.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central spacing & dimension tokens (Material 3 4dp grid).
 *
 * Every component must consume these instead of raw `12.dp`/`16.dp` literals
 * so density, tablet padding, and accessibility scaling stay coherent in one
 * place. Named after M3 spec roles (space-1 … space-12) plus component-level
 * dimensions that recur across screens.
 */
object Dimens {

    // --- Spacing scale (4dp grid, M3) ---
    val Space1: Dp = 4.dp
    val Space2: Dp = 8.dp
    val Space3: Dp = 12.dp
    val Space4: Dp = 16.dp
    val Space5: Dp = 20.dp
    val Space6: Dp = 24.dp
    val Space8: Dp = 32.dp
    val Space10: Dp = 40.dp
    val Space12: Dp = 48.dp
    val Space14: Dp = 56.dp

    // --- Component dimensions ---
    /** Horizontal gutter for screen content (16 on compact, 24 on expanded). */
    val ScreenHorizontalPadding: Dp = Space4

    /** Icon badge sizes — 40 on settings rows, 56 on key actions. */
    const val IconBadgeSmall: Int = 40
    const val IconBadgeLarge: Int = 56

    /** Settings row leading-icon circle. */
    const val RowIconSize: Int = 40

    /** Empty-state illustration size. */
    const val EmptyStateIconSize: Int = 56

    /** Bottom navigation bar / top app bar heights. */
    val TopAppBarHeight: Dp = 64.dp
    val NavigationBarHeight: Dp = 80.dp

    /** Navigation rail width (M3 spec). */
    val NavigationRailWidth: Dp = 80.dp

    /** Max content width for large screens (list-detail / centered panes). */
    val MaxContentWidth: Dp = 840.dp

    // --- Ink Glass tokens (24/28 radii, hairline borders, Dock metrics) ---

    /** Content card, bento tile and flow-container radius. */
    val CardRadius: Dp = 24.dp

    /** Grouped list surface (picker, settings, history) radius. */
    val GroupRadius: Dp = 28.dp

    /** Routine icon tile edge (squircle). */
    val RoutineTile: Dp = 48.dp

    /** Routine icon tile corner radius. */
    val RoutineTileRadius: Dp = 16.dp

    /** Picker/settings row icon tile edge. */
    val PickerTile: Dp = 44.dp

    /** Picker/settings row icon tile corner radius. */
    val PickerTileRadius: Dp = 14.dp

    /** Circular icon button visual size (touch target is 48). */
    val IconButton: Dp = 44.dp

    /** Screen header height (icon button + vertical breathing room). */
    val ScreenHeaderHeight: Dp = 44.dp

    /** Search pill height. */
    val SearchHeight: Dp = 52.dp

    /** Category chip height (touch target is 48 via minimumInteractiveComponentSize). */
    val ChipHeight: Dp = 40.dp

    /** Multi-select check circle diameter. */
    val CheckSize: Dp = 28.dp

    /** Dock (glass bar) height, radius, insets and internal metrics. */
    val DockHeight: Dp = 68.dp
    val DockRadius: Dp = 34.dp
    val DockMarginH: Dp = 16.dp
    val DockMarginBottom: Dp = 28.dp
    val DockPadding: Dp = 8.dp
    val DockButton: Dp = 52.dp
    val DockMaxWidth: Dp = 560.dp

    /** Hairline border width. */
    val HairlineBorder: Dp = 1.dp

    /** Ambient glow diameters. */
    val GlowA: Dp = 340.dp
    val GlowB: Dp = 360.dp

    /** Default gap between stacked cards and bento tiles. */
    val CardGap: Dp = Space3

    /** Gap between an icon tile and its text column. */
    val TileTextGap: Dp = 14.dp

    /** Builder node connector: 2dp line, 16dp tall, centred on the tile axis. */
    val ConnectorWidth: Dp = 2.dp
    val ConnectorHeight: Dp = Space4

    /** Corner radius for flow chips. */
    val FlowChipRadius: Dp = 12.dp

    /** Flow label column width in FlowStepRow. */
    val FlowLabelWidth: Dp = 44.dp
}
