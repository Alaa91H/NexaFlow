package com.nexaflow.core.execution.handler

import com.nexaflow.core.execution.plugin.PluginFireClient
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginConfigParser
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType

/**
 * Executes an external plugin action ([ActionType.PLUGIN_FIRE]) over the
 * Locale protocol: an explicit ordered FIRE_SETTING broadcast to the plugin's
 * receiver with the saved config bundle, awaiting the plugin's result code.
 */
class PluginFireHandler : ActionHandler {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.PLUGIN_FIRE)

    private val client = PluginFireClient()

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val packageName = action.config["package"].orEmpty()
        val receiverClass = action.config["receiver"].orEmpty()
        if (packageName.isBlank() || receiverClass.isBlank()) {
            return SystemControlResult.fail("Plugin not configured")
        }
        // The JSON config is opaque to %variable resolution (see ExecutionEngine
        // OPAQUE keys); rebuild the protocol bundle exactly as saved. A corrupt
        // or oversized saved config must fail loudly, never fire an empty
        // bundle silently.
        val configMap = runCatching {
            PluginConfigParser.parseJson(action.config["bundleJson"].orEmpty())
        }.getOrElse {
            return SystemControlResult.fail("Plugin config is invalid")
        }
        val bundle = runCatching {
            PluginConfigParser.toBundle(configMap)
        }.getOrElse {
            return SystemControlResult.fail("Plugin config exceeds the size limit")
        }
        val result = client.fire(ctx.appContext, packageName, receiverClass, bundle)
        return when {
            result.timedOut ->
                SystemControlResult.fail("Plugin did not respond (timeout)")
            result.resultCode == LocaleContract.RESULT_CODE_OK ->
                SystemControlResult.ok("Plugin executed")
            result.resultCode == LocaleContract.RESULT_CODE_PENDING ->
                SystemControlResult.ok("Plugin started (pending)")
            result.resultCode == LocaleContract.RESULT_CODE_FAILED ->
                SystemControlResult.fail(result.message ?: "Plugin failed")
            else ->
                SystemControlResult.fail(result.message ?: "Plugin canceled or missing")
        }
    }
}
