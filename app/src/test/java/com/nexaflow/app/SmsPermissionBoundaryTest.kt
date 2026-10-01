package com.nexaflow.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Test

class SmsPermissionBoundaryTest {
    @Test fun appDoesNotRequestFullSmsInboxPermission() {
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val permissions = manifest.getElementsByTagName("uses-permission")
        val smsReadDeclared = (0 until permissions.length).any { index ->
            permissions.item(index).attributes?.getNamedItem("android:name")?.nodeValue == "android.permission.READ_SMS"
        }
        assertFalse(smsReadDeclared)
    }
}
