/*
 * This file is a Kotlin port of MapRouteArrow from maplibre-navigation-android.
 * Original source: https://github.com/maplibre/maplibre-navigation-android
 *
 * Copyright (c) 2024 MapLibre contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.graphhopper.navigationplugin

import android.content.Context
import android.graphics.Color
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.common.toJvm
import org.maplibre.geojson.model.LineString
import org.maplibre.geojson.model.Point
import org.maplibre.navigation.core.routeprogress.RouteProgress
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws an arrow on the map indicating the upcoming maneuver direction.
 * This is a Kotlin port of the package-private MapRouteArrow from maplibre-navigation-android.
 */
class MapRouteArrow(
    private val mapView: MapView,
    private val mapLibreMap: MapLibreMap,
    styleRes: Int,
    private val aboveLayerId: String? = null
) {
    companion object {
        // Layer and source IDs
        private const val ARROW_SHAFT_SOURCE_ID = "mapbox-navigation-arrow-shaft-source"
        private const val ARROW_HEAD_SOURCE_ID = "mapbox-navigation-arrow-head-source"
        private const val ARROW_SHAFT_CASING_LAYER_ID = "mapbox-navigation-arrow-shaft-casing-layer"
        private const val ARROW_SHAFT_LAYER_ID = "mapbox-navigation-arrow-shaft-layer"
        private const val ARROW_HEAD_CASING_LAYER_ID = "mapbox-navigation-arrow-head-casing-layer"
        private const val ARROW_HEAD_LAYER_ID = "mapbox-navigation-arrow-head-layer"

        // Icon IDs
        private const val ARROW_HEAD_ICON = "mapbox-navigation-arrow-head-icon"
        private const val ARROW_HEAD_CASING_ICON = "mapbox-navigation-arrow-head-casing-icon"

        // Arrow dimensions
        private const val ARROW_HEAD_ICON_SIZE = 0.6f
        private const val ARROW_HEAD_CASING_ICON_SIZE = 0.75f
        private const val ARROW_SHAFT_LINE_WIDTH = 10f
        private const val ARROW_SHAFT_CASING_LINE_WIDTH = 16f

        // Zoom level for visibility
        private const val MIN_ARROW_ZOOM = 10.0f

        // Distance in meters to slice for arrow (each side of junction)
        private const val ARROW_SLICE_DISTANCE = 15.0
    }

    private val context: Context = mapView.context
    private var arrowColor: Int = Color.parseColor("#4A90D9")
    private var arrowBorderColor: Int = Color.WHITE
    private val arrowLayerIds = mutableListOf<String>()

    private var arrowShaftGeoJsonSource: GeoJsonSource? = null
    private var arrowHeadGeoJsonSource: GeoJsonSource? = null

    init {
        // Read colors from style
        val attrs = context.obtainStyledAttributes(styleRes, R.styleable.NavigationMapRoute)
        try {
            arrowColor = attrs.getColor(
                R.styleable.NavigationMapRoute_upcomingManeuverArrowColor,
                arrowColor
            )
            arrowBorderColor = attrs.getColor(
                R.styleable.NavigationMapRoute_upcomingManeuverArrowBorderColor,
                arrowBorderColor
            )
        } finally {
            attrs.recycle()
        }

        initialize()
    }

    private fun initialize() {
        val style = mapLibreMap.style ?: return

        initializeSources(style)
        initializeArrowLayers(style)
    }

    private fun initializeSources(style: Style) {
        // Create empty GeoJSON sources
        arrowShaftGeoJsonSource = GeoJsonSource(ARROW_SHAFT_SOURCE_ID)
        arrowHeadGeoJsonSource = GeoJsonSource(ARROW_HEAD_SOURCE_ID)

        style.addSource(arrowShaftGeoJsonSource!!)
        style.addSource(arrowHeadGeoJsonSource!!)
    }

    private fun initializeArrowLayers(style: Style) {
        // Add arrow head icons to style
        context.getDrawable(R.drawable.ic_arrow_head)?.let {
            style.addImage(ARROW_HEAD_ICON, it)
        }
        context.getDrawable(R.drawable.ic_arrow_head_casing)?.let {
            style.addImage(ARROW_HEAD_CASING_ICON, it)
        }

        // Shaft casing layer (blue border)
        val shaftCasingLayer = LineLayer(ARROW_SHAFT_CASING_LAYER_ID, ARROW_SHAFT_SOURCE_ID).apply {
            setProperties(
                lineColor(arrowColor),
                lineWidth(ARROW_SHAFT_CASING_LINE_WIDTH),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(Property.VISIBLE),
                lineOpacity(
                    Expression.step(
                        Expression.zoom(),
                        Expression.literal(0.0),
                        Expression.stop(MIN_ARROW_ZOOM, 1.0)
                    )
                )
            )
        }
        if (aboveLayerId != null && style.getLayer(aboveLayerId) != null) {
            style.addLayerAbove(shaftCasingLayer, aboveLayerId)
        } else {
            style.addLayer(shaftCasingLayer)
        }
        arrowLayerIds.add(ARROW_SHAFT_CASING_LAYER_ID)

        // Shaft layer (white fill)
        val shaftLayer = LineLayer(ARROW_SHAFT_LAYER_ID, ARROW_SHAFT_SOURCE_ID).apply {
            setProperties(
                lineColor(arrowBorderColor),
                lineWidth(ARROW_SHAFT_LINE_WIDTH),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                visibility(Property.VISIBLE),
                lineOpacity(
                    Expression.step(
                        Expression.zoom(),
                        Expression.literal(0.0),
                        Expression.stop(MIN_ARROW_ZOOM, 1.0)
                    )
                )
            )
        }
        style.addLayerAbove(shaftLayer, ARROW_SHAFT_CASING_LAYER_ID)
        arrowLayerIds.add(ARROW_SHAFT_LAYER_ID)

        // Arrow head casing layer (blue border - color baked in drawable)
        val headCasingLayer = SymbolLayer(ARROW_HEAD_CASING_LAYER_ID, ARROW_HEAD_SOURCE_ID).apply {
            setProperties(
                iconImage(ARROW_HEAD_CASING_ICON),
                iconSize(ARROW_HEAD_CASING_ICON_SIZE),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconRotate(Expression.get("bearing")),
                visibility(Property.VISIBLE),
                iconOpacity(
                    Expression.step(
                        Expression.zoom(),
                        Expression.literal(0.0),
                        Expression.stop(MIN_ARROW_ZOOM, 1.0)
                    )
                )
            )
        }
        style.addLayerAbove(headCasingLayer, ARROW_SHAFT_LAYER_ID)
        arrowLayerIds.add(ARROW_HEAD_CASING_LAYER_ID)

        // Arrow head layer (white fill - color baked in drawable)
        val headLayer = SymbolLayer(ARROW_HEAD_LAYER_ID, ARROW_HEAD_SOURCE_ID).apply {
            setProperties(
                iconImage(ARROW_HEAD_ICON),
                iconSize(ARROW_HEAD_ICON_SIZE),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconRotate(Expression.get("bearing")),
                visibility(Property.VISIBLE),
                iconOpacity(
                    Expression.step(
                        Expression.zoom(),
                        Expression.literal(0.0),
                        Expression.stop(MIN_ARROW_ZOOM, 1.0)
                    )
                )
            )
        }
        style.addLayerAbove(headLayer, ARROW_HEAD_CASING_LAYER_ID)
        arrowLayerIds.add(ARROW_HEAD_LAYER_ID)
    }

    /**
     * Updates the arrow to show the upcoming maneuver based on route progress.
     */
    fun addUpcomingManeuverArrow(routeProgress: RouteProgress) {
        val currentStepPoints = routeProgress.currentLegProgress?.currentStepProgress?.step?.geometry?.let {
            decodePolyline(it)
        } ?: return

        val upcomingStepPoints = routeProgress.currentLegProgress?.upComingStep?.geometry?.let {
            decodePolyline(it)
        } ?: return

        if (currentStepPoints.size < 2 || upcomingStepPoints.size < 2) {
            return
        }

        // Get arrow points from current and upcoming steps
        val arrowPoints = obtainArrowPointsFrom(currentStepPoints, upcomingStepPoints)
        if (arrowPoints.size < 2) {
            return
        }

        // Update arrow shaft
        updateArrowShaftWith(arrowPoints)

        // Update arrow head
        updateArrowHeadWith(arrowPoints)
    }

    private fun obtainArrowPointsFrom(
        currentStepPoints: List<Point>,
        upcomingStepPoints: List<Point>
    ): List<Point> {
        val arrowPoints = mutableListOf<Point>()

        // Slice from end of current step (reversed)
        val reversedCurrentPoints = currentStepPoints.reversed()
        val slicedCurrentPoints = lineSliceAlong(reversedCurrentPoints, ARROW_SLICE_DISTANCE)
        arrowPoints.addAll(slicedCurrentPoints.reversed())

        // Slice from start of upcoming step
        val slicedUpcomingPoints = lineSliceAlong(upcomingStepPoints, ARROW_SLICE_DISTANCE)
        // Skip first point to avoid duplicate at junction
        if (slicedUpcomingPoints.isNotEmpty()) {
            arrowPoints.addAll(slicedUpcomingPoints.drop(1))
        }

        return arrowPoints
    }

    /**
     * Slices a line to the specified distance in meters.
     */
    private fun lineSliceAlong(points: List<Point>, distanceMeters: Double): List<Point> {
        if (points.size < 2) return points

        val result = mutableListOf<Point>()
        var remainingDistance = distanceMeters

        result.add(points[0])

        for (i in 0 until points.size - 1) {
            val from = points[i]
            val to = points[i + 1]
            val segmentDistance = haversineDistance(from, to)

            if (segmentDistance <= remainingDistance) {
                result.add(to)
                remainingDistance -= segmentDistance
            } else {
                // Interpolate point at remaining distance
                val fraction = remainingDistance / segmentDistance
                val interpolatedPoint = interpolate(from, to, fraction)
                result.add(interpolatedPoint)
                break
            }
        }

        return result
    }

    /**
     * Calculates haversine distance between two points in meters.
     */
    private fun haversineDistance(p1: Point, p2: Point): Double {
        val R = 6371000.0 // Earth's radius in meters
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return R * c
    }

    /**
     * Interpolates between two points.
     */
    private fun interpolate(p1: Point, p2: Point, fraction: Double): Point {
        val lng = p1.longitude + (p2.longitude - p1.longitude) * fraction
        val lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
        return Point(lng, lat)
    }

    /**
     * Calculates bearing between two points in degrees.
     */
    private fun bearing(from: Point, to: Point): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun updateArrowShaftWith(arrowPoints: List<Point>) {
        val lineString = LineString(arrowPoints)
        // Pass geometry directly - GeoJsonSource accepts Geometry
        arrowShaftGeoJsonSource?.setGeoJson(lineString.toJvm())
    }

    private fun updateArrowHeadWith(arrowPoints: List<Point>) {
        if (arrowPoints.size < 2) return

        // Position at last point, bearing from second-to-last to last
        val lastPoint = arrowPoints.last()
        val secondToLast = arrowPoints[arrowPoints.size - 2]
        val bearingValue = bearing(secondToLast, lastPoint)

        // Build GeoJSON Feature string with bearing property
        val geoJson = """
            {
                "type": "Feature",
                "geometry": {
                    "type": "Point",
                    "coordinates": [${lastPoint.longitude}, ${lastPoint.latitude}]
                },
                "properties": {
                    "bearing": $bearingValue
                }
            }
        """.trimIndent()
        arrowHeadGeoJsonSource?.setGeoJson(geoJson)
    }

    /**
     * Decodes a polyline string into a list of Points.
     * Uses precision 6 (GraphHopper default).
     */
    private fun decodePolyline(encoded: String, precision: Int = 6): List<Point> {
        val factor = Math.pow(10.0, precision.toDouble())
        val len = encoded.length
        var index = 0
        var lat = 0
        var lng = 0
        val coordinates = mutableListOf<Point>()

        while (index < len) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            coordinates.add(Point(lng / factor, lat / factor))
        }

        return coordinates
    }

    /**
     * Removes arrow layers from the map.
     */
    fun removeArrow() {
        val style = mapLibreMap.style ?: return

        arrowLayerIds.forEach { layerId ->
            style.removeLayer(layerId)
        }

        arrowShaftGeoJsonSource?.let { style.removeSource(it.id) }
        arrowHeadGeoJsonSource?.let { style.removeSource(it.id) }
    }
}
