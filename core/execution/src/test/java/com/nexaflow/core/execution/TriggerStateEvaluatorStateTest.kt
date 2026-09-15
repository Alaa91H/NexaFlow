package com.nexaflow.core.execution

import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Manual-gate fix for DEVICE (Bluetooth events), BLUETOOTH_DEVICE and
 * APPLICATION triggers: these types previously fell through
 * `triggerSatisfied`'s fail-closed catch-all, so the manual-run gate reported
 * "could not verify" for conditions that are fully verifiable from public
 * platform state (bonded-device link state, radio state, foreground app).
 * These tests pin the new evaluators:
 *
 *  - pure decision functions keep Unknown only for genuinely unreadable
 *    states and return definitive booleans otherwise;
 *  - Bluetooth radio-off resolves definitively for both event polarities,
 *    mirroring BluetoothMonitor's radio-off = immediate-disconnect rule;
 *  - the manual-gate classification sets include the three fixed types
 *    (verified false → Unsatisfied, unreadable → Unknown).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TriggerStateEvaluatorStateTest {

    // ---- Pure decision functions ----

    @Test
    fun deviceEventsMapToCurrentState() {
        assertTrue(
            TriggerStateEvaluator.deviceEventSatisfied("SCREEN_ON", screenOn = true, charging = false, wiredHeadset = false)
        )
        assertFalse(
            TriggerStateEvaluator.deviceEventSatisfied("SCREEN_ON", screenOn = false, charging = false, wiredHeadset = false)
        )
        assertTrue(
            TriggerStateEvaluator.deviceEventSatisfied("POWER_DISCONNECTED", screenOn = false, charging = false, wiredHeadset = false)
        )
        assertTrue(
            TriggerStateEvaluator.deviceEventSatisfied("HEADSET_CONNECTED", screenOn = false, charging = false, wiredHeadset = true)
        )
        // Unrecognized events are definitively false (gate-reported).
        assertFalse(
            TriggerStateEvaluator.deviceEventSatisfied("UNRECOGNIZED", screenOn = true, charging = true, wiredHeadset = false)
        )
    }

    @Test
    fun bluetoothEventDecisionMirrorsMonitorSemantics() {
        // A live link satisfies the CONNECTED-wait and blocks the DISCONNECTED-wait.
        assertTrue(TriggerStateEvaluator.bluetoothEventSatisfied(true, TriggerStateEvaluator.BluetoothDeviceState.CONNECTED))
        assertFalse(TriggerStateEvaluator.bluetoothEventSatisfied(false, TriggerStateEvaluator.BluetoothDeviceState.CONNECTED))
        // BONDING_OR_ONLINE (bonded, radio on, not linked) and GONE_OR_RADIO_OFF
        // block a CONNECTED-wait and satisfy a DISCONNECTED-wait.
        assertFalse(TriggerStateEvaluator.bluetoothEventSatisfied(true, TriggerStateEvaluator.BluetoothDeviceState.BONDING_OR_ONLINE))
        assertTrue(TriggerStateEvaluator.bluetoothEventSatisfied(false, TriggerStateEvaluator.BluetoothDeviceState.BONDING_OR_ONLINE))
        assertTrue(TriggerStateEvaluator.bluetoothEventSatisfied(false, TriggerStateEvaluator.BluetoothDeviceState.GONE_OR_RADIO_OFF))
        assertFalse(TriggerStateEvaluator.bluetoothEventSatisfied(true, TriggerStateEvaluator.BluetoothDeviceState.GONE_OR_RADIO_OFF))
    }

    @Test
    fun appForegroundDecisionOverConfiguredSet() {
        val configured = setOf("com.pay.app", "com.music.app")
        assertTrue(TriggerStateEvaluator.applicationEventSatisfied(configured, "com.pay.app"))
        assertFalse(TriggerStateEvaluator.applicationEventSatisfied(configured, "com.other.app"))
        // No packages configured = any app satisfies.
        assertTrue(TriggerStateEvaluator.applicationEventSatisfied(emptySet(), "com.anything.app"))
    }

    // ---- Manual-gate classification (Robolectric: real service plumbing) ----

    @Test
    fun gateReportsUnsatisfiedWhenRadioOffForConnectWait() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Robolectric's BluetoothAdapter defaults to radio-off, so a
        // CONNECTED-wait resolves definitively Unsatisfied (was "could not
        // verify" before the fix).
        assertEquals(
            ConditionResult.Unsatisfied,
            runBlocking {
                TriggerStateEvaluator.evaluateAsync(
                    context,
                    listOf(Trigger(TriggerType.DEVICE, mapOf("event" to "BLUETOOTH_CONNECTED")))
                )
            }
        )
    }

    @Test
    fun gateReportsSatisfiedWhenRadioOffForDisconnectWait() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Radio-off = device gone = the disconnected condition already holds.
        // This is the exact live case: a task waiting for a Bluetooth device
        // to disconnect while the radio is off must run on a manual tap.
        assertEquals(
            ConditionResult.Satisfied,
            runBlocking {
                TriggerStateEvaluator.evaluateAsync(
                    context,
                    listOf(Trigger(TriggerType.DEVICE, mapOf("event" to "BLUETOOTH_DISCONNECTED")))
                )
            }
        )
    }

    @Test
    fun gateReportsUnsatisfiedWhenConfiguredDeviceIsMissing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Radio on with an empty bonded set: the configured device cannot be
        // linked — a definitive Unsatisfied, not "could not verify".
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        val manager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
        org.junit.Assert.assertNotNull(manager)
        org.robolectric.Shadows.shadowOf(manager.adapter).setState(android.bluetooth.BluetoothAdapter.STATE_ON)
        assertEquals(
            ConditionResult.Unsatisfied,
            runBlocking {
                TriggerStateEvaluator.evaluateAsync(
                    context,
                    listOf(
                        Trigger(
                            TriggerType.DEVICE,
                            mapOf("event" to "BLUETOOTH_CONNECTED", "deviceName" to "Pixel Buds", "deviceAddress" to "AA:BB:CC:DD:EE:FF")
                        )
                    )
                )
            }
        )
    }

    @Test
    fun gateReportsUnknownWhenForegroundAppIsUnreadable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // No accessibility provider and no usage access in the JVM: the
        // foreground state is unreadable → Unknown (end-behavior path), never
        // a fabricated false block.
        assertEquals(
            ConditionResult.Unknown,
            runBlocking {
                TriggerStateEvaluator.evaluateAsync(
                    context,
                    listOf(Trigger(TriggerType.APPLICATION, mapOf("packages" to "com.pay.app")))
                )
            }
        )
    }
}
