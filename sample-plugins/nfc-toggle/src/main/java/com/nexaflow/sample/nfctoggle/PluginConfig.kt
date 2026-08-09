package com.nexaflow.sample.nfctoggle

import android.os.Bundle
import org.json.JSONObject

/**
 * The NexaFlow bundle ⇄ JSON convention, implemented here with ONLY org.json
 * (which ships inside Android — no third-party JSON library needed).
 *
 * The raw Locale protocol is primitive-typed; structured config is carried as
 * a JSON object string under [LocaleProtocol.KEY_CONFIG]. This keeps the bundle
 * compatible with every Locale host while giving the plugin a real schema.
 */
object PluginConfig {

    /**
     * Serializes [config] into a protocol bundle. Throws
     * [IllegalArgumentException] for non-serializable values.
     */
    fun toBundle(config: Map<String, Any?>): Bundle = Bundle().apply {
        putString(LocaleProtocol.KEY_CONFIG, toJson(config))
        putInt(LocaleProtocol.KEY_SDK_VERSION, LocaleProtocol.SDK_VERSION)
    }

    /** Serializes [config] as a JSON object string (primitives/Strings only). */
    fun toJson(config: Map<String, Any?>): String = try {
        JSONObject(config).toString()
    } catch (e: org.json.JSONException) {
        throw IllegalArgumentException(
            "Plugin config contains a non-serializable value (primitives/Strings only)",
            e
        )
    }

    /** Rebuilds a config map from a protocol bundle (missing/invalid → empty). */
    fun fromBundle(bundle: Bundle?): Map<String, Any?> {
        val json = bundle?.getString(LocaleProtocol.KEY_CONFIG) ?: return emptyMap()
        return parseJson(json)
    }

    /**
     * Parses a JSON object string into a plain [Map]. Invalid JSON → empty.
     *
     * Values are primitives/Strings only (the protocol rule): a nested object
     * surfaces as its raw `JSONObject` rather than a nested [Map] — keep
     * configs flat, exactly like `PluginConfigParser` in `core/plugin-sdk`.
     */
    fun parseJson(json: String): Map<String, Any?> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            buildMap { root.keys().forEach { key -> put(key, root.opt(key)) } }
        }.getOrDefault(emptyMap())
    }
}
