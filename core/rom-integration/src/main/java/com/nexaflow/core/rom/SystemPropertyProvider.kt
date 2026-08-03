package com.nexaflow.core.rom

import java.io.File

internal object SystemPropertyProvider {
    private val systemPropertiesClass by lazy {
        runCatching { Class.forName("android.os.SystemProperties") }.getOrNull()
    }

    private val buildPropCache by lazy {
        parseBuildProp()
    }

    fun get(name: String): String {
        try {
            val clazz = systemPropertiesClass
            if (clazz != null) {
                val method = clazz.getMethod("get", String::class.java, String::class.java)
                val value = method.invoke(null, name, "") as? String
                if (!value.isNullOrBlank()) return value
            }
        } catch (_: Throwable) {
            // Fall through to build.prop parsing.
        }
        return buildPropCache[name] ?: ""
    }

    private fun parseBuildProp(): Map<String, String> {
        return try {
            val file = File("/system/build.prop")
            if (!file.exists()) return emptyMap()
            file.bufferedReader().useLines { lines ->
                val result = HashMap<String, String>()
                lines.forEach { line ->
                    val index = line.indexOf('=')
                    if (index > 0) {
                        val key = line.substring(0, index).trim()
                        if (key.isNotEmpty()) {
                            result[key] = line.substring(index + 1).trim()
                        }
                    }
                }
                result
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}
