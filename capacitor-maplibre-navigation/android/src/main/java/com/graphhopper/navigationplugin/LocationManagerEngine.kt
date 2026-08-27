package com.graphhopper.navigationplugin

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.maplibre.navigation.core.location.Location
import org.maplibre.navigation.core.location.engine.LocationEngine
import org.maplibre.navigation.core.location.toLocation

/**
 * GMS-free LocationEngine backed directly by LocationManager, replacing the SDK's
 * MapLibreLocationEngine whose isBetterLocation filter caused a frozen puck and wrong
 * network fixes. Subscribes to a single provider, unfiltered: the system FUSED_PROVIDER
 * on API 31+ (GMS-quality fusion without a GMS dependency in the APK), raw GPS below.
 */
class LocationManagerEngine(
    context: Context,
    private val looper: Looper,
) : LocationEngine {

    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val provider: String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            locationManager.allProviders.contains(LocationManager.FUSED_PROVIDER) ->
            LocationManager.FUSED_PROVIDER
        locationManager.allProviders.contains(LocationManager.GPS_PROVIDER) ->
            LocationManager.GPS_PROVIDER
        else -> LocationManager.PASSIVE_PROVIDER
    }

    @SuppressLint("MissingPermission")
    override fun listenToLocation(request: LocationEngine.Request): Flow<Location> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                trySend(location.toLocation())
            }

            // No-op overrides required: default implementations need newer platform levels than minSdk
            @Deprecated("Deprecated in LocationListener")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        Log.i(TAG, "Requesting location updates from provider '$provider'")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Quality must be set explicitly: the legacy overload below implies balanced power,
            // for which the fused provider never engages GPS (one coarse fix every ~20s)
            val platformRequest = LocationRequest.Builder(request.minIntervalMilliseconds)
                .setQuality(
                    when (request.accuracy) {
                        LocationEngine.Request.Accuracy.HIGH -> LocationRequest.QUALITY_HIGH_ACCURACY
                        LocationEngine.Request.Accuracy.BALANCED -> LocationRequest.QUALITY_BALANCED_POWER_ACCURACY
                        else -> LocationRequest.QUALITY_LOW_POWER
                    }
                )
                .setMinUpdateDistanceMeters(request.minUpdateDistanceMeters)
                .build()
            val handler = Handler(looper)
            locationManager.requestLocationUpdates(provider, platformRequest, { handler.post(it) }, listener)
        } else {
            // Pre-31 the provider is GPS, where the implicit quality is irrelevant
            locationManager.requestLocationUpdates(
                provider, request.minIntervalMilliseconds, request.minUpdateDistanceMeters, listener, looper
            )
        }
        awaitClose { locationManager.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation(): Location? =
        locationManager.allProviders
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.toLocation()

    companion object {
        private const val TAG = "LocationManagerEngine"
    }
}
