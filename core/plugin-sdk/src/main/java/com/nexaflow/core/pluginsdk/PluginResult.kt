package com.nexaflow.core.pluginsdk

/**
 * Outcome of a plugin execution, reported back to the host through the
 * ordered-broadcast result contract.
 */
sealed class PluginResult {

    /** Executed successfully. */
    data object Ok : PluginResult()

    /** Work started; will finish asynchronously (host should not wait). */
    data object Pending : PluginResult()

    /** The user (or the plugin) canceled the operation. */
    data object Canceled : PluginResult()

    /**
     * Execution failed.
     *
     * @param code Tasker-compatible %err code (0..999).
     * @param message Human-readable error, delivered as %errmsg.
     */
    data class Failed(val code: Int, val message: String) : PluginResult()

    /** Maps to the ordered-broadcast [LocaleContract] result code. */
    fun toResultCode(): Int = when (this) {
        is Ok -> LocaleContract.RESULT_CODE_OK
        is Pending -> LocaleContract.RESULT_CODE_PENDING
        is Canceled -> LocaleContract.RESULT_CODE_CANCELED
        is Failed -> LocaleContract.RESULT_CODE_FAILED
    }
}
