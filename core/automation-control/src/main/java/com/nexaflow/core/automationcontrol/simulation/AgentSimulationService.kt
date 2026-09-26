package com.nexaflow.core.automationcontrol.simulation

import com.nexaflow.core.automationcontrol.AutomationCommandService
import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewRequestV1
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewResult
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewService
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewV1
import com.nexaflow.core.automationcontrol.schedule.AgentScheduleZonePolicyV1
import com.nexaflow.core.execution.capability.semantic.SemanticWorkflowNodePlan
import com.nexaflow.core.execution.compat.WorkflowCapabilityValidationResult
import kotlinx.serialization.Serializable

@Serializable
data class AgentSimulationScheduleOptionsV1(
    val fromEpochMillis: Long,
    val count: Int = AgentSchedulePreviewRequestV1.DEFAULT_PREVIEW_COUNT,
    val zonePolicy: AgentScheduleZonePolicyV1 = AgentScheduleZonePolicyV1.TASK_CONFIG,
    val fixedZoneId: String? = null
)

@Serializable
data class AgentSimulationRequestV1(
    val task: AgentTaskDraftV1,
    val existingAutomationId: String? = null,
    val schedule: AgentSimulationScheduleOptionsV1? = null
)

@Serializable
data class AgentSimulationIssueV1(
    val code: String,
    val path: String,
    val message: String
)

@Serializable
data class AgentSimulationPrivilegeV1(
    val surface: String,
    val key: String
)

@Serializable
data class AgentSimulationRequirementV1(
    val state: String,
    val admissible: Boolean,
    val missingCapabilities: List<String> = emptyList(),
    val missingPrivileges: List<AgentSimulationPrivilegeV1> = emptyList(),
    val unknownCapabilities: List<String> = emptyList(),
    val unknownPrivileges: List<AgentSimulationPrivilegeV1> = emptyList(),
    val blockedOwners: List<String> = emptyList(),
    val unknownOwners: List<String> = emptyList()
)

@Serializable
data class AgentSimulationStrategyV1(
    val strategy: String,
    val available: Boolean,
    val selected: Boolean,
    val interactive: Boolean,
    val permissionRequired: Boolean,
    val confidence: Int,
    val reason: String,
    val privilegeCost: Int
)

@Serializable
data class AgentSimulationNodeV1(
    val owner: String,
    val actionType: String,
    val operation: String,
    val status: String,
    val selectedStrategy: String? = null,
    val message: String,
    val errorCode: String? = null,
    val candidates: List<AgentSimulationStrategyV1> = emptyList()
)

@Serializable
data class AgentSimulationReportV1(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val valid: Boolean,
    val executable: Boolean,
    /** Explicit contract marker for REST/MCP/A2A consumers. */
    val noSideEffects: Boolean = true,
    val summary: String,
    val issues: List<AgentSimulationIssueV1> = emptyList(),
    val requirement: AgentSimulationRequirementV1? = null,
    val nodes: List<AgentSimulationNodeV1> = emptyList(),
    val pendingUserActionOwners: List<String> = emptyList(),
    val unavailableOwners: List<String> = emptyList(),
    val schedulePreview: AgentSchedulePreviewV1? = null,
    val scheduleIssue: AgentSimulationIssueV1? = null
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * Complete, side-effect-free execution simulation for agent/API callers.
 *
 * It deliberately reuses AutomationCommandService.validateDraft(), which means
 * mapping, typed config validation, dependency validation and WorkflowDryRun
 * capability/semantic planning are identical to a real mutation preflight.
 * No repository write, scheduler registration, action handler, capability
 * backend execute(), root command or Shizuku operation is invoked here.
 */
class AgentSimulationService(
    private val commandService: AutomationCommandService,
    private val schedulePreviewService: AgentSchedulePreviewService
) {

    suspend fun simulate(
        request: AgentSimulationRequestV1
    ): AgentSimulationReportV1 {
        val preflight = commandService.validateDraft(
            draft = request.task,
            existingId = request.existingAutomationId
        )

        val issues = buildList {
            preflight.mappingError?.let { mapping ->
                add(
                    AgentSimulationIssueV1(
                        code = mapping.code,
                        path = mapping.path,
                        message = "Task mapping failed"
                    )
                )
            }
            preflight.validation?.workflowIssues?.forEach { issue ->
                add(
                    AgentSimulationIssueV1(
                        code = issue.code.name,
                        path = issue.location,
                        message = issue.message
                    )
                )
            }
            preflight.validation?.configIssues?.forEach { issue ->
                add(
                    AgentSimulationIssueV1(
                        code = issue.code,
                        path = configPath(issue.owner, issue.key),
                        message = "Node configuration is invalid"
                    )
                )
            }
        }

        val scheduleResult = request.schedule?.let { options ->
            schedulePreviewService.preview(
                AgentSchedulePreviewRequestV1(
                    task = request.task,
                    fromEpochMillis = options.fromEpochMillis,
                    count = options.count,
                    zonePolicy = options.zonePolicy,
                    fixedZoneId = options.fixedZoneId
                )
            )
        }
        val schedulePreview = (scheduleResult as? AgentSchedulePreviewResult.Success)?.preview
        val scheduleIssue = (scheduleResult as? AgentSchedulePreviewResult.Rejected)?.let {
            AgentSimulationIssueV1(
                code = it.code,
                path = it.path,
                message = it.message
            )
        }

        val dryRun = preflight.dryRun
        val semanticPlan = dryRun?.semanticPlan
        val structurallyValid = preflight.isStructurallyValid
        val requestValid = structurallyValid && scheduleIssue == null
        val executable = requestValid && dryRun?.executable == true

        return AgentSimulationReportV1(
            valid = requestValid,
            executable = executable,
            summary = when {
                preflight.mappingError != null -> "Task mapping failed"
                preflight.validation?.isValid == false -> "Task validation failed"
                scheduleIssue != null -> "Schedule preview failed"
                dryRun == null -> "Execution simulation is unavailable"
                else -> dryRun.summary
            },
            issues = issues,
            requirement = dryRun?.requirementValidation?.toAgentModel(),
            nodes = semanticPlan?.nodes.orEmpty().map { it.toAgentModel() },
            pendingUserActionOwners = semanticPlan
                ?.pendingUserActionOwners
                .orEmpty()
                .sorted(),
            unavailableOwners = semanticPlan
                ?.unavailableOwners
                .orEmpty()
                .sorted(),
            schedulePreview = schedulePreview,
            scheduleIssue = scheduleIssue
        )
    }

    private fun WorkflowCapabilityValidationResult.toAgentModel() =
        AgentSimulationRequirementV1(
            state = state.name,
            admissible = admissible,
            missingCapabilities = missingCapabilities.map { it.name }.sorted(),
            missingPrivileges = missingPrivileges
                .map { AgentSimulationPrivilegeV1(it.surface.name, it.key) }
                .sortedWith(compareBy({ it.surface }, { it.key })),
            unknownCapabilities = unknownCapabilities.map { it.name }.sorted(),
            unknownPrivileges = unknownPrivileges
                .map { AgentSimulationPrivilegeV1(it.surface.name, it.key) }
                .sortedWith(compareBy({ it.surface }, { it.key })),
            blockedOwners = blockedOwners.sorted(),
            unknownOwners = unknownOwners.sorted()
        )

    private fun SemanticWorkflowNodePlan.toAgentModel() =
        AgentSimulationNodeV1(
            owner = owner,
            actionType = actionType.name,
            operation = plan.operation.name,
            status = plan.status.name,
            selectedStrategy = plan.selectedStrategy?.name,
            message = plan.message,
            errorCode = plan.errorCode?.name,
            candidates = plan.candidates.map { candidate ->
                AgentSimulationStrategyV1(
                    strategy = candidate.strategy.name,
                    available = candidate.available,
                    selected = candidate.selected,
                    interactive = candidate.interactive,
                    permissionRequired = candidate.permissionRequired,
                    confidence = candidate.confidence,
                    reason = candidate.reason,
                    privilegeCost = candidate.privilegeCost
                )
            }
        )

    private fun configPath(owner: String, key: String): String =
        if (key == "*") owner else "$owner.config.$key"
}
