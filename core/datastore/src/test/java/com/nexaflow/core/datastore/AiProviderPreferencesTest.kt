package com.nexaflow.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexaflow.core.airuntime.AiApiDialect
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun providerProfilesRoundTripThroughVersion2State() = runTest {
        val preferences = preferences()
        val profile = AiProviderProfileSettings(
            id = "openai-main",
            presetId = "openai",
            displayName = "OpenAI",
            protocol = "OPENAI_CHAT_COMPLETIONS",
            baseUrl = "https://api.openai.com/v1",
            modelId = "gpt-5.6",
            reasoningEffort = "high"
        )

        preferences.upsertProfile(profile)

        val roundTrip = preferences.currentProfiles().single()
        val state = preferences.currentConnectionsState()
        assertEquals(profile.id, roundTrip.id)
        assertEquals(profile.modelId, roundTrip.modelId)
        assertEquals("openai", roundTrip.providerDefinitionId)
        assertEquals(AiApiDialect.OPENAI_RESPONSES.name, roundTrip.dialect)
        assertEquals(2, state.version)
        assertEquals(1, state.connections.size)
        assertEquals(1, state.models.size)
    }

    @Test
    fun v1MigrationPreservesValidProfilesWhenOneEntryIsCorrupt() = runTest {
        val store = store("v1-corrupt.preferences_pb")
        val key = stringPreferencesKey("profiles_json")
        store.edit { preferences ->
            preferences[key] = """
                {
                  "version":1,
                  "profiles":[
                    {
                      "id":"good",
                      "presetId":"openai",
                      "displayName":"Good",
                      "protocol":"OPENAI_CHAT_COMPLETIONS",
                      "baseUrl":"https://api.openai.com/v1",
                      "modelId":"gpt-5.6",
                      "local":false,
                      "enabled":true,
                      "reasoningEffort":"medium"
                    },
                    {
                      "id":[],
                      "displayName":"Broken",
                      "protocol":"OPENAI_CHAT_COMPLETIONS",
                      "baseUrl":"https://example.com/v1"
                    }
                  ]
                }
            """.trimIndent()
        }
        val preferences = AiProviderPreferences(store)

        val profiles = preferences.currentProfiles()
        assertEquals(listOf("good"), profiles.map(AiProviderProfileSettings::id))

        val state = preferences.migrateConnectionsStateIfNeeded()
        val persisted = store.data.first()[key].orEmpty()
        assertEquals(listOf("good"), state.connections.map(AiConnectionSettings::id))
        assertTrue(persisted.contains("\"version\":2"))
        assertTrue(persisted.contains("\"connections\""))
        assertFalse(persisted.contains("Broken"))
    }

    @Test
    fun version2DecoderDropsOnlyMalformedConnection() = runTest {
        val store = store("v2-corrupt.preferences_pb")
        val key = stringPreferencesKey("profiles_json")
        store.edit { preferences ->
            preferences[key] = """
                {
                  "version":2,
                  "connections":[
                    {
                      "id":"good",
                      "presetId":"claude",
                      "providerDefinitionId":"anthropic",
                      "providerKind":"ANTHROPIC",
                      "displayName":"Claude",
                      "dialect":"ANTHROPIC_MESSAGES",
                      "baseUrl":"https://api.anthropic.com/v1",
                      "authScheme":"X_API_KEY",
                      "credentialRef":"ai.provider.profile.good.api_key",
                      "local":false,
                      "enabled":true
                    },
                    {
                      "id":[],
                      "providerDefinitionId":"custom",
                      "providerKind":"CUSTOM",
                      "displayName":"Broken",
                      "baseUrl":"https://example.com/v1",
                      "authScheme":"BEARER_TOKEN"
                    }
                  ],
                  "models":[
                    {
                      "connectionId":"good",
                      "modelId":"claude-test",
                      "reasoningEffort":"high"
                    }
                  ]
                }
            """.trimIndent()
        }
        val preferences = AiProviderPreferences(store)

        val profiles = preferences.currentProfiles()

        assertEquals(1, profiles.size)
        assertEquals("good", profiles.single().id)
        assertEquals("claude-test", profiles.single().modelId)
        assertEquals("ai.provider.profile.good.api_key", profiles.single().credentialRef)
    }

    private fun preferences(): AiProviderPreferences =
        AiProviderPreferences(store("ai.preferences_pb"))

    private fun store(name: String) = PreferenceDataStoreFactory.create {
        File(temporaryFolder.root, name)
    }
}
