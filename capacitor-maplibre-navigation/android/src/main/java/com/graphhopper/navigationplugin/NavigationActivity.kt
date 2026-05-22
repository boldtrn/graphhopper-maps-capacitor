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
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.common.toJvm
import org.maplibre.geojson.model.LineString
import org.maplibre.geojson.model.Point
import org.maplibre.navigation.core.location.engine.LocationEngine
import org.maplibre.navigation.core.location.engine.MapLibreLocationEngine
import org.maplibre.navigation.core.location.replay.ReplayRouteLocationEngine
import org.maplibre.navigation.core.utils.Constants
import org.maplibre.navigation.core.location.toAndroidLocation
import org.maplibre.navigation.core.models.DirectionsResponse
import org.maplibre.navigation.core.models.DirectionsRoute
import org.maplibre.navigation.core.models.ManeuverModifier
import org.maplibre.navigation.core.models.RouteOptions
import org.maplibre.navigation.core.models.StepManeuver
import org.maplibre.navigation.core.navigation.AndroidMapLibreNavigation
import org.maplibre.navigation.core.route.RouteListener
import org.json.JSONObject
import org.maplibre.navigation.core.navigation.MapLibreNavigationOptions
import org.maplibre.navigation.core.milestone.VoiceInstructionMilestone
import org.maplibre.navigation.core.routeprogress.RouteProgress
import org.maplibre.navigation.android.navigation.ui.v5.voice.NavigationSpeechPlayer
import org.maplibre.navigation.android.navigation.ui.v5.voice.SpeechAnnouncement
import org.maplibre.navigation.android.navigation.ui.v5.voice.SpeechPlayer
import org.maplibre.navigation.android.navigation.ui.v5.voice.SpeechPlayerProvider
import java.util.Locale

class NavigationActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "NavigationActivity"
        private const val ROUTE_SOURCE_ID = "route-source"
        private const val ROUTE_LAYER_ID = "route-layer"
        private const val DEST_SOURCE_ID = "destination-marker-source"
        private const val DEST_LAYER_ID = "destination-marker-layer"
        private const val DEST_ICON_ID = "destination-marker-icon"
        private const val VIA_SOURCE_ID = "via-marker-source"
        private const val VIA_LAYER_ID = "via-marker-layer"
        private const val VIA_ICON_ID = "via-marker-icon"
        private const val DEFAULT_STYLE_URL =
            "https://tiles.mapilion.com/assets/osm-bright/style.json?key=b582abd4-d55d-4cb1-8f34-f4254cd52aa7"
        private const val LOCATION_PERMISSION_REQUEST = 1001

        // Set to true to simulate GPS along the route instead of using real GPS
        // But for a better fake solution use Lockito or similar
        private const val FAKE_GPS = false
    }

    // Map components
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    // Navigation components
    private var navigation: AndroidMapLibreNavigation? = null
    private var currentRoute: DirectionsRoute? = null
    private var mapRouteArrow: MapRouteArrow? = null

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
    private var speedUnit by mutableStateOf("km/h")
    private var speedLimit by mutableStateOf<Int?>(null)  // null = not shown
    private var speedLimitUnlimited by mutableStateOf(false)
    private var showRecenter by mutableStateOf(false)
    private var thenTurnIconRes by mutableStateOf<Int?>(null)
    private var roundaboutExit by mutableStateOf<Int?>(null)

    // For temporary storage after permission granted (and later for GraphHopperRouteFetcher)
    private var navigateUrl: String? = null
    private var requestJson: JSONObject? = null

    // Route fetching
    private var routeFetcher: GraphHopperRouteFetcher? = null
    private var lastRouteProgress: RouteProgress? = null
    private var arrived = false
    // Voice instructions emitted while on the final (ARRIVE) step are
    private var pendingArrivalAnnouncement: SpeechAnnouncement? = null

    // Unit settings
    private var showDistanceInMiles = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MapLibreNavigationPlugin.ACTION_STOP_NAVIGATION -> finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Translation.init(this, getLocale())
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
                speedUnit = speedUnit,
                speedLimit = speedLimit,
                speedLimitUnlimited = speedLimitUnlimited,
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
        val url = intent.getStringExtra(MapLibreNavigationPlugin.EXTRA_NAVIGATE_URL)
        val requestBody = intent.getStringExtra(MapLibreNavigationPlugin.EXTRA_REQUEST_BODY)
        showDistanceInMiles = intent.getBooleanExtra(MapLibreNavigationPlugin.EXTRA_SHOW_DISTANCE_IN_MILES, false)
        if (url == null || requestBody == null) {
            Log.e(TAG, "No navigate URL or request body provided")
            finish()
            return
        }

        // Parse JSON once with error handling
        val json = try {
            JSONObject(requestBody)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse request body: ${e.message}", e)
            finish()
            return
        }

        // Store for later use (after permission granted)
        navigateUrl = url
        requestJson = json

        // Extract start coordinates for initial camera position
        val startPosition = try {
            val points = json.getJSONArray("points")
            val start = points.getJSONArray(0)
            LatLng(start.getDouble(1), start.getDouble(0)) // lat, lng
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract start position: ${e.message}")
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
            // Hide attribution (covered by UI anyway)
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false

            // Set initial camera to start position
            startPosition?.let {
                map.cameraPosition = CameraPosition.Builder()
                    .target(it)
                    .zoom(15.0)
                    .build()
            }
            map.setStyle(Style.Builder().fromUri(DEFAULT_STYLE_URL)) { style ->
                checkPermissionAndLoadInitialRoute(style)
            }
        }
    }

    private fun checkPermissionAndLoadInitialRoute(style: Style) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else if (!isLocationProviderEnabled()) {
            failAndClose(Translation.tr("waiting_for_gps", "Please enable GPS / Location Services"))
        } else {
            fetchInitialRoute(style)
        }
    }

    private fun isLocationProviderEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /** Show an explanatory message and close the navigation screen. Safe to call from any thread. */
    private fun failAndClose(message: String) {
        Log.e(TAG, "Closing navigation: $message")
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                finish()
            }
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
                if (!isLocationProviderEnabled()) {
                    failAndClose(Translation.tr("waiting_for_gps", "Please enable GPS / Location Services"))
                } else {
                    mapLibreMap?.style?.let { fetchInitialRoute(it) }
                }
            } else {
                failAndClose(Translation.tr("location_permission_denied", "Location permission is required for navigation"))
            }
        }
    }

    private fun fetchInitialRoute(style: Style) {
        val url = navigateUrl ?: return
        val json = requestJson ?: return

        // Create route fetcher once - uses pre-parsed JSON
        routeFetcher = GraphHopperRouteFetcher(url, json).apply {
            // RouteListener is used for reroutes (triggered by findRouteFromRouteProgress)
            addRouteListener(object : RouteListener {
                override fun onResponseReceived(response: DirectionsResponse, routeProgress: RouteProgress) {
                    runOnUiThread { applyReroute(response) }
                }

                override fun onErrorReceived(throwable: Throwable) {
                    Log.e(TAG, "Reroute failed: ${throwable.message}", throwable)
                }
            })
            // Fetch initial route using callbacks
            fetchInitialRoute(
                onSuccess = { response ->
                    runOnUiThread { initializeNavigation(response, style) }
                },
                onError = { e ->
                    Log.e(TAG, "Initial route fetch failed: ${e.message}", e)
                    failAndClose(Translation.tr("route_fetch_failed", "Could not fetch route: ${e.message}"))
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeNavigation(directionsResponse: DirectionsResponse, style: Style) {
        try {
            val routes = directionsResponse.routes

            if (routes.isEmpty()) {
                failAndClose(Translation.tr("locations_not_found", "No route found"))
                return
            }
            val route = directionsResponse.routes.first()
            currentRoute = route.copy(
                routeOptions = createWtfObject()
            )

            // MapLibreLocationEngine (not LocationEngineProvider.getBestLocationEngine) so the build stays GMS-free for F-Droid.
            val locationEngine: LocationEngine = if (FAKE_GPS) {
                ReplayRouteLocationEngine().also { it.assign(currentRoute!!) }
            } else {
                MapLibreLocationEngine(applicationContext, Looper.getMainLooper())
            }

            // Initialize speech player using route's voice language or device locale
            val voiceLanguage = currentRoute?.voiceLanguage ?: Locale.getDefault().language
            speechPlayer = NavigationSpeechPlayer(SpeechPlayerProvider(this, voiceLanguage, true))
            speechPlayer?.setMuted(isMuted)

            // Initialize navigation with default milestones enabled (for voice instructions)
            // metersRemainingTillArrival is declared by the SDK but unused in 5.0.0-pre12;
            // we read it ourselves in updateNavigationUI for the manual arrival check.
            val options = MapLibreNavigationOptions(
                defaultMilestonesEnabled = true,
                // offRouteThresholdRadiusMeters = 50.0,
                metersRemainingTillArrival = 15.0,
                snapToRoute = false
                // snapping works in general but has sometimes strange back-and-forth behavour
                // probably related to: https://github.com/maplibre/maplibre-navigation-android/issues/67
            )
            navigation = AndroidMapLibreNavigation(
                context = applicationContext,
                locationEngine = locationEngine,
                options = options
            )

            // Setup progress listener
            navigation?.addProgressChangeListener { location, routeProgress ->
                // Track for off-route rerouting
                lastRouteProgress = routeProgress

                runOnUiThread {
                    try {
                        val androidLocation = location.toAndroidLocation()
                        updateNavigationUI(androidLocation, routeProgress)

                        // Skip location/camera updates (rotation) when stationary
                        if (androidLocation.speed > 0f) {
                            mapLibreMap?.locationComponent?.forceLocationUpdate(androidLocation)
                            updateCameraPosition(androidLocation)
                        }

                        // Update maneuver arrow
                        mapRouteArrow?.addUpcomingManeuverArrow(routeProgress)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in progress UI update: ${e.message}", e)
                    }
                }
            }

            // Voice instructions via SDK milestones
            navigation?.addMilestoneEventListener { routeProgress, instruction, milestone ->
                if (milestone is VoiceInstructionMilestone) {
                    val speechAnnouncement = SpeechAnnouncement.builder().voiceInstructionMilestone(milestone).build()
                    // Voice instructions are attached to the step *before* their maneuver -> upComingStep
                    val isArrivalVoice = routeProgress.currentLegProgress.upComingStep?.maneuver?.type == StepManeuver.Type.ARRIVE
                    if (isArrivalVoice) {
                        pendingArrivalAnnouncement = speechAnnouncement
                    } else {
                        speechPlayer?.play(speechAnnouncement)
                    }
                }
            }

            // Setup off-route listener
            navigation?.addOffRouteListener { location ->
                val progress = lastRouteProgress
                Log.i(TAG, "Off route detected at ${location.latitude}, ${location.longitude}, leg ${progress?.legIndex}")
                if (progress != null) {
                    routeFetcher?.findRouteFromRouteProgress(location, progress)
                }
            }

            // Draw route on map first so the location puck renders on top
            drawRoute(style, currentRoute!!)

            // Initialize maneuver arrow (above route layer)
            mapRouteArrow = MapRouteArrow(mapView, mapLibreMap!!, R.style.NavigationMapRoute, ROUTE_LAYER_ID)

            // Waypoint markers go above arrows; the puck added next will end up on top.
            drawWaypointMarkers(style)

            // Setup location component for navigation puck
            setupLocationComponent(style)

            // Start navigation
            navigation?.startNavigation(currentRoute!!)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize navigation: ${e.message}", e)
            failAndClose(Translation.tr("navigation_start_failed", "Could not start navigation: ${e.message}"))
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

        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup location component: ${e.message}", e)
            failAndClose(Translation.tr("waiting_for_gps", "Please enable GPS / Location Services"))
        }
    }

    private fun recenterCamera() {
        showRecenter = false
        mapLibreMap?.locationComponent?.apply {
            cameraMode = CameraMode.TRACKING_GPS
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

        // Add below arrow layers (if they exist from a previous draw), else below puck shadow
        val arrowCasingLayerId = "mapbox-navigation-arrow-shaft-casing-layer"
        val shadowLayerId = "mapbox-location-shadow-layer"
        if (style.getLayer(arrowCasingLayerId) != null) {
            style.addLayerBelow(routeLayer, arrowCasingLayerId)
        } else if (style.getLayer(shadowLayerId) != null) {
            style.addLayerBelow(routeLayer, shadowLayerId)
        } else {
            style.addLayer(routeLayer)
        }
    }

    // Permanent markers from the original request — red pin at destination, blue pins at any
    // intermediate VIA points. The request's "points" array is [origin, ..., destination].
    private fun drawWaypointMarkers(style: Style) {
        style.removeLayer(DEST_LAYER_ID)
        style.removeSource(DEST_SOURCE_ID)
        style.removeLayer(VIA_LAYER_ID)
        style.removeSource(VIA_SOURCE_ID)

        val points = requestJson?.optJSONArray("points") ?: return
        if (points.length() < 2) return

        fun ensureImage(id: String, drawableRes: Int) {
            if (style.getImage(id) == null) {
                ContextCompat.getDrawable(this, drawableRes)?.let { style.addImage(id, it) }
            }
        }
        fun pointAt(i: Int) = points.getJSONArray(i).let {
            org.maplibre.geojson.Point.fromLngLat(it.getDouble(0), it.getDouble(1))
        }
        fun markerLayer(id: String, sourceId: String, iconId: String) = SymbolLayer(id, sourceId).apply {
            setProperties(
                iconImage(iconId),
                iconAllowOverlap(true),
                iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            )
        }

        ensureImage(DEST_ICON_ID, R.drawable.ic_destination_red)
        style.addSource(GeoJsonSource(DEST_SOURCE_ID, pointAt(points.length() - 1)))
        style.addLayer(markerLayer(DEST_LAYER_ID, DEST_SOURCE_ID, DEST_ICON_ID))

        if (points.length() > 2) {
            ensureImage(VIA_ICON_ID, R.drawable.ic_destination_blue)
            val viaFeatures = (1 until points.length() - 1).map {
                org.maplibre.geojson.Feature.fromGeometry(pointAt(it))
            }
            style.addSource(GeoJsonSource(VIA_SOURCE_ID, org.maplibre.geojson.FeatureCollection.fromFeatures(viaFeatures)))
            style.addLayer(markerLayer(VIA_LAYER_ID, VIA_SOURCE_ID, VIA_ICON_ID))
        }
    }

    private fun updateNavigationUI(location: Location, routeProgress: RouteProgress) {
        // Update current step info
        val currentLegProgress = routeProgress.currentLegProgress
        val currentStepProgress = currentLegProgress.currentStepProgress
        val currentStep = currentStepProgress.step

        // The upcoming step's maneuver describes the next turn the user must make.
        // currentStep.maneuver describes how the user *entered* the current step (already done).
        val upcomingManeuver = currentLegProgress.upComingStep?.maneuver

        currentStep?.let { step ->
            val bannerInstruction = step.bannerInstructions?.firstOrNull()
            val instructionStr = bannerInstruction?.primary?.text
                ?: upcomingManeuver?.instruction
                ?: currentLegProgress.upComingStep?.name ?: ""
            instruction = instructionStr

            // Update turn icon based on upcoming maneuver
            val type = bannerInstruction?.primary?.type ?: upcomingManeuver?.type
            val modifier = bannerInstruction?.primary?.modifier ?: upcomingManeuver?.modifier
            val degrees = bannerInstruction?.primary?.degrees
            val isFinalLeg = routeProgress.legIndex >= (currentRoute?.legs?.size ?: 1) - 1
            turnIconRes = getManeuverIcon(type, modifier, degrees, isFinalLeg)

            // Show exit number for roundabouts
            roundaboutExit = if (isRoundaboutType(type)) upcomingManeuver?.exit else null

            // Distance to next maneuver (= remaining distance in current step)
            val distanceToNextManeuver = currentStepProgress.distanceRemaining
            distanceToTurn = Converters.formatDistance(distanceToNextManeuver, showDistanceInMiles, getLocale())

            // "Then" turn — show when the next maneuver is close and there's a sub instruction
            val sub = bannerInstruction?.sub
            thenTurnIconRes = if (sub != null && distanceToNextManeuver < 200) {
                getManeuverIcon(sub.type, sub.modifier, sub.degrees, isFinalLeg)
            } else {
                null
            }
        }

        // Update remaining distance and time
        val locale = getLocale()
        val distanceRemainingVal = routeProgress.distanceRemaining
        val durationRemaining = routeProgress.durationRemaining

        // implement finish navigation behavior as SDK has no built-in arrival event yet (metersRemainingTillArrival is declared  but unused)
        val arrivalThreshold = navigation?.options?.metersRemainingTillArrival ?: 40.0
        if (!arrived && pendingArrivalAnnouncement != null && distanceRemainingVal < arrivalThreshold) {
            arrived = true
            // Stop the engine so it no longer emits off-route / progress events that could
            // talk over the arrival announcement, and cancel any reroute fetch already in flight.
            navigation?.stopNavigation()
            routeFetcher?.cancelRouteCall()
            pendingArrivalAnnouncement?.let { speechPlayer?.play(it) }
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 5000)
        }

        remainingDistance = Converters.formatDistance(distanceRemainingVal, showDistanceInMiles, locale)
        remainingTime = Converters.formatDuration(durationRemaining, locale)

        // Calculate and display ETA
        val etaMillis = System.currentTimeMillis() + (durationRemaining * 1000).toLong()
        eta = Converters.formatTime(etaMillis, locale)

        // Update current speed
        currentSpeed = Converters.formatSpeed(location.speed, showDistanceInMiles).toString()
        speedUnit = Converters.getSpeedUnit(showDistanceInMiles)

        // Update speed limit from annotation
        val maxSpeedAnnotation = routeProgress.currentLegProgress?.currentLegAnnotation?.maxSpeed
        when {
            maxSpeedAnnotation?.none == true -> {
                speedLimit = null
                speedLimitUnlimited = true
            }
            maxSpeedAnnotation?.unknown == true || maxSpeedAnnotation == null -> {
                speedLimit = null
                speedLimitUnlimited = false
            }
            else -> {
                speedLimit = maxSpeedAnnotation.speed?.let { limit ->
                    Converters.convertSpeedLimit(limit, maxSpeedAnnotation.unit, showDistanceInMiles)
                }
                speedLimitUnlimited = false
            }
        }
    }

    private fun updateCameraPosition(location: Location) {
        mapLibreMap?.locationComponent?.apply {
            // Shift the focal point down so the puck sits in the lower third
            val topPadding = mapView.height * 0.25
            paddingWhileTracking(doubleArrayOf(0.0, topPadding, 0.0, 0.0))
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
        degrees: Double? = null,
        isFinalLeg: Boolean = true,
    ): Int {
        return when {
            type == StepManeuver.Type.ARRIVE ->
                if (isFinalLeg) R.drawable.ic_destination_red else R.drawable.ic_destination_blue
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
            modifier == ManeuverModifier.Type.UTURN -> R.drawable.ic_uturn
            modifier == ManeuverModifier.Type.STRAIGHT -> R.drawable.ic_straight
            else -> R.drawable.ic_straight
        }
    }


    private fun applyReroute(directionsResponse: DirectionsResponse) {
        try {
            val routes = directionsResponse.routes
            if (routes.isEmpty()) {
                Log.e(TAG, "No routes in reroute response")
                return
            }

            speechPlayer?.onOffRoute()
            speechPlayer?.play(SpeechAnnouncement.builder().announcement(Translation.tr("reroute", "Rerouting")).build())

            val newRoute = routes.first().copy(
                routeOptions = createWtfObject()
            )
            currentRoute = newRoute
            navigation?.startNavigation(newRoute)
            mapLibreMap?.style?.let { style ->
                drawRoute(style, newRoute)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply reroute: ${e.message}", e)
        }
    }

    private fun getLocale(): Locale = resources.configuration.locales[0]

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
