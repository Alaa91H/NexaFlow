package com.nexaflow.feature.builder

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.nexaflow.core.engine.LocationAccess
import com.nexaflow.core.ui.NexaFlowTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val MAP_MIN_RADIUS_M = 50
private const val MAP_MAX_RADIUS_M = 2000
private const val MAP_RADIUS_STEPS = (MAP_MAX_RADIUS_M - MAP_MIN_RADIUS_M) / 50 - 1

/**
 * Embedded Google Maps picker. The map is only a VIEWING/selection surface:
 *
 *  - shows the map with a tap-to-place / draggable marker,
 *  - search box (Geocoder) to jump to a place,
 *  - a radius circle drawn around the point (the activation range),
 *  - Confirm writes "lat,lng" + radius back via savedStateHandle.
 *
 * The actual "did the user arrive" detection stays entirely in the real
 * Location/Geofencing system (core:automation-engine) — Google Maps plays no
 * part in task execution.
 *
 * Requires a Google Maps API key (NEXAFLOW_MAPS_API_KEY gradle property ->
 * com.google.android.geo.API_KEY manifest meta-data). Without a key the
 * screen shows a setup hint instead of a blank map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapPickerScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Initial point + radius passed by the builder ("map_picker_init" = "lat,lng,radius").
    val previous = navController.previousBackStackEntry
    val initValues = remember {
        previous?.savedStateHandle?.get<String>("map_picker_init")
            ?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }
    }
    val hasInitPoint = initValues != null && initValues.size >= 2

    var currentMap by remember { mutableStateOf<GoogleMap?>(null) }
    var mapRequested by remember { mutableStateOf(false) }
    var markerPos by remember {
        mutableStateOf(
            if (hasInitPoint) LatLng(initValues!![0], initValues!![1]) else null
        )
    }
    var radius by remember {
        mutableStateOf(
            (initValues?.getOrNull(2)?.toInt() ?: 100)
                .coerceIn(MAP_MIN_RADIUS_M, MAP_MAX_RADIUS_M)
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var circle by remember { mutableStateOf<Circle?>(null) }
    // Hoisted at composition so coroutines below never read resources lazily.
    val mapSearchNotFoundText = stringResource(R.string.map_search_not_found)
    val locationFixFailedText = stringResource(R.string.location_fix_failed)

    // Google Maps SDK reads the key from the manifest meta-data. Empty key =>
    // show a setup hint instead of initializing a blank map.
    val apiKey = remember {
        runCatching {
            val ai = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            ai.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        }.getOrDefault("")
    }

    // The MapView lives for the whole composition; lifecycle events drive it.
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    fun placePoint(pos: LatLng, moveCamera: Boolean = true) {
        markerPos = pos
        val g = currentMap ?: return
        marker?.remove()
        marker = g.addMarker(
            MarkerOptions()
                .position(pos)
                .title(String.format(Locale.US, "%.6f, %.6f", pos.latitude, pos.longitude))
                .draggable(true)
        )
        circle?.remove()
        circle = g.addCircle(
            CircleOptions()
                .center(pos)
                .radius(radius.toDouble())
                .strokeWidth(2f)
                .strokeColor(0xCC1A73E8.toInt())
                .fillColor(0x331A73E8.toInt())
        )
        if (moveCamera) {
            g.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
        }
    }

    @Suppress("EmptyFunctionBlock")
    fun setupMap(g: GoogleMap) {
        currentMap = g
        g.uiSettings.isZoomControlsEnabled = true
        g.uiSettings.isCompassEnabled = true
        g.uiSettings.isMyLocationButtonEnabled = false
        // Tap anywhere to drop/re-move the point; drag the marker to fine-tune.
        g.setOnMapClickListener { pos -> placePoint(pos) }
        g.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(m: Marker) {}
            override fun onMarkerDrag(m: Marker) {}
            override fun onMarkerDragEnd(m: Marker) {
                markerPos = m.position
                circle?.center = m.position
            }
        })
        if (hasInitPoint) {
            placePoint(LatLng(initValues!![0], initValues!![1]))
        } else {
            // Default view: try the device location, else a broad world view.
            scope.launch {
                val fix = runCatching { LocationAccess.getCurrentLocation(context, 6_000) }.getOrNull()
                if (fix != null) {
                    placePoint(LatLng(fix.latitude, fix.longitude))
                } else {
                    g.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(0.0, 0.0), 2f))
                }
            }
        }
    }

    fun searchPlace(query: String) {
        if (query.isBlank()) return
        scope.launch {
            searchError = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    android.location.Geocoder(context).getFromLocationName(query.trim(), 1)
                        ?.firstOrNull()?.let { it.latitude to it.longitude }
                }.getOrNull()
            }
            if (result != null) {
                placePoint(LatLng(result.first, result.second))
            } else {
                searchError = mapSearchNotFoundText
            }
        }
    }

    fun useCurrentLocation() {
        scope.launch {
            locating = true
            val fix = runCatching { LocationAccess.getCurrentLocation(context, 8_000) }.getOrNull()
            locating = false
            if (fix != null) {
                placePoint(LatLng(fix.latitude, fix.longitude))
            } else {
                searchError = locationFixFailedText
            }
        }
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.pick_on_map),
                onBack = { navController.popBackStack() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (apiKey.isBlank()) {
                // No Google Maps key configured — explain instead of a blank map.
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.map_no_key),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            } else {
                // Search row: find a place or jump to the current location.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.map_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { searchPlace(searchQuery) }) {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    }
                    IconButton(
                        onClick = { useCurrentLocation() },
                        enabled = !locating
                    ) {
                        Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null)
                    }
                }
                // The embedded map.
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 4.dp),
                    update = { view ->
                        if (!mapRequested) {
                            mapRequested = true
                            view.getMapAsync { g -> setupMap(g) }
                        }
                    }
                )
                if (searchError != null) {
                    Text(
                        text = searchError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // Activation radius — mirrors the geofence radius of the task.
                Text(
                    text = stringResource(R.string.radius_meters_format, radius),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = radius.toFloat(),
                    onValueChange = { r ->
                        radius = r.toInt()
                        circle?.radius = radius.toDouble()
                    },
                    valueRange = MAP_MIN_RADIUS_M.toFloat()..MAP_MAX_RADIUS_M.toFloat(),
                    steps = MAP_RADIUS_STEPS
                )
                Text(
                    text = stringResource(R.string.map_pick_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            // Confirm — enabled only once a point exists on the map.
            Button(
                onClick = {
                    val pos = markerPos
                    if (pos != null) {
                        val handle = previous?.savedStateHandle
                        handle?.set(
                            "picked_location",
                            String.format(Locale.US, "%.6f,%.6f", pos.latitude, pos.longitude)
                        )
                        handle?.set("picked_radius", radius.toString())
                        navController.popBackStack()
                    }
                },
                enabled = markerPos != null && apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                Text(
                    text = stringResource(R.string.confirm_location),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
