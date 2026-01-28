package com.graphhopper.navigationplugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class NavigationActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "NavigationActivity"
        const val ACTION_STOP_NAVIGATION = "com.graphhopper.navigationplugin.STOP_NAVIGATION"
        private const val ROUTE_SOURCE_ID = "route-source"
        private const val ROUTE_LAYER_ID = "route-layer"
        private const val DEFAULT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    }

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_NAVIGATION) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        // Create MapView and set content view first (required before accessing window.insetsController)
        mapView = MapView(this)
        setContentView(mapView)

        // Setup edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

        // Register broadcast receiver for stop command
        val filter = IntentFilter(ACTION_STOP_NAVIGATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }

        val coordinatesJson = intent.getStringExtra(MapLibreNavigationPlugin.EXTRA_COORDINATES)
        val boundsString = intent.getStringExtra(MapLibreNavigationPlugin.EXTRA_BOUNDS)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri(DEFAULT_STYLE_URL)) { style ->
                if (coordinatesJson != null) {
                    val points = parseCoordinates(coordinatesJson)
                    addRouteToMap(style, points)
                    if (boundsString != null) {
                        fitCameraToBounds(boundsString)
                    } else if (points.isNotEmpty()) {
                        fitCameraToPoints(points)
                    }
                }
            }
        }
    }

    private fun parseCoordinates(json: String): List<LatLng> {
        val points = mutableListOf<LatLng>()
        try {
            val jsonArray = org.json.JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val coord = jsonArray.getJSONArray(i)
                points.add(LatLng(coord.getDouble(1), coord.getDouble(0)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse coordinates: ${e.message}", e)
        }
        return points
    }

    private fun addRouteToMap(style: Style, points: List<LatLng>) {
        if (points.isEmpty()) return
        val linePoints = points.map { Point.fromLngLat(it.longitude, it.latitude) }
        val feature = Feature.fromGeometry(LineString.fromLngLats(linePoints))
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, feature))
        style.addLayer(LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#2962FF")),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
        })
    }

    private fun fitCameraToBounds(boundsString: String) {
        val parts = boundsString.split(",")
        if (parts.size != 4) return
        val swLng = parts[0].toDoubleOrNull() ?: return
        val swLat = parts[1].toDoubleOrNull() ?: return
        val neLng = parts[2].toDoubleOrNull() ?: return
        val neLat = parts[3].toDoubleOrNull() ?: return
        val bounds = LatLngBounds.Builder()
            .include(LatLng(swLat, swLng))
            .include(LatLng(neLat, neLng))
            .build()
        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }

    private fun fitCameraToPoints(points: List<LatLng>) {
        if (points.isEmpty()) return
        val boundsBuilder = LatLngBounds.Builder()
        points.forEach { boundsBuilder.include(it) }
        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) {}
        mapView.onDestroy()
    }
}
