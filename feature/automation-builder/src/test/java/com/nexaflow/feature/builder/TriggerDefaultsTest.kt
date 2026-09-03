package com.nexaflow.feature.builder

import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TriggerDefaultsTest {
    @Test
    fun locationStateTrigger_defaultsToSimpleOnMode() {
        assertEquals(mapOf("mode" to "ON"), defaultTriggerConfig(TriggerType.LOCATION_STATE))
    }

    @Test
    fun smsTrigger_defaultConfig_containsOnlyMatchingFilters() {
        val config = defaultTriggerConfig(TriggerType.SMS)

        assertEquals(mapOf("from" to "", "contains" to ""), config)
        assertFalse(config.containsKey("reply"))
    }
}
