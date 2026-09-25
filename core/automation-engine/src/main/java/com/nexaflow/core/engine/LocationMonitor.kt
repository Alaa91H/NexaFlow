package com.nexaflow.core.engine

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationLifecycleContext
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.TriggerMatchPolicy
import com.nexaflow.core.execution.TriggerOccurrence
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    private val exitCoordinator: ExitCoordinator,
    private val runtimeStore: AutomationRuntimeStore,
    private val activeStore: ActiveTriggerStore,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    @Volatile
    private var initialized = false
    private var listening = false
    /** Last definitive inside/outside state per configured location trigger. */
    private val insideByTrigger = mutableMapOf<LocationTriggerKey, Boolean>()
    private val lastRunAt = mutableMapOf<String, Long>()
    /** Automations currently in their triggered state (to fire exit when leaving). */
    private val activeStates = mutableMapOf<String, Boolean>()
    /** Location trigger indices that own the accepted occurrence lifecycle. */
    private val activeTriggerIndices = mutableMapOf<String, Set<Int>>()
    /** Serializes location transitions so an exit cannot race activation. */
    private val evaluationMutex = Mutex()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocation(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        scope.launch {
            // Re-arm the durable active set BEFORE listening starts: the next
            // location fix reconciles against the restored state, so a task
            // whose ENTER/EXIT condition already ended while the process was
            // down fires its missed exit on the first fix.
            rearmFromLedger()
            repository.getAutomations().collect { automations ->
                val hasLocationTrigger = automations.any { automation ->
                    automation.enabled && automation.triggers.any { it.type == TriggerType.LOCATION }
                }
                updateListening(hasLocationTrigger, automations)
            }
        }
    }

    /**
     * Restores the durable active ids into the in-memory map. Stale keys for
     * deleted/disabled automations are pruned.
     */
    private suspend fun rearmFromLedger() {
        val automations = repository.getAutomations().first().associateBy { it.id }
        val enabledIds = automations.values.filter { it.enabled }.map { it.id }.toSet()
        // The occurrence ledger is authoritative. Restore it before consuming
        // location fixes so a process death cannot lose a real active task.
        runtimeStore.activeStates()
            .filter { it.source == SOURCE }
            .forEach { state ->
                if (state.automationId in enabledIds) {
                    activeStates[state.automationId] = true
                    activeTriggerIndices[state.automationId] =
                        decodeSourceKey(state.sourceKey).ifEmpty {
                            fallbackLocationTriggerIndices(automations[state.automationId])
                        }
                    activeStore.markActive(SOURCE, state.automationId)
                } else {
                    runtimeStore.clear(state.automationId, state.occurrenceId)
                }
            }
        activeStore.activeKeys(SOURCE).forEach { key ->
            val id = key.substringBefore('|')
            if (id in enabledIds) {
                activeStates[id] = true
                if (id !in activeTriggerIndices) {
                    activeTriggerIndices[id] =
                        fallbackLocationTriggerIndices(automations[id])
                }
            } else {
                activeStates.remove(id)
                activeTriggerIndices.remove(id)
                activeStore.clearAutomation(SOURCE, id)
            }
        }
    }

    fun stop() {
        if (!initialized) return
        initialized = false
        try {
            locationManager.removeUpdates(listener)
        } catch (_: Throwable) {
            // ignore
        }
        listening = false
    }

    /**
     * Re-reads the automation set and (re)registers location providers. Used by
     * the periodic location checker after it silently enables location — the
     * providers were off when monitoring first registered, so they must be
     * requested again for fixes to flow.
     */
    fun refresh() {
        scope.launch {
            repository.getAutomations().collect { automations ->
                val hasLocationTrigger = automations.any { automation ->
                    automation.enabled && automation.triggers.any { it.type == TriggerType.LOCATION }
                }
                updateListening(hasLocationTrigger, automations)
            }
        }
    }

    /**
     * Evaluates one freshly-obtained fix against every location-triggered
     * automation (ENTER/EXIT + radius). Same engine as live updates — the
     * periodic checker feeds a single-shot fix here after enabling location.
     */
    fun checkLocation(location: Location) {
        scope.launch { handleLocation(location) }
    }

    private fun updateListening(shouldListen: Boolean, automations: List<Automation>) {
        if (shouldListen && !listening) {
            val fineGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val coarseGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!fineGranted && !coarseGranted) return
            try {
                // PASSIVE provider: fixes computed by OTHER apps (maps,
                // navigation, weather) are delivered here with zero additional
                // battery cost. It is the primary background source; the active
                // provider below only fills the gaps while the screen is on.
                if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.PASSIVE_PROVIDER, 0L, 0f, listener, android.os.Looper.getMainLooper()
                    )
                }
                // Adaptive active polling: interval and distance scale with the
                // smallest configured radius, so a wide "arrive in the city"
                // geofence does not burn GPS fixes every minute.
                val (minTime, minDistance) = adaptiveParams(automations)
                val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val provider = when {
                    networkEnabled && gpsEnabled ->
                        if (minDistance <= 50f) LocationManager.GPS_PROVIDER
                        else LocationManager.NETWORK_PROVIDER
                    networkEnabled -> LocationManager.NETWORK_PROVIDER
                    gpsEnabled -> LocationManager.GPS_PROVIDER
                    else -> null
                }
                if (provider != null) {
                    locationManager.requestLocationUpdates(provider, minTime, minDistance, listener, android.os.Looper.getMainLooper())
                }
                listening = true
            } catch (_: Throwable) {
                listening = false
            }
        } else if (!shouldListen && listening) {
            locationManager.removeUpdates(listener)
            listening = false
        }
    }

    /**
     * Polling budget derived from the smallest ENTER/EXIT radius in use:
     * tight geofences need frequent, accurate fixes; wide ones can wait.
     */
    private fun adaptiveParams(automations: List<Automation>): Pair<Long, Float> {
        val radius = automations
            .asSequence()
            .filter { it.enabled }
            .flatMap { automation ->
                automation.triggers.asSequence()
                    .filter { it.type == TriggerType.LOCATION }
            }
            .mapNotNull { it.config["radius"]?.toFloatOrNull() }
            .minOrNull()
        return when {
            radius == null -> 120_000L to 100f
            radius < 150f -> 60_000L to 50f
            radius < 500f -> 120_000L to 100f
            else -> 300_000L to 250f
        }
    }

    private fun handleLocation(location: Location) {
        scope.launch {
            evaluationMutex.withLock {
                val automations = repository.getAutomations().first()
                val now = System.currentTimeMillis()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.LOCATION } }
                .forEach { automation ->
                    val evaluations = automation.triggers.mapIndexedNotNull { index, trigger ->
                        if (trigger.type != TriggerType.LOCATION) return@mapIndexedNotNull null
                        val key = LocationTriggerKey(automation.id, index)
                        val result = LocationTriggerEvidenceEvaluator.evaluate(
                            trigger = trigger,
                            deviceLatitude = location.latitude,
                            deviceLongitude = location.longitude,
                            previousInside = insideByTrigger[key],
                        ) ?: return@mapIndexedNotNull null
                        IndexedLocationEvaluation(index, result)
                    }
                    if (evaluations.isEmpty()) return@forEach

                    evaluations.forEach { evaluation ->
                        insideByTrigger[
                            LocationTriggerKey(automation.id, evaluation.index)
                        ] = evaluation.result.inside
                    }

                    var lifecycleActive = activeStates[automation.id] == true
                    if (lifecycleActive) {
                        val ownedIndices = activeTriggerIndices[automation.id]
                            .orEmpty()
                            .ifEmpty { fallbackLocationTriggerIndices(automation) }
                        val byIndex = evaluations.associateBy { it.index }
                        val ownedResults = ownedIndices.map { index ->
                            byIndex[index]?.result?.let { evaluation ->
                                if (evaluation.targetStateActive) {
                                    ConditionResult.Satisfied
                                } else {
                                    ConditionResult.Unsatisfied
                                }
                            } ?: ConditionResult.Unknown
                        }

                        val lifecycleDecision = if (ownedResults.isEmpty()) {
                            ConditionResult.Unknown
                        } else {
                            TriggerMatchPolicy.aggregate(
                                mode = automation.triggerMatch,
                                results = ownedResults,
                            )
                        }

                        // Unknown/unreadable state never ends an active
                        // occurrence. Only a logically confirmed false
                        // expression may request the durable exit.
                        if (lifecycleDecision == ConditionResult.Unsatisfied) {
                            when (
                                exitCoordinator.requestExit(
                                    automation,
                                    ExitReason.TRIGGER_FALSE,
                                )
                            ) {
                                is ExitCoordinatorResult.Executed,
                                ExitCoordinatorResult.NotActive,
                                ExitCoordinatorResult.StaleOccurrence -> {
                                    activeStates.remove(automation.id)
                                    activeTriggerIndices.remove(automation.id)
                                    activeStore.clearAutomation(SOURCE, automation.id)
                                    lifecycleActive = false
                                }
                                ExitCoordinatorResult.AlreadyInProgress,
                                is ExitCoordinatorResult.RecoveryRequired -> {
                                    activeStates[automation.id] = true
                                    lifecycleActive = true
                                }
                            }
                        }
                    }

                    val matchedTriggerIndices = evaluations
                        .filter { it.result.eventMatched }
                        .mapTo(linkedSetOf()) { it.index }

                    if (
                        !lifecycleActive &&
                        matchedTriggerIndices.isNotEmpty() &&
                        now - (lastRunAt[automation.id] ?: 0L) > automation.cooldownMillis
                    ) {
                        lastRunAt[automation.id] = now
                        val occurrenceId = "location:${automation.id}:${UUID.randomUUID()}"
                        executionEngine.runAutomation(
                            automation = automation,
                            lifecycleContext = AutomationLifecycleContext(
                                occurrenceId = occurrenceId,
                                source = SOURCE,
                                sourceKey = encodeSourceKey(matchedTriggerIndices),
                            ),
                            triggerOccurrence = TriggerOccurrence(
                                matchedTriggerIndices = matchedTriggerIndices,
                                occurredAtEpochMs = now,
                                sourceId = SOURCE,
                                eventId = occurrenceId,
                            ),
                        )
                        val accepted = runtimeStore.current(automation.id)?.let { state ->
                            state.occurrenceId == occurrenceId && state.source == SOURCE
                        } == true
                        if (accepted) {
                            activeStates[automation.id] = true
                            activeTriggerIndices[automation.id] = matchedTriggerIndices
                            activeStore.markActive(SOURCE, automation.id)
                        } else {
                            // Never claim an active state when durable lifecycle
                            // admission or main execution was not established.
                            lastRunAt.remove(automation.id)
                        }
                    }
                }
            }
        }
    }

    private fun fallbackLocationTriggerIndices(
        automation: Automation?,
    ): Set<Int> {
        if (automation == null) return emptySet()
        val first = automation.triggers.indexOfFirst { it.type == TriggerType.LOCATION }
        return if (first >= 0) setOf(first) else emptySet()
    }

    private fun encodeSourceKey(indices: Set<Int>): String =
        SOURCE_KEY_PREFIX + indices.sorted().joinToString(",")

    private fun decodeSourceKey(sourceKey: String): Set<Int> {
        if (!sourceKey.startsWith(SOURCE_KEY_PREFIX)) return emptySet()
        return sourceKey.removePrefix(SOURCE_KEY_PREFIX)
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .filter { it >= 0 }
            .toSet()
    }

    private data class LocationTriggerKey(
        val automationId: String,
        val triggerIndex: Int,
    )

    private data class IndexedLocationEvaluation(
        val index: Int,
        val result: LocationTriggerEvidenceEvaluator.Result,
    )

    private companion object {
        const val SOURCE = "location"
        const val SOURCE_KEY_PREFIX = "location-indices:"
    }

}
