package com.nexaflow.core.rom

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.nexaflow.core.rom.model.SystemControlResult

/** Package names only: no scheme, query or path may enter the store URI. */
private val STORE_PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")

/**
 * Opens the Play Store, optionally on one app's listing.
 *
 * The `SYSTEM_OPEN_PLAY_STORE_APP` action declares a required package, so a
 * configured package opens `market://details?id=`, falls back to the web
 * listing when no store client handles the market scheme, and refuses anything
 * that is not a plain package name. A blank package keeps the older behaviour
 * of opening the store home surface.
 */
object AppStoreLauncher {

    fun open(context: Context, packageName: String?): SystemControlResult {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isEmpty()) {
            return try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.android.vending")
                    ?: return SystemControlResult.fail("Play Store not installed")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                SystemControlResult.ok("Play Store opened")
            } catch (t: Throwable) {
                SystemControlResult.fail("Play Store failed: ${t.message}")
            }
        }
        if (!STORE_PACKAGE_NAME.matches(pkg)) {
            return SystemControlResult.fail("Invalid package name for the Play Store listing")
        }
        val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (marketIntent.resolveActivity(context.packageManager) != null) {
            return try {
                context.startActivity(marketIntent)
                SystemControlResult.ok("Play Store listing opened")
            } catch (t: Throwable) {
                SystemControlResult.fail("Play Store listing failed: ${t.message}")
            }
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$pkg".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(webIntent)
            SystemControlResult.ok("Play Store listing opened in the browser")
        } catch (t: Throwable) {
            SystemControlResult.fail("Play Store listing failed: ${t.message}")
        }
    }
}
