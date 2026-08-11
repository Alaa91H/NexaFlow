package com.nexaflow.feature.builder

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.nexaflow.core.engine.LocationAccess
import com.nexaflow.core.ui.NexaFlowTopBar
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * In-app OpenStreetMap point picker. Replaces the old "hand off to an
 * external maps app" flow: modern Google Maps dropped ACTION_PICK, so the
 * user could never get a picked point back. Here a fixed crosshair stays in
 * the middle of the map while the user pans/zooms, and the Confirm button
 * writes the center coordinates back to the previous destination's
 * savedStateHandle (same pattern as the icon picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapPickerScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Remember a MapView instance across recompositions so the tile cache and
    // zoom state survive; it is disposed when the screen leaves composition.
    val mapView = remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 2.0
            maxZoomLevel = 20.0
            controller.setZoom(16.0)
        }
    }
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf(GeoPoint(0.0, 0.0)) }
    // Keep the coordinates in sync with whatever sits under the crosshair.
    val syncCenter: () -> Unit = {
        val p = mapView.projection?.fromPixels(
            mapView.width / 2,
            mapView.height / 2
        )
        if (p != null) center = GeoPoint(p.latitude, p.longitude)
    }
    DisposableEffect(mapView) {
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                syncCenter()
                return true
            }
            override fun onZoom(event: ZoomEvent): Boolean {
                syncCenter()
                return true
            }
        })
        mapView.addOnFirstLayoutListener { _, _, _, _, _ ->
            syncCenter()
        }
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {}
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = stringResource(R.string.pick_on_map),
                onBack = { navController.popBackStack() }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.location_coordinates,
                        "%.6f".format(center.latitude),
                        "%.6f".format(center.longitude)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                Button(
                    onClick = {
                        navController.previousBackStackEntry?.savedStateHandle?.set(
                            "picked_location",
                            "${center.latitude},${center.longitude}"
                        )
                        navController.popBackStack()
                    },
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
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { syncCenter() }
            )
            // Fixed crosshair overlay: the selection is whatever sits under it.
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 24.dp)
                    .size(40.dp)
            )
            // Quick "center on my location" button. Needs the same location
            // permission as the trigger itself; silently no-ops without it.
            IconButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            LocationAccess.getCurrentLocation(context, 8_000)?.let { fix ->
                                mapView.controller.animateTo(GeoPoint(fix.latitude, fix.longitude))
                                center = GeoPoint(fix.latitude, fix.longitude)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null)
            }
        }
    }
}
