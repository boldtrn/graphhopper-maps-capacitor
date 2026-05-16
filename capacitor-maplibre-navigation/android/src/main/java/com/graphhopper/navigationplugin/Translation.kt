package com.graphhopper.navigationplugin

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Loads tr.json bundled from the graphhopper-maps submodule and resolves
 * translations against the device locale. Single source of truth: the web
 * app's translation file.
 *
 * Locale matching tries language_COUNTRY (e.g. de_DE), then bare language
 * (e.g. de), then any variant starting with the language. Falls back to
 * en_US, then to the caller-provided default if a key is missing or empty.
 */
object Translation {
    private var translations: Map<String, String> = emptyMap()
    private var fallback: Map<String, String> = emptyMap()

    fun init(context: Context, locale: Locale) {
        val json = context.assets.open("tr.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        fallback = root.optJSONObject("en_US")?.let(::toMap) ?: emptyMap()
        val matched = pickLocale(root.keys().asSequence().toList(), locale)
        translations = matched?.let(root::optJSONObject)?.let(::toMap) ?: emptyMap()
    }

    fun tr(key: String, default: String = key): String {
        translations[key]?.takeIf { it.isNotEmpty() }?.let { return it }
        fallback[key]?.takeIf { it.isNotEmpty() }?.let { return it }
        return default
    }

    private fun pickLocale(available: List<String>, locale: Locale): String? {
        val full = "${locale.language}_${locale.country}"
        return when {
            full in available -> full
            locale.language in available -> locale.language
            else -> available.firstOrNull { it.startsWith("${locale.language}_") }
        }
    }

    private fun toMap(obj: JSONObject): Map<String, String> {
        val map = mutableMapOf<String, String>()
        obj.keys().forEach { k -> map[k] = obj.optString(k) }
        return map
    }
}
