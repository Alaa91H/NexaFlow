package com.nexaflow.core.engine

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
 * A call occurrence stays active until CALL_STATE_IDLE, including an incoming
 * call that transitions RINGING -> OFFHOOK after it is answered. Durable
 * ownership lives in [AutomationRuntimeStore]; [ActiveTriggerStore] is a
 * compatibility mirror for installs upgraded from the older monitor model.
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

    @Volatile
    private var lastState: Int? = null

    private val evaluationMutex = Mutex()
    private var modernCallback: Any? = null

    @Suppress("DEPRECATION")
    private val legacyListener = object : PhoneStateListener() {
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            this@CallStateMonitor.onCallStateChanged(state)
        }
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
        val previous = lastState
        if (state == previous) return
        lastState = state
        scope.launch {
            reconcileState(
                state = state,
                previousState = previous,
                transitionKnown = previous != null
            )
        }
    }

    fun initialize() {
        if (registered) return
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return
        registered = true
        val registeredNow = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerModernCallback(telephony)
            } else {
                @Suppress("DEPRECATION")
                telephony.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            true
        }.getOrDefault(false)
        if (!registeredNow) {
            registered = false
            return
        }

        // A synchronous call-state read is best effort. UNKNOWN must preserve
        // durable ownership rather than becoming an implicit call end.
        scope.launch {
            val current = readCurrentCallState(telephony)
            if (current != null) {
                val previous = lastState
                lastState = current
                reconcileState(
                    state = current,
                    previousState = previous,
                    transitionKnown = false
                )
            } else {
                rearmFromLedger(repository.getAutomations().first().associateBy { it.id })
            }
        }
    }

    fun reconcileAutomations() {
        scope.launch {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val current = telephony?.let(::readCurrentCallState)
            if (current == null) {
                rearmFromLedger(repository.getAutomations().first().associateBy { it.id })
            } else {
                reconcileState(
                    state = current,
                    previousState = lastState,
                    transitionKnown = false
                )
            }
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (telephony != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    unregisterModernCallback(telephony)
                } else {
                    @Suppress("DEPRECATION")
                    telephony.listen(legacyListener, PhoneStateListener.LISTEN_NONE)
                }
            }
        }
        lastState = null
    }

    /**
     * Deterministic lifecycle boundary used by callbacks and tests.
     *
     * OFFHOOK alone cannot prove an OUTGOING call: an answered incoming call
     * also becomes OFFHOOK. We only create a new OUTGOING occurrence when the
     * transition is known and the previous state was not RINGING. Existing
     * durable call ownership is preserved through RINGING/OFFHOOK and ends only
     * on a known IDLE state.
     */
    internal suspend fun reconcileState(
        state: Int,
        previousState: Int?,
        transitionKnown: Boolean
    ) = evaluationMutex.withLock {
        val automations = repository.getAutomations().first()
        val byId = automations.associateBy { it.id }

        rearmFromLedger(byId)

        val activeCall = state != TelephonyManager.CALL_STATE_IDLE

        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { runtime ->
                val automation = byId[runtime.automationId]
                when {
                    automation == null -> {
                        // No immutable definition means no safe exit action can
                        // be reconstructed. Keep durable evidence visible.
                        clearLegacyState(runtime.automationId)
                    }
                    !automation.enabled ||
                        automation.triggers.none { it.type == TriggerType.CALL_STATE } -> {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.AUTOMATION_DISABLED,
                            occurrenceId = runtime.occurrenceId
                        )
                    }
                    !activeCall -> {
                        requestExit(
                            automation = automation,
                            reason = ExitReason.TRIGGER_FALSE,
                            occurrenceId = runtime.occurrenceId
                        )
                    }
                    else -> markLegacyActive(runtime)
                }
            }

        if (!activeCall) return@withLock

        automations
            .filter {
                it.enabled && it.triggers.any { trigger ->
                    trigger.type == TriggerType.CALL_STATE
                }
            }
            .forEach { automation ->
                val current = runtimeStore.current(automation.id)
                when {
                    current?.source == SOURCE -> {
                        markLegacyActive(current)
                        return@forEach
                    }
                    current != null -> {
                        clearLegacyState(automation.id)
                        return@forEach
                    }
                }

                val desired = automation.triggers
                    .first { it.type == TriggerType.CALL_STATE }
                    .config["event"]
                    ?: "INCOMING"

                val shouldActivate = when (desired) {
                    "INCOMING" -> state == TelephonyManager.CALL_STATE_RINGING
                    "OUTGOING" ->
                        state == TelephonyManager.CALL_STATE_OFFHOOK &&
                            transitionKnown &&
                            previousState != TelephonyManager.CALL_STATE_RINGING
                    else -> false
                }
                if (shouldActivate) activate(automation, desired)
            }
    }

    private suspend fun rearmFromLedger(automations: Map<String, Automation>) {
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                val automation = automations[state.automationId]
                when {
                    automation == null -> clearLegacyState(state.automationId)
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
                val desired = automation.triggers
                    .firstOrNull { it.type == TriggerType.CALL_STATE }
                    ?.config
                    ?.get("event")
                    ?: "INCOMING"
                runtimeStore.activateStrict(
                    AutomationRuntimeState(
                        automationId = automationId,
                        occurrenceId = "legacy:$SOURCE:$automationId:${UUID.randomUUID()}",
                        source = SOURCE,
                        sourceKey = "$automationId|$desired",
                        lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                        activatedAt = System.currentTimeMillis()
                    )
                )
            }

            val current = runtimeStore.current(automationId)
            if (current?.source == SOURCE) {
                markLegacyActive(current)
                if (!automation.enabled ||
                    automation.triggers.none { it.type == TriggerType.CALL_STATE }
                ) {
                    requestExit(
                        automation = automation,
                        reason = ExitReason.AUTOMATION_DISABLED,
                        occurrenceId = current.occurrenceId
                    )
                }
            } else {
                // A stale call marker must never exit a lifecycle owned by
                // another stateful trigger.
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

    @Suppress("DEPRECATION", "MissingPermission")
    private fun readCurrentCallState(telephony: TelephonyManager): Int? =
        runCatching { telephony.callState }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.S)
    private class ModernCallStateCallback(
        private val onStateChanged: (Int) -> Unit
    ) : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) = onStateChanged(state)
    }

    private companion object {
        const val SOURCE = "call"
    }
}
