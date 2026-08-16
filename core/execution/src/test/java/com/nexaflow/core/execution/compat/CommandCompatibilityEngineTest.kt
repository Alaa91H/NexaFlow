package com.nexaflow.core.execution.compat

import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandCompatibilityEngineTest {

    private val engine = CommandCompatibilityEngine()

    private fun profile(
        sdk: Int = 36,
        family: RomFamily = RomFamily.AOSP,
        level: IntegrationLevel = IntegrationLevel.NORMAL,
        caps: Set<RomCapability> = emptySet(),
        perms: Set<String> = emptySet(),
        elevated: Boolean = false
    ) = DeviceProfile(
        sdk = sdk,
        romFamily = family,
        integrationLevel = level,
        capabilities = caps,
        grantedPermissions = perms,
        hasElevatedShell = elevated
    )

    // ── Universal commands work everywhere ────────────────────────────────
    @Test
    fun `universal commands supported on any rom`() {
        val p = profile()
        assertTrue(engine.isSupported(ActionType.SYSTEM_TOAST, p))
        assertTrue(engine.isSupported(ActionType.SYSTEM_CLIPBOARD_SET, p))
        assertTrue(engine.isSupported(TriggerType.TIME, p))
        assertTrue(engine.isSupported(TriggerType.BATTERY, p))
    }

    // ── Elevated-only commands hidden without root/shizuku ───────────────
    @Test
    fun `elevated commands unsupported without shell`() {
        val p = profile()
        assertFalse(engine.isSupported(ActionType.SYSTEM_REBOOT, p))
        assertFalse(engine.isSupported(ActionType.SYSTEM_SCREENSHOT, p))
        assertFalse(engine.isSupported(ActionType.SYSTEM_INPUT_TEXT, p))
    }

    @Test
    fun `elevated commands supported with root shell`() {
        val p = profile(elevated = true)
        assertTrue(engine.isSupported(ActionType.SYSTEM_REBOOT, p))
        assertTrue(engine.isSupported(ActionType.SYSTEM_INPUT_TAP, p))
    }

    // ── Capability-gated commands ────────────────────────────────────────
    @Test
    fun `status bar commands need status bar capability`() {
        val plain = profile()
        val capable = profile(caps = setOf(RomCapability.STATUS_BAR_CONTROL))
        assertFalse(engine.isSupported(ActionType.SYSTEM_EXPAND_STATUS_BAR, plain))
        assertTrue(engine.isSupported(ActionType.SYSTEM_EXPAND_STATUS_BAR, capable))
    }

    // ── Version gating (Android 17 = SDK 37) ─────────────────────────────
    @Test
    fun `version gating respects sdk bounds`() {
        // Commands that exist across versions remain supported on every SDK
        // when the required capability is present.
        val caps = setOf(RomCapability.WRITE_SETTINGS)
        val old = profile(sdk = 26, caps = caps)
        val new = profile(sdk = 37, caps = caps)
        assertTrue(engine.isSupported(ActionType.SYSTEM_BRIGHTNESS, old))
        assertTrue(engine.isSupported(ActionType.SYSTEM_BRIGHTNESS, new))
    }

    // ── Permission-gated triggers ────────────────────────────────────────
    @Test
    fun `permission-gated triggers hidden without permission`() {
        val noPerm = profile()
        val withPerm = profile(perms = setOf("android.permission.READ_CALENDAR"))
        assertFalse(engine.isSupported(TriggerType.CALENDAR, noPerm))
        assertTrue(engine.isSupported(TriggerType.CALENDAR, withPerm))
    }

    // ── Filtering drops unsupported items, keeps the rest ────────────────
    @Test
    fun `filter keeps supported and drops unsupported`() {
        val p = profile()
        val items = listOf(
            ActionType.SYSTEM_TOAST,   // universal → kept
            ActionType.SYSTEM_REBOOT,  // elevated → dropped
            ActionType.SYSTEM_OPEN_URL // universal → kept
        )
        val kept = engine.filterSupported(items, p) { it }
        assertEquals(listOf(ActionType.SYSTEM_TOAST, ActionType.SYSTEM_OPEN_URL), kept)
    }

    // ── Strategy resolution ──────────────────────────────────────────────
    @Test
    fun `strategy resolves to elevated for root-only commands`() {
        val spec = CommandSpec.elevated()
        assertEquals(ExecutionStrategy.UNSUPPORTED, engine.resolve(spec, profile()))
        assertEquals(ExecutionStrategy.ELEVATED, engine.resolve(spec, profile(elevated = true)))
    }

    // ── Unified duplicates are hidden from the pickers ───────────────────
    @Test
    fun `unified duplicates are hidden everywhere`() {
        val p = profile(elevated = true)
        // Even with full elevation the duplicate entries are hidden as if
        // they did not exist; the canonical command remains.
        assertFalse(engine.isSupported(ActionType.SYSTEM_VOLUME, p))
        assertFalse(engine.isSupported(ActionType.SYSTEM_RING_VOLUME, p))
        assertFalse(engine.isSupported(ActionType.APPLICATION_LAUNCH_APP, p))
        assertTrue(engine.isSupported(ActionType.SYSTEM_STREAM_VOLUME, p))
        assertTrue(engine.isSupported(ActionType.SYSTEM_OPEN_APP, p))
        assertEquals(ActionType.SYSTEM_STREAM_VOLUME, engine.canonical(ActionType.SYSTEM_VOLUME))
        assertEquals(ActionType.SYSTEM_OPEN_APP, engine.canonical(ActionType.APPLICATION_LAUNCH_APP))
    }

    // ── Catalog covers every declared type ───────────────────────────────
    @Test
    fun `catalog covers every action and trigger type`() {
        val missingActions = ActionType.entries.filter { CommandCatalog.specFor(it) == null }
        val missingTriggers = TriggerType.entries.filter { CommandCatalog.specFor(it) == null }
        assertTrue("Missing action specs: $missingActions", missingActions.isEmpty())
        assertTrue("Missing trigger specs: $missingTriggers", missingTriggers.isEmpty())
    }
}
