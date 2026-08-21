package com.nexaflow.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginDestinationTest {

    @Test
    fun pluginStatus_usesTheRegisteredPluginManagerRoute() {
        assertEquals("plugins", PluginDestination.ROUTE)
    }
}
