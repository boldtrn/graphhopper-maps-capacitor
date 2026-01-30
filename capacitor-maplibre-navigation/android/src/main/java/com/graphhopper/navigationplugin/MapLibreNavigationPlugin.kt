package com.graphhopper.navigationplugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "MapLibreNavigation")
class MapLibreNavigationPlugin : Plugin() {
    companion object {
        const val EXTRA_NAVIGATE_URL = "navigate_url"
        const val EXTRA_REQUEST_BODY = "request_body"
        const val ACTION_STOP_NAVIGATION = "com.graphhopper.navigationplugin.STOP_NAVIGATION"
        const val ACTION_NAVIGATION_CLOSED = "com.graphhopper.navigationplugin.NAVIGATION_CLOSED"
    }

    private val navigationClosedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            notifyListeners("navigationClosed", JSObject())
        }
    }

    override fun load() {
        super.load()
        val filter = IntentFilter(ACTION_NAVIGATION_CLOSED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(navigationClosedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(navigationClosedReceiver, filter)
        }
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        try {
            context.unregisterReceiver(navigationClosedReceiver)
        } catch (_: Exception) {
        }
    }

    @PluginMethod
    fun startNavigation(call: PluginCall) {
        val navigateUrl = call.getString("navigateUrl") ?: return call.reject("navigateUrl is required")
        val requestBody = call.getString("requestBody") ?: return call.reject("requestBody is required")

        val intent = Intent(activity, NavigationActivity::class.java).apply {
            putExtra(EXTRA_NAVIGATE_URL, navigateUrl)
            putExtra(EXTRA_REQUEST_BODY, requestBody)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        call.resolve()
    }

    @PluginMethod
    fun stopNavigation(call: PluginCall) {
        context.sendBroadcast(Intent(ACTION_STOP_NAVIGATION))
        call.resolve()
    }
}
