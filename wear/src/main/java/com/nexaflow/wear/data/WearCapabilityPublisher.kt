package com.nexaflow.wear.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.nexaflow.core.wearprotocol.WearCapability
import com.nexaflow.core.wearprotocol.WearCapabilitySnapshot
import com.nexaflow.core.wearprotocol.WearProtocol as SharedWearProtocol
import com.nexaflow.core.wearprotocol.WearProtocolJson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/**
 * Publishes the watch's durable identity and implemented protocol capabilities.
 *
 * The snapshot is a DataItem, not a MessageClient message, so it remains
 * available across temporary disconnects and can bootstrap the phone registry
 * after process death.
 */
@Singleton
class WearCapabilityPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataClient: DataClient,
    private val installIdentity: WearInstallIdentity,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            publishNow()
        }
    }

    suspend fun publishNow(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val now = System.currentTimeMillis()
            val snapshot = WearCapabilitySnapshot(
                watchInstallId = installIdentity.getOrCreateInstallId(),
                capabilities = IMPLEMENTED_CAPABILITIES,
                deviceName = Build.MODEL,
                appVersionName = packageInfo.versionName,
                appVersionCode = packageInfo.longVersionCode,
                wearOsSdk = Build.VERSION.SDK_INT,
                updatedAtEpochMs = now,
            )
            val request = PutDataMapRequest
                .create(SharedWearProtocol.PATH_CAPABILITIES_V1)
                .apply {
                    dataMap.putString(
                        SharedWearProtocol.KEY_PAYLOAD,
                        WearProtocolJson.format.encodeToString(snapshot),
                    )
                    dataMap.putLong(SharedWearProtocol.KEY_UPDATED_AT, now)
                    dataMap.putInt(
                        SharedWearProtocol.KEY_PROTOCOL_VERSION,
                        SharedWearProtocol.CURRENT_VERSION,
                    )
                    dataMap.putString(
                        SharedWearProtocol.KEY_MESSAGE_ID,
                        snapshot.watchInstallId,
                    )
                }
                .asPutDataRequest()
                .setUrgent()

            dataClient.putDataItem(request).await()
            true
        }.getOrElse { error ->
            Log.w(TAG, "Failed to publish Wear capability snapshot", error)
            false
        }
    }

    private companion object {
        const val TAG = "WearCapabilities"

        val IMPLEMENTED_CAPABILITIES = setOf(
            WearCapability.PROTOCOL_V1,
            WearCapability.AUTOMATION_SYNC,
            WearCapability.RUN_AUTOMATION,
            WearCapability.TOGGLE_AUTOMATION,
        )
    }
}
