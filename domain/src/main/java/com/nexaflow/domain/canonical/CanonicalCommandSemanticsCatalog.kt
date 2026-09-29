package com.nexaflow.domain.canonical

/**
 * Single T26 command-semantics registry for every operation emitted by the
 * reviewed 233-entry legacy mapping inventory.
 *
 * Conservative classifications are deliberate: user-visible/external effects
 * are never promoted to blindly retryable merely because a provider happens
 * to be available.
 */
object CanonicalCommandSemanticsCatalog {

    fun all(): List<CommandSemantics> = listOf(
        semantic("batch_write", CommandIdempotency.IDEMPOTENT, true),
        semantic("capture", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("clear", CommandIdempotency.IDEMPOTENT, false),
        semantic("clear_data", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("compose_or_send", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("connect", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("create", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("date_time", CommandIdempotency.IDEMPOTENT, false),
        semantic("dial", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("execute", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("force_stop", CommandIdempotency.IDEMPOTENT, true),
        semantic("forget", CommandIdempotency.IDEMPOTENT, false),
        semantic("generate_random", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("input", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("install", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("invoke", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("invoke_pattern", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("invoke_toggle", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("open", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("open_app_page", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("reboot", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("reject", CommandIdempotency.IDEMPOTENT, false),
        semantic("restart", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("scan", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("schedule", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("search_and_play", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("send", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("set_blocked", CommandIdempotency.IDEMPOTENT, true),
        semantic("set_configuration", CommandIdempotency.IDEMPOTENT, true),
        semantic("set_enabled", CommandIdempotency.IDEMPOTENT, true),
        semantic("set_state", CommandIdempotency.IDEMPOTENT, true),
        semantic("set_value", CommandIdempotency.IDEMPOTENT, true),
        semantic("show", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("shutdown", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("silence", CommandIdempotency.IDEMPOTENT, true),
        semantic("soft_restart", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("swipe", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("tap", CommandIdempotency.NON_IDEMPOTENT, false),
        semantic("transform", CommandIdempotency.IDEMPOTENT, true),
        semantic("uninstall", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("update_apps", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
        semantic("wait", CommandIdempotency.IDEMPOTENT, false),
        semantic("wake", CommandIdempotency.IDEMPOTENT, false),
    ).also { semantics ->
        require(semantics.map { it.operation }.distinct().size == semantics.size) {
            "duplicate canonical command semantics"
        }
    }

    private fun semantic(
        suffix: String,
        idempotency: CommandIdempotency,
        reversible: Boolean,
    ): CommandSemantics = CommandSemantics(
        operation = OperationId("core.operation.$suffix"),
        idempotency = idempotency,
        reversible = reversible,
    )
}
