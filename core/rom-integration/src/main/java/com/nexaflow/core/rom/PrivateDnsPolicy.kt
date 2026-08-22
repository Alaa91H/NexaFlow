package com.nexaflow.core.rom

/**
 * Validates Android Private DNS requests before they reach the elevated
 * settings path. This controls the device-wide Android Private DNS setting;
 * it is deliberately not presented as a per-app DNS tunnel.
 */
enum class PrivateDnsMode(val settingValue: String) {
    OFF("off"),
    AUTOMATIC("opportunistic"),
    HOSTNAME("hostname");

    companion object {
        fun fromConfig(value: String?): PrivateDnsMode? = entries.firstOrNull {
            it.name == value?.uppercase() || it.settingValue == value?.lowercase()
        }
    }
}

data class PrivateDnsRequest(
    val mode: PrivateDnsMode,
    val hostname: String
)

object PrivateDnsPolicy {
    const val MODE_KEY = "private_dns_mode"
    const val SPECIFIER_KEY = "private_dns_specifier"

    private val hostnamePattern = Regex(
        "^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+" +
            "[a-zA-Z](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$"
    )

    /**
     * `HOSTNAME` is the only mode that needs a provider name. The other modes
     * explicitly clear it so an old hostname cannot be mistaken for an active
     * configuration in a future mode transition.
     */
    fun request(modeValue: String?, hostnameValue: String?): Result<PrivateDnsRequest> {
        val mode = PrivateDnsMode.fromConfig(modeValue)
            ?: return Result.failure(IllegalArgumentException("Unsupported Private DNS mode"))
        val hostname = hostnameValue?.trim().orEmpty().lowercase()
        if (mode == PrivateDnsMode.HOSTNAME) {
            if (!hostnamePattern.matches(hostname)) {
                return Result.failure(
                    IllegalArgumentException("Private DNS provider must be a valid DNS hostname")
                )
            }
        }
        return Result.success(
            PrivateDnsRequest(
                mode = mode,
                hostname = if (mode == PrivateDnsMode.HOSTNAME) hostname else ""
            )
        )
    }
}
