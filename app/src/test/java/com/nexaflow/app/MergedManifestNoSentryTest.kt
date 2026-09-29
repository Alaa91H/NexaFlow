package com.nexaflow.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element

/**
 * Boot-safety guarantee (P0-2, "open-crash fix"): sentry-android-core would
 * auto-register SentryInitProvider (and SentryPerformanceProvider) for
 * automatic initialization on every app start. Sentry then throws "DSN is
 * required" and force-closes the app when no NEXAFLOW_SENTRY_DSN was baked
 * into the build — before Application.onCreate even runs. The manifest
 * keeps both library providers explicitly disabled and sets
 * io.sentry.auto-init=false, so a DSN-less build must boot without any Sentry
 * code running before the user's opt-in.
 *
 * This test verifies the guarantee against the REAL merged manifest file
 * (Robolectric's queryContentProviders returns an empty registry here, so a
 * runtime query can only assert absence vacuously — parsing the merged
 * manifest itself is the authoritative check):
 *  - every <provider> is known and any Sentry provider is explicitly disabled
 *    (no rogue auto-initializer can break the DSN-less boot),
 *  - every initializer routed through androidx.startup carries the
 *    "androidx.startup" marker,
 *  - WorkManager does NOT auto-initialize — the app bootstraps it manually
 *    in [NexaFlowApplication], and a second auto-initializer is exactly the
 *    failure class this test exists to catch.
 */
@RunWith(RobolectricTestRunner::class)
// The app targets SDK 37 but Robolectric 4.17 sandboxes for SDK 36+ need
// Java 21 while this build runs Java 17; these tests are SDK-agnostic, so
// run them on 35 (the newest SDK that supports Java 17).
class MergedManifestNoSentryTest {

    /** Sentry providers may be merged, but Android must never instantiate them. */
    private val sentryProviders = setOf(
        "io.sentry.android.core.SentryInitProvider",
        "io.sentry.android.core.SentryPerformanceProvider"
    )

    /**
     * Providers the app legitimately ships. Everything else in the merged
     * manifest is by definition an auto-initializer the app does not own —
     * the exact class of failure that could break boot without a DSN.
     */
    private val knownSafeProviders = setOf(
        // Shizuku IPC bind provider; no initialization code of its own, the
        // library connects on demand from app code.
        "rikka.shizuku.ShizukuProvider",
        // Plain AndroidX FileProvider; purely declarative, no init code.
        "androidx.core.content.FileProvider",
        // The androidx.startup dispatcher; its initializer list is asserted
        // separately below (all must route through androidx.startup and
        // WorkManager's marker is explicitly disabled).
        "androidx.startup.InitializationProvider",
        // Sentry providers are retained only as disabled declarations so the
        // manifest merger stays warning-free in both app and unit-test APKs.
        "io.sentry.android.core.SentryInitProvider",
        "io.sentry.android.core.SentryPerformanceProvider"
    )

    /** Initializers the startup dispatcher may run (all safe without a DSN). */
    private val knownStartupInitializers = setOf(
        "androidx.emoji2.text.EmojiCompatInitializer",
        "androidx.lifecycle.ProcessLifecycleInitializer",
        "androidx.profileinstaller.ProfileInstallerInitializer"
    )

    private fun mergedManifestDocument(): org.w3c.dom.Document {
        // Gradle runs unit tests with the working directory set to the module
        // directory (app/), so the merged manifest of the debug variant is
        // reachable relative to it. A couple of AGP layouts are tried so a
        // minor AGP upgrade does not silently weaken the test.
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val candidates = listOf(
            File(cwd, "build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"),
            File(cwd, "build/intermediates/merged_manifest/debug/AndroidManifest.xml"),
            File(cwd, "build/intermediates/merged_manifests/debug/processDebugMainManifest/AndroidManifest.xml")
        )
        val file = candidates.firstOrNull { it.isFile } ?: throw AssertionError(
            "Merged manifest not found for the debug variant; tried: " +
                candidates.joinToString() + ". Run testDebugUnitTest (it " +
                "depends on manifest processing) before asserting boot safety."
        )
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        return builder.parse(file)
    }

    private fun providerElements(doc: org.w3c.dom.Document): List<Element> {
        val nodes = doc.getElementsByTagName("provider")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    /** Direct <meta-data> children of a provider element. */
    private fun metaDataChildren(provider: Element): List<Element> {
        return (0 until provider.childNodes.length)
            .mapNotNull { provider.childNodes.item(it) as? Element }
            .filter { it.tagName == "meta-data" }
    }

    private fun applicationMetaData(doc: org.w3c.dom.Document): Map<String, String> {
        val application = doc.getElementsByTagName("application").item(0) as Element
        return (0 until application.childNodes.length)
            .mapNotNull { application.childNodes.item(it) as? Element }
            .filter { it.tagName == "meta-data" }
            .associate {
                it.getAttribute("android:name") to it.getAttribute("android:value")
            }
    }

    @Test
    fun `READ_PHONE_STATE remains unbounded in the merged manifest`() {
        val doc = mergedManifestDocument()
        val permissions = doc.getElementsByTagName("uses-permission")
        val readPhoneState = (0 until permissions.length)
            .map { permissions.item(it) as Element }
            .singleOrNull {
                it.getAttribute("android:name") == "android.permission.READ_PHONE_STATE"
            }
            ?: throw AssertionError("READ_PHONE_STATE missing from merged manifest")

        assertFalse(
            "READ_PHONE_STATE must not regain maxSdkVersion from a transitive manifest",
            readPhoneState.hasAttribute("android:maxSdkVersion")
        )
    }

    @Test
    fun `sentry auto-init providers are disabled in the merged manifest`() {
        val doc = mergedManifestDocument()
        val providers = providerElements(doc)
            .associateBy { it.getAttribute("android:name") }

        sentryProviders.forEach { name ->
            val provider = providers[name]
                ?: throw AssertionError("$name missing from merged manifest")
            assertEquals(
                "$name must stay disabled until SentryReporter performs the opt-in init",
                "false",
                provider.getAttribute("android:enabled")
            )
        }

        val appMetadata = applicationMetaData(doc)
        assertEquals(
            "Sentry manifest auto-init must remain disabled",
            "false",
            appMetadata["io.sentry.auto-init"]
        )
    }

    @Test
    fun `every provider in the merged manifest is a known safe one`() {
        val doc = mergedManifestDocument()
        val providerNames = providerElements(doc)
            .map { it.getAttribute("android:name") }
            .filter { it.isNotBlank() }
            .toSet()

        val unknown = providerNames - knownSafeProviders
        assertTrue(
            "Merged manifest contains provider(s) the app does not own — " +
                "any of these could be a rogue auto-initializer that breaks " +
                "boot without a DSN. Unknown: ${unknown.joinToString()}. " +
                "Allowed: ${knownSafeProviders.joinToString()}",
            unknown.isEmpty()
        )
        assertTrue(
            "Expected the known providers to be present; missing: " +
                (knownSafeProviders - providerNames).joinToString(),
            (knownSafeProviders - providerNames).isEmpty()
        )
    }

    @Test
    fun `startup initializers all route through one androidx startup provider and none auto-initialize WorkManager`() {
        val doc = mergedManifestDocument()
        val startupProviders = providerElements(doc).filter {
            it.getAttribute("android:name") == "androidx.startup.InitializationProvider"
        }
        assertEquals(
            "The merged manifest must contain exactly one androidx.startup.InitializationProvider; " +
                "duplicate dispatchers can initialize startup components more than once.",
            1,
            startupProviders.size
        )
        val startup = startupProviders.single()
        assertNotNull(
            "androidx.startup.InitializationProvider must exist to host the " +
                "app's initializers",
            startup
        )

        val metadata = metaDataChildren(startup)
        val entries = metadata.associate { it.getAttribute("android:name") to it.getAttribute("android:value") }

        // 1) Every initializer must be declared with the "androidx.startup"
        //    marker — that routes it through the dispatcher where the app's
        //    tools:node="remove" overrides apply. A provider-style direct
        //    initializer would surface here as a different marker or as a
        //    separate <provider> (caught by the allowlist test).
        val misMarked = entries.filter { (name, value) ->
            (name in knownStartupInitializers ||
                name.endsWith("Initializer") ||
                name.contains("Sentry")) && value != "androidx.startup"
        }
        assertTrue(
            "Startup initializers must carry android:value=\"androidx.startup\" " +
                "so the app's removal overrides apply; mis-marked: " +
                misMarked.keys.joinToString(),
            misMarked.isEmpty()
        )

        // 2) WorkManager must NOT auto-initialize via androidx.startup. App
        //    Startup only discovers metadata whose value equals
        //    "androidx.startup"; the app overrides WorkManager's marker with a
        //    non-discoverable value and supplies Configuration.Provider itself.
        assertEquals(
            "WorkManager initializer must remain explicitly disabled in " +
                "androidx.startup metadata",
            "nexaflow.disabled",
            entries["androidx.work.WorkManagerInitializer"]
        )
    }

    @Test
    fun `runtime package manager never exposes an enabled sentry provider`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providers = context.packageManager.queryContentProviders(null, 0, 0).orEmpty()
        val sentry = providers.filter { it.name in sentryProviders }
        assertTrue(
            "Robolectric/runtime package manager must not expose an enabled " +
                "Sentry provider before opt-in",
            sentry.none { it.enabled }
        )
    }
}
