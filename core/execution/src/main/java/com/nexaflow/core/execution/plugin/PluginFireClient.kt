package com.nexaflow.core.execution.plugin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nexaflow.core.pluginsdk.LocaleContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Outcome of firing a plugin, as reported back through the ordered broadcast.
 */
data class PluginFireResult(
    /** [LocaleContract] result code set by the plugin (or the initial code). */
    val resultCode: Int = LocaleContract.RESULT_CODE_CANCELED,
    /** Optional human-readable message (resultData / %errmsg). */
    val message: String? = null,
    /** True when the plugin never answered within the timeout. */
    val timedOut: Boolean = false,
    /** Bounded, lower-case Tasker output variables returned by the plugin. */
    val outputVariables: Map<String, String> = emptyMap()
) {
    val isSuccess: Boolean get() = !timedOut && resultCode == LocaleContract.RESULT_CODE_OK
}

/**
 * Host-side execution of a Locale plugin action: builds the explicit
 * [LocaleContract.ACTION_FIRE_SETTING] broadcast, sends it as an **ordered**
 * broadcast and awaits the plugin's result code with a timeout, so a hung or
 * missing plugin can never block a task forever.
 */
class PluginFireClient(
    private val timeoutMs: Long = 5_000
) {

    suspend fun fire(
        context: Context,
        packageName: String,
        receiverClass: String,
        bundle: Bundle?
    ): PluginFireResult = withContext(Dispatchers.IO) {
        val intent = Intent(LocaleContract.ACTION_FIRE_SETTING).apply {
            component = ComponentName(packageName, receiverClass)
            putExtra(LocaleContract.EXTRA_BUNDLE, bundle ?: Bundle())
            putExtra(
                LocaleContract.EXTRA_HOST_CAPABILITIES,
                LocaleContract.HOST_CAPABILITY_SETTING_OUTPUT_VARIABLES
            )
            // Wake force-stopped apps and mark the fire as background-initiated,
            // exactly like Tasker/MacroDroid deliver FIRE_SETTING.
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_FROM_BACKGROUND)
        }
        val latch = CountDownLatch(1)
        var code = LocaleContract.RESULT_CODE_CANCELED
        var data: String? = null
        var outputVariables: Map<String, String> = emptyMap()
        try {
            context.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, received: Intent?) {
                        code = resultCode
                        data = resultData
                        outputVariables = boundedOutputVariables(
                            getResultExtras(false)?.getBundle(LocaleContract.EXTRA_VARIABLES_BUNDLE)
                        )
                        latch.countDown()
                    }
                },
                null,
                LocaleContract.RESULT_CODE_CANCELED, // no receiver → canceled
                null,
                null
            )
            val answered = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (answered) PluginFireResult(
                resultCode = code,
                message = data,
                outputVariables = outputVariables
            )
            else PluginFireResult(timedOut = true)
        } catch (e: Exception) {
            PluginFireResult(
                resultCode = LocaleContract.RESULT_CODE_FAILED,
                message = e.message ?: e.javaClass.simpleName
            )
        }
    }

    /**
     * Tasker documents action outputs as lower-case local variables.  Keep the
     * inter-process result bounded before it reaches the workflow context so a
     * hostile or defective plugin cannot exhaust the 256KB run budget.
     */
    private fun boundedOutputVariables(bundle: Bundle?): Map<String, String> {
        if (bundle == null) return emptyMap()
        return bundle.keySet()
            .asSequence()
            .filter { it.matches(TASKER_OUTPUT_VARIABLE_NAME) }
            .sorted()
            .take(MAX_OUTPUT_VARIABLES)
            .mapNotNull { key -> bundle.get(key)?.toString()?.let { key to it.take(MAX_OUTPUT_VALUE_CHARS) } }
            .toMap(LinkedHashMap())
    }

    private companion object {
        val TASKER_OUTPUT_VARIABLE_NAME = Regex("[a-z][a-z0-9_]*")
        const val MAX_OUTPUT_VARIABLES = 32
        const val MAX_OUTPUT_VALUE_CHARS = 4_096
    }
}
