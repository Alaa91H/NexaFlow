package com.nexaflow.core.execution.handler

import android.content.Context
import com.nexaflow.core.compat.ExecutionProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.execution.WorkflowRunContext
import com.nexaflow.core.execution.capability.CapabilityExecutionService
import com.nexaflow.core.execution.variables.ScopedDataRuntime
import com.nexaflow.core.rom.SystemController

/** Keeps run-context assembly out of the execution engine's dispatch path. */
internal object ActionExecutionContextFactory {
    fun create(
        appContext: Context,
        controller: SystemController,
        notificationSettings: NotificationSettings,
        channel: ExecutionProvider?,
        automationId: String?,
        executionId: String?,
        nodeId: String,
        revertOnExit: Boolean,
        runContext: WorkflowRunContext?,
        dataRuntime: ScopedDataRuntime?,
        capabilityService: CapabilityExecutionService?,
        triggerEventData: Map<String, String>,
    ): ActionExecutionContext = ActionExecutionContext(
        appContext = appContext,
        controller = controller,
        notificationSettings = notificationSettings,
        channel = channel,
        automationId = automationId,
        executionId = executionId,
        nodeId = nodeId,
        revertOnExit = revertOnExit,
        runContext = runContext,
        dataRuntime = dataRuntime,
        capabilityService = capabilityService,
        triggerEventData = triggerEventData,
    )
}
