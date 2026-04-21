package com.graphhopper.navigationplugin

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for GraphHopperRouteFetcher waypoint skipping logic.
 *
 * Original waypoints: [A(0), B(1), C(2), D(3)]
 * Initial route legs: A→B(0), B→C(1), C→D(2)
 */
class GraphHopperRouteFetcherTest {

    private lateinit var requestJson: JSONObject

    @Before
    fun setup() {
        requestJson = JSONObject().apply {
            put("points", JSONArray().apply {
                put(JSONArray().apply { put(0.0); put(0.0) }) // A
                put(JSONArray().apply { put(1.0); put(1.0) }) // B
                put(JSONArray().apply { put(2.0); put(2.0) }) // C
                put(JSONArray().apply { put(3.0); put(3.0) }) // D
            })
            put("profile", "car")
        }
    }

    private fun createFetcher() = GraphHopperRouteFetcher("http://test", requestJson)

    private fun getPoints(json: JSONObject): List<Pair<Double, Double>> {
        val points = json.getJSONArray("points")
        return (0 until points.length()).map { i ->
            val p = points.getJSONArray(i)
            Pair(p.getDouble(0), p.getDouble(1))
        }
    }

    /**
     * Full scenario covering all cases:
     * 1. Initial route, off-route on leg 1 (B→C) -> [Current, C, D]
     * 2. New route, off-route on leg 0 (Current→C) -> [Current, C, D]
     * 3. Off-route on leg 1 (C→D) -> [Current, D]
     * 4. Only D remains, off-route on leg 0 -> [Current, D]
     */
    @Test
    fun fullScenario() {
        val fetcher = createFetcher()
        assertEquals(1, fetcher.skipCount)

        // 1. Off-route on leg 1 -> [Current, C, D]
        var result = fetcher.createRerouteRequestJson(99.0, 99.0, null, 1)
        var points = getPoints(result)
        assertEquals(3, points.size)
        assertEquals(Pair(2.0, 2.0), points[1]) // C
        assertEquals(Pair(3.0, 3.0), points[2]) // D
        // now mock off-route request with routeProgress.legIndex == 1
        fetcher.skipCount += 1
        assertEquals(2, fetcher.skipCount)

        // 2. Off-route on leg 0 -> still [Current, C, D]
        result = fetcher.createRerouteRequestJson(88.0, 88.0, null, 0)
        assertEquals(3, getPoints(result).size)
        fetcher.skipCount += 0
        assertEquals(2, fetcher.skipCount)

        // 3. Off-route on leg 1 -> [Current, D]
        result = fetcher.createRerouteRequestJson(77.0, 77.0, null, 1)
        points = getPoints(result)
        assertEquals(2, points.size)
        assertEquals(Pair(3.0, 3.0), points[1]) // D
        fetcher.skipCount += 1
        assertEquals(3, fetcher.skipCount)

        // 4. Off-route on leg 0 -> still [Current, D]
        result = fetcher.createRerouteRequestJson(66.0, 66.0, null, 0)
        assertEquals(2, getPoints(result).size)
    }

    @Test
    fun failureDoesNotChangeSkipCount() {
        val fetcher = createFetcher()
        fetcher.createRerouteRequestJson(99.0, 99.0, null, 1)
        assertEquals(1, fetcher.skipCount) // Not updated without explicit skipCount += legIndex
    }
}
