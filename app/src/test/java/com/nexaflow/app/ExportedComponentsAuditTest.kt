package com.nexaflow.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.w3c.dom.Element

/**
 * P0.7 — exported-components threat-model audit.
 *
 * Enumerates every `exported="true"` component across all source manifests
 * and enforces the trust-boundary policy: every exported component must
 * either be protected by a system permission gate or appear in the
 * reviewed justification allowlist below. A new exported component fails
 * CI until it is added here with a documented threat-model reason.
 */
@RunWith(JUnit4::class)
class ExportedComponentsAuditTest {

    private data class Exported(val manifest: String, val tag: String, val name: String)

    private val COMPONENT_TAGS =
        listOf("activity", "activity-alias", "service", "receiver", "provider")

    /** Module namespace per manifest, used to resolve relative `.Name` forms. */
    private val manifestPackage = mapOf(
        "src/main/AndroidManifest.xml" to "com.nexaflow.app",
        "../core/automation-engine/src/main/AndroidManifest.xml" to "com.nexaflow.core.engine",
        "../core/rom-integration/src/main/AndroidManifest.xml" to "com.nexaflow.core.rom",
        "../feature/widgets/src/main/AndroidManifest.xml" to "com.nexaflow.feature.widgets",
    )

    private fun manifestFiles(): List<File> =
        manifestPackage.keys.map { File(it) }.filter { it.exists() }

    /** Fully-qualified component name from the raw manifest attribute. */
    private fun resolveName(raw: String, manifest: File): String {
        if (!raw.startsWith(".")) return raw
        val pkg = manifestPackage[manifest.path.replace('\\', '/')]
            ?: error("Unknown manifest package for ${manifest.path}")
        return "$pkg$raw"
    }

    /**
     * Parses one manifest and returns (resolvedName, tag, element) for every
     * component declaration directly under <application>.
     */
    private fun components(manifest: File): List<Triple<String, String, Element>> {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(manifest)
        val appNode = doc.getElementsByTagName("application").item(0) as? Element
            ?: return emptyList() // library manifest without an application block
        val result = mutableListOf<Triple<String, String, Element>>()
        val children = appNode.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName in COMPONENT_TAGS) {
                val rawName = node.getAttribute("android:name")
                result += Triple(resolveName(rawName, manifest), node.tagName, node)
            }
        }
        return result
    }

    /** Components that passed threat-model review with an explicit reason. */
    private val reviewedExported: Map<String, String> = mapOf(
        // Launcher entry + the documented deep-link surface (P0.2 token-gated).
        "com.nexaflow.app.MainActivity" to
            "Launcher + nexaflow:// deep link; execution is token-gated (P0.2)",
        // Share-sheet target: opens SAF picker only; receives ACTION_SEND.
        "com.nexaflow.app.SaveBackupActivity" to
            "Share-sheet «Save locally» target; only opens the SAF write picker",
        // System-gated telephony receivers.
        "com.nexaflow.core.engine.SmsReceiver" to
            "System SMS delivery; guarded by android.permission.BROADCAST_SMS",
        "com.nexaflow.core.engine.SmsConsentReceiver" to
            "Play services SMS consent broadcast; guarded by BROADCAST_SMS",
        // Shizuku integration: required contract, protected by system permission.
        "rikka.shizuku.ShizukuProvider" to
            "Shizuku API contract; guarded by INTERACT_ACROSS_USERS_FULL",
        // System-bound services (system app binds, gated by bind permissions).
        "com.nexaflow.core.engine.NexaCallScreeningService" to
            "CallScreeningService; guarded by BIND_SCREENING_SERVICE",
        "com.nexaflow.feature.widgets.TaskTile1Service" to
            "QuickSettings tile; guarded by BIND_QUICK_SETTINGS_TILE",
        "com.nexaflow.feature.widgets.TaskTile2Service" to
            "QuickSettings tile; guarded by BIND_QUICK_SETTINGS_TILE",
        "com.nexaflow.feature.widgets.TaskTile3Service" to
            "QuickSettings tile; guarded by BIND_QUICK_SETTINGS_TILE",
        "com.nexaflow.feature.widgets.TaskTile4Service" to
            "QuickSettings tile; guarded by BIND_QUICK_SETTINGS_TILE",
        "com.nexaflow.feature.widgets.TaskTile5Service" to
            "QuickSettings tile; guarded by BIND_QUICK_SETTINGS_TILE",
        "com.nexaflow.feature.widgets.TaskTile6Service" to
            "QuickSettings tile; guarded by BIND_QUICK_SETTINGS_TILE",
        "com.nexaflow.feature.widgets.TaskTile7Service" to
            "QuickSettings tile; guarded by BIND_QUICK_SETTINGS_TILE",
        "com.nexaflow.feature.widgets.TaskTile8Service" to
            "QuickSettings tile; guarded by BIND_QUICK_SETTINGS_TILE",
    )

    @Test
    fun `every exported component is permission-gated or reviewed`() {
        val exported = mutableListOf<Exported>()
        val permissionGated = mutableMapOf<String, String>()

        for (manifest in manifestFiles()) {
            for ((name, tag, el) in components(manifest)) {
                if (el.getAttribute("android:exported") == "true") {
                    exported += Exported(manifest.path, tag, name)
                    val perm = el.getAttribute("android:permission")
                    if (perm.isNotBlank()) permissionGated[name] = perm
                }
            }
        }

        assertTrue(
            "No exported components found — manifest parsing is broken",
            exported.isNotEmpty()
        )

        val violations = exported.filter {
            it.name !in permissionGated && it.name !in reviewedExported
        }
        assertTrue(
            "New exported component(s) without threat-model review: " +
                violations.joinToString { "${it.tag}:${it.name} (${it.manifest})" } +
                ". Add them to the reviewedExported allowlist with a documented reason, " +
                "or protect them with a system permission.",
            violations.isEmpty()
        )

        // Every reviewed entry must still exist (drop stale allowlist entries).
        val existingNames = exported.map { it.name }.toSet()
        val stale = reviewedExported.keys.filter { it !in existingNames }
        assertTrue(
            "Stale allowlist entries (component no longer exported): $stale",
            stale.isEmpty()
        )
    }

    @Test
    fun `bind-permission gates actually protect their exported components`() {
        val expectedGates = mapOf(
            "com.nexaflow.core.engine.SmsReceiver" to "android.permission.BROADCAST_SMS",
            "com.nexaflow.core.engine.SmsConsentReceiver" to "android.permission.BROADCAST_SMS",
            "rikka.shizuku.ShizukuProvider" to "android.permission.INTERACT_ACROSS_USERS_FULL",
            "com.nexaflow.core.engine.NexaCallScreeningService" to
                "android.permission.BIND_SCREENING_SERVICE",
            "com.nexaflow.feature.widgets.TaskTile1Service" to
                "android.permission.BIND_QUICK_SETTINGS_TILE",
        )
        val found = mutableMapOf<String, String>()
        for (manifest in manifestFiles()) {
            for ((name, _, el) in components(manifest)) {
                if (el.getAttribute("android:exported") == "true") {
                    val perm = el.getAttribute("android:permission")
                    if (perm.isNotBlank()) found[name] = perm
                }
            }
        }
        for ((name, gate) in expectedGates) {
            assertEquals(
                "Component $name must be exported with permission gate $gate",
                gate,
                found[name]
            )
        }
    }
}
