package com.nexaflow.data.backup

import java.io.InputStream

/** Scan before deserialization; caps parser work, string allocations and recursion. */
object BackupLimits {
    const val MAX_BYTES = 4 * 1024 * 1024
    fun read(input: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= MAX_BYTES) { "Backup too large" }
        }
        return output.toString("UTF-8")
    }

    fun accepts(text: String): Boolean {
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return false
        var depth = 0
        var quoted = false
        var escaped = false
        var stringLength = 0
        var elements = 0
        for (c in text) {
            if (quoted) {
                if (++stringLength > 65536) return false
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> { quoted = true; stringLength = 0 }
                '{', '[' -> { if (++depth > 32 || ++elements > 100000) return false }
                '}', ']' -> { if (--depth < 0) return false }
                ',', ':' -> { if (++elements > 100000) return false }
            }
        }
        return !quoted && depth == 0
    }
}
