package com.graphhopper.navigationplugin

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.common.toJvm
import org.maplibre.geojson.model.LineString
import org.maplibre.geojson.model.Point
import org.maplibre.navigation.core.location.engine.LocationEngine
import org.maplibre.navigation.core.location.replay.ReplayRouteLocationEngine
import org.maplibre.navigation.core.utils.Constants
import org.maplibre.navigation.core.location.toAndroidLocation
import org.maplibre.navigation.core.models.DirectionsResponse
import org.maplibre.navigation.core.models.DirectionsRoute
import org.maplibre.navigation.core.models.ManeuverModifier
import org.maplibre.navigation.core.models.StepManeuver
import org.maplibre.navigation.core.navigation.AndroidMapLibreNavigation
import org.maplibre.navigation.core.navigation.MapLibreNavigationOptions
import org.maplibre.navigation.core.routeprogress.RouteProgress
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class NavigationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    companion object {
        private const val TAG = "NavigationActivity"
        private const val ROUTE_SOURCE_ID = "route-source"
        private const val ROUTE_LAYER_ID = "route-layer"
        private const val DEFAULT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }

    // Map components
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    // Navigation components
    private val locationEngine: LocationEngine? = null
    private var navigation: AndroidMapLibreNavigation? = null
    private var currentRoute: DirectionsRoute? = null
    private var lastKnownLocation: Location? = null

    // Voice
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isMuted = false
    private var lastAnnouncedStepIndex: Int = -1
    private var announcedThresholds: MutableSet<Int> = mutableSetOf()

    // UI components
    private lateinit var turnIcon: ImageView
    private lateinit var distanceToTurnText: TextView
    private lateinit var instructionText: TextView
    private lateinit var muteButton: ImageButton
    private lateinit var etaText: TextView
    private lateinit var remainingTimeText: TextView
    private lateinit var remainingDistanceText: TextView
    private lateinit var currentSpeedText: TextView
    private lateinit var speedLimitText: TextView
    private lateinit var stopButton: ImageButton
    private lateinit var recenterButton: ImageButton

    // Rerouting
    private var navigateUrl: String? = null
    private var requestBody: String? = null

    // Current step tracking
    private var distanceToNextManeuver = 0.0

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MapLibreNavigationPlugin.ACTION_STOP_NAVIGATION -> finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        setContentView(R.layout.activity_navigation)
        bindViews()
        setupClickListeners()

        // Setup edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        setupWindowInsets()

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Register broadcast receiver
        val filter = IntentFilter().apply {
            addAction(MapLibreNavigationPlugin.ACTION_STOP_NAVIGATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, filter)
        }

        // Parse request from intent
        val navigateUrl = intent.getStringExtra(MapLibreNavigationPlugin.EXTRA_NAVIGATE_URL)
        val requestBody = intent.getStringExtra(MapLibreNavigationPlugin.EXTRA_REQUEST_BODY)
        if (navigateUrl == null || requestBody == null) {
            Log.e(TAG, "No navigate URL or request body provided")
            finish()
            return
        }

        // Initialize map
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.isCompassEnabled = false
            map.setStyle(Style.Builder().fromUri(DEFAULT_STYLE_URL)) { style ->
                checkLocationPermissionAndStart(navigateUrl, requestBody, style)
            }
        }
    }

    private fun bindViews() {
        turnIcon = findViewById(R.id.turnIcon)
        distanceToTurnText = findViewById(R.id.distanceToTurnText)
        instructionText = findViewById(R.id.instructionText)
        muteButton = findViewById(R.id.muteButton)
        etaText = findViewById(R.id.etaText)
        remainingTimeText = findViewById(R.id.remainingTimeText)
        remainingDistanceText = findViewById(R.id.remainingDistanceText)
        currentSpeedText = findViewById(R.id.currentSpeedText)
        speedLimitText = findViewById(R.id.speedLimitText)
        stopButton = findViewById(R.id.stopButton)
        recenterButton = findViewById(R.id.recenterButton)
    }

    private fun setupClickListeners() {
        muteButton.setOnClickListener {
            isMuted = !isMuted
            updateMuteButtonIcon()
        }

        stopButton.setOnClickListener {
            finish()
        }

        recenterButton.setOnClickListener {
            recenterCamera()
        }
    }

    private fun setupWindowInsets() {
        val topBar = findViewById<View>(R.id.topBar)
        val bottomBar = findViewById<View>(R.id.bottomBar)
        val baseMargin = (16 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            (v.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = insets.top + baseMargin
                leftMargin = insets.left + baseMargin
                rightMargin = insets.right + baseMargin
            }
            v.requestLayout()
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            (v.layoutParams as ViewGroup.MarginLayoutParams).apply {
                bottomMargin = insets.bottom + baseMargin
                leftMargin = insets.left + baseMargin
                rightMargin = insets.right + baseMargin
            }
            v.requestLayout()
            windowInsets
        }
    }

    private fun updateMuteButtonIcon() {
        muteButton.setImageResource(
            if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        )
    }

    private fun checkLocationPermissionAndStart(navigateUrl: String, requestBody: String, style: Style) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
            intent.putExtra("pending_navigate_url", navigateUrl)
            intent.putExtra("pending_request_body", requestBody)
        } else {
            fetchAndInitializeNavigation(navigateUrl, requestBody, style)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val navigateUrl = intent.getStringExtra("pending_navigate_url")
                val requestBody = intent.getStringExtra("pending_request_body")
                if (navigateUrl != null && requestBody != null) {
                    mapLibreMap?.style?.let { style ->
                        fetchAndInitializeNavigation(navigateUrl, requestBody, style)
                    }
                }
            } else {
                Log.e(TAG, "Location permission denied")
                finish()
            }
        }
    }

    private fun fetchAndInitializeNavigation(navigateUrl: String, requestBody: String, style: Style) {
        this.navigateUrl = navigateUrl
        this.requestBody = requestBody
        Thread {
            val routeJson = fetchNavigateRoute(navigateUrl, requestBody)
            runOnUiThread {
                if (routeJson != null) {
                    initializeNavigation(routeJson, style)
                } else {
                    Log.e(TAG, "Failed to fetch route from $navigateUrl")
                    finish()
                }
            }
        }.start()
    }

    private fun fetchNavigateRoute(navigateUrl: String, requestBody: String): String? {
        return try {
            val connection = URL(navigateUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                }
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                Log.e(TAG, "Navigate request failed: ${connection.responseCode} ${connection.responseMessage}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Navigate request error: ${e.message}", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeNavigation(routeJson: String, style: Style) {
        try {
            val directionsResponse = DirectionsResponse.fromJson(routeJson)
            val routes = directionsResponse.routes
            if (routes.isEmpty()) {
                Log.e(TAG, "No routes in response")
                finish()
                return
            }
            currentRoute = routes.first()

            // Initialize replay location engine
            val locationEngine = ReplayRouteLocationEngine()
            locationEngine?.assign(currentRoute!!)

            // Initialize navigation
            val options = MapLibreNavigationOptions(
                defaultMilestonesEnabled = false
            )
            navigation = AndroidMapLibreNavigation(
                context = applicationContext,
                locationEngine = locationEngine,
                options = options
            )

            // Setup progress listener
            navigation?.addProgressChangeListener { location, routeProgress ->
                runOnUiThread {
                    val androidLocation = location.toAndroidLocation()
                    lastKnownLocation = androidLocation
                    updateNavigationUI(androidLocation, routeProgress)

                    // Update location puck
                    mapLibreMap?.locationComponent?.forceLocationUpdate(androidLocation)
                }
            }

            // Setup off-route listener — reroute natively
            navigation?.addOffRouteListener { location ->
                Log.i(TAG, "Off route detected at ${location.latitude}, ${location.longitude}")
                val url = navigateUrl
                val body = requestBody
                if (url != null && body != null) {
                    try {
                        val json = JSONObject(body)
                        val points = json.getJSONArray("points")
                        val destination = points.getJSONArray(points.length() - 1)
                        val newPoints = JSONArray().apply {
                            put(JSONArray().apply {
                                put(location.longitude)
                                put(location.latitude)
                            })
                            put(destination)
                        }
                        json.put("points", newPoints)
                        fetchAndReroute(url, json.toString())
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to build reroute request: ${e.message}", e)
                    }
                }
            }

            // Draw route on map first so the location puck renders on top
            drawRoute(style, currentRoute!!)

            // Setup location component for navigation puck (after route layer)
            setupLocationComponent(style)

            // Fit camera to route
            fitCameraToRoute(currentRoute!!)

            // Start navigation
            navigation?.startNavigation(currentRoute!!)

            // Speak starting instruction
            speakInstruction("Starting navigation")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize navigation: ${e.message}", e)
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationComponent(style: Style) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        mapLibreMap?.locationComponent?.apply {
            activateLocationComponent(
                LocationComponentActivationOptions.builder(this@NavigationActivity, style)
                    .useDefaultLocationEngine(false) // We'll push updates via forceLocationUpdate
                    .build()
            )
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING_GPS
            renderMode = RenderMode.GPS

            addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingChanged(currentMode: Int) {
                    if (currentMode == CameraMode.NONE) {
                        recenterButton.visibility = View.VISIBLE
                    } else {
                        recenterButton.visibility = View.GONE
                    }
                }
                override fun onCameraTrackingDismissed() {
                    recenterButton.visibility = View.VISIBLE
                }
            })
        }
    }

    private fun recenterCamera() {
        cameraTrackingStarted = false
        recenterButton.visibility = View.GONE
        mapLibreMap?.locationComponent?.apply {
            cameraMode = CameraMode.TRACKING_GPS
            val topPadding = mapView.height * 0.25
            paddingWhileTracking(doubleArrayOf(0.0, topPadding, 0.0, 0.0))
            zoomWhileTracking(17.0)
            tiltWhileTracking(45.0)
        }
    }

    private fun drawRoute(style: Style, route: DirectionsRoute) {
        // Remove existing route if any
        style.removeLayer(ROUTE_LAYER_ID)
        style.removeSource(ROUTE_SOURCE_ID)

        // Parse geometry from route
        val geometry = route.geometry ?: return
        val lineString = LineString(geometry, Constants.PRECISION_6)

        // Add source and layer
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, lineString.toJvm()))
        style.addLayer(
            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
                setProperties(
                    lineColor(Color.parseColor("#B34A90D9")),
                    lineWidth(8f),
                    lineCap("round"),
                    lineJoin("round")
                )
            }
        )
    }

    private fun fitCameraToRoute(route: DirectionsRoute) {
        val geometry = route.geometry ?: return
        val lineString = LineString(geometry, Constants.PRECISION_6)
        val coordinates = lineString.coordinates

        if (coordinates.isEmpty()) return

        val boundsBuilder = LatLngBounds.Builder()
        coordinates.forEach { point ->
            boundsBuilder.include(LatLng(point.latitude, point.longitude))
        }

        mapLibreMap?.animateCamera(
            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100)
        )
    }

    private fun updateNavigationUI(location: Location, routeProgress: RouteProgress) {
        // Update current step info
        val currentLegProgress = routeProgress.currentLegProgress
        val currentStepProgress = currentLegProgress?.currentStepProgress
        val currentStep = currentStepProgress?.step

        // The upcoming step's maneuver describes the next turn the user must make.
        // currentStep.maneuver describes how the user *entered* the current step (already done).
        val upcomingManeuver = currentLegProgress?.upComingStep?.maneuver

        currentStep?.let { step ->
            val bannerInstruction = step.bannerInstructions?.firstOrNull()
            val instruction = bannerInstruction?.primary?.text
                ?: upcomingManeuver?.instruction
                ?: currentLegProgress?.upComingStep?.name ?: ""
            instructionText.text = instruction

            // Update turn icon based on upcoming maneuver
            val type = bannerInstruction?.primary?.type ?: upcomingManeuver?.type
            val modifier = bannerInstruction?.primary?.modifier ?: upcomingManeuver?.modifier
            turnIcon.setImageResource(getManeuverIcon(type, modifier))

            // Distance to next maneuver (= remaining distance in current step)
            distanceToNextManeuver = currentStepProgress.distanceRemaining
            distanceToTurnText.text = formatDistance(distanceToNextManeuver)

            // Voice instruction — use the translated maneuver text from the response.
            // If the upcoming step is short (< 150m), merge with the follow-on instruction
            // so the user hears both turns in one sentence.
            var voiceText = upcomingManeuver?.instruction ?: instruction
            val upcomingStepDistance = currentLegProgress?.upComingStep?.distance ?: Double.MAX_VALUE
            val followOnInstruction = currentLegProgress?.followOnStep?.maneuver?.instruction
            if (upcomingStepDistance < 150 && followOnInstruction != null) {
                voiceText = "$voiceText, then $followOnInstruction"
            }
            val stepIndex = currentLegProgress?.stepIndex ?: -1
            handleVoiceInstruction(voiceText, distanceToNextManeuver, stepIndex)
        }

        // Update remaining distance and time
        val distanceRemaining = routeProgress.distanceRemaining
        val durationRemaining = routeProgress.durationRemaining

        remainingDistanceText.text = formatDistance(distanceRemaining)
        remainingTimeText.text = formatDuration(durationRemaining)

        // Calculate and display ETA
        val etaMillis = System.currentTimeMillis() + (durationRemaining * 1000).toLong()
        etaText.text = formatTime(etaMillis)

        // Update current speed
        val speedMps = location.speed
        currentSpeedText.text = formatSpeed(speedMps)

        // Speed limit not available
        speedLimitText.text = "--"

        // Update camera to follow location
        updateCameraPosition(location)
    }

    private var cameraTrackingStarted = false

    private fun updateCameraPosition(location: Location) {
        mapLibreMap?.locationComponent?.apply {
            if (!cameraTrackingStarted) {
                cameraTrackingStarted = true
                // Re-enable tracking — fitCameraToRoute's animateCamera switches
                // the camera mode to NONE, so we must restore it here.
                cameraMode = CameraMode.TRACKING_GPS
                // Shift the focal point down so the arrow sits in the lower third
                val topPadding = mapView.height * 0.25
                paddingWhileTracking(doubleArrayOf(0.0, topPadding, 0.0, 0.0))
            }
            zoomWhileTracking(17.0)
            tiltWhileTracking(45.0)
        }
    }

    private fun getManeuverIcon(type: StepManeuver.Type?, modifier: ManeuverModifier.Type?): Int {
        return when {
            type == StepManeuver.Type.ARRIVE -> R.drawable.ic_destination
            type == StepManeuver.Type.DEPART -> R.drawable.ic_straight
            type == StepManeuver.Type.ROUNDABOUT || type == StepManeuver.Type.ROTARY ||
                type == StepManeuver.Type.ROUNDABOUT_TURN || type == StepManeuver.Type.EXIT_ROUNDABOUT ||
                type == StepManeuver.Type.EXIT_ROTARY -> when (modifier) {
                    ManeuverModifier.Type.LEFT, ManeuverModifier.Type.SHARP_LEFT, ManeuverModifier.Type.SLIGHT_LEFT -> R.drawable.ic_roundabout_left
                    ManeuverModifier.Type.RIGHT, ManeuverModifier.Type.SHARP_RIGHT, ManeuverModifier.Type.SLIGHT_RIGHT -> R.drawable.ic_roundabout_right
                    else -> R.drawable.ic_roundabout
                }
            modifier == ManeuverModifier.Type.SHARP_LEFT -> R.drawable.ic_turn_sharp_left
            modifier == ManeuverModifier.Type.SHARP_RIGHT -> R.drawable.ic_turn_sharp_right
            modifier == ManeuverModifier.Type.SLIGHT_LEFT -> R.drawable.ic_turn_slight_left
            modifier == ManeuverModifier.Type.SLIGHT_RIGHT -> R.drawable.ic_turn_slight_right
            modifier == ManeuverModifier.Type.LEFT -> R.drawable.ic_turn_left
            modifier == ManeuverModifier.Type.RIGHT -> R.drawable.ic_turn_right
            modifier == ManeuverModifier.Type.UTURN -> R.drawable.ic_turn_sharp_left
            modifier == ManeuverModifier.Type.STRAIGHT -> R.drawable.ic_straight
            else -> R.drawable.ic_straight
        }
    }

    private val voiceThresholds = listOf(1000, 200, 30)

    private fun handleVoiceInstruction(instruction: String, distanceToManeuver: Double, stepIndex: Int) {
        if (isMuted) return

        // On step change, pre-mark thresholds above the entry distance as already
        // announced so a GPS jump or fast speed into a step never triggers a
        // threshold the user has already passed.
        if (stepIndex != lastAnnouncedStepIndex) {
            lastAnnouncedStepIndex = stepIndex
            announcedThresholds.clear()
            for (t in voiceThresholds) {
                if (t > distanceToManeuver) {
                    announcedThresholds.add(t)
                }
            }
        }

        // Determine which threshold we are within
        val threshold = voiceThresholds.lastOrNull { distanceToManeuver <= it } ?: return

        // Only announce each threshold once per step
        if (threshold in announcedThresholds) return
        announcedThresholds.add(threshold)

        val spokenText = when (threshold) {
            30 -> instruction
            200 -> "In 200 meters, $instruction"
            1000 -> "In 1 kilometer, $instruction"
            else -> return
        }

        speakInstruction(spokenText)
    }

    private fun speakInstruction(text: String) {
        if (isTtsReady && !isMuted) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_instruction")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!isTtsReady) {
                Log.w(TAG, "TTS language not supported, trying English")
                tts?.setLanguage(Locale.US)
                isTtsReady = true
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    private fun fetchAndReroute(navigateUrl: String, requestBody: String) {
        Thread {
            val routeJson = fetchNavigateRoute(navigateUrl, requestBody)
            runOnUiThread {
                if (routeJson != null) {
                    applyReroute(routeJson)
                } else {
                    Log.e(TAG, "Failed to fetch reroute from $navigateUrl")
                }
            }
        }.start()
    }

    private fun applyReroute(routeJson: String) {
        try {
            val directionsResponse = DirectionsResponse.fromJson(routeJson)
            val routes = directionsResponse.routes
            if (routes.isEmpty()) {
                Log.e(TAG, "No routes in reroute response")
                return
            }

            val newRoute = routes.first()
            currentRoute = newRoute

            // rerouting should not update route for replay location engine
            // locationEngine?.assign(newRoute)

            navigation?.startNavigation(newRoute)

            mapLibreMap?.style?.let { style ->
                drawRoute(style, newRoute)
            }

            lastAnnouncedStepIndex = -1
            announcedThresholds.clear()
            speakInstruction("Route updated")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply reroute: ${e.message}", e)
        }
    }

    private fun formatDistance(meters: Double): String {
        return when {
            meters >= 1000 -> String.format(Locale.getDefault(), "%.1f km", meters / 1000)
            else -> String.format(Locale.getDefault(), "%d m", meters.roundToInt())
        }
    }

    private fun formatDuration(seconds: Double): String {
        val totalMinutes = (seconds / 60).roundToInt()
        return when {
            totalMinutes >= 60 -> {
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                String.format(Locale.getDefault(), "%d h %d min", hours, mins)
            }
            else -> String.format(Locale.getDefault(), "%d min", totalMinutes)
        }
    }

    private fun formatTime(millis: Long): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    private fun formatSpeed(mps: Float): String {
        val kmh = mps * 3.6f
        return String.format(Locale.getDefault(), "%d km/h", kmh.roundToInt())
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (_: Exception) {
        }
        // Notify the plugin that navigation has closed (survives Activity recreation)
        sendBroadcast(Intent(MapLibreNavigationPlugin.ACTION_NAVIGATION_CLOSED))
        navigation?.stopNavigation()
        navigation?.onDestroy()
        tts?.shutdown()
        mapView.onDestroy()
    }
}
