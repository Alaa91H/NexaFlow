package com.nexaflow.core.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stateful CALL_STATE trigger.
 *
 * RINGING is exposed as INCOMING and OFFHOOK as OUTGOING for compatibility with
 * the existing trigger vocabulary. Once either side activates a lifecycle, that
 * occurrence remains active until CALL_STATE_IDLE (ENDED); answering an incoming
 * call must not be interpreted as its end.
 */
@Singleton
class CallStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val activeStore: ActiveTriggerStore,
    private val runtimeStore: AutomationRuntimeStore,
    private val exitCoordinator: ExitCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    private val evaluationMutex = Mutex()

    @Volatile
    private var lastState: Int? = null

    private var modernCallback: Any? = null

    @Suppress("DEPRECATION")
    private val legacyListener = object : PhoneStateListener() {
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) =
            this@CallStateMonitor.onCallStateChanged(state)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernCallback(telephony: TelephonyManager) {
        val callback = ModernCallStateCallback(::onCallStateChanged)
        modernCallback = callback
        telephony.registerTelephonyCallback(context.mainExecutor, callback)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterModernCallback(telephony: TelephonyManager) {
        val callback = modernCallback as? ModernCallStateCallback ?: return
        telephony.unregisterTelephonyCallback(callback)
        modernCallback = null
    }

    private fun onCallStateChanged(state: Int) {
        if (lastState == state) return
        lastState = state
        scope.launch { reconcileState(state) }
    }

    fun initialize() {
        if (registered) return
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return
        registered = true
        scope.launch {
            // Establish durable ownership before callback registration. Some
            // devices deliver the current call state immediately on register.
            rearmFromLedger()
            if (!registered) return@launch

            val callbackRegistered = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    registerModernCallback(telephony)
                } else {
                    @Suppress("DEPRECATION")
                    telephony.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
                }
                true
            }.getOrDefault(false)
            if (!callbackRegistered) {
                registered = false
                return@launch
            }

            // Reconcile the current state so an ENDED transition that occurred
            // while the process was dead cannot leave an earned exit stranded.
            readCurrentCallState(telephony)?.let { state ->
                lastState = state
                reconcileState(state)
            }
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                unregisterModernCallback(telephony)
            } else {
                @Suppress("DEPRECATION")
                telephony.listen(legacyListener, PhoneStateListener.LISTEN_NONE)
            }
        }
        lastState = null
    }

    /** Reconcile enable/disable/edit changes against the current call state. */
    fun reconcileAutomations() {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return
        scope.launch {
            readCurrentCallState(telephony)?.let { reconcileState(it) }
                ?: rearmFromLedger()
        }
    }

    /**
     * Deterministic lifecycle boundary shared by telephony callbacks, startup
     * reconciliation and tests.
     */
    internal suspend fun reconcileState(state: Int) = evaluationMutex.withLock {
        val event = eventForState(state)
        val automations = repository.getAutomations().first()
        val byId = automations.associateBy { it.id }

        rearmFromLedger(byId)

        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { runtime ->
                val automation = byId[runtime.automationId]
                when {
                    automation == null -> clearLegacyState(runtime.automationId)
                    !automation.enabled ||
                        automation.triggers.none { it.type == TriggerType.CALL_STATE } -> {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.AUTOMATION_DISABLED,
                            occurrenceId = runtime.occurrenceId
                        )
                    }
                    event == EVENT_ENDED -> {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.TRIGGER_FALSE,
                            occurrenceId = runtime.occurrenceId
                        )
                    }
                    else -> markLegacyActive(runtime)
                }
            }

        // ENDED is an exit-only signal. RINGING/OFFHOOK may create new
        // occurrences for matching enabled triggers.
        if (event == EVENT_ENDED) return@withLock

        automations
            .filter {
                it.enabled && it.triggers.any { trigger ->
                    trigger.type == TriggerType.CALL_STATE
                }
            }
            .forEach { automation ->
                val wantsEvent = automation.triggers
                    .filter { it.type == TriggerType.CALL_STATE }
                    .any { (it.config["event"] ?: EVENT_INCOMING) == event }
                if (!wantsEvent) return@forEach

                val current = runtimeStore.current(automation.id)
                when {
                    current?.source == SOURCE -> markLegacyActive(current)
                    current != null -> clearLegacyState(automation.id)
                    else -> activate(automation, event)
                }
            }
    }

    private suspend fun rearmFromLedger(
        automations: Map<String, Automation> = repository.getAutomations().first().associateBy { it.id }
    ) {
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                when {
                    automation == null -> {
                        // No immutable definition means no safe end behavior can
                        // be reconstructed. Preserve durable evidence, remove
                        // only the compatibility mirror.
                        clearLegacyState(state.automationId)
                    }
                    !automation.enabled ||
                        automation.triggers.none { it.type == TriggerType.CALL_STATE } -> {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.AUTOMATION_DISABLED,
                            occurrenceId = state.occurrenceId
                        )
                    }
                    else -> markLegacyActive(state)
                }
            }

        activeStore.activeKeys(SOURCE).forEach { key ->
            val automationId = key.substringBefore('|')
            val automation = automations[automationId]
            if (automation == null) {
                clearLegacyState(automationId)
                return@forEach
            }

            if (runtimeStore.current(automationId) == null) {
                val configuredEvent = automation.triggers
                    .firstOrNull { it.type == TriggerType.CALL_STATE }
                    ?.config
                    ?.get("event")
                    ?: EVENT_INCOMING
                runtimeStore.activateStrict(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = key.takeIf { it.contains('|') }
                            ?: "$automationId|$configuredEvent",
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }

            val current = runtimeStore.current(automationId)
            if (current?.source == SOURCE) {
                if (!automation.enabled ||
                    automation.triggers.none { it.type == TriggerType.CALL_STATE }
                ) {
                    requestExit(
                        automation = automation,
                        reason = ExitReason.AUTOMATION_DISABLED,
                        occurrenceId = current.occurrenceId
                    )
                } else {
                    markLegacyActive(current)
                }
            } else {
                // Another stateful source owns this automation. A stale call
                // marker must never authorize that foreign lifecycle's exit.
                clearLegacyState(automationId)
            }
        }
    }

    private suspend fun activate(automation: Automation, event: String) {
        val occurrenceId = "call:${automation.id}:${UUID.randomUUID()}"
        val sourceKey = "${automation.id}|$event"
        executionEngine.runAutomation(
            automation = automation,
            lifecycleContext = AutomationLifecycleContext(
                occurrenceId = occurrenceId,
                source = SOURCE,
                sourceKey = sourceKey
            )
        )
        runtimeStore.current(automation.id)
            ?.takeIf { it.source == SOURCE && it.occurrenceId == occurrenceId }
            ?.let { markLegacyActive(it) }
            ?: clearLegacyState(automation.id)
    }

    private suspend fun requestExit(
        automation: Automation,
        reason: ExitReason,
        occurrenceId: String
    ) {
        when (
            exitCoordinator.requestExit(
                automation = automation,
                reason = reason,
                occurrenceId = occurrenceId
            )
        ) {
            is ExitCoordinatorResult.Executed,
            ExitCoordinatorResult.NotActive,
            ExitCoordinatorResult.StaleOccurrence -> clearLegacyState(automation.id)
            ExitCoordinatorResult.AlreadyInProgress,
            is ExitCoordinatorResult.RecoveryRequired -> {
                runtimeStore.current(automation.id)
                    ?.takeIf { it.source == SOURCE }
                    ?.let { markLegacyActive(it) }
            }
        }
    }

    private suspend fun markLegacyActive(state: AutomationRuntimeState) {
        activeStore.markActive(SOURCE, state.sourceKey)
    }

    private suspend fun clearLegacyState(automationId: String) {
        activeStore.clearAutomation(SOURCE, automationId)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun readCurrentCallState(telephony: TelephonyManager): Int? =
        runCatching { telephony.callState }.getOrNull()

    private fun eventForState(state: Int): String = when (state) {
        TelephonyManager.CALL_STATE_RINGING -> EVENT_INCOMING
        TelephonyManager.CALL_STATE_OFFHOOK -> EVENT_OUTGOING
        else -> EVENT_ENDED
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class ModernCallStateCallback(
        private val onStateChanged: (Int) -> Unit
    ) : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) = onStateChanged(state)
    }

    private companion object {
        const val SOURCE = "call"
        const val EVENT_INCOMING = "INCOMING"
        const val EVENT_OUTGOING = "OUTGOING"
        const val EVENT_ENDED = "ENDED"
    }
}
