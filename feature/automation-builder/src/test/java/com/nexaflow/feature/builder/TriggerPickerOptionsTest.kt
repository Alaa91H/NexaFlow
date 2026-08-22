package com.nexaflow.feature.builder

import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerPickerOptionsTest {

    @Test
    fun geographicLocationRemainsAddableButLocationModeIsNotOffered() {
        assertTrue(TriggerType.LOCATION in triggerTypeOptions)
        assertFalse(TriggerType.LOCATION_STATE in triggerTypeOptions)
    }
}
