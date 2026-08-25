package com.nexaflow.core.rom

import android.content.Context
import android.os.IBinder
import android.telephony.TelephonyManager

/**
 * Direct ITelephony fallback used exclusively inside [UserShellService], whose
 * process runs as shell or root. OEM TelephonyShell implementations may omit
 * the standard `cmd phone` command or alter its output, whereas the telephony
 * binder remains the API behind that command on AOSP.
 *
 * All access stays reflective because ITelephony signatures differ across
 * Android releases and OEM builds. Failure is intentionally represented by
 * null/false so the caller can fall back to the reviewed TelephonyShell argv
 * rather than crashing or inventing a capability list.
 */
internal object PrivilegedTelephonyBridge {
    private const val PHONE_PACKAGE = "com.android.shell"

    fun readUserAllowedNetworkTypes(subscriptionId: Int): Long? {
        if (subscriptionId < 0) return null
        val service = telephonyService() ?: return null
        service.javaClass.methods
            .filter { it.name == "getAllowedNetworkTypesForReason" }
            .forEach { method ->
                val value = runCatching {
                    when {
                        method.parameterCount == 2 -> method.invoke(
                            service,
                            subscriptionId,
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER
                        )
                        method.parameterCount == 3 && method.parameterTypes[2] == String::class.java ->
                            method.invoke(
                                service,
                                subscriptionId,
                                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                                PHONE_PACKAGE
                            )
                        else -> null
                    }
                }.getOrNull()
                (value as? Number)?.toLong()
                    ?.and(NetworkModePolicy.BITMASK_SELECTABLE_CELLULAR)
                    ?.takeIf { it > 0L }
                    ?.let { return it }
            }
        return null
    }

    fun setUserAllowedNetworkTypes(subscriptionId: Int, allowedNetworkTypes: Long): Boolean {
        if (subscriptionId < 0) return false
        val service = telephonyService() ?: return false
        service.javaClass.methods
            .filter { it.name == "setAllowedNetworkTypesForReason" }
            .forEach { method ->
                val supportedSignature = method.parameterCount == 3 ||
                    (method.parameterCount == 4 && method.parameterTypes[3] == String::class.java)
                if (!supportedSignature) return@forEach
                val result = runCatching {
                    if (method.parameterCount == 3) {
                        method.invoke(
                            service,
                            subscriptionId,
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                            allowedNetworkTypes
                        )
                    } else {
                        method.invoke(
                            service,
                            subscriptionId,
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                            allowedNetworkTypes,
                            PHONE_PACKAGE
                        )
                    }
                }
                if (result.isFailure) return@forEach
                // AOSP returns Boolean while several OEM stubs are void. A
                // non-throwing void call is a dispatch, and the caller always
                // confirms it through a subsequent read before declaring success.
                if (result.getOrNull() == null || result.getOrNull() == Unit ||
                    result.getOrNull() == true
                ) return true
            }
        return false
    }

    private fun telephonyService(): Any? = runCatching {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, Context.TELEPHONY_SERVICE) as? IBinder
            ?: return null
        Class.forName("com.android.internal.telephony.ITelephony\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
    }.getOrNull()
}
