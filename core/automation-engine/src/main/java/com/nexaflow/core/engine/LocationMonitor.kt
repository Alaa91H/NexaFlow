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
import com.nexaflow.domain.models.Automation
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
    private val insideByAutomation = mutableMapOf<String, Boolean>()
    private val lastRunAt = mutableMapOf<String, Long>()
    /** Automations currently in their triggered state (to fire exit when leaving). */
    private val activeStates = mutableMapOf<String, Boolean>()
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
                    activeStore.markActive(SOURCE, state.automationId)
                } else {
                    runtimeStore.clear(state.automationId, state.occurrenceId)
                }
            }
        activeStore.activeKeys(SOURCE).forEach { key ->
            val id = key.substringBefore('|')
            if (id in enabledIds) {
                activeStates[id] = true
            } else {
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
                        LocationManager.PASSIVE_PROVIDER, 0L, 0f, listener
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
                    locationManager.requestLocationUpdates(provider, minTime, minDistance, listener)
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
            .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.LOCATION } }
            .mapNotNull {
                it.triggers.first { t -> t.type == TriggerType.LOCATION }.config["radius"]?.toFloatOrNull()
            }
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
                    val trigger = automation.triggers.first { it.type == TriggerType.LOCATION }
                    val lat = trigger.config["lat"]?.toDoubleOrNull() ?: return@forEach
                    val lng = trigger.config["lng"]?.toDoubleOrNull() ?: return@forEach
                    val radius = trigger.config["radius"]?.toDoubleOrNull() ?: return@forEach
                    val event = trigger.config["event"] ?: "ENTER"
                    val source = trigger.config["source"] ?: "current"
                    if (!FixedLocationEvaluator.isValidCoordinate(lat, lng) ||
                        !FixedLocationEvaluator.isValidRadius(radius) ||
                        !FixedLocationEvaluator.isValidCoordinate(location.latitude, location.longitude) ||
                        event !in setOf("ENTER", "EXIT")
                    ) return@forEach

                    val distance = FloatArray(1)
                    Location.distanceBetween(lat, lng, location.latitude, location.longitude, distance)
                    val inside = distance[0].toDouble() <= radius
                    val wasInside = insideByAutomation[automation.id]

                    // Selected fixed locations deliberately initialise their state
                    // without emitting an event after process/device restart.
                    // The legacy current-location flow retains its established
                    // first-fix behavior.
                    val shouldRun = if (source == "selected") {
                        val previous = wasInside?.let {
                            if (it) FixedLocationEvaluator.TransitionState.INSIDE
                            else FixedLocationEvaluator.TransitionState.OUTSIDE
                        } ?: FixedLocationEvaluator.TransitionState.UNKNOWN
                        val requested = if (event == "ENTER") FixedLocationEvaluator.EventType.ENTER
                        else FixedLocationEvaluator.EventType.EXIT
                        FixedLocationEvaluator.transition(previous, inside, requested) != null
                    } else {
                        when (event) {
                            "ENTER" -> inside && wasInside != true
                            "EXIT" -> !inside && wasInside != false
                            else -> false
                        }
                    }
                    if (shouldRun && now - (lastRunAt[automation.id] ?: 0L) > automation.cooldownMillis) {
                        lastRunAt[automation.id] = now
                        val occurrenceId = "location:${automation.id}:${UUID.randomUUID()}"
                        executionEngine.runAutomation(
                            automation = automation,
                            lifecycleContext = AutomationLifecycleContext(
                                occurrenceId = occurrenceId,
                                source = SOURCE,
                                sourceKey = automation.id
                            )
                        )
                        val accepted = runtimeStore.current(automation.id)?.let { state ->
                            state.occurrenceId == occurrenceId && state.source == SOURCE
                        } == true
                        if (accepted) {
                            activeStates[automation.id] = true
                            activeStore.markActive(SOURCE, automation.id)
                        } else {
                            // Never claim an active state when durable lifecycle
                            // admission or main execution was not established.
                            lastRunAt.remove(automation.id)
                        }
                    }
                    // Exit behavior: fire when the configured state (ENTER=inside, EXIT=outside) ends.
                    val activeShouldEnd = when (event) {
                        "ENTER" -> !inside && activeStates[automation.id] == true
                        "EXIT" -> inside && activeStates[automation.id] == true
                        else -> false
                    }
                    // Cooldown guards repeated entries only. Once a task is
                    // active, its configured end behavior must run immediately
                    // when the location condition ends, even during cooldown.
                    if (activeShouldEnd) {
                        when (exitCoordinator.requestExit(automation, ExitReason.TRIGGER_FALSE)) {
                            is ExitCoordinatorResult.Executed,
                            ExitCoordinatorResult.NotActive,
                            ExitCoordinatorResult.StaleOccurrence -> {
                                activeStates.remove(automation.id)
                                activeStore.clearAutomation(SOURCE, automation.id)
                            }
                            ExitCoordinatorResult.AlreadyInProgress,
                            is ExitCoordinatorResult.RecoveryRequired -> {
                                // Retain the active marker until the durable
                                // coordinator confirms a successful end.
                                activeStates[automation.id] = true
                            }
                        }
                    }
                    insideByAutomation[automation.id] = inside
                }
            }
        }
    }

    private companion object {
        const val SOURCE = "location"
    }

}
