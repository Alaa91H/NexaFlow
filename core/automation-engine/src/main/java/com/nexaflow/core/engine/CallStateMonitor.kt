package com.nexaflow.core.engine

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Standalone CALL_STATE trigger: fires a task when a call goes INCOMING or
 * OUTGOING (per the configured `event`), once per transition, and runs the
 * task's exit behavior when the call ENDS.
 */
@Singleton
class CallStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    /** Automations currently in their triggered state (to fire exit on ENDED). */
    private val activeStates = mutableMapOf<String, String>()

    private var lastState = TelephonyManager.CALL_STATE_IDLE

    private val listener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            if (state == lastState) return
            lastState = state
            handleState(state)
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        runCatching {
            telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        runCatching {
            telephony.listen(listener, PhoneStateListener.LISTEN_NONE)
        }
    }

    private fun handleState(state: Int) {
        val event = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> "INCOMING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OUTGOING"
            else -> "ENDED"
        }
        scope.launch {
            val automations = repository.getAutomations().first()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.CALL_STATE } }
                .forEach { automation ->
                    val want = automation.triggers.first { it.type == TriggerType.CALL_STATE }
                        .config["event"] ?: "INCOMING"
                    if (event == want) {
                        // Fire once per transition into the triggered state.
                        if (activeStates.put(automation.id, event) == null) {
                            activeStore.markActive(SOURCE, automation.id)
                            executionEngine.runAutomation(automation)
                        }
                    } else if (event == "ENDED" && activeStates.remove(automation.id) != null) {
                        activeStore.clearAutomation(SOURCE, automation.id)
                        executionEngine.runExit(automation)
                    }
                }
        }
    }

    private companion object {
        const val SOURCE = "call"
    }
}
