package com.nexaflow.core.execution.capability

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityParameterType
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityRiskLevel
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.capability.PrivilegeLevel
import java.lang.ref.WeakReference
import kotlinx.coroutines.delay

/** Explicit acknowledgement of the in-app disclosure shown before system accessibility settings. */
object AccessibilityCapabilityConsent {
    private const val PREFS = "nexaflow_accessibility_capability"
    private const val KEY_DISCLOSED = "disclosure_acknowledged"

    fun isGranted(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISCLOSED, false)

    fun grant(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DISCLOSED, true).apply()
    }
}

/**
 * Singleton bridge owned by the running AccessibilityService. It keeps only a
 * weak service reference and enforces that every request targets the current
 * active-window package; it has no workflow, EventBus, or ExecutionEngine API.
 */
class AccessibilityInteractionBridge {
    @Volatile private var serviceRef: WeakReference<AccessibilityService>? = null

    fun attach(service: AccessibilityService) {
        serviceRef = WeakReference(service)
    }

    fun detach(service: AccessibilityService) {
        if (serviceRef?.get() === service) serviceRef = null
    }

    fun isAttached(): Boolean = serviceRef?.get() != null

    internal fun execute(operation: AccessibilityOperation): CapabilityResult {
        val service = serviceRef?.get()
            ?: return unavailable("Accessibility service is not connected")
        val root = service.rootInActiveWindow
            ?: return unavailable("No active accessibility window is available")
        if (root.packageName?.toString() != operation.packageName) {
            root.recycleSafely()
            return unavailable("Active window does not match the approved target package")
        }
        return try {
            when (operation) {
                is AccessibilityOperation.FindNode -> find(root, operation.selector)
                is AccessibilityOperation.Click -> withNode(root, operation.selector) { node ->
                    complete(node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Click")
                }
                is AccessibilityOperation.Scroll -> withNode(root, operation.selector) { node ->
                    val action = if (operation.forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    complete(node.performAction(action), "Scroll")
                }
                is AccessibilityOperation.InputText -> withNode(root, operation.selector) { node ->
                    val args = android.os.Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, operation.text)
                    }
                    complete(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args), "Input text")
                }
                // Wait is implemented by the backend loop so this bridge never
                // owns a long-running operation on an accessibility service.
                is AccessibilityOperation.WaitForNode -> unavailable("Wait must be evaluated by AccessibilityCapabilityBackend")
                is AccessibilityOperation.Gesture -> gesture(service, operation)
            }
        } finally {
            root.recycleSafely()
        }
    }

    private fun find(root: AccessibilityNodeInfo, selector: AccessibilitySelector): CapabilityResult {
        val node = root.findBounded(selector)
            ?: return unavailable("No matching node found in the active target window")
        return try {
            if (!node.refresh()) unavailable("Matching node became stale")
            else CapabilityResult(
                status = CapabilityStatus.SUCCESS,
                backend = CapabilityBackendId.ACCESSIBILITY,
                message = "Matching node found",
                metadata = mapOf("selectorType" to selector.type.name)
            )
        } finally {
            node.recycleSafely()
        }
    }

    private fun withNode(
        root: AccessibilityNodeInfo,
        selector: AccessibilitySelector,
        action: (AccessibilityNodeInfo) -> CapabilityResult
    ): CapabilityResult {
        val node = root.findBounded(selector)
            ?: return unavailable("No matching node found in the active target window")
        return try {
            if (!node.refresh()) unavailable("Matching node became stale") else action(node)
        } finally {
            node.recycleSafely()
        }
    }

    private fun gesture(service: AccessibilityService, operation: AccessibilityOperation.Gesture): CapabilityResult {
        val path = Path().apply { moveTo(operation.x.toFloat(), operation.y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, operation.durationMs))
            .build()
        val accepted = service.dispatchGesture(gesture, null, null)
        return complete(accepted, "Gesture")
    }

    private fun complete(success: Boolean, action: String): CapabilityResult = if (success) {
        CapabilityResult(CapabilityStatus.SUCCESS, CapabilityBackendId.ACCESSIBILITY, message = "$action dispatched")
    } else {
        unavailable("$action was rejected by the active window")
    }

    private fun unavailable(message: String) = CapabilityResult.failed(
        CapabilityErrorCode.ACCESSIBILITY_UNAVAILABLE,
        message,
        CapabilityBackendId.ACCESSIBILITY
    )
}

object AccessibilityCapabilityCatalog {
    fun descriptors(): List<CapabilityDescriptor> = listOf(
        descriptor(CapabilityId.ACCESSIBILITY_FIND_NODE, "Find node", "Finds a bounded selector in the current approved app window"),
        descriptor(CapabilityId.ACCESSIBILITY_CLICK, "Click node", "Clicks a bounded selector in the current approved app window"),
        descriptor(CapabilityId.ACCESSIBILITY_SCROLL, "Scroll node", "Scrolls a bounded selector in the current approved app window", extra = listOf(
            CapabilityParameterSpec("direction", CapabilityParameterType.STRING, required = true, allowedValues = listOf("FORWARD", "BACKWARD"))
        )),
        descriptor(CapabilityId.ACCESSIBILITY_INPUT_TEXT, "Input text", "Sets text on a bounded editable selector in the current approved app window", extra = listOf(
            CapabilityParameterSpec("text", CapabilityParameterType.STRING, required = true, maximumLength = 512)
        )),
        descriptor(CapabilityId.ACCESSIBILITY_WAIT_FOR_NODE, "Wait for node", "Waits up to the execution-policy timeout for a bounded selector in the current approved app window"),
        CapabilityDescriptor(
            id = CapabilityId.ACCESSIBILITY_GESTURE,
            displayName = "Dispatch constrained gesture",
            description = "Dispatches one tap-like gesture only inside the current approved app window",
            risk = CapabilityRiskLevel.HIGH,
            minimumPrivilege = PrivilegeLevel.NONE,
            supportedBackends = listOf(CapabilityBackendId.ACCESSIBILITY),
            parameters = listOf(
                CapabilityParameterSpec("packageName", CapabilityParameterType.PACKAGE_NAME, required = true),
                CapabilityParameterSpec("x", CapabilityParameterType.INTEGER, required = true, minimumInteger = 0, maximumInteger = 10_000),
                CapabilityParameterSpec("y", CapabilityParameterType.INTEGER, required = true, minimumInteger = 0, maximumInteger = 10_000),
                CapabilityParameterSpec("durationMs", CapabilityParameterType.INTEGER, required = true, minimumInteger = 1, maximumInteger = 1_000)
            )
        )
    )

    private fun descriptor(
        id: CapabilityId,
        displayName: String,
        description: String,
        extra: List<CapabilityParameterSpec> = emptyList()
    ) = CapabilityDescriptor(
        id = id,
        displayName = displayName,
        description = description,
        risk = CapabilityRiskLevel.HIGH,
        minimumPrivilege = PrivilegeLevel.NONE,
        supportedBackends = listOf(CapabilityBackendId.ACCESSIBILITY),
        parameters = listOf(
            CapabilityParameterSpec("packageName", CapabilityParameterType.PACKAGE_NAME, required = true),
            CapabilityParameterSpec("selectorType", CapabilityParameterType.STRING, required = true, allowedValues = AccessibilitySelectorType.entries.map { it.name }),
            CapabilityParameterSpec("selector", CapabilityParameterType.STRING, required = true, maximumLength = 256)
        ) + extra
    )
}

class AccessibilityCapabilityBackend(
    private val context: Context,
    private val bridge: AccessibilityInteractionBridge,
    private val consentGranted: (Context) -> Boolean = AccessibilityCapabilityConsent::isGranted
) : CapabilityBackend {
    override val id: CapabilityBackendId = CapabilityBackendId.ACCESSIBILITY
    override val supportedCapabilities: Set<CapabilityId> = ACCESSIBILITY_CAPABILITIES

    override suspend fun availability(request: CapabilityRequest): BackendAvailability = when {
        request.capability !in supportedCapabilities -> BackendAvailability(id, CapabilityAvailability.UNSUPPORTED, "Capability is not implemented by Accessibility backend")
        request.policy.allowedBackends != listOf(id) || !request.policy.allowPrivilegedBackends -> BackendAvailability(id, CapabilityAvailability.PERMISSION_REQUIRED, "Accessibility backend must be explicitly selected in execution policy")
        !consentGranted(context) -> BackendAvailability(id, CapabilityAvailability.PERMISSION_REQUIRED, "Accessibility disclosure has not been acknowledged")
        !bridge.isAttached() -> BackendAvailability(id, CapabilityAvailability.UNAVAILABLE, "NexaFlow Accessibility service is not enabled and connected")
        else -> BackendAvailability(id, CapabilityAvailability.AVAILABLE)
    }

    override suspend fun execute(request: CapabilityRequest): CapabilityResult {
        if (!consentGranted(context)) return failure("Accessibility disclosure has not been acknowledged")
        if (!bridge.isAttached()) return failure("NexaFlow Accessibility service is not enabled and connected")
        val operation = runCatching { AccessibilityOperation.from(request) }.getOrElse { error ->
            return CapabilityResult.failed(CapabilityErrorCode.INVALID_CONFIGURATION, error.message ?: "Invalid accessibility request", id)
        }
        if (operation is AccessibilityOperation.WaitForNode) return waitFor(operation, request.policy.timeoutMs)
        return bridge.execute(operation)
    }

    private suspend fun waitFor(operation: AccessibilityOperation.WaitForNode, timeoutMs: Long): CapabilityResult {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtMost(MAX_WAIT_MS)
        while (SystemClock.elapsedRealtime() < deadline) {
            val result = bridge.execute(AccessibilityOperation.FindNode(operation.packageName, operation.selector))
            if (result.isSuccess) return result.copy(message = "Matching node found before timeout")
            delay(WAIT_INTERVAL_MS)
        }
        return CapabilityResult.failed(CapabilityErrorCode.TIMEOUT, "Matching node did not appear before timeout", id)
    }

    private fun failure(message: String) = CapabilityResult.failed(CapabilityErrorCode.ACCESSIBILITY_UNAVAILABLE, message, id)

    private companion object {
        const val WAIT_INTERVAL_MS = 100L
        const val MAX_WAIT_MS = 30_000L
    }
}

internal enum class AccessibilitySelectorType { VIEW_ID, TEXT, CONTENT_DESCRIPTION }
internal data class AccessibilitySelector(val type: AccessibilitySelectorType, val value: String)

internal sealed interface AccessibilityOperation {
    val packageName: String
    data class FindNode(override val packageName: String, val selector: AccessibilitySelector) : AccessibilityOperation
    data class Click(override val packageName: String, val selector: AccessibilitySelector) : AccessibilityOperation
    data class Scroll(override val packageName: String, val selector: AccessibilitySelector, val forward: Boolean) : AccessibilityOperation
    data class InputText(override val packageName: String, val selector: AccessibilitySelector, val text: String) : AccessibilityOperation
    data class WaitForNode(override val packageName: String, val selector: AccessibilitySelector) : AccessibilityOperation
    data class Gesture(override val packageName: String, val x: Int, val y: Int, val durationMs: Long) : AccessibilityOperation

    companion object {
        fun from(request: CapabilityRequest): AccessibilityOperation {
            val packageName = checkNotNull(request.parameters["packageName"])
            if (request.capability == CapabilityId.ACCESSIBILITY_GESTURE) {
                return Gesture(packageName, checkNotNull(request.parameters["x"]).toInt(), checkNotNull(request.parameters["y"]).toInt(), checkNotNull(request.parameters["durationMs"]).toLong())
            }
            val selector = AccessibilitySelector(
                type = AccessibilitySelectorType.valueOf(checkNotNull(request.parameters["selectorType"])),
                value = checkNotNull(request.parameters["selector"])
            )
            return when (request.capability) {
                CapabilityId.ACCESSIBILITY_FIND_NODE -> FindNode(packageName, selector)
                CapabilityId.ACCESSIBILITY_CLICK -> Click(packageName, selector)
                CapabilityId.ACCESSIBILITY_SCROLL -> Scroll(packageName, selector, checkNotNull(request.parameters["direction"]) == "FORWARD")
                CapabilityId.ACCESSIBILITY_INPUT_TEXT -> InputText(packageName, selector, checkNotNull(request.parameters["text"]))
                CapabilityId.ACCESSIBILITY_WAIT_FOR_NODE -> WaitForNode(packageName, selector)
                else -> error("Unsupported accessibility capability")
            }
        }
    }
}

private fun AccessibilityNodeInfo.findBounded(selector: AccessibilitySelector): AccessibilityNodeInfo? {
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    pending.add(this)
    var inspected = 0
    while (pending.isNotEmpty() && inspected++ < MAX_NODE_INSPECTION) {
        val node = pending.removeFirst()
        val matched = when (selector.type) {
            AccessibilitySelectorType.VIEW_ID -> node.viewIdResourceName == selector.value
            AccessibilitySelectorType.TEXT -> node.text?.toString() == selector.value
            AccessibilitySelectorType.CONTENT_DESCRIPTION -> node.contentDescription?.toString() == selector.value
        }
        if (matched) return node.obtainCopy()
        for (index in 0 until node.childCount) node.getChild(index)?.let(pending::add)
    }
    return null
}

private fun AccessibilityNodeInfo.obtainCopy(): AccessibilityNodeInfo = AccessibilityNodeInfo.obtain(this)
private fun AccessibilityNodeInfo.recycleSafely() = runCatching { recycle() }
private const val MAX_NODE_INSPECTION = 2_000
private val ACCESSIBILITY_CAPABILITIES = setOf(
    CapabilityId.ACCESSIBILITY_FIND_NODE,
    CapabilityId.ACCESSIBILITY_CLICK,
    CapabilityId.ACCESSIBILITY_SCROLL,
    CapabilityId.ACCESSIBILITY_INPUT_TEXT,
    CapabilityId.ACCESSIBILITY_WAIT_FOR_NODE,
    CapabilityId.ACCESSIBILITY_GESTURE
)
