package com.nexaflow.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AgentNetworkPreferencesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultsToLoopbackOnly() = runTest {
        val preferences = preferences()

        assertFalse(preferences.settings.first().lanAccessEnabled)
        assertFalse(preferences.current().lanAccessEnabled)
    }

    @Test
    fun explicitLanChoicePersists() = runTest {
        val preferences = preferences()

        preferences.setLanAccessEnabled(true)
        assertTrue(preferences.current().lanAccessEnabled)

        preferences.setLanAccessEnabled(false)
        assertFalse(preferences.current().lanAccessEnabled)
    }

    private fun preferences(): AgentNetworkPreferences {
        val file = File(temporaryFolder.root, "agent-network.preferences_pb")
        val store = PreferenceDataStoreFactory.create { file }
        return AgentNetworkPreferences(store)
    }
}
