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
import android.util.Log
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
import org.maplibre.navigation.core.location.engine.LocationEngineProvider
import org.maplibre.navigation.core.location.replay.ReplayRouteLocationEngine
import org.maplibre.navigation.core.utils.Constants
import org.maplibre.navigation.core.location.toAndroidLocation
import org.maplibre.navigation.core.models.DirectionsResponse
import org.maplibre.navigation.core.models.DirectionsRoute
import org.maplibre.navigation.core.models.ManeuverModifier
import org.maplibre.navigation.core.models.RouteOptions
import org.maplibre.navigation.core.models.StepManeuver
import org.maplibre.navigation.core.navigation.AndroidMapLibreNavigation
import org.maplibre.navigation.core.navigation.MapLibreNavigationOptions
import org.maplibre.navigation.core.milestone.VoiceInstructionMilestone
import org.maplibre.navigation.core.routeprogress.RouteProgress
import org.maplibre.navigation.android.navigation.ui.v5.voice.NavigationSpeechPlayer
import org.maplibre.navigation.android.navigation.ui.v5.voice.SpeechAnnouncement
import org.maplibre.navigation.android.navigation.ui.v5.voice.SpeechPlayer
import org.maplibre.navigation.android.navigation.ui.v5.voice.SpeechPlayerProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class NavigationActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "NavigationActivity"
        private const val ROUTE_SOURCE_ID = "route-source"
        private const val ROUTE_LAYER_ID = "route-layer"
        private const val DEFAULT_STYLE_URL = "https://tiles.mapilion.com/assets/osm-bright/style.json?key=b582abd4-d55d-4cb1-8f34-f4254cd52aa7"
        private const val LOCATION_PERMISSION_REQUEST = 1001

        // Set to true to simulate GPS along the route instead of using real GPS
        private const val FAKE_GPS = false
    }

    // Map components
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    // Navigation components
    private var navigation: AndroidMapLibreNavigation? = null
    private var currentRoute: DirectionsRoute? = null
    private var lastKnownLocation: Location? = null

    // Voice
    private var speechPlayer: SpeechPlayer? = null

    // Compose state — drives the overlay UI
    private var turnIconRes by mutableIntStateOf(R.drawable.ic_straight)
    private var distanceToTurn by mutableStateOf("")
    private var instruction by mutableStateOf("")
    private var isMuted by mutableStateOf(false)
    private var eta by mutableStateOf("")
    private var remainingTime by mutableStateOf("")
    private var remainingDistance by mutableStateOf("")
    private var currentSpeed by mutableStateOf("")
    private var showRecenter by mutableStateOf(false)
    private var thenTurnIconRes by mutableStateOf<Int?>(null)
    private var roundaboutExit by mutableStateOf<Int?>(null)

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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Create MapView before setContent so it can be embedded via AndroidView
        mapView = MapView(this)

        setContent {
            NavigationScreen(
                mapView = mapView,
                turnIconRes = turnIconRes,
                distanceToTurn = distanceToTurn,
                instruction = instruction,
                isMuted = isMuted,
                eta = eta,
                remainingTime = remainingTime,
                remainingDistance = remainingDistance,
                currentSpeed = currentSpeed,
                showRecenter = showRecenter,
                thenTurnIconRes = thenTurnIconRes,
                roundaboutExit = roundaboutExit,
                onMuteToggle = {
                    isMuted = !isMuted
                    speechPlayer?.setMuted(isMuted)
                },
                onStop = { finish() },
                onRecenter = { recenterCamera() },
            )
        }

        // Setup edge-to-edge display (after setContent so DecorView exists)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

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

        // Parse start coordinates from request body for initial camera position
        val startPosition = try {
            val json = JSONObject(requestBody)
            val points = json.getJSONArray("points")
            val start = points.getJSONArray(0)
            LatLng(start.getDouble(1), start.getDouble(0)) // lat, lng
        } catch (e: Exception) {
            null
        }

        // Initialize map
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            // Position compass below the top instruction panel
            val density = resources.displayMetrics.density
            map.uiSettings.setCompassMargins(0, (140 * density).toInt(), (16 * density).toInt(), 0)
            // Use custom white compass image
            ContextCompat.getDrawable(this, R.drawable.ic_compass)?.let {
                map.uiSettings.setCompassImage(it)
            }
            // Set initial camera to start position
            startPosition?.let {
                map.cameraPosition = CameraPosition.Builder()
                    .target(it)
                    .zoom(15.0)
                    .build()
            }
            map.setStyle(Style.Builder().fromUri(DEFAULT_STYLE_URL)) { style ->
                checkLocationPermissionAndStart(navigateUrl, requestBody, style)
            }
        }
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
                val navigateUrl = intent.getStringExtra(MapLibreNavigationPlugin.EXTRA_NAVIGATE_URL)
                val requestBody = intent.getStringExtra(MapLibreNavigationPlugin.EXTRA_REQUEST_BODY)
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
        val location = lastKnownLocation
        Thread {
            val routeJson = fetchNavigateRoute(navigateUrl, requestBody, location)
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

    private fun prepareNavigateRequestBody(requestBody: String, currentLocation: Location?): String {
        val json = JSONObject(requestBody)

        // Replace first point with current location if available
        if (currentLocation != null) {
            val points = json.getJSONArray("points")
            val destination = points.getJSONArray(points.length() - 1)
            val newPoints = JSONArray().apply {
                put(JSONArray().apply {
                    put(currentLocation.longitude)
                    put(currentLocation.latitude)
                })
                put(destination)
            }
            json.put("points", newPoints)

            if (currentLocation.hasBearing()) {
                json.put("headings", JSONArray().put(currentLocation.bearing.toDouble()))
            }
        }

        // Force a few request parameters for navigation
        json.put("type", "mapbox")
        json.put("ch.disable", true)
        json.remove("elevation")
        json.remove("points_encoded")
        json.remove("points_encoded_multiplier")

        // with path details we get:
//        IndexOutOfBoundsException: Index 0 out of bounds for length 0
//        at jdk.internal.util.Preconditions.outOfBounds(Preconditions.java:64)
//        at jdk.internal.util.Preconditions.outOfBoundsCheckIndex(Preconditions.java:70)
//        at jdk.internal.util.Preconditions.checkIndex(Preconditions.java:266)
//        at java.util.Objects.checkIndex(Objects.java:391)
//        at java.util.ArrayList.get(ArrayList.java:434)
//        at org.maplibre.navigation.core.navigation.NavigationHelper.findCurrentIntersection(NavigationHelper.kt:378)
        json.remove("details")
        // json.put("details", JSONArray().put("max_speed"))
        return json.toString()
    }

    private fun fetchNavigateRoute(navigateUrl: String, requestBody: String, currentLocation: Location?): String? {
        val preparedBody = prepareNavigateRequestBody(requestBody, currentLocation)
        return try {
            val connection = URL(navigateUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write(preparedBody)
                }
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                Log.e(TAG, "Navigate request failed: ${connection.responseCode} ${connection.responseMessage} - $errorBody")
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
            currentRoute = directionsResponse.routes.first().copy(
                routeOptions = createWtfObject()
            )

            // Initialize location engine based on fakeGps setting
            val locationEngine: LocationEngine = if (FAKE_GPS) {
                ReplayRouteLocationEngine().also { it.assign(currentRoute!!) }
            } else {
                LocationEngineProvider.getBestLocationEngine(applicationContext)
            }

            // Initialize speech player using route's voice language or device locale
            val voiceLanguage = currentRoute?.voiceLanguage ?: Locale.getDefault().language
            speechPlayer = NavigationSpeechPlayer(SpeechPlayerProvider(this, voiceLanguage, true))
            speechPlayer?.setMuted(isMuted)

            // Initialize navigation with default milestones enabled (for voice instructions)
            val options = MapLibreNavigationOptions(
                defaultMilestonesEnabled = true,
                // offRouteThresholdRadiusMeters = 50.0,

                snapToRoute = false
                // snapping works in general but has sometimes strange back-and-forth behavour
                // TODO why would we need to set useDefaultLocationEngine=false?
                // https://github.com/maplibre/maplibre-navigation-android/blob/534334f768ca14fbe9ac50d8d29859ec1c54b4de/app/src/main/java/org/maplibre/navigation/android/example/SnapToRouteNavigationActivity.kt#L154
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

            // Voice instructions via SDK milestones
            navigation?.addMilestoneEventListener { routeProgress, instruction, milestone ->
                if (milestone is VoiceInstructionMilestone) {
                    val speechAnnouncement = SpeechAnnouncement.builder().voiceInstructionMilestone(milestone).build()
                    speechPlayer?.play(speechAnnouncement)
                }
            }

            // Setup off-route listener — reroute natively
            navigation?.addOffRouteListener { location ->
                Log.i(TAG, "Off route detected at ${location.latitude}, ${location.longitude}")
                val url = navigateUrl
                val body = requestBody
                if (url != null && body != null) {
                    fetchAndReroute(url, body, location.toAndroidLocation())
                }
            }

            // Draw route on map first so the location puck renders on top
            drawRoute(style, currentRoute!!)

            // Setup location component for navigation puck
            setupLocationComponent(style)

            // Start navigation
            navigation?.startNavigation(currentRoute!!)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize navigation: ${e.message}", e)
            finish()
        }
    }

    private fun createWtfObject(): RouteOptions {
        return RouteOptions(
            // These dummy route options are not not used to create directions,
            // but currently they are necessary to start the navigation
            // and to use the banner & voice instructions.
            // Again, this isn't ideal, but it is a requirement of the framework.
            baseUrl = "https://graphhopper.com",
            profile = "graphhopper",
            user = "graphhopper",
            accessToken = "graphhopper",
            voiceInstructions = true,
            bannerInstructions = true,
            language = "en-US",
            coordinates = listOf(Point(9.6935451, 52.3758408), Point(9.9769191, 53.5426183)),
            requestUuid = "0000-0000-0000-0000"
        )
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
                    .useDefaultLocationEngine(!FAKE_GPS) // Use real GPS engine when not faking
                    .build()
            )
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING_GPS
            renderMode = RenderMode.GPS

            addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingChanged(currentMode: Int) {
                    showRecenter = currentMode == CameraMode.NONE
                }
                override fun onCameraTrackingDismissed() {
                    showRecenter = true
                }
            })
        }
    }

    private fun recenterCamera() {
        cameraTrackingStarted = false
        showRecenter = false
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

        // Add source and layer (below location puck)
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, lineString.toJvm()))
        val routeLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
            setProperties(
                lineColor(Color.parseColor("#B34A90D9")),
                lineWidth(8f),
                lineCap("round"),
                lineJoin("round")
            )
        }

        // Log last layers (location puck layers are typically added on top)
        // val allLayers = style.layers.map { it.id }
        // Log.i(TAG, "Last 10 layers: ${allLayers.takeLast(10)}")

        // Add below location puck shadow layer (if it exists), otherwise on top
        val shadowLayerId = "mapbox-location-shadow-layer"
        if (style.getLayer(shadowLayerId) != null) {
            style.addLayerBelow(routeLayer, shadowLayerId)
        } else {
            style.addLayer(routeLayer)
        }
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
            val instructionStr = bannerInstruction?.primary?.text
                ?: upcomingManeuver?.instruction
                ?: currentLegProgress?.upComingStep?.name ?: ""
            instruction = instructionStr

            // Update turn icon based on upcoming maneuver
            val type = bannerInstruction?.primary?.type ?: upcomingManeuver?.type
            val modifier = bannerInstruction?.primary?.modifier ?: upcomingManeuver?.modifier
            val degrees = bannerInstruction?.primary?.degrees
            turnIconRes = getManeuverIcon(type, modifier, degrees)

            // Show exit number for roundabouts
            roundaboutExit = if (isRoundaboutType(type)) upcomingManeuver?.exit else null

            // Distance to next maneuver (= remaining distance in current step)
            distanceToNextManeuver = currentStepProgress.distanceRemaining
            distanceToTurn = formatDistance(distanceToNextManeuver)

            // "Then" turn — show when the next maneuver is close and there's a sub instruction
            val sub = bannerInstruction?.sub
            thenTurnIconRes = if (sub != null && distanceToNextManeuver < 200) {
                getManeuverIcon(sub.type, sub.modifier, sub.degrees)
            } else {
                null
            }
        }

        // Update remaining distance and time
        val distanceRemainingVal = routeProgress.distanceRemaining
        val durationRemaining = routeProgress.durationRemaining

        remainingDistance = formatDistance(distanceRemainingVal)
        remainingTime = formatDuration(durationRemaining)

        // Calculate and display ETA
        val etaMillis = System.currentTimeMillis() + (durationRemaining * 1000).toLong()
        eta = formatTime(etaMillis)

        // Update current speed (number only, unit is in the composable)
        val speedKmh = (location.speed * 3.6f).roundToInt()
        currentSpeed = speedKmh.toString()

        // Update camera to follow location
        updateCameraPosition(location)
    }

    private var cameraTrackingStarted = false

    private fun updateCameraPosition(location: Location) {
        mapLibreMap?.locationComponent?.apply {
            if (!cameraTrackingStarted) {
                cameraTrackingStarted = true
                // Shift the focal point down so the arrow sits in the lower third
                val topPadding = mapView.height * 0.25
                paddingWhileTracking(doubleArrayOf(0.0, topPadding, 0.0, 0.0))
            }
            zoomWhileTracking(17.0)
            tiltWhileTracking(45.0)
        }
    }

    private fun isRoundaboutType(type: StepManeuver.Type?): Boolean {
        return type == StepManeuver.Type.ROUNDABOUT || type == StepManeuver.Type.ROTARY ||
            type == StepManeuver.Type.ROUNDABOUT_TURN || type == StepManeuver.Type.EXIT_ROUNDABOUT ||
            type == StepManeuver.Type.EXIT_ROTARY
    }

    private fun getManeuverIcon(
        type: StepManeuver.Type?,
        modifier: ManeuverModifier.Type?,
        degrees: Double? = null
    ): Int {
        return when {
            type == StepManeuver.Type.ARRIVE -> R.drawable.ic_destination
            type == StepManeuver.Type.DEPART -> R.drawable.ic_straight
            isRoundaboutType(type) -> {
                // Use degrees (angle through roundabout) to determine exit direction
                if (degrees != null) {
                    when {
                        degrees < 145 -> R.drawable.ic_roundabout_right
                        degrees < 215 -> R.drawable.ic_roundabout_straight
                        else -> R.drawable.ic_roundabout_left
                    }
                } else {
                    R.drawable.ic_roundabout
                }
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

    private fun fetchAndReroute(navigateUrl: String, requestBody: String, currentLocation: Location) {
        Thread {
            val routeJson = fetchNavigateRoute(navigateUrl, requestBody, currentLocation)
            if (routeJson != null) {
                runOnUiThread { applyReroute(routeJson) }
            } else {
                Log.e(TAG, "Failed to fetch reroute from $navigateUrl")
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

            speechPlayer?.onOffRoute();
            // TODO announce re-routing?

            val newRoute = routes.first().copy(
                routeOptions = createWtfObject()
            )
            currentRoute = newRoute
            navigation?.startNavigation(newRoute)
            mapLibreMap?.style?.let { style ->
                drawRoute(style, newRoute)
            }

        } catch (e: Exception) {
            // TODO announce that re-routing failed?
            Log.e(TAG, "Failed to apply reroute: ${e.message}", e)
        }
    }

    private fun formatDistance(meters: Double): String {
        return when {
            meters >= 10000 -> String.format(Locale.getDefault(), "%.0f km", meters / 1000)
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
        speechPlayer?.onDestroy()
        mapView.onDestroy()
    }
}
