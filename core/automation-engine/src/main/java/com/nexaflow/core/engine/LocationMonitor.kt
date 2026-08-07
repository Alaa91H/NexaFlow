package com.nexaflow.core.engine

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.models.cooldownMillis
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
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
            repository.getAutomations().collect { automations ->
                val hasLocationTrigger = automations.any { automation ->
                    automation.enabled && automation.triggers.any { it.type == TriggerType.LOCATION }
                }
                updateListening(hasLocationTrigger)
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

    private fun updateListening(shouldListen: Boolean) {
        if (shouldListen && !listening) {
            val fineGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val coarseGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!fineGranted && !coarseGranted) return
            try {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(provider, 60_000L, 100f, listener)
                    }
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

    private fun handleLocation(location: Location) {
        scope.launch {
            val automations = repository.getAutomations().first()
            val now = System.currentTimeMillis()
            automations
                .filter { it.enabled && it.triggers.any { t -> t.type == TriggerType.LOCATION } }
                .forEach { automation ->
                    val trigger = automation.triggers.first { it.type == TriggerType.LOCATION }
                    val lat = trigger.config["lat"]?.toDoubleOrNull() ?: return@forEach
                    val lng = trigger.config["lng"]?.toDoubleOrNull() ?: return@forEach
                    val radius = trigger.config["radius"]?.toFloatOrNull() ?: 100f
                    val event = trigger.config["event"] ?: "ENTER"

                    val distance = FloatArray(1)
                    Location.distanceBetween(lat, lng, location.latitude, location.longitude, distance)
                    val inside = distance[0] <= radius
                    val wasInside = insideByAutomation[automation.id]

                    val shouldRun = when (event) {
                        "ENTER" -> inside && wasInside != true
                        "EXIT" -> !inside && wasInside != false
                        else -> false
                    }
                    if (shouldRun && now - (lastRunAt[automation.id] ?: 0L) > automation.cooldownMillis) {
                        lastRunAt[automation.id] = now
                        activeStates[automation.id] = true
                        executionEngine.runAutomation(automation)
                    }
                    // Exit behavior: fire when the configured state (ENTER=inside, EXIT=outside) ends.
                    val activeShouldEnd = when (event) {
                        "ENTER" -> !inside && activeStates[automation.id] == true
                        "EXIT" -> inside && activeStates[automation.id] == true
                        else -> false
                    }
                    if (activeShouldEnd && now - (lastRunAt[automation.id] ?: 0L) > automation.cooldownMillis) {
                        lastRunAt[automation.id] = now
                        activeStates.remove(automation.id)
                        executionEngine.runExit(automation)
                    }
                    insideByAutomation[automation.id] = inside
                }
        }
    }

}
