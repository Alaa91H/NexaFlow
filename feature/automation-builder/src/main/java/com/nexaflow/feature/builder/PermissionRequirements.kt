package com.nexaflow.feature.builder

import com.nexaflow.core.execution.handler.HttpLanAccessRequirements
import com.nexaflow.domain.models.ActionType

/**
 * P0.6 — conditional `ACCESS_LOCAL_NETWORK` requirement.
 *
 * The Android 17 local-network runtime permission is only meaningful for
 * destinations that actually live on the device's local networks. Requesting
 * it for every HTTP action is over-scoped (privacy + Play review friction),
 * so the requirement is inferred from the action's configured URL:
 *
 * - public destination → no LAN permission (runtime permissions list is empty);
 * - private/loopback destination → LAN permission required;
 * - not statically decidable (host names, `{variables}`) → conditional: the
 *   permission is requested lazily at save/run time only when the resolved
 *   destination is private, and the runtime re-checks from the resolved URL
 *   before firing the request.
 *
 * Kept in the builder module so the permission catalog, the editor's
 * requirement row, and the runtime gate all read the same classification.
 */
object PermissionRequirements {

    /**
     * Static permission list for an HTTP action. Returns an empty list for
     * public destinations; non-HTTP action types fall through to the legacy
     * catalog (this inference only covers the URL-driven HTTP action).
     */
    fun runtimePermissionsForAction(
        type: ActionType,
        config: Map<String, String>
    ): List<String> = when (type) {
        ActionType.SYSTEM_HTTP_REQUEST ->
            if (HttpLanAccessRequirements.lanRequirementFor(config[HttpLanAccessRequirements.URL_KEY]) ==
                HttpLanAccessRequirements.Requirement.NotRequired
            ) {
                emptyList()
            } else {
                // Required or Conditional: the LAN permission remains part of
                // the request surface; the conditional row explains it is
                // only enforced for LAN-resolved destinations.
                listOf(android.Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        else -> PermissionCatalog.runtimePermissionsFor(type)
    }

    /**
     * Distinguishes the conditional case so the UI can label it honestly
     * ("needed only if the URL resolves to your local network") instead of
     * presenting an unconditional grant prompt.
     */
    fun isConditionalLanRequirement(
        type: ActionType,
        config: Map<String, String>
    ): Boolean = type == ActionType.SYSTEM_HTTP_REQUEST &&
        HttpLanAccessRequirements.lanRequirementFor(config[HttpLanAccessRequirements.URL_KEY]) ==
        HttpLanAccessRequirements.Requirement.Conditional
}
