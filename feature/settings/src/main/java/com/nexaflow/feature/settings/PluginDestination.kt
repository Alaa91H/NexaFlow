package com.nexaflow.feature.settings

/**
 * Shared navigation contract for the installed-plugin status screen.
 *
 * Both the Settings status row and the app NavHost use this single route so a
 * user tap cannot silently devolve into a refresh-only action.
 */
object PluginDestination {
    const val ROUTE = "plugins"
}
