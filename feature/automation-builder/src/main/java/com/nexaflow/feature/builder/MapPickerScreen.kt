package com.nexaflow.feature.builder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexaflow.core.ui.NexaFlowTopBar
import java.util.Locale

private const val MAP_MIN_RADIUS_M = 50
private const val MAP_MAX_RADIUS_M = 2000

/**
 * Opens the user's installed maps application without coupling NexaFlow to a
 * map SDK, provider, API key, tiles, or offline map database.
 */
internal fun openExternalMaps(context: Context, latitude: Double?, longitude: Double?): Boolean {
    val query = if (latitude != null && longitude != null) {
        "geo:$latitude,$longitude?q=$latitude,$longitude"
    } else {
        "geo:0,0?q="
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(query)).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

internal fun validCoordinate(latitude: Double?, longitude: Double?): Boolean =
    latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0

internal fun validRadius(radius: Int): Boolean = radius in MAP_MIN_RADIUS_M..MAP_MAX_RADIUS_M

/**
 * Provider-independent fixed-location picker. External maps are used only for
 * navigation/search; because Android maps apps do not share a standard
 * coordinate-return contract, manual coordinate entry remains the reliable
 * fallback on every device.
 */
@Composable
fun MapPickerScreen(navController: NavController) {
    val context = LocalContext.current
    val previous = navController.previousBackStackEntry
    val initial = remember {
        previous?.savedStateHandle?.get<String>("map_picker_init")
            ?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }
    }
    var latitude by remember { mutableStateOf(initial?.getOrNull(0)?.let { String.format(Locale.US, "%.6f", it) } ?: "") }
    var longitude by remember { mutableStateOf(initial?.getOrNull(1)?.let { String.format(Locale.US, "%.6f", it) } ?: "") }
    var radius by remember { mutableStateOf((initial?.getOrNull(2)?.toInt() ?: 100).coerceIn(MAP_MIN_RADIUS_M, MAP_MAX_RADIUS_M).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var mapsUnavailable by remember { mutableStateOf(false) }

    fun confirm() {
        val lat = latitude.trim().toDoubleOrNull()
        val lng = longitude.trim().toDoubleOrNull()
        val radiusValue = radius.trim().toIntOrNull()
        error = when {
            !validCoordinate(lat, lng) -> "Enter a valid latitude (-90..90) and longitude (-180..180)."
            radiusValue == null || !validRadius(radiusValue) -> "Radius must be between 50 and 2000 meters."
            else -> null
        }
        if (error == null) {
            previous?.savedStateHandle?.set("picked_location", String.format(Locale.US, "%.6f,%.6f", lat, lng))
            previous?.savedStateHandle?.set("picked_radius", radiusValue.toString())
            previous?.savedStateHandle?.set("picked_location_source", "external_maps")
            navController.popBackStack()
        }
    }

    Scaffold(topBar = { NexaFlowTopBar(title = "Choose location", onBack = { navController.popBackStack() }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Selected location", style = MaterialTheme.typography.titleMedium)
            Text(
                "NexaFlow does not embed a map. Open your installed maps app to search or select a place, then enter the returned coordinates below.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = {
                    mapsUnavailable = !openExternalMaps(
                        context,
                        latitude.toDoubleOrNull(),
                        longitude.toDoubleOrNull()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open installed maps app") }
            if (mapsUnavailable) {
                Text("No compatible maps application is installed. Enter coordinates manually.", color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Latitude") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Longitude") },
                    singleLine = true
                )
            }
            OutlinedTextField(
                value = radius,
                onValueChange = { radius = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Radius (meters)") },
                supportingText = { Text("Allowed range: $MAP_MIN_RADIUS_M–$MAP_MAX_RADIUS_M m") },
                singleLine = true
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::confirm, modifier = Modifier.fillMaxWidth()) { Text("Save location") }
        }
    }
}
