package com.nexaflow.app.wear

import com.nexaflow.core.execution.WEAR_PATH_RUN_COMMAND
import com.nexaflow.core.execution.WEAR_PATH_TOGGLE_COMMAND
import com.nexaflow.core.execution.WEAR_TOGGLE_SEPARATOR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the toggle payload encoding/decoding contract used by
 * [WearCommandListenerService] and [WearDataLayerClient] without
 * instantiating Android services.
 */
class WearCommandProtocolTest {

    @Test
    fun `toggle payload encodes automationId and true correctly`() {
        val automationId = "abc-123"
        val enabled = true
        val payload = "$automationId${WEAR_TOGGLE_SEPARATOR}$enabled"

        assertEquals("abc-123:true", payload)
    }

    @Test
    fun `toggle payload encodes automationId and false correctly`() {
        val automationId = "xyz-987"
        val enabled = false
        val payload = "$automationId${WEAR_TOGGLE_SEPARATOR}$enabled"

        assertEquals("xyz-987:false", payload)
    }

    @Test
    fun `toggle payload parses automationId and enabled correctly`() {
        val payload = "test-id-456:true"
        val separatorIndex = payload.indexOf(WEAR_TOGGLE_SEPARATOR)

        val automationId = payload.substring(0, separatorIndex)
        val enabledStr = payload.substring(separatorIndex + 1)
        val enabled = enabledStr.toBooleanStrictOrNull()

        assertEquals("test-id-456", automationId)
        assertEquals(true, enabled)
    }

    @Test
    fun `toggle payload parses false enabled correctly`() {
        val payload = "some-id:false"
        val separatorIndex = payload.indexOf(WEAR_TOGGLE_SEPARATOR)

        val automationId = payload.substring(0, separatorIndex)
        val enabled = payload.substring(separatorIndex + 1).toBooleanStrictOrNull()

        assertEquals("some-id", automationId)
        assertFalse(enabled!!)
    }

    @Test
    fun `toggle payload with no separator returns null for enabled`() {
        val payload = "invalid-no-separator"
        val separatorIndex = payload.indexOf(WEAR_TOGGLE_SEPARATOR)

        assertTrue("Expected -1 for missing separator", separatorIndex < 0)
    }

    @Test
    fun `toggle payload with garbage enabled value returns null`() {
        val payload = "my-id:notABoolean"
        val separatorIndex = payload.indexOf(WEAR_TOGGLE_SEPARATOR)
        val enabledStr = payload.substring(separatorIndex + 1)
        val enabled = enabledStr.toBooleanStrictOrNull()

        assertNull("Expected null for invalid boolean", enabled)
    }

    @Test
    fun `run command path is correct`() {
        assertEquals("/nexaflow/run", WEAR_PATH_RUN_COMMAND)
    }

    @Test
    fun `toggle command path is correct`() {
        assertEquals("/nexaflow/toggle", WEAR_PATH_TOGGLE_COMMAND)
    }
}
