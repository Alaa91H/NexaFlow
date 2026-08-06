package com.nexaflow.feature.widgets

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hilt entry point so the system-instantiated tile services can reach the
 * data layer without a ViewModel (tiles are created by the system, not Compose).
 * The concrete repository binding comes from the app's Hilt graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TileEntryPoint {
    fun automationRepository(): AutomationRepository
}

/**
 * Base class for the four quick-settings tiles. Each tile controls one task:
 * the task pinned to its slot (see [TileBindingStore]), otherwise the first
 * enabled task. Tapping the tile toggles that task on/off.
 */
abstract class TaskTileService : TileService() {

    /** 1-based slot this tile represents (1..4). */
    protected abstract val slot: Int

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val repository = entryPoint().automationRepository()
            val automations = repository.getAutomations().first()
            val target = TileTargetResolver.resolveTarget(
                automations = automations,
                slot = slot,
                boundId = TileBindingStore.bindingFor(this@TaskTileService, slot)
            )
            if (target != null) {
                repository.updateAutomationStatus(target.id, !target.enabled)
            }
            withContext(Dispatchers.Main) {
                refreshTile()
            }
            // Keep the home-screen widgets and the other tiles in sync.
            applicationContext.sendBroadcast(
                Intent(ACTION_AUTOMATIONS_CHANGED).setPackage(applicationContext.packageName)
            )
        }
    }

    /** Relabels the tile with the current task and its enabled state. */
    private fun refreshTile() {
        if (qsTile == null) return
        scope.launch {
            val repository = entryPoint().automationRepository()
            val automations = repository.getAutomations().first()
            val target = TileTargetResolver.resolveTarget(
                automations = automations,
                slot = slot,
                boundId = TileBindingStore.bindingFor(this@TaskTileService, slot)
            )
            withContext(Dispatchers.Main) {
                val tile = qsTile ?: return@withContext
                tile.label = target?.name ?: getString(R.string.tile_unbound)
                tile.state = if (target?.enabled == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.updateTile()
            }
        }
    }

    private fun entryPoint(): TileEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, TileEntryPoint::class.java)

    companion object {
        /**
         * Mirrors [com.nexaflow.core.execution.AutomationIntents.ACTION_AUTOMATIONS_CHANGED]
         * so the widgets module can refresh home-screen widgets without taking a
         * dependency on the whole execution engine.
         */
        const val ACTION_AUTOMATIONS_CHANGED = "com.nexaflow.core.execution.action.AUTOMATIONS_CHANGED"

        /** All four tile components, in slot order. */
        fun allComponents(context: Context): List<ComponentName> = listOf(
            ComponentName(context, TaskTile1Service::class.java),
            ComponentName(context, TaskTile2Service::class.java),
            ComponentName(context, TaskTile3Service::class.java),
            ComponentName(context, TaskTile4Service::class.java)
        )
    }
}

class TaskTile1Service : TaskTileService() {
    override val slot = 1
}

class TaskTile2Service : TaskTileService() {
    override val slot = 2
}

class TaskTile3Service : TaskTileService() {
    override val slot = 3
}

class TaskTile4Service : TaskTileService() {
    override val slot = 4
}
