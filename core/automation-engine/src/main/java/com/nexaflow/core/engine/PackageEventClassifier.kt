package com.nexaflow.core.engine

import android.content.Intent

/** Completed package lifecycle events accepted by the APP_INSTALLED trigger. */
internal enum class PackageTriggerEvent {
    INSTALLED,
    REMOVED,
    UPDATED
}

/** Classifies package broadcasts without treating replacement as uninstall plus install. */
internal object PackageEventClassifier {

    fun classify(action: String?, replacing: Boolean): PackageTriggerEvent? = when (action) {
        Intent.ACTION_PACKAGE_ADDED ->
            if (replacing) PackageTriggerEvent.UPDATED else PackageTriggerEvent.INSTALLED

        Intent.ACTION_PACKAGE_REMOVED ->
            if (replacing) null else PackageTriggerEvent.REMOVED

        else -> null
    }
}
