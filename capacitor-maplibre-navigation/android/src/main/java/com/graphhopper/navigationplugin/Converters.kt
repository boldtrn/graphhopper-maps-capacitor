package com.graphhopper.navigationplugin

import org.maplibre.navigation.core.models.SpeedLimit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Converters {

    /**
     * Format distance in meters to human-readable string.
     * Uses feet/miles when showDistanceInMiles is true, otherwise meters/km.
     */
    fun formatDistance(meters: Double, showDistanceInMiles: Boolean, locale: Locale): String {
        return if (showDistanceInMiles) {
            when {
                meters < 160.934 -> String.format(locale, "%d ft", (meters / 0.3048).toInt())
                meters >= 16093.4 -> String.format(locale, "%.0f mi", meters / 1609.34)
                else -> String.format(locale, "%.1f mi", meters / 1609.34)
            }
        } else {
            when {
                meters >= 10000 -> String.format(locale, "%.0f km", meters / 1000)
                meters >= 1000 -> String.format(locale, "%.1f km", meters / 1000)
                else -> String.format(locale, "%d m", meters.roundToInt())
            }
        }
    }

    /**
     * Format duration in seconds to human-readable string (e.g., "5 min", "1 h 30 min").
     */
    fun formatDuration(seconds: Double, locale: Locale): String {
        val totalMinutes = (seconds / 60).roundToInt()
        return when {
            totalMinutes >= 60 -> {
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                String.format(locale, "%d h %d min", hours, mins)
            }
            else -> String.format(locale, "%d min", totalMinutes)
        }
    }

    /**
     * Format timestamp to time string (e.g., "14:30").
     */
    fun formatTime(millis: Long, locale: Locale): String {
        val formatter = SimpleDateFormat("HH:mm", locale)
        return formatter.format(Date(millis))
    }

    /**
     * Convert speed from m/s to display value.
     * Returns speed in mph when showDistanceInMiles is true, otherwise km/h.
     */
    fun formatSpeed(metersPerSecond: Float, showDistanceInMiles: Boolean): Int {
        return if (showDistanceInMiles) {
            (metersPerSecond * 2.23694f).roundToInt() // m/s to mph
        } else {
            (metersPerSecond * 3.6f).roundToInt() // m/s to km/h
        }
    }

    /**
     * Get the speed unit string based on distance unit preference.
     */
    fun getSpeedUnit(showDistanceInMiles: Boolean): String {
        return if (showDistanceInMiles) "mph" else "km/h"
    }

    /**
     * Convert speed limit to the user's preferred unit.
     * If the speed limit unit doesn't match the user's preference, convert it.
     */
    fun convertSpeedLimit(speed: Int, unit: SpeedLimit.Unit?, showDistanceInMiles: Boolean): Int {
        val isSourceMph = unit == SpeedLimit.Unit.MPH
        return when {
            // Source is mph, user wants km/h -> convert mph to km/h
            isSourceMph && !showDistanceInMiles -> (speed * 1.60934).roundToInt()
            // Source is km/h, user wants mph -> convert km/h to mph
            !isSourceMph && showDistanceInMiles -> (speed / 1.60934).roundToInt()
            // No conversion needed
            else -> speed
        }
    }
}
