package com.nexaflow.feature.builder

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
 * Extracts a latitude/longitude pair from arbitrary clipboard text: plain
 * "lat,lng", Google Maps share text, "@lat,lng,zoom", "q=lat,lng" URLs,
 * "!3dlat!4dlng" place links, or "lat;lng". Returns null when nothing
 * coordinate-like is found. This is the paste half of the maps round-trip:
 * copy the place from any maps app, paste it here.
 */
internal fun parseCoordinatesFromText(raw: String): Pair<Double, Double>? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    // All decimal numbers found in order; every link format above keeps
    // lat/lng as the first two coordinate-like decimals once the noise is
    // stripped, so scanning is more robust than one fixed pattern.
    val numbers = Regex("-?\\d+\\.?\\d*").findAll(text)
        .mapNotNull { it.value.toDoubleOrNull() }
        .filter { it.isFinite() }
        .toList()
    // Prefer an explicit "3d/4d" pair (Google place links) when present.
    val threeD = Regex("!3d(-?\\d+\\.?\\d*)!4d(-?\\d+\\.?\\d*)").find(text)
    if (threeD != null) {
        val lat = threeD.groupValues[1].toDoubleOrNull()
        val lng = threeD.groupValues[2].toDoubleOrNull()
        if (lat != null && lng != null && validCoordinate(lat, lng)) return lat to lng
    }
    // A valid pair must be lat (-90..90) followed by lng (-180..180).
    for (i in 0 until numbers.size - 1) {
        val lat = numbers[i]
        val lng = numbers[i + 1]
        if (validCoordinate(lat, lng) && lat != 0.0 || (lat == 0.0 && lng != 0.0)) {
            if (validCoordinate(lat, lng)) return lat to lng
        }
    }
    return null
}

/**
 * Provider-independent fixed-location picker. The installed maps app is used
 * to search/select a place; the coordinates move between the two apps through
 * the clipboard — copy in the maps app, paste here — so no map SDK or
 * coordinate-return contract is required.
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
    // Validation errors are stored as resource ids (arg-less) and resolved at
    // render time: a local helper function cannot call composable APIs.
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var mapsUnavailable by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var pasteFailed by remember { mutableStateOf(false) }

    fun copyCoordinates() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null && validCoordinate(latitude.trim().toDoubleOrNull(), longitude.trim().toDoubleOrNull())) {
            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    "coordinates",
                    String.format(Locale.US, "%s,%s", latitude.trim(), longitude.trim())
                )
            )
            copied = true
            pasteFailed = false
        }
    }

    fun pasteCoordinates() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val raw = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        val parsed = parseCoordinatesFromText(raw)
        if (parsed != null) {
            latitude = String.format(Locale.US, "%.6f", parsed.first)
            longitude = String.format(Locale.US, "%.6f", parsed.second)
            errorRes = null
            pasteFailed = false
            copied = false
        } else {
            pasteFailed = true
            copied = false
        }
    }

    fun confirm() {
        val lat = latitude.trim().toDoubleOrNull()
        val lng = longitude.trim().toDoubleOrNull()
        val radiusValue = radius.trim().toIntOrNull()
        errorRes = when {
            !validCoordinate(lat, lng) -> R.string.map_error_coordinates
            radiusValue == null || !validRadius(radiusValue) -> R.string.map_error_radius
            else -> null
        }
        if (errorRes == null) {
            previous?.savedStateHandle?.set("picked_location", String.format(Locale.US, "%.6f,%.6f", lat, lng))
            previous?.savedStateHandle?.set("picked_radius", radiusValue.toString())
            previous?.savedStateHandle?.set("picked_location_source", "external_maps")
            navController.popBackStack()
        }
    }

    Scaffold(topBar = { NexaFlowTopBar(title = stringResource(R.string.map_picker_title), onBack = { navController.popBackStack() }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.map_selected_location), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.map_picker_hint),
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
            ) { Text(stringResource(R.string.map_open_maps_app)) }
            if (mapsUnavailable) {
                Text(stringResource(R.string.map_no_maps_app), color = MaterialTheme.colorScheme.error)
            }
            // The clipboard round-trip: copy the selected place's coordinates
            // in the maps app, paste them here (or copy them back out again).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { pasteCoordinates() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Filled.ContentPaste, contentDescription = null)
                    Text(stringResource(R.string.map_paste_coordinates), modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = { copyCoordinates() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                    Text(stringResource(R.string.map_copy_coordinates), modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (copied) {
                Text(
                    stringResource(R.string.map_copied_feedback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (pasteFailed) {
                Text(
                    stringResource(R.string.map_paste_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.map_latitude)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.map_longitude)) },
                    singleLine = true
                )
            }
            OutlinedTextField(
                value = radius,
                onValueChange = { input -> radius = input.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.map_radius_meters)) },
                supportingText = { Text(stringResource(R.string.map_radius_range, MAP_MIN_RADIUS_M, MAP_MAX_RADIUS_M)) },
                singleLine = true
            )
            errorRes?.let { res -> Text(stringResource(res), color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::confirm, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.map_save_location))
            }
        }
    }
}
