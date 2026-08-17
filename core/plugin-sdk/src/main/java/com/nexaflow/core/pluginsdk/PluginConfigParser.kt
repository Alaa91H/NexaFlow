package com.nexaflow.core.pluginsdk

import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thrown when the serialized plugin config bundle exceeds the Locale protocol
 * limit, so plugins fail fast in development instead of on a user's device.
 */
class PluginBundleTooLargeException(val sizeBytes: Int) :
    IllegalStateException("Plugin bundle is $sizeBytes bytes (max ${LocaleContract.MAX_BUNDLE_BYTES})")

/**
 * NexaFlow's Bundle ⇄ JSON ⇄ Map convention (see `docs/PLUGIN_SDK.md` §3).
 *
 * The raw protocol is primitive-typed; structured config is serialized as a
 * JSON object string under the [KEY_CONFIG] key, with [KEY_SDK_VERSION] as an
 * opt-in marker for future protocol bumps. The bundle itself never carries
 * custom objects, so every Locale-compatible host keeps working.
 */
object PluginConfigParser {

    const val KEY_CONFIG = "config"
    const val KEY_SDK_VERSION = "sdkVersion"
    const val SDK_VERSION = 1

    /**
     * Serializes [config] into a protocol bundle. Throws
     * [PluginBundleTooLargeException] when the serialized bundle exceeds the
     * 25 KB limit.
     */
    fun toBundle(config: Map<String, Any?>): Bundle {
        val bundle = Bundle().apply {
            putString(KEY_CONFIG, encode(config))
            putInt(KEY_SDK_VERSION, SDK_VERSION)
        }
        // serializedSize() is a hidden API; measure through a real Parcel, the
        // same way the framework computes the wire size of a bundle.
        val size = serializedSize(bundle)
        if (size > LocaleContract.MAX_BUNDLE_BYTES) {
            throw PluginBundleTooLargeException(size)
        }
        return bundle
    }

    /** Encodes [config] as JSON, failing fast with a clear message on bad values. */
    private fun encode(config: Map<String, Any?>): String = try {
        JSONObject(config).toString()
    } catch (e: org.json.JSONException) {
        throw IllegalArgumentException(
            "Plugin config contains a non-serializable value (primitives/Strings only)",
            e
        )
    }

    /** Real serialized byte size of a bundle (public-API Parcel round-trip). */
    private fun serializedSize(bundle: Bundle): Int {
        val parcel = android.os.Parcel.obtain()
        try {
            bundle.writeToParcel(parcel, 0)
            return parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    /** Rebuilds a config map from a protocol bundle (missing/invalid → empty). */
    fun fromBundle(bundle: Bundle?): Map<String, Any?> {
        val json = bundle?.getString(KEY_CONFIG) ?: return emptyMap()
        return parseJson(json)
    }

    /** Parses a JSON object string into a plain [Map]. Invalid JSON → empty. */
    fun parseJson(json: String): Map<String, Any?> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            jsonObjectToMap(root)
        }.getOrDefault(emptyMap())
    }

    /** Serializes [config] to a JSON object string (values are primitives). */
    fun toJson(config: Map<String, Any?>): String = JSONObject(config).toString()

    /**
     * Extracts the flat primitive extras of a legacy bundle into a map, so
     * plugins that stored loose extras (instead of the JSON convention) can
     * still be read. JSON [KEY_CONFIG] wins when both are present.
     */
    fun flattenBundle(bundle: Bundle?): Map<String, Any?> {
        if (bundle == null) return emptyMap()
        return buildMap {
            bundle.keySet().forEach { key ->
                if (key == KEY_CONFIG || key == KEY_SDK_VERSION) return@forEach
                when (val value = bundle.getLegacyValue(key)) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                    is Long -> put(key, value)
                    is Double -> put(key, value)
                    is Float -> put(key, value)
                    is Boolean -> put(key, value)
                    is Array<*> -> put(key, value.toList())
                    else -> Unit // non-primitive extras are ignored
                }
            }
        }
    }

    /**
     * Legacy loose extras may contain any supported primitive type. Bundle has
     * no public typed equivalent for inspecting an unknown value, so confine
     * the platform deprecation to this compatibility boundary.
     */
    @Suppress("DEPRECATION")
    private fun Bundle.getLegacyValue(key: String): Any? = get(key)

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> = buildMap {
        obj.keys().forEach { key ->
            put(key, jsonValue(obj.opt(key)))
        }
    }

    private fun jsonValue(value: Any?): Any? = when (value) {
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> (0 until value.length()).map { jsonValue(value.opt(it)) }
        JSONObject.NULL -> null
        else -> value
    }
}
