package com.nexaflow.data.agents

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.agentruntime.AgentDefinition
import com.nexaflow.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The builder picker must never offer a disabled agent: the runtime refuses it
 * as `agent_unavailable`, so an offered-but-unusable choice would only be
 * discovered after the user saved and ran the task.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryAgentChoiceSourceTest {

    private lateinit var database: AppDatabase
    private lateinit var definitions: AgentDefinitionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        definitions = AgentDefinitionRepository(database.agentDefinitionDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun projectsOnlyEnabledAgentsWithTheDetailThePickerShows() = runBlocking {
        definitions.create(
            AgentDefinition(
                id = "agent-on",
                name = "Enabled agent",
                description = "Reviews builds",
                modelId = "model-a",
                enabled = true
            )
        )
        definitions.create(
            AgentDefinition(
                id = "agent-off",
                name = "Disabled agent",
                enabled = false
            )
        )

        val agents = RepositoryAgentChoiceSource(definitions).observeEnabled().first()

        assertEquals(listOf("agent-on"), agents.map { it.id })
        assertEquals("Enabled agent", agents.single().name)
        assertEquals("Reviews builds", agents.single().description)
        assertEquals("model-a", agents.single().modelId)
    }
}
