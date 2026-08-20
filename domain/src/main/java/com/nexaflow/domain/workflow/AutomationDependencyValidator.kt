package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Automation

/**
 * Validates automation-level maintenance dependencies against one explicit
 * catalog. It is intentionally pure: it neither loads data nor schedules or
 * executes work. Callers provide the same catalog they are about to save,
 * import, or inspect.
 */
object AutomationDependencyValidator {

    fun validate(automations: Collection<Automation>): DependencyValidationResult {
        val issues = linkedMapOf<String, MutableList<WorkflowValidationIssue>>()
        fun report(automationId: String, code: WorkflowValidationCode, location: String) {
            issues.getOrPut(automationId) { mutableListOf() } += WorkflowValidationIssue(
                code = code,
                location = location,
                message = code.name.lowercase().replace('_', ' ')
            )
        }

        val grouped = automations.groupBy { it.id }
        grouped.filterValues { it.size > 1 }.forEach { (id, duplicates) ->
            duplicates.forEach { report(id, WorkflowValidationCode.DUPLICATE_AUTOMATION_ID, "id") }
        }
        val catalog = grouped.mapValues { (_, values) -> values.first() }
        val graph = linkedMapOf<String, Set<String>>()
        catalog.forEach { (id, automation) ->
            val dependencies = automation.maintenanceProfile?.dependencyAutomationIds.orEmpty()
            dependencies.forEachIndexed { index, dependencyId ->
                val location = "maintenanceProfile.dependencyAutomationIds[$index]"
                when {
                    dependencyId == id -> report(id, WorkflowValidationCode.SELF_DEPENDENCY, location)
                    dependencyId !in catalog -> report(id, WorkflowValidationCode.MISSING_DEPENDENCY, location)
                }
            }
            graph[id] = dependencies.filter { it in catalog && it != id }.toSet()
        }

        cycleParticipants(graph).forEach { id ->
            report(id, WorkflowValidationCode.CIRCULAR_DEPENDENCY, "maintenanceProfile.dependencyAutomationIds")
        }
        return DependencyValidationResult(issues.mapValues { (_, value) -> value.toList() })
    }

    private fun cycleParticipants(graph: Map<String, Set<String>>): Set<String> {
        val visiting = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        val cycleParticipants = linkedSetOf<String>()

        fun visit(node: String) {
            if (node in visiting) {
                cycleParticipants += visiting.dropWhile { it != node }
                return
            }
            if (!visited.add(node)) return
            visiting += node
            graph[node].orEmpty().forEach(::visit)
            visiting.remove(node)
        }

        graph.keys.forEach(::visit)
        return cycleParticipants
    }
}

data class DependencyValidationResult(
    private val issuesByAutomationId: Map<String, List<WorkflowValidationIssue>>
) {
    fun issuesFor(automationId: String): List<WorkflowValidationIssue> =
        issuesByAutomationId[automationId].orEmpty()

    val isValid: Boolean get() = issuesByAutomationId.values.all { it.isEmpty() }
}
