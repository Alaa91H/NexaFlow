package com.nexaflow.core.execution.compat

import android.util.Log
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.SystemAppStatusDetector
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.canonical.AtomicCommand
import com.nexaflow.domain.models.Action
import kotlinx.coroutines.CancellationException

/**
 * T39 legacy-provider containment boundary.
 *
 * The product runtime has already canonicalized, validated and planned the
 * action before entering this class. Legacy Action/ActionType is accepted only
 * as the payload required by the historical provider handlers. Handler
 * selection cannot happen without the matching [AtomicCommand].
 */
class CanonicalCompatibilityActionDispatcher(
    private val registry: ActionRegistry = ActionRegistry.default(),
) {
    suspend fun dispatch(
        action: Action,
        canonicalCommand: AtomicCommand,
        executionContext: ActionExecutionContext,
    ): SystemControlResult {
        require(executionContext.nodeId == canonicalCommand.commandId) {
            "compatibility dispatch requires canonical command identity"
        }

        val handler = registry.handlerFor(action.type)
            ?: return SystemControlResult.fail("No compatibility handler registered")

        return try {
            var result = handler.execute(action, executionContext)
            if (!result.success && result.message.contains("No elevated runtime")) {
                // The first attempt never reached an elevated runtime, therefore
                // one post-grant retry is safe. It reuses the SAME canonical
                // command/node identity rather than creating a second command.
                val reProbed = try {
                    SystemAppStatusDetector.refreshAndProbe()
                } catch (_: Throwable) {
                    PrivilegedRunner.isRootAvailable()
                }
                if (reProbed) {
                    result = try {
                        handler.execute(action, executionContext)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Throwable) {
                        SystemControlResult.fail(
                            failure.message ?: "Compatibility action execution failed",
                        )
                    }
                }
                // Never write user config or dynamic error bodies to logcat.
                Log.w("CanonicalCompat", "elevated compatibility provider failed")
            }
            result
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            SystemControlResult.fail(
                failure.message ?: "Compatibility action execution failed",
            )
        }
    }
}
