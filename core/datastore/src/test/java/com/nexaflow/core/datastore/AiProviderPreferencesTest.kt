package com.nexaflow.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiProviderPreferencesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultsAreDisabledAndLocal() = runTest {
        val preferences = preferences()

        val value = preferences.settings.first()

        assertEquals(false, value.enabled)
        assertTrue(value.local)
        assertEquals("", value.baseUrl)
        assertEquals("", value.modelId)
    }

    @Test
    fun updatePersistsNormalizedFields() = runTest {
        val preferences = preferences()

        preferences.update(
            AiProviderSettings(
                enabled = true,
                displayName = "  Ollama  ",
                baseUrl = "  http://192.168.1.2:11434/v1  ",
                modelId = "  qwen3  ",
                local = true
            )
        )

        assertEquals(
            AiProviderSettings(
                enabled = true,
                displayName = "Ollama",
                baseUrl = "http://192.168.1.2:11434/v1",
                modelId = "qwen3",
                local = true
            ),
            preferences.current()
        )
    }

    private fun preferences(): AiProviderPreferences {
        val file = File(temporaryFolder.root, "ai.preferences_pb")
        val store = PreferenceDataStoreFactory.create { file }
        return AiProviderPreferences(store)
    }

    @Test
    fun routingPolicyPersistsWithProviderSettings() = runTest {
        val preferences = preferences()
        val expected = AiProviderSettings(
            enabled = true,
            displayName = "Cloud model",
            baseUrl = "https://example.com/v1",
            modelId = "model-x",
            local = false,
            routingMode = "SELECTED_PROVIDER",
            selectedProviderId = "openai_compatible",
            allowCloudFallback = true
        )

        preferences.update(expected)

        assertEquals(expected, preferences.current())
    }

    @Test
    fun providerProfilesRoundTripAndRemainMetadataOnly() = runTest {
        val preferences = preferences()
        val profile = AiProviderProfileSettings(
            id = "openai-main",
            presetId = "openai",
            displayName = "OpenAI",
            protocol = "OPENAI_CHAT_COMPLETIONS",
            baseUrl = "https://api.openai.com/v1",
            modelId = "gpt-5.6"
        )

        preferences.upsertProfile(profile)

        assertEquals(listOf(profile), preferences.currentProfiles())
    }

}
