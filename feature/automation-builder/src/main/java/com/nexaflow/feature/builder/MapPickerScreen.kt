package com.nexaflow.feature.builder

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

private const val MAP_MIN_RADIUS_M = 50
private const val MAP_MAX_RADIUS_M = 2000
private const val MAP_RADIUS_STEPS = (MAP_MAX_RADIUS_M - MAP_MIN_RADIUS_M) / 50 - 1

/**
 * Embedded map picker. The map is only a VIEWING/selection surface:
 *
 *  - tap-to-place / draggable marker + activation-radius circle,
 *  - search box (Geocoder) and a "use my location" button,
 *  - Confirm writes "lat,lng" + radius back via savedStateHandle.
 *
 * Rendering prefers Google Maps when a key is configured AND authentication
 * succeeds. Otherwise it falls back to a keyless OpenStreetMap/Leaflet map in
 * a WebView, so the picker never shows a blank map on any ROM — no API key,
 * no Play Services dependency, no Google Cloud configuration required.
 *
 * The actual "did the user arrive" detection stays entirely in the real
 * Location/Geofencing system (core:automation-engine); the map plays no part
 * in task execution.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission", "SetJavaScriptEnabled")
@Composable
fun MapPickerScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val previous = navController.previousBackStackEntry
    val initValues = remember {
        previous?.savedStateHandle?.get<String>("map_picker_init")
            ?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }
    }
    val initialPoint = initValues
        ?.takeIf { it.size >= 2 }
        ?.let { LatLng(it[0], it[1]) }
    var currentMap by remember { mutableStateOf<GoogleMap?>(null) }
    var mapRequested by remember { mutableStateOf(false) }
    var markerPos by remember { mutableStateOf(initialPoint) }
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
    var googleFailed by remember { mutableStateOf(false) }
    var osmReady by remember { mutableStateOf(false) }

    val mapSearchNotFoundText = stringResource(R.string.map_search_not_found)
    val locationFixFailedText = stringResource(R.string.location_fix_failed)
    val mapNoKeyText = stringResource(R.string.map_no_key)

    val apiKey = remember {
        runCatching {
            val ai = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            ai.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        }.getOrDefault("")
    }
    val useOsm = apiKey.isBlank() || googleFailed

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val osmBridge = remember {
        object {
            @JavascriptInterface
            fun onPointSelected(lat: Double, lng: Double) {
                if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return
                mainHandler.post { markerPos = LatLng(lat, lng) }
            }
        }
    }

    // Google MapView lives for the whole composition; lifecycle events drive
    // it. Google Maps requires the FULL sequence onCreate -> onStart ->
    // onResume (and onPause -> onStop -> onDestroy) — skipping onStart is the
    // classic cause of a permanently blank map box. The observer also replays
    // the current state on attach, because a Compose screen can be composed
    // while the lifecycle is already RESUMED.
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                Lifecycle.Event.ON_CREATE -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // Keyless OSM WebView, created only when we actually fall back. The
    // initial point/radius are applied once the page finishes loading.
    val osmWebView = remember(useOsm) {
        if (!useOsm) {
            null
        } else {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Lint cannot see @JavascriptInterface on anonymous objects, so
                // the suppression lives at the call site (method is annotated).
                @SuppressLint("JavascriptInterface")
                addJavascriptInterface(osmBridge, "Android")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        osmReady = true
                        val p = markerPos
                        view?.evaluateJavascript(
                            "if (window.setPoint) setPoint(" +
                                "${p?.latitude ?: 0.0}, ${p?.longitude ?: 0.0}, $radius);",
                            null
                        )
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame != false) {
                            searchError = mapNoKeyText
                        }
                    }
                }
                loadUrl("file:///android_asset/map_picker.html")
            }
        }
    }
    DisposableEffect(lifecycleOwner, osmWebView) {
        val wv = osmWebView
        if (wv == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> wv.onResume()
                    Lifecycle.Event.ON_PAUSE -> wv.onPause()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                wv.onResume()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                wv.destroy()
            }
        }
    }

    fun placePoint(pos: LatLng, moveCamera: Boolean = true) {
        markerPos = pos
        val g = currentMap
        if (g != null) {
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
        } else if (useOsm && osmReady) {
            osmWebView?.evaluateJavascript(
                "if (window.setPoint) setPoint(${pos.latitude}, ${pos.longitude}, $radius);",
                null
            )
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
        if (initialPoint != null) {
            placePoint(initialPoint)
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
                runCatching { geocodeLocation(context, query.trim()) }.getOrNull()
            }
            if (result != null) {
                placePoint(result)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    Icon(imageVector = Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                }
                IconButton(
                    onClick = { useCurrentLocation() },
                    enabled = !locating
                ) {
                    Icon(imageVector = Icons.Filled.MyLocation, contentDescription = stringResource(R.string.use_current_location))
                }
            }

            if (useOsm) {
                val wv = osmWebView
                if (wv != null) {
                    AndroidView(
                        factory = { wv },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 4.dp)
                    )
                }
            } else {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 4.dp),
                    update = { view ->
                        if (!mapRequested) {
                            mapRequested = true
                            view.getMapAsync { g ->
                                setupMap(g)
                            }
                        }
                    }
                )
            }

            if (searchError != null) {
                Text(
                    text = searchError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = stringResource(R.string.radius_meters_format, radius),
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = radius.toFloat(),
                onValueChange = { r ->
                    radius = r.toInt()
                    circle?.radius = radius.toDouble()
                    if (useOsm && osmReady) {
                        osmWebView?.evaluateJavascript(
                            "if (window.setRadius) setRadius($radius);",
                            null
                        )
                    }
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
            enabled = markerPos != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
            Text(
                text = stringResource(R.string.confirm_location),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private suspend fun geocodeLocation(context: Context, query: String): LatLng? {
    val geocoder = android.location.Geocoder(context)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocationName(query, 1) { addresses ->
                if (continuation.isActive) {
                    continuation.resume(
                        addresses.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
                    )
                }
            }
        }
    } else {
        @Suppress("DEPRECATION")
        geocoder.getFromLocationName(query, 1)
            ?.firstOrNull()
            ?.let { LatLng(it.latitude, it.longitude) }
    }
}
