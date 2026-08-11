package com.nexaflow.feature.widgets

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Automation
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

    /** Relabels the tile with the current task, its icon and its enabled state. */
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
                tile.icon = target?.let { taskIcon(it) }
                tile.state = if (target?.enabled == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.updateTile()
            }
        }
    }

    /**
     * The task's chosen icon rendered as a tile icon. The icon name is mapped
     * through the shared catalog and its vector paths are drawn into a 96x96
     * bitmap — Quick Settings shows it as the tile's icon (the system tints
     * it by state, matching every other tile). Falls back to null (the
     * manifest-provided static icon) on any rendering hiccup, never crashes.
     */
    private fun taskIcon(target: Automation): android.graphics.drawable.Icon? = runCatching {
        val vector = iconVector(target.icon)
        val bitmap = android.graphics.Bitmap.createBitmap(
            96, 96, android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }
        canvas.save()
        canvas.scale(96f / vector.viewportWidth, 96f / vector.viewportHeight)
        renderVectorNode(canvas, paint, vector.root)
        canvas.restore()
        android.graphics.drawable.Icon.createWithBitmap(bitmap)
    }.getOrNull()

    /** Draws a vector node tree (groups with transforms, filled/stroked paths). */
    private fun renderVectorNode(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        node: VectorNode
    ) {
        when (node) {
            is VectorGroup -> {
                canvas.save()
                canvas.translate(node.translationX, node.translationY)
                canvas.translate(node.pivotX, node.pivotY)
                canvas.rotate(node.rotation)
                canvas.scale(node.scaleX, node.scaleY)
                canvas.translate(-node.pivotX, -node.pivotY)
                node.forEach { renderVectorNode(canvas, paint, it) }
                canvas.restore()
            }
            is VectorPath -> {
                val path = toAndroidPath(node.pathData)
                if (node.fill != null) {
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.alpha = (node.fillAlpha * 255).toInt().coerceIn(0, 255)
                    canvas.drawPath(path, paint)
                }
                if (node.stroke != null) {
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = node.strokeLineWidth
                    paint.strokeCap = when (node.strokeLineCap) {
                        StrokeCap.Round -> android.graphics.Paint.Cap.ROUND
                        StrokeCap.Square -> android.graphics.Paint.Cap.SQUARE
                        else -> android.graphics.Paint.Cap.BUTT
                    }
                    paint.strokeJoin = when (node.strokeLineJoin) {
                        StrokeJoin.Round -> android.graphics.Paint.Join.ROUND
                        StrokeJoin.Bevel -> android.graphics.Paint.Join.BEVEL
                        else -> android.graphics.Paint.Join.MITER
                    }
                    paint.strokeMiter = node.strokeLineMiter
                    paint.alpha = (node.strokeAlpha * 255).toInt().coerceIn(0, 255)
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    /** Converts a list of [PathNode]s into an android [android.graphics.Path]. */
    private fun toAndroidPath(nodes: List<PathNode>): android.graphics.Path =
        PathParser()
            .apply { addPathNodes(nodes) }
            .toPath()
            .asAndroidPath()

    private fun entryPoint(): TileEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, TileEntryPoint::class.java)

    companion object {
        /**
         * Mirrors [com.nexaflow.core.execution.AutomationIntents.ACTION_AUTOMATIONS_CHANGED]
         * so the widgets module can refresh home-screen widgets without taking a
         * dependency on the whole execution engine.
         */
        const val ACTION_AUTOMATIONS_CHANGED = "com.nexaflow.core.execution.action.AUTOMATIONS_CHANGED"

        /**
         * All tile components, in slot order. Quick Settings has no strict
         * per-app cap, so the app offers eight slots — far beyond the original
         * four — and each can be pinned to any task.
         */
        fun allComponents(context: Context): List<ComponentName> = listOf(
            ComponentName(context, TaskTile1Service::class.java),
            ComponentName(context, TaskTile2Service::class.java),
            ComponentName(context, TaskTile3Service::class.java),
            ComponentName(context, TaskTile4Service::class.java),
            ComponentName(context, TaskTile5Service::class.java),
            ComponentName(context, TaskTile6Service::class.java),
            ComponentName(context, TaskTile7Service::class.java),
            ComponentName(context, TaskTile8Service::class.java)
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

class TaskTile5Service : TaskTileService() {
    override val slot = 5
}

class TaskTile6Service : TaskTileService() {
    override val slot = 6
}

class TaskTile7Service : TaskTileService() {
    override val slot = 7
}

class TaskTile8Service : TaskTileService() {
    override val slot = 8
}
