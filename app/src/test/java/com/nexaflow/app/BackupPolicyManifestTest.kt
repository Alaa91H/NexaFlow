package com.nexaflow.app

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

/** Protects the app's no-backup policy for private automation data. */
@RunWith(RobolectricTestRunner::class)
class BackupPolicyManifestTest {
    private fun mergedManifest(): org.w3c.dom.Document {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val candidates = listOf(
            File(cwd, "build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"),
            File(cwd, "build/intermediates/merged_manifest/debug/AndroidManifest.xml"),
            File(cwd, "build/intermediates/merged_manifests/debug/processDebugMainManifest/AndroidManifest.xml")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("Merged debug manifest not found: ${candidates.joinToString()}")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private fun appResXml(name: String): org.w3c.dom.Document {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val file = File(cwd, "src/main/res/xml/$name")
        assertTrue("Expected backup policy resource $file", file.isFile)
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private fun application(doc: org.w3c.dom.Document): Element =
        doc.getElementsByTagName("application").item(0) as Element

    @Test
    fun `merged application disables backup and references both policy resources`() {
        val app = application(mergedManifest())
        assertEquals("false", app.getAttribute("android:allowBackup"))
        assertNotNull("Modern Android backup policy must be declared", app.getAttribute("android:dataExtractionRules").takeIf { it.isNotBlank() })
        assertNotNull("Legacy Android backup policy must be declared", app.getAttribute("android:fullBackupContent").takeIf { it.isNotBlank() })
        assertTrue(app.getAttribute("android:dataExtractionRules").endsWith("data_extraction_rules"))
        assertTrue(app.getAttribute("android:fullBackupContent").endsWith("backup_rules"))
    }

    @Test
    fun `backup policy excludes private data from cloud and device transfer`() {
        val modern = appResXml("data_extraction_rules.xml")
        val domains = setOf("root", "file", "database", "sharedpref", "external")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val parent = modern.getElementsByTagName(section).item(0) as Element
            val children = (0 until parent.childNodes.length)
                .mapNotNull { parent.childNodes.item(it) as? Element }
                .filter { it.tagName == "exclude" }
            val excludedDomains = children.filter { it.getAttribute("path") == "." }
                .map { it.getAttribute("domain") }.toSet()
            assertEquals("$section must exclude every private storage domain", domains, excludedDomains)
        }

        val legacy = appResXml("backup_rules.xml")
        val excludes = legacy.getElementsByTagName("exclude")
        val legacyDomains = (0 until excludes.length)
            .map { excludes.item(it) as Element }
            .filter { it.getAttribute("path") == "." }
            .map { it.getAttribute("domain") }.toSet()
        assertEquals("Legacy full backup must exclude every private storage domain", domains, legacyDomains)
        assertFalse("No inclusion rule may override the no-backup policy", legacy.getElementsByTagName("include").length > 0)
    }
}
