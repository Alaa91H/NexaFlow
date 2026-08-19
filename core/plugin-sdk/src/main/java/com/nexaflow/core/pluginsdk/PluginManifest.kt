package com.nexaflow.core.pluginsdk

/**
 * Static compatibility declaration for a Locale-style plugin. It is metadata
 * only: NexaFlow continues to invoke the receiver through the existing Locale
 * protocol and never dynamically loads plugin code.
 */
data class PluginManifest(
    val packageName: String,
    val receiverClass: String,
    val displayName: String,
    val protocolVersion: Int = PluginConfigParser.SDK_VERSION,
    val minimumHostProtocolVersion: Int = PluginConfigParser.SDK_VERSION,
    val declaredActionIds: Set<String> = emptySet()
)

enum class PluginManifestIssue {
    INVALID_PACKAGE,
    INVALID_RECEIVER,
    BLANK_LABEL,
    UNSUPPORTED_PROTOCOL,
    INVALID_ACTION_ID,
    TOO_MANY_ACTIONS
}

data class PluginManifestValidation(val issues: List<PluginManifestIssue>) {
    val isValid: Boolean get() = issues.isEmpty()
}

/** Pure manifest validation used by discovery/import UI before enabling a plugin. */
object PluginManifestValidator {
    private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val className = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+")
    private val actionId = Regex("[A-Za-z][A-Za-z0-9_.-]{0,95}")
    private const val MAX_ACTIONS = 128

    fun validate(manifest: PluginManifest): PluginManifestValidation {
        val issues = buildList {
            if (!packageName.matches(manifest.packageName)) add(PluginManifestIssue.INVALID_PACKAGE)
            if (!className.matches(manifest.receiverClass)) add(PluginManifestIssue.INVALID_RECEIVER)
            if (manifest.displayName.isBlank() || manifest.displayName.length > 120) add(PluginManifestIssue.BLANK_LABEL)
            if (manifest.protocolVersion < 1 ||
                manifest.minimumHostProtocolVersion !in 1..PluginConfigParser.SDK_VERSION
            ) add(PluginManifestIssue.UNSUPPORTED_PROTOCOL)
            if (manifest.declaredActionIds.size > MAX_ACTIONS) add(PluginManifestIssue.TOO_MANY_ACTIONS)
            if (manifest.declaredActionIds.any { !actionId.matches(it) }) add(PluginManifestIssue.INVALID_ACTION_ID)
        }
        return PluginManifestValidation(issues)
    }
}
