package com.nexaflow.core.execution.plugin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.domain.models.ConditionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Result of one bounded Locale condition query. */
data class PluginConditionQueryResult(
    val condition: ConditionResult,
    val resultCode: Int? = null,
    val timedOut: Boolean = false
)

/**
 * Host-side implementation of Locale's `ACTION_QUERY_CONDITION` protocol.
 *
 * The query is always explicit and ordered. The adapter returns [ConditionResult]
 * rather than Boolean so an absent response, timeout, or protocol state cannot be
 * silently interpreted as an unsatisfied condition.
 */
class PluginConditionClient(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    init {
        require(timeoutMs in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            "timeoutMs must be in $MIN_TIMEOUT_MS..$MAX_TIMEOUT_MS"
        }
    }

    suspend fun query(
        context: Context,
        packageName: String,
        receiverClass: String,
        bundle: Bundle
    ): PluginConditionQueryResult = withContext(Dispatchers.IO) {
        val intent = Intent(LocaleContract.ACTION_QUERY_CONDITION).apply {
            component = ComponentName(packageName, receiverClass)
            putExtra(LocaleContract.EXTRA_BUNDLE, bundle)
            // Locale requires stopped package inclusion; the query is a
            // background-priority automatic host operation, never foreground IPC.
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_FROM_BACKGROUND)
        }
        val latch = CountDownLatch(1)
        var code = LocaleContract.RESULT_CONDITION_UNKNOWN
        var completed = false
        try {
            context.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, received: Intent?) {
                        code = resultCode
                        completed = true
                        latch.countDown()
                    }
                },
                null,
                LocaleContract.RESULT_CONDITION_UNKNOWN,
                null,
                null
            )
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return@withContext PluginConditionQueryResult(
                    condition = ConditionResult.Unknown,
                    timedOut = true
                )
            }
            PluginConditionQueryResult(
                condition = mapResultCode(code),
                resultCode = code
            )
        } catch (_: SecurityException) {
            PluginConditionQueryResult(condition = ConditionResult.Unavailable)
        } catch (failure: Exception) {
            PluginConditionQueryResult(
                condition = ConditionResult.Error(
                    failure.message?.takeIf { it.isNotBlank() } ?: "Condition query failed"
                )
            )
        }
    }

    private fun mapResultCode(resultCode: Int): ConditionResult = when (resultCode) {
        LocaleContract.RESULT_CONDITION_SATISFIED -> ConditionResult.Satisfied
        LocaleContract.RESULT_CONDITION_UNSATISFIED -> ConditionResult.Unsatisfied
        LocaleContract.RESULT_CONDITION_UNKNOWN -> ConditionResult.Unknown
        else -> ConditionResult.Error("Plugin returned unsupported condition result code: $resultCode")
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        const val MIN_TIMEOUT_MS = 1_000L
        const val MAX_TIMEOUT_MS = 300_000L
    }
}
