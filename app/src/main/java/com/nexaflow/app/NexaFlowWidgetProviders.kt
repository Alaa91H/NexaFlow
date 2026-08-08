package com.nexaflow.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.nexaflow.core.database.AutomationDao
import com.nexaflow.core.database.ExecutionDao
import com.nexaflow.core.execution.ACTION_AUTOMATIONS_CHANGED
import com.nexaflow.feature.widgets.TaskTileService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ACTION_TOGGLE_ALL = "com.nexaflow.app.action.TOGGLE_ALL"
private const val ACTION_REFRESH = "com.nexaflow.app.action.REFRESH"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun automationDao(): AutomationDao
    fun executionDao(): ExecutionDao
}

class NexaFlowToggleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.refreshAll(context)
            } finally {
                result.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_ALL) {
            val result = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    toggleAll(context)
                    WidgetUpdater.refreshAll(context)
                } finally {
                    result.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    private suspend fun toggleAll(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val dao = entryPoint.automationDao()
        val automations = dao.getAllAutomations().first()
        val enable = automations.none { it.enabled }
        automations.forEach { automation ->
            if (automation.enabled != enable) {
                dao.updateAutomationStatus(automation.id, enable)
            }
        }
    }
}

class NexaFlowStatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.refreshAll(context)
            } finally {
                result.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH || intent.action == ACTION_AUTOMATIONS_CHANGED) {
            val result = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    WidgetUpdater.refreshAll(context)
                } finally {
                    result.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }
}

object WidgetUpdater {

    suspend fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val automations = entryPoint.automationDao().getAllAutomations().first()
        val latest = entryPoint.executionDao().getLatestExecution()
        val enabled = automations.count { it.enabled }
        val total = automations.size
        val manager = AppWidgetManager.getInstance(appContext)

        val toggleIds = manager.getAppWidgetIds(ComponentName(appContext, NexaFlowToggleWidgetProvider::class.java))
        if (toggleIds.isNotEmpty()) {
            val views = buildToggleViews(appContext, enabled, total)
            toggleIds.forEach { manager.updateAppWidget(it, views) }
        }

        val statusIds = manager.getAppWidgetIds(ComponentName(appContext, NexaFlowStatusWidgetProvider::class.java))
        if (statusIds.isNotEmpty()) {
            val views = buildStatusViews(appContext, enabled, total, latest?.executedAt)
            statusIds.forEach { manager.updateAppWidget(it, views) }
        }

        // Keep the Quick Settings tiles in sync: request a listening state so
        // each tile refreshes its label/state (API 33+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TaskTileService.allComponents(appContext).forEach { component ->
                try {
                    TileService.requestListeningState(appContext, component)
                } catch (_: Throwable) {
                    // Tile may not be added yet; ignore.
                }
            }
        }
    }

    private fun buildToggleViews(context: Context, enabled: Int, total: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_toggle)
        views.setTextViewText(
            R.id.widget_subtitle,
            if (total == 0) {
                context.getString(R.string.widget_no_automations)
            } else {
                context.getString(R.string.widget_active_count, enabled.toString(), total.toString())
            }
        )
        views.setTextViewText(
            R.id.widget_action,
            if (enabled > 0) {
                context.getString(R.string.widget_disable_all)
            } else {
                context.getString(R.string.widget_enable_all)
            }
        )
        val intent = Intent(context, NexaFlowToggleWidgetProvider::class.java)
            .setAction(ACTION_TOGGLE_ALL)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_action, pendingIntent)
        return views
    }

    private fun buildStatusViews(context: Context, enabled: Int, total: Int, lastRunAt: Long?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_status)
        views.setTextViewText(
            R.id.widget_status_count,
            context.getString(R.string.widget_status_count_format, enabled, total)
        )
        views.setTextViewText(
            R.id.widget_status_last,
            if (lastRunAt == null) {
                context.getString(R.string.widget_status_last)
            } else {
                context.getString(R.string.widget_last_run_format, formatTime(lastRunAt))
            }
        )
        return views
    }

    private fun formatTime(millis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }
}
