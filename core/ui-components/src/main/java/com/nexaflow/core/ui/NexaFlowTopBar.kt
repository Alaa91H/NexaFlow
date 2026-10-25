@file:OptIn(ExperimentalMaterial3Api::class)

package com.nexaflow.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow

/**
 * Stable holder for the Material 3 top-app-bar scroll behavior (the M3 API
 * itself is experimental). Lets screens wire the scroll tint without opting
 * in to experimental APIs at every call site.
 *
 * Wire [nestedScrollConnection] to your Scaffold via
 * `Modifier.nestedScroll(behavior.nestedScrollConnection)` so scroll events
 * reach the bar.
 */
class TopBarScrollBehavior internal constructor(
    val nestedScrollConnection: NestedScrollConnection,
    internal val behavior: TopAppBarScrollBehavior
)

/**
 * Pinned top bar that tints to the scrolled container color (surface
 * container) as content scrolls under it.
 */
@Composable
fun rememberPinnedTopBarScrollBehavior(): TopBarScrollBehavior {
    val behavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    return remember(behavior) {
        TopBarScrollBehavior(behavior.nestedScrollConnection, behavior)
    }
}

/**
 * The bar a screen shows when it was pushed onto the stack.
 *
 * The bar is transparent by default: the ambient canvas draws behind it, so
 * the screen reads as one continuous surface rather than as a header band
 * sitting on top of a different one. The title carries the weight — a
 * bold headline rather than a title — so a screen's name is legible at a
 * glance from a distance.
 */
@Composable
fun NexaFlowTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    subtitle: String? = null,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    scrollBehavior: TopBarScrollBehavior? = null
) {
    TopAppBar(
        title = {
            if (subtitle == null) {
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Column {
                    Text(
                        text = title,
                        color = contentColor,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = contentColor
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior?.behavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
            actionIconContentColor = contentColor
        )
    )
}

@Preview(name = "NexaFlowTopBar", showBackground = true)
@Preview(name = "NexaFlowTopBar (dark)", showBackground = true)
@Composable
private fun NexaFlowTopBarPreview() {
    MaterialTheme {
        NexaFlowTopBar(
            title = "Task details",
            onBack = {}
        )
    }
}
