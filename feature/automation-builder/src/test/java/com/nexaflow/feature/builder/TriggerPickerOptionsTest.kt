package com.nexaflow.feature.builder

import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerPickerOptionsTest {

    @Test
    fun gpsGeofenceAndLocationModeAreAddable() {
        assertTrue(TriggerType.LOCATION in triggerTypeOptions)
        assertTrue(TriggerType.LOCATION_STATE in triggerTypeOptions)
    }

    @Test
    fun wifiAndMobileDataTriggersAreSeparateOptions() {
        assertTrue(TriggerType.WIFI_CONNECTED in triggerTypeOptions)
        assertTrue(TriggerType.MOBILE_DATA_CONNECTED in triggerTypeOptions)
        assertTrue(TriggerType.HOTSPOT in triggerTypeOptions)
        // The legacy combined network trigger is no longer offered.
        assertFalse(TriggerType.CONNECTIVITY in triggerTypeOptions)
    }

    @Test
    fun pluginEventsRemainRestrictedToVerifiedPluginConfiguration() {
        assertFalse(TriggerType.PLUGIN_EVENT in triggerTypeOptions)
    }
}
