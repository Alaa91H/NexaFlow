package com.nexaflow.core.rom.model

data class SystemControlResult(
    val success: Boolean,
    val message: String
) {
    companion object {
        fun ok(message: String) = SystemControlResult(true, message)
        fun fail(message: String) = SystemControlResult(false, message)
    }
}
