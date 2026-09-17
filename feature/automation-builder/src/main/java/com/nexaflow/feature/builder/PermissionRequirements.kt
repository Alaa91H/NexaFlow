package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.security.HttpAccessPolicy

/** All HTTP permission decisions use the explicit private-network opt-in. */
object PermissionRequirements {
    fun runtimePermissionsForAction(
        type: ActionType,
        config: Map<String, String>,
        sdk: Int = android.os.Build.VERSION.SDK_INT
    ): List<String> = if (type == ActionType.SYSTEM_HTTP_REQUEST)
        HttpAccessPolicy.runtimePermissions(config, sdk)
    else PermissionCatalog.runtimePermissionsFor(type)
}
