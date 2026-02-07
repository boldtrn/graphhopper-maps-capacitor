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
import org.maplibre.navigation.core.utils.Constants
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfMeasurement
import org.maplibre.turf.TurfMisc

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
        private const val ARROW_HEAD_ICON_SIZE = 0.45f
        private const val ARROW_HEAD_CASING_ICON_SIZE = 0.6f
        private const val ARROW_SHAFT_LINE_WIDTH = 10f
        private const val ARROW_SHAFT_CASING_LINE_WIDTH = 16f

        // Zoom level for visibility
        private const val MIN_ARROW_ZOOM = 10.0f

        // Distance in meters to slice for arrow (each side of junction)
        private const val ARROW_SLICE_DISTANCE = 25.0
        private const val ARROW_SHAFT_TRIM_DISTANCE = 3.0
    }

    private val context: Context = mapView.context
    private var arrowColor: Int = Color.WHITE
    private var arrowBorderColor: Int = Color.BLACK
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

        // Shaft casing layer (white border)
        val shaftCasingLayer = LineLayer(ARROW_SHAFT_CASING_LAYER_ID, ARROW_SHAFT_SOURCE_ID).apply {
            setProperties(
                lineColor(arrowBorderColor),
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

        // Shaft layer (blue fill)
        val shaftLayer = LineLayer(ARROW_SHAFT_LAYER_ID, ARROW_SHAFT_SOURCE_ID).apply {
            setProperties(
                lineColor(arrowColor),
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

        // Arrow head casing layer (white border - color baked in drawable)
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

        // Arrow head layer (blue fill - color baked in drawable)
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
        val currentGeometry = routeProgress.currentLegProgress?.currentStepProgress?.step?.geometry ?: return
        val upcomingGeometry = routeProgress.currentLegProgress?.upComingStep?.geometry ?: return

        // Decode polylines using LineString constructor
        val currentLine = LineString(currentGeometry, Constants.PRECISION_6)
        val upcomingLine = LineString(upcomingGeometry, Constants.PRECISION_6)

        if (currentLine.coordinates.size < 2 || upcomingLine.coordinates.size < 2) {
            return
        }

        // Convert to JVM types for Turf operations
        val currentLineJvm = currentLine.toJvm() as org.maplibre.geojson.LineString
        val upcomingLineJvm = upcomingLine.toJvm() as org.maplibre.geojson.LineString

        // Slice from end of current step
        val currentLength = TurfMeasurement.length(currentLineJvm, TurfConstants.UNIT_METERS)
        val sliceStart = maxOf(0.0, currentLength - ARROW_SLICE_DISTANCE)
        val slicedCurrentJvm = TurfMisc.lineSliceAlong(currentLineJvm, sliceStart, currentLength, TurfConstants.UNIT_METERS)

        // Slice from start of upcoming step
        val slicedUpcomingJvm = TurfMisc.lineSliceAlong(upcomingLineJvm, 0.0, ARROW_SLICE_DISTANCE, TurfConstants.UNIT_METERS)

        // Combine points for arrow
        val arrowPoints = mutableListOf<org.maplibre.geojson.Point>()
        arrowPoints.addAll(slicedCurrentJvm.coordinates())
        // Skip first point of upcoming to avoid duplicate at junction
        val upcomingCoords = slicedUpcomingJvm.coordinates()
        if (upcomingCoords.size > 1) {
            arrowPoints.addAll(upcomingCoords.drop(1))
        }

        if (arrowPoints.size < 2) {
            return
        }

        // Update arrow shaft (trim end so it doesn't poke through the arrow head)
        val fullShaftLine = org.maplibre.geojson.LineString.fromLngLats(arrowPoints)
        val shaftLength = TurfMeasurement.length(fullShaftLine, TurfConstants.UNIT_METERS)
        val trimmedEnd = maxOf(0.0, shaftLength - ARROW_SHAFT_TRIM_DISTANCE)
        val arrowLineString = if (trimmedEnd > 0.0) {
            TurfMisc.lineSliceAlong(fullShaftLine, 0.0, trimmedEnd, TurfConstants.UNIT_METERS)
        } else {
            fullShaftLine
        }
        arrowShaftGeoJsonSource?.setGeoJson(arrowLineString)

        // Update arrow head with bearing
        val lastPoint = arrowPoints.last()
        val secondToLast = arrowPoints[arrowPoints.size - 2]
        val bearingValue = TurfMeasurement.bearing(secondToLast, lastPoint)

        val geoJson = """
            {
                "type": "Feature",
                "geometry": {
                    "type": "Point",
                    "coordinates": [${lastPoint.longitude()}, ${lastPoint.latitude()}]
                },
                "properties": {
                    "bearing": $bearingValue
                }
            }
        """.trimIndent()
        arrowHeadGeoJsonSource?.setGeoJson(geoJson)
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
