package com.nexaflow.app.agent

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.nexaflow.core.agentsecurity.AgentIdentityBinding
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAgentCallerIdentityResolver @Inject constructor(
    @ApplicationContext context: Context
) {
    private val packageManager = context.packageManager

    fun resolve(callingUid: Int, claimedPackageName: String): AgentIdentityBinding? {
        val packageName = claimedPackageName.trim()
        if (packageName.isBlank() || packageName.length > MAX_PACKAGE_NAME_LENGTH) {
            return null
        }
        val ownedPackages = packageManager.getPackagesForUid(callingUid)
            ?.toSet()
            .orEmpty()
        if (packageName !in ownedPackages) return null

        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            }
        }.getOrNull() ?: return null

        val signingInfo = packageInfo.signingInfo ?: return null
        val signatures = signingInfo.apkContentsSigners
            ?.map { it.toByteArray() }
            .orEmpty()
        if (signatures.isEmpty() || signatures.size > MAX_SIGNERS) return null

        return AgentIdentityBinding(
            packageName = packageName,
            signingCertificateSha256 = fingerprint(signatures)
        )
    }

    internal companion object {
        const val MAX_PACKAGE_NAME_LENGTH = 255
        const val MAX_SIGNERS = 8

        fun fingerprint(certificates: List<ByteArray>): String =
            certificates
                .map { certificate ->
                    MessageDigest.getInstance("SHA-256")
                        .digest(certificate)
                        .joinToString(separator = "") { byte ->
                            "%02x".format(byte.toInt() and 0xff)
                        }
                }
                .sorted()
                .joinToString(separator = ",")
    }
}
