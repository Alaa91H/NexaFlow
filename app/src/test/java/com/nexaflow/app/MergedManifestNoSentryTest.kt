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
 * removes both providers with tools:node="remove", so a DSN-less build must
 * boot.
 *
 * This test verifies the guarantee against the REAL merged manifest file
 * (Robolectric's queryContentProviders returns an empty registry here, so a
 * runtime query can only assert absence vacuously — parsing the merged
 * manifest itself is the authoritative check):
 *  - every <provider> is a known-safe one (no rogue auto-initializer that
 *    could break the DSN-less boot),
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

    /** Provider names that must be gone: crash the DSN-less build at boot. */
    private val forbiddenProviders = setOf(
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
        // separately below (all must route through androidx.startup and none
        // may auto-initialize WorkManager).
        "androidx.startup.InitializationProvider"
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

    @Test
    fun `sentry auto-init providers are stripped from the merged manifest`() {
        val doc = mergedManifestDocument()
        val providerNames = providerElements(doc)
            .map { it.getAttribute("android:name") }
            .filter { it.isNotBlank() }
            .joinToString()

        forbiddenProviders.forEach { forbidden ->
            assertFalse(
                "$forbidden must be removed from the merged manifest " +
                    "(it force-closes DSN-less builds); found providers: $providerNames",
                providerNames.contains(forbidden)
            )
        }

        // Belt and braces: nothing Sentry-* may survive the merge anywhere.
        assertFalse(
            "No Sentry provider may survive manifest merging; found: $providerNames",
            providerNames.contains("Sentry")
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

        // 2) WorkManager must NOT auto-initialize via androidx.startup — the
        //    app removes WorkManagerInitializer and bootstraps WorkManager
        //    manually in NexaFlowApplication. A second auto-initializer here
        //    is the same failure class as Sentry: it would init before the
        //    Application and could break the DSN-less boot.
        assertFalse(
            "androidx.work.WorkManagerInitializer must be removed from the " +
                "startup metadata (the app initializes WorkManager manually " +
                "in NexaFlowApplication); found: ${entries.keys.joinToString()}",
            entries.containsKey("androidx.work.WorkManagerInitializer")
        )
    }

    @Test
    fun `no sentry provider is registered in the runtime package manager`() {
        // Kept from the original test: documents the Robolectric-visible
        // state. Robolectric's provider registry is empty here, so absence
        // assertions hold trivially — the authoritative checks above parse
        // the merged manifest file itself.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providers = context.packageManager.queryContentProviders(null, 0, 0).orEmpty()
        val providerNames = providers.map { it.name }.joinToString()
        forbiddenProviders.forEach { forbidden ->
            assertFalse(
                "$forbidden must not be registered at runtime; found: $providerNames",
                providerNames.contains(forbidden)
            )
        }
    }
}
