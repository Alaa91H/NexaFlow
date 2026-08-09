package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.smsDataStore by preferencesDataStore(
    name = "nexaflow_sms",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Android 17 (API 37) blocks OTP/verification SMS from apps targeting SDK 37
 * for 3 hours (SMS_RECEIVED_ACTION withheld + provider rows filtered). The
 * SMS User Consent API is the instant path that needs no READ_SMS: the user
 * approves each message in a system dialog and the app reads it immediately.
 *
 * [SmsSettings.userConsentEnabled] opts NexaFlow's SMS trigger into that path.
 * When off, the legacy SMS_RECEIVED receiver is used (works for non-OTP
 * messages and on older Android versions).
 */
data class SmsSettings(
    val userConsentEnabled: Boolean = false
)

class SmsPreferences(private val context: Context) {

    private val dataStore = context.smsDataStore

    val settings: Flow<SmsSettings> = dataStore.data.map { preferences ->
        SmsSettings(
            userConsentEnabled = preferences[KEY_USER_CONSENT] ?: false
        )
    }

    suspend fun setUserConsentEnabled(value: Boolean) {
        dataStore.edit { it[KEY_USER_CONSENT] = value }
    }

    private companion object {
        val KEY_USER_CONSENT = booleanPreferencesKey("sms_user_consent")
    }
}
