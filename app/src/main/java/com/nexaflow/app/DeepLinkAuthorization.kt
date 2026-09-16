package com.nexaflow.app

import java.security.MessageDigest

/**
 * P0.2 deep-link authorization: the automation id in a `nexaflow://run-task/`
 * link is not an authorization — custom URI schemes have no verified
 * ownership, so any app can craft such a link. External execution requires a
 * per-task opt-in token (128-bit, user-minted, rotatable/revocable).
 *
 * This comparison is constant-time so a malicious caller cannot measure
 * byte-at-a-time timing differences to recover the stored token. A null or
 * empty stored token never matches: external execution is disabled by
 * default for every task.
 */
internal fun deepLinkTokenAuthorized(presented: String?, stored: String?): Boolean {
    if (presented.isNullOrEmpty() || stored.isNullOrEmpty()) return false
    return MessageDigest.isEqual(
        presented.toByteArray(Charsets.UTF_8),
        stored.toByteArray(Charsets.UTF_8)
    )
}
