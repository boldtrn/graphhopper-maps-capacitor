package com.graphhopper.navigationplugin

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.navigation.core.location.Location
import org.maplibre.navigation.core.models.DirectionsResponse
import org.maplibre.navigation.core.route.RouteFetcher
import org.maplibre.navigation.core.routeprogress.RouteProgress
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * GraphHopper implementation of RouteFetcher for rerouting.
 * Fetches routes from a GraphHopper /navigate endpoint.
 *
 * @param navigateUrl The URL to fetch routes from
 * @param requestJson The parsed request JSON (caller is responsible for parsing and error handling)
 * @param initialSkipCount Starting skip count
 */
class GraphHopperRouteFetcher(
    private val navigateUrl: String,
    private val requestJson: JSONObject
) : RouteFetcher() {

    companion object {
        private const val TAG = "GraphHopperRouteFetcher"
    }

    init {
        requestJson.put("type", "mapbox")
        requestJson.put("ch.disable", true)

        // Force empty snap_preventions to snap on everything, which is important for the current location
        requestJson.put("snap_preventions", JSONArray())

        // important: do not forget "intersection" otherwise maplibre will throw an exception:
        //        at org.maplibre.navigation.core.navigation.NavigationHelper.findCurrentIntersection(NavigationHelper.kt:378)
        requestJson.put("details", JSONArray().put("max_speed").put("intersection").put("distance").put("time").put("average_speed"))

        // The navigate endpoint does not want some parameters:
        requestJson.remove("elevation")
        requestJson.remove("points_encoded")
        requestJson.remove("points_encoded_multiplier")
    }

    private var currentThread: Thread? = null

    // Tracks how many original waypoints to skip.
    // Starts at 1 because original[0] is the start point, destinations begin at original[1].
    // After each successful reroute: skipCount += legIndex
    internal var skipCount = 1

    /**
     * Fetch initial route (no override for current location, no leg skipping).
     */
    fun fetchInitialRoute(onSuccess: (DirectionsResponse) -> Unit, onError: (Exception) -> Unit) {
        Thread {
            try {
                val responseJson = executeRequest(requestJson)

                if (responseJson != null) {
                    onSuccess(DirectionsResponse.fromJson(responseJson))
                } else {
                    onError(RuntimeException("Failed to fetch route"))
                }
            } catch (e: Exception) {
                onError(e)
            }
        }.start()
    }

    /**
     * Fetch reroute from current location, skipping already visited waypoints.
     */
    override fun findRouteFromRouteProgress(location: Location, routeProgress: RouteProgress) {
        cancelRouteCall()
        val legIndex = routeProgress.legIndex
        Log.i(TAG, "Reroute from leg $legIndex at ${location.latitude}, ${location.longitude}")

        currentThread = Thread {
            try {
                val responseJson = executeRequest(createRerouteRequestJson(
                    currentLng = location.longitude,
                    currentLat = location.latitude,
                    bearing = location.bearing?.toDouble(),
                    legIndex = legIndex
                ))

                if (Thread.currentThread().isInterrupted) return@Thread

                if (responseJson != null) {
                    // Update skip count for next reroute before notifying listeners
                    skipCount += legIndex
                    val directionsResponse = DirectionsResponse.fromJson(responseJson)
                    routeListeners.forEach { it.onResponseReceived(directionsResponse, routeProgress) }
                } else {
                    routeListeners.forEach { it.onErrorReceived(RuntimeException("Failed to fetch reroute")) }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Request cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Request error: ${e.message}", e)
                routeListeners.forEach { it.onErrorReceived(e) }
            }
        }.also { it.start() }
    }

    override fun cancelRouteCall() {
        currentThread?.interrupt()
        currentThread = null
    }

    /**
     * Create the request JSON for a reroute.
     * Uses skipCount + legIndex to determine which original waypoints are still ahead.
     */
    internal fun createRerouteRequestJson(
        currentLng: Double,
        currentLat: Double,
        bearing: Double?,
        legIndex: Int
    ): JSONObject {
        val originalPoints = requestJson.getJSONArray("points")
        val startIndex = skipCount + legIndex
        val newPoints = JSONArray().apply {
            // Current location as start
            put(JSONArray().apply {
                put(currentLng)
                put(currentLat)
            })
            // Remaining waypoints from original (cannot really use the SDK like routeUtils.calculateRemainingWaypoints(progress))
            for (i in startIndex until originalPoints.length()) {
                put(originalPoints.getJSONArray(i))
            }
        }
        Log.i(TAG, "Reroute with ${newPoints.length()} points (skipCount=$skipCount, legIndex=$legIndex)")

        // Build request with modified points
        val json = JSONObject(requestJson.toString())
        json.put("points", newPoints)
        if (bearing != null) {
            json.put("headings", JSONArray().put(bearing))
        }
        return json
    }

    private fun executeRequest(request: JSONObject): String? {
        val body = request.toString()
        val points = request.getJSONArray("points")
        Log.d(TAG, "Navigate request with ${points.length()} points: ${body}")
        val connection = URL(navigateUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }

            return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                Log.e(TAG, "Request failed: ${connection.responseCode} ${connection.responseMessage} - $errorBody")
                null
            }
        } finally {
            connection.disconnect()
        }
    }
}
