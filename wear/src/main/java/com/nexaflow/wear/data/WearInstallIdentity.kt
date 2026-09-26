package com.nexaflow.wear.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable identity for one NexaFlow installation on a watch.
 *
 * Wearable node ids can change after re-pairing, so automation configuration
 * must never use a node id as the durable watch identity.
 */
@Singleton
class WearInstallIdentity @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getOrCreateInstallId(): String = synchronized(INSTALL_ID_LOCK) {
        preferences.getString(KEY_INSTALL_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return@synchronized it }

        val installId = UUID.randomUUID().toString()
        check(preferences.edit().putString(KEY_INSTALL_ID, installId).commit()) {
            "Unable to persist Wear install identity"
        }
        installId
    }

    private companion object {
        /**
         * Multiple WearInstallIdentity instances can briefly overlap during
         * application/test startup. Synchronizing on the instance is not
         * sufficient: two instances could both observe an empty preference and
         * persist different UUIDs. One process-wide lock makes the read/create/
         * commit sequence atomic across every instance.
         */
        val INSTALL_ID_LOCK = Any()
        const val PREFERENCES_NAME = "nexaflow_wear_identity"
        const val KEY_INSTALL_ID = "install_id"
    }
}
