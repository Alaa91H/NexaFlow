package com.nexaflow.app

/** Compatibility entry point; admission uses the shared bounded token policy. */
internal fun deepLinkTokenAuthorized(presented: String?, stored: String?): Boolean =
    com.nexaflow.domain.security.ExternalAccessPolicy.authorized(stored, presented)
