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
        const val EXTRA_NAVIGATE_URL = "navigate_url"
        const val EXTRA_REQUEST_BODY = "request_body"
        const val ACTION_STOP_NAVIGATION = "com.graphhopper.navigationplugin.STOP_NAVIGATION"
    }

    @PluginMethod
    fun startNavigation(call: PluginCall) {
        val navigateUrl = call.getString("navigateUrl") ?: return call.reject("navigateUrl is required")
        val requestBody = call.getString("requestBody") ?: return call.reject("requestBody is required")

        val intent = Intent(context, NavigationActivity::class.java).apply {
            putExtra(EXTRA_NAVIGATE_URL, navigateUrl)
            putExtra(EXTRA_REQUEST_BODY, requestBody)
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
        context.sendBroadcast(Intent(ACTION_STOP_NAVIGATION))
        call.resolve()
    }
}
