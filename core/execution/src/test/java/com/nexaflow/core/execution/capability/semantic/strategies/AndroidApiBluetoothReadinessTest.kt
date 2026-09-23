package com.nexaflow.core.execution.capability.semantic.strategies

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.execution.capability.semantic.TypedOperationRequest
import com.nexaflow.domain.capability.operation.SemanticOperationId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidApiBluetoothReadinessTest {

    @Test
    fun bluetoothReadAndWriteRequireConnectPermissionOnModernAndroid() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApplication = shadowOf(context)
        shadowApplication.denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val strategy = AndroidApiStateStrategy(context)

        val read = strategy.availability(
            TypedOperationRequest(SemanticOperationId.BLUETOOTH_GET_STATE),
            SemanticOperationId.BLUETOOTH_GET_STATE
        )
        val write = strategy.availability(
            TypedOperationRequest(
                SemanticOperationId.BLUETOOTH_SET_STATE,
                parameters = mapOf("enabled" to "true")
            ),
            SemanticOperationId.BLUETOOTH_SET_STATE
        )

        assertFalse(read.available)
        assertTrue(read.permissionRequired)
        assertFalse(write.available)
        assertTrue(write.permissionRequired)

        shadowApplication.grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        assertTrue(
            strategy.availability(
                TypedOperationRequest(SemanticOperationId.BLUETOOTH_GET_STATE),
                SemanticOperationId.BLUETOOTH_GET_STATE
            ).available
        )
        assertTrue(
            strategy.availability(
                TypedOperationRequest(
                    SemanticOperationId.BLUETOOTH_SET_STATE,
                    parameters = mapOf("enabled" to "true")
                ),
                SemanticOperationId.BLUETOOTH_SET_STATE
            ).available
        )
    }
}
