package com.nexaflow.domain.canonical

/**
 * Persistence trust boundary for canonical-only nodes.
 *
 * Persisted or agent-supplied nodes cannot establish their own schema. Every
 * newly executable canonical node must be added here and registered with its
 * runtime contract/handler before the editor can save it.
 */
object CanonicalNativeNodeSchemaRegistry {
    const val DELAY_ID = "core.workflow.delay"
    const val BATTERY_THRESHOLD_ID = "android.battery.threshold"

    val delay = NodeSchema(
        schemaId = DELAY_ID,
        kind = NodeSchemaKind.ACTION,
        target = TargetId("core.flow.delay"),
        operation = OperationId("core.operation.wait"),
        title = "Delay",
        fields = listOf(
            NodeSchemaField(
                id = CanonicalFieldId("duration_ms"),
                type = NodeFieldType.DURATION_MS,
                default = NodeFieldDefault(DurationValue(0L)),
                minimum = 0L,
                maximum = 300_000L,
                helpText = "Pause this workflow for up to five minutes.",
            ),
        ),
        summaryTemplate = "Wait {duration_ms} milliseconds",
    )

    val batteryThreshold = NodeSchema(
        schemaId = BATTERY_THRESHOLD_ID,
        kind = NodeSchemaKind.TRIGGER,
        target = TargetId("android.device.battery"),
        predicate = PredicateId(BATTERY_THRESHOLD_ID),
        title = "Battery threshold",
        securityClass = NodeSecurityClass.SENSITIVE,
        fields = listOf(
            NodeSchemaField(CanonicalFieldId("threshold_percent"), NodeFieldType.INTEGER, alwaysRequired = true, minimum = 0, maximum = 100),
            NodeSchemaField(CanonicalFieldId("direction"), NodeFieldType.ENUM_TOKEN, alwaysRequired = true, enumType = "battery.direction", allowedTokens = listOf("ABOVE", "BELOW")),
            NodeSchemaField(CanonicalFieldId("charger_type"), NodeFieldType.ENUM_TOKEN, alwaysRequired = true, enumType = "battery.charger", allowedTokens = listOf("ANY", "AC", "USB", "WIRELESS")),
            NodeSchemaField(CanonicalFieldId("charging_state"), NodeFieldType.ENUM_TOKEN, alwaysRequired = true, enumType = "battery.charging", allowedTokens = listOf("ANY", "CHARGING", "NOT_CHARGING")),
        ),
        summaryTemplate = "Battery {direction} {threshold_percent}%",
    )

    private val schemas = mapOf(
        DELAY_ID to delay,
        BATTERY_THRESHOLD_ID to batteryThreshold,
    )

    fun trustedSchemaFor(definitionId: String): NodeSchema? = schemas[definitionId]

    fun requireTrusted(node: CanonicalWorkflowNode) {
        val trusted = trustedSchemaFor(node.definitionId)
            ?: throw IllegalArgumentException("canonical node has no trusted persistence schema")
        require(node.schema == trusted) { "canonical node schema does not match trusted persistence schema" }
        require(node.endBehavior == null) {
            "canonical node end behavior has no trusted persistence contract"
        }
    }
}
