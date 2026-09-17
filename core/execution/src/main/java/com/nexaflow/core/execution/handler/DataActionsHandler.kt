package com.nexaflow.core.execution.handler

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.workflow.DataTransforms

/** Publishes a transform result into the same context consumed by downstream nodes. */
class DataActionsHandler : ActionHandler {
    override val supportedTypes = DataTransforms.operations.keys

    override suspend fun execute(action: Action, ctx: ActionExecutionContext): SystemControlResult {
        val context = ctx.runContext ?: return SystemControlResult.fail("DATA_CONTEXT_REQUIRED")
        val output = action.config["outputPath"].orEmpty().ifBlank { "$.data.result" }
        return try {
            validatePath(output)
            val ignoresInput = action.type == ActionType.DATA_RANDOM ||
                action.type == ActionType.DATA_DATE_TIME && action.config["operation"] == "NOW"
            val input = if (ignoresInput) "" else action.config["inputPath"]?.takeIf { it.isNotBlank() }?.let {
                validatePath(it)
                val value = context.get(it)
                check(value != null || it == "$" || it in context.paths()) { "MISSING_INPUT" }
                DataTransforms.inputText(value)
            } ?: action.config["input"] ?: DataTransforms.defaultInput(action.type)
            context.put(output, DataTransforms.apply(action.type, action.config, input))
            SystemControlResult.ok("DATA_TRANSFORM_COMPLETE")
        } catch (_: IllegalArgumentException) {
            SystemControlResult.fail("INVALID_DATA_CONFIGURATION")
        } catch (_: IllegalStateException) {
            SystemControlResult.fail("DATA_CONTEXT_OR_LIMIT_ERROR")
        } catch (_: ArithmeticException) {
            SystemControlResult.fail("INVALID_ARITHMETIC")
        } catch (_: java.time.DateTimeException) {
            SystemControlResult.fail("INVALID_DATE_TIME")
        } catch (_: ClassCastException) {
            SystemControlResult.fail("INVALID_DATA_TYPE")
        } catch (_: IndexOutOfBoundsException) {
            SystemControlResult.fail("INVALID_DATA_INDEX")
        }
    }

    private fun validatePath(path: String) {
        require(path.length <= 512 && path.matches(CONTEXT_PATH))
    }

    private companion object {
        val CONTEXT_PATH = Regex("""\$(?:\.[A-Za-z_][A-Za-z0-9_-]*|\[(?:0|[1-9][0-9]*)]){0,32}""")
    }
}
