package com.nexaflow.core.rom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Settings.Secure enabled-services parsing that previously lived
 * duplicated (and with two latent bugs) in three modules. These exact string
 * formats are what the platform stores, so the tests pin the behaviour that
 * the accessibility/notification-access checks rely on.
 */
class PermissionStatusTest {

    @Test
    fun `single enabled component is matched exactly`() {
        assertTrue(
            PermissionStatus.containsComponent(
                "com.nexaflow.app/com.nexaflow.core.engine.AppTriggerAccessibilityService",
                "com.nexaflow.app/com.nexaflow.core.engine.AppTriggerAccessibilityService"
            )
        )
    }

    @Test
    fun `multiple enabled components are matched by exact equality`() {
        val flat = "com.a/com.a.ServiceA:com.nexaflow.app/com.nexaflow.core.engine.NotificationListener:com.b/com.b.ServiceB"
        assertTrue(
            PermissionStatus.containsComponent(
                flat,
                "com.nexaflow.app/com.nexaflow.core.engine.NotificationListener"
            )
        )
        assertTrue(
            PermissionStatus.containsComponent(
                flat,
                "com.a/com.a.ServiceA"
            )
        )
        assertTrue(
            PermissionStatus.containsComponent(
                flat,
                "com.b/com.b.ServiceB"
            )
        )
    }

    @Test
    fun `component is not present returns false`() {
        val flat = "com.a/com.a.ServiceA:com.b/com.b.ServiceB"
        assertFalse(
            PermissionStatus.containsComponent(
                flat,
                "com.nexaflow.app/com.nexaflow.core.engine.AppTriggerAccessibilityService"
            )
        )
    }

    @Test
    fun `empty string never contains a component`() {
        assertFalse(PermissionStatus.containsComponent("", "com.nexaflow.app/com.x"))
    }

    @Test
    fun `substring is not enough - needs full component equality`() {
        // Regression guard: a previous implementation used a packageName
        // contains-match, which (a) never matched the flattened component
        // string and (b) could match unrelated apps with a similar package.
        val flat = "com.nexaflow.app/com.nexaflow.core.engine.NotificationListener"
        assertFalse(PermissionStatus.containsComponent(flat, "com.nexaflow.app"))
        assertFalse(
            PermissionStatus.containsComponent(
                flat,
                "com.nexaflow.app/com.nexaflow.core.engine.Notification"
            )
        )
    }

    @Test
    fun `accessibility component constant matches manifest component`() {
        // The no-arg path builds ComponentName(context, ACCESSIBILITY_SERVICE_CLASS);
        // the constant must exactly equal what Settings.Secure stores.
        assertTrue(
            PermissionStatus.containsComponent(
                "com.nexaflow.app/${PermissionStatus.ACCESSIBILITY_SERVICE_CLASS}",
                "com.nexaflow.app/${PermissionStatus.ACCESSIBILITY_SERVICE_CLASS}"
            )
        )
    }

    @Test
    fun `notification listener constant matches manifest component`() {
        assertTrue(
            PermissionStatus.containsComponent(
                "com.nexaflow.app/${PermissionStatus.NOTIFICATION_LISTENER_CLASS}",
                "com.nexaflow.app/${PermissionStatus.NOTIFICATION_LISTENER_CLASS}"
            )
        )
    }
}
