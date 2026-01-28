package com.graphhopper.navigationplugin

import android.content.Intent
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "MapLibreNavigation")
class MapLibreNavigationPlugin : Plugin() {
    companion object {
        const val EXTRA_COORDINATES = "coordinates"
        const val EXTRA_BOUNDS = "bounds"
    }

    @PluginMethod
    fun startNavigation(call: PluginCall) {
        val coordinatesArray = call.getArray("coordinates") ?: return call.reject("coordinates is required")
        val boundsArray = call.getArray("bounds") ?: return call.reject("bounds is required")

        val sw = boundsArray.getJSONArray(0)
        val ne = boundsArray.getJSONArray(1)
        val boundsString = "${sw.getDouble(0)},${sw.getDouble(1)},${ne.getDouble(0)},${ne.getDouble(1)}"

        val intent = Intent(context, NavigationActivity::class.java).apply {
            putExtra(EXTRA_COORDINATES, coordinatesArray.toString())
            putExtra(EXTRA_BOUNDS, boundsString)
        }
        startActivityForResult(call, intent, "navigationResult")
    }

    @ActivityCallback
    private fun navigationResult(call: PluginCall, result: ActivityResult) {
        notifyListeners("navigationClosed", JSObject())
        call.resolve()
    }

    @PluginMethod
    fun stopNavigation(call: PluginCall) {
        context.sendBroadcast(Intent(NavigationActivity.ACTION_STOP_NAVIGATION))
        call.resolve()
    }
}
