package com.nexaflow.domain.canonical

/** Generated communication mappings, split to keep the legacy table bounded. */
internal object LegacyCommunicationMappingEntries {
    val entries: Map<Pair<LegacyNodeKind, String>, LegacyMappingTable.Entry> = mapOf(
        Pair(LegacyNodeKind.ACTION, "CALL_BLOCK_SILENT") to LegacyMappingTable.Entry(
            observe = false,
            target = TargetId("core.communication.call"),
            operation = OperationId("core.operation.reject"),
            predicate = null,
        ),
        Pair(LegacyNodeKind.ACTION, "CALL_REPLY_WITH_SMS") to LegacyMappingTable.Entry(
            observe = false,
            target = TargetId("core.communication.sms"),
            operation = OperationId("core.operation.send"),
            predicate = null,
        ),
        Pair(LegacyNodeKind.ACTION, "SMS_BLOCK_INCOMING") to LegacyMappingTable.Entry(
            observe = false,
            target = TargetId("core.communication.sms"),
            operation = OperationId("core.operation.set_blocked"),
            predicate = null,
        ),
        Pair(LegacyNodeKind.ACTION, "SMS_REPLY") to LegacyMappingTable.Entry(
            observe = false,
            target = TargetId("core.communication.sms"),
            operation = OperationId("core.operation.send"),
            predicate = null,
        ),
    )
}
