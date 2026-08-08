package com.nexaflow.core.engine

import android.content.Context
import com.google.android.gms.auth.api.phone.SmsRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the SMS User Consent API (Play services).
 *
 * `startListening()` registers a one-shot consent request. When a matching
 * message arrives, the system shows a dialog; the consent result is delivered
 * to [SmsConsentReceiver] as an SMS_CONSENT_REQUEST broadcast whose
 * [SmsRetriever.EXTRA_CONSENT_INTENT] extra carries the message intent (or is
 * absent on denial). The receiver then re-arms via [startListening] for the
 * next message.
 */
@Singleton
class SmsConsentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Registers a consent request. `null` sender = any sender. Safe no-op on
     * devices without Play services or when the API is unavailable. Returns
     * true when the request was submitted (false = no Play services / API
     * error). Returned as a plain Boolean so the GMS Task type never leaks
     * beyond this module.
     */
    fun startListening(sender: String? = null): Boolean = runCatching {
        SmsRetriever.getClient(context).startSmsUserConsent(sender)
    }.isSuccess

    /** True when Play services is present on the device. */
    fun isAvailable(): Boolean = runCatching {
        com.google.android.gms.common.GoogleApiAvailability
            .getInstance()
            .isGooglePlayServicesAvailable(context) ==
            com.google.android.gms.common.ConnectionResult.SUCCESS
    }.getOrDefault(false)

    companion object {
        /**
         * Broadcast action Play services sends with the consent result.
         * Defined literally: the 18.x SDK no longer exposes it as a constant.
         */
        const val SMS_CONSENT_REQUEST = "com.google.android.gms.auth.api.phone.SMS_CONSENT_REQUEST"
    }
}
