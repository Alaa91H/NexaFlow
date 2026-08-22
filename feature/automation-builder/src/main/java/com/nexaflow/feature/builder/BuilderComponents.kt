package com.nexaflow.feature.builder

import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexaflow.core.rom.ElevatedAccessShortcuts
import com.nexaflow.core.rom.PermissionStatus
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.RootPermissionGranter
import com.nexaflow.core.rom.SystemAppStatusDetector
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.alternatingSurfaceColor
import com.nexaflow.core.ui.SelectChip
import com.nexaflow.core.ui.StatusPill
import com.nexaflow.core.ui.theme.NexaFlowTheme
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerAlert(
    initialTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.substringBefore(":").toIntOrNull() ?: 8,
        initialMinute = initialTime.substringAfter(":").toIntOrNull() ?: 0
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(pickerState.hour, pickerState.minute)) }) {
                Text(text = stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        text = { TimePicker(state = pickerState) }
    )
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OptionChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labels: Map<String, String>? = null
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            SelectChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = labels?.get(option) ?: option
            )
        }
    }
}

@Composable
fun PermissionHint(
    text: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        TextButton(onClick = onClick) {
            Text(text = buttonLabel)
        }
    }
}

/**
 * A permission hint row that only exists while at least one of the given
 * runtime permissions is still missing — once everything is granted the row
 * disappears entirely (no permanent "granted" reminder in the cards).
 */
@Composable
fun RuntimePermissionHint(
    context: Context,
    permissions: List<String>,
    text: String,
    buttonLabel: String,
    onRequest: () -> Unit
) {
    val anyMissing = permissions.any {
        context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    if (anyMissing) {
        PermissionHint(text = text, buttonLabel = buttonLabel, onClick = onRequest)
    }
}

/**
 * Live state of a special permission shown in an action card.
 *
 * - [GRANTED]: the permission is currently granted (green pill).
 * - [AVAILABLE]: grantable now — root/Shizuku present but not yet granted
 *   (amber pill, the row stays tappable).
 * - [NOT_AVAILABLE]: not granted, or the elevated channel is absent entirely
 *   (grey pill). Binary permissions never report [AVAILABLE].
 */
enum class SpecialStatus { GRANTED, AVAILABLE, NOT_AVAILABLE }

/** True when the permission is binary (granted or not) with no intermediate state. */
private fun SpecialPermission.isBinary(): Boolean =
    this != SpecialPermission.ROOT &&
        this != SpecialPermission.SHIZUKU &&
        this != SpecialPermission.ELEVATED

/**
 * Pure status resolution for a special permission — extracted from the
 * composable so the probe logic is unit-testable without Compose. The root
 * TTL cache is refreshed first so a freshly granted root is never hidden
 * behind a stale cached result.
 */
internal fun resolveSpecialStatus(context: Context, special: SpecialPermission): SpecialStatus = when (special) {
    SpecialPermission.ROOT -> {
        SystemAppStatusDetector.refreshRootAvailability()
        when {
            PermissionShortcuts.isGranted(context, SpecialPermission.ROOT) -> SpecialStatus.GRANTED
            SystemAppStatusDetector.isSuBinaryAvailable() -> SpecialStatus.AVAILABLE
            else -> SpecialStatus.NOT_AVAILABLE
        }
    }
    SpecialPermission.SHIZUKU -> when {
        PermissionShortcuts.isGranted(context, SpecialPermission.SHIZUKU) -> SpecialStatus.GRANTED
        PrivilegedRunner.isShizukuRunning() -> SpecialStatus.AVAILABLE
        else -> SpecialStatus.NOT_AVAILABLE
    }
    SpecialPermission.ELEVATED -> {
        SystemAppStatusDetector.refreshRootAvailability()
        when {
            PermissionShortcuts.isGranted(context, SpecialPermission.ELEVATED) -> SpecialStatus.GRANTED
            SystemAppStatusDetector.isSuBinaryAvailable() || PrivilegedRunner.isShizukuRunning() ->
                SpecialStatus.AVAILABLE
            else -> SpecialStatus.NOT_AVAILABLE
        }
    }
    // Binary permissions (write settings, DND, notification access,
    // accessibility, bluetooth): granted or not — no middle state.
    else -> if (PermissionShortcuts.isGranted(context, special)) {
        SpecialStatus.GRANTED
    } else {
        SpecialStatus.NOT_AVAILABLE
    }
}

/**
 * Samsung-style live status row shown inside an action card for ANY special
 * permission — root, Shizuku, elevated, write settings, DND access,
 * notification access, accessibility, bluetooth. Instead of a plain button it
 * shows a colour-coded pill (green = granted, amber = grantable now for the
 * elevated trio, grey = not granted) that refreshes when the screen resumes —
 * after returning from the root manager, Shizuku grant dialog or a settings
 * screen the badge updates without reopening the task.
 */
@Composable
fun SpecialPermissionStatusRow(
    hintText: String,
    special: SpecialPermission,
    context: Context,
    refreshKey: Int = 0,
    onRequest: () -> Unit,
    // Test seam: pins the live status so Compose UI tests can render each
    // visual state deterministically without touching the real system probes.
    probe: (() -> SpecialStatus)? = null
) {
    var status by remember(special) { mutableStateOf(SpecialStatus.NOT_AVAILABLE) }
    // Re-probe on every refresh tick (e.g. ON_RESUME after a grant dialog) and
    // on first composition. The root probe can take up to a few seconds, so it
    // runs off the main thread. The root TTL cache is invalidated first so a
    // freshly granted root is never hidden behind a stale cached result.
    LaunchedEffect(refreshKey, special, probe) {
        status = if (probe != null) {
            probe()
        } else {
            withContext(Dispatchers.IO) { resolveSpecialStatus(context, special) }
        }
    }
    // The row exists only to collect a missing permission. Once granted, it
    // disappears entirely — no permanent "granted" badge cluttering the card.
    if (status == SpecialStatus.GRANTED) return

    val (pillText, pillBg, pillFg) = when (status) {
        // Unreachable: the GRANTED early-return above already exited.
        SpecialStatus.GRANTED -> return
        SpecialStatus.AVAILABLE -> Triple(
            stringResource(R.string.elevated_status_available),
            NexaFlowTheme.colors.warningContainer,
            NexaFlowTheme.colors.warning
        )
        SpecialStatus.NOT_AVAILABLE -> Triple(
            stringResource(
                if (special.isBinary()) R.string.special_status_not_granted else R.string.elevated_status_unavailable
            ),
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.secondary
        )
    }
    // The whole row is the button: tap it (or the pill) to start the grant flow.
    // A chevron hints the row is tappable — no separate button, matching the
    // "badge instead of a button" Samsung-style request.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("special_status_row")
            .clickable(enabled = status != SpecialStatus.GRANTED, onClick = onRequest),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = hintText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        StatusPill(text = pillText, background = pillBg, contentColor = pillFg)
        if (status != SpecialStatus.GRANTED) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.testTag("special_status_chevron")
            )
        }
    }
}

@Composable
fun ItemHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun CategoryAccordion(
    tabs: List<Pair<String, ImageVector?>>,
    expandedIndex: Int?,
    onExpandedChange: (Int?) -> Unit,
    content: @Composable ColumnScope.(Int) -> Unit
) {
    // Compatibility filtering can change the category list while this screen
    // remains composed. Ignore a stale saved index instead of indexing a new,
    // shorter list and destabilising the whole task editor.
    val selectedIndex = expandedIndex?.takeIf { it in tabs.indices }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // لا نلف شرائح الفئات إلى صف ثانٍ: على الهاتف الضيق قد يبلغ FlowRow
        // ارتفاعاً غير متوقع بينما تظهر قائمة الخيارات أسفله، فتتراكب العناصر
        // بصرياً. الصف القابل للسحب يحافظ على ارتفاع ثابت وفئة واحدة في كل صف.
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("category_tabs"),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(
                items = tabs,
                key = { index, tab -> "$index:${tab.first}" }
            ) { index, (label, icon) ->
                SelectChip(
                    selected = selectedIndex == index,
                    onClick = { onExpandedChange(if (selectedIndex == index) null else index) },
                    label = label,
                    leadingIcon = icon,
                    alternatingIndex = index,
                    // Bound each chip so a long localised label is ellipsized
                    // inside its own cell and never widens the strip into an
                    // unstable layout on compact RTL displays.
                    modifier = Modifier
                        .widthIn(max = 180.dp)
                        .testTag("category_tab_$index")
                )
            }
        }
        // لا نعرض إلا محتوى الفئة المحددة. النقر على الفئة نفسها يطوي
        // الخيارات، وهو مناسب بعد اختيار عنصر أو عندما يريد المستخدم التركيز
        // على الملخصات التي أضافها بالفعل.
        selectedIndex?.let { index ->
            val (label, icon) = tabs[index]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            // Keep the option catalogue in a dedicated layout boundary. This
            // prevents future callers from layering sibling options at the same
            // origin and isolates option drawing from the tab strip above.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                content(index)
            }
        }
    }
}

@Composable
fun ActionOptionRow(
    option: ActionOption,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    alternatingIndex: Int? = null
) {
    CatalogOptionRow(
        title = stringResource(option.titleRes),
        subtitle = stringResource(option.subtitleRes),
        icon = option.icon,
        color = option.color,
        checked = checked,
        onToggle = onToggle,
        modifier = modifier,
        alternatingIndex = alternatingIndex
    )
}

/**
 * A condition choice deliberately uses the exact catalogue row as an action:
 * icon badge, localised title and description, and trailing selection control.
 * Conditions are single-select, so the picker closes immediately after a tap;
 * using the shared row keeps its compact RTL geometry identical to execution.
 */
@Composable
fun TriggerOptionRow(
    type: TriggerType,
    checked: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    alternatingIndex: Int? = null
) {
    val categoryColor = triggerCategoryOf[type]?.color ?: MaterialTheme.colorScheme.primary
    CatalogOptionRow(
        title = stringResource(type.labelRes()),
        subtitle = stringResource(type.descRes()),
        icon = type.icon(),
        color = categoryColor,
        checked = checked,
        onToggle = onSelect,
        modifier = modifier,
        alternatingIndex = alternatingIndex
    )
}

@Composable
private fun CatalogOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    alternatingIndex: Int?
) {
    val rowSurface = alternatingIndex?.let { alternatingSurfaceColor(it) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (rowSurface != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowSurface)
                        .padding(horizontal = 8.dp)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBadge(
            icon = icon,
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
fun PermissionHintForAction(
    actionType: ActionType,
    context: Context,
    refreshKey: Int = 0,
    onRequestPermission: (Array<String>) -> Unit = {},
    // Default keeps the pre-explain behavior (open settings directly) so a call
    // site that forgets to wire the explain screen never gets a dead button.
    onExplainSpecial: (SpecialPermission) -> Unit = { PermissionShortcuts.openSpecial(context, it) }
) {
    val runtimePermissions = PermissionCatalog.runtimePermissionsFor(actionType)
    if (runtimePermissions.isNotEmpty()) {
        // The hint exists only to collect a missing permission: once every
        // permission of this action is granted, the row disappears entirely.
        val allGranted = runtimePermissions.all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            PermissionHint(
                text = stringResource(
                    when (actionType) {
                        ActionType.SYSTEM_SEND_SMS -> R.string.sms_permission_hint
                        ActionType.SYSTEM_FLASHLIGHT -> R.string.flashlight_hint
                        ActionType.SYSTEM_SEND_NOTIFICATION,
                        ActionType.SYSTEM_SEND_REMINDER,
                        ActionType.BATTERY_ALERTS,
                        ActionType.BATTERY_CHARGING_NOTIFICATIONS -> R.string.notification_permission_hint
                        ActionType.SYSTEM_HTTP_REQUEST -> R.string.http_request_hint
                        else -> R.string.location_hint
                    }
                ),
                buttonLabel = stringResource(R.string.grant),
                onClick = { onRequestPermission(runtimePermissions.toTypedArray()) }
            )
        }
        return
    }

    val special = PermissionCatalog.specialPermissionFor(actionType)
    if (special != null) {
        val textRes = when (special) {
            SpecialPermission.WRITE_SETTINGS -> R.string.write_settings_hint
            SpecialPermission.DND_ACCESS -> R.string.dnd_hint
            SpecialPermission.SHIZUKU -> R.string.shizuku_hint
            SpecialPermission.ROOT -> R.string.root_hint
            SpecialPermission.ELEVATED -> R.string.elevated_hint
            SpecialPermission.NOTIFICATION_ACCESS -> R.string.notification_access_hint
            else -> return
        }
        SpecialPermissionStatusRow(
            hintText = stringResource(textRes),
            special = special,
            context = context,
            refreshKey = refreshKey,
            onRequest = { onExplainSpecial(special) }
        )
    }
}

object PermissionShortcuts {
    /**
     * True when the special permission is already granted on this device, so
     * the aggressive flow skips already-satisfied requirements.
     */
    fun isGranted(context: Context, type: SpecialPermission): Boolean = try {
        when (type) {
            SpecialPermission.WRITE_SETTINGS -> Settings.System.canWrite(context)
            SpecialPermission.DND_ACCESS -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.isNotificationPolicyAccessGranted
            }
            SpecialPermission.NOTIFICATION_ACCESS -> PermissionStatus.isNotificationListenerGranted(context)
            SpecialPermission.ACCESSIBILITY -> PermissionStatus.isAccessibilityServiceEnabled(context)
            // Real detection via the rom-integration module (same probes the
            // execution engine uses), so a previously granted Shizuku/root
            // permission is never re-requested.
            SpecialPermission.SHIZUKU -> PrivilegedRunner.isShizukuGranted()
            SpecialPermission.ROOT -> PrivilegedRunner.isRootAvailable()
            SpecialPermission.ELEVATED -> {
                // Elevated covers root/Shizuku/system; grant is best-effort.
                PrivilegedRunner.isRootAvailable() || PrivilegedRunner.isShizukuGranted()
            }
            SpecialPermission.BLUETOOTH -> {
                android.Manifest.permission.BLUETOOTH_CONNECT in context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .requestedPermissions.orEmpty() &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
    } catch (_: Throwable) {
        false
    }

    /** Opens the dedicated system screen for a special permission. */
    fun openSpecial(context: Context, type: SpecialPermission) {
        when (type) {
            SpecialPermission.WRITE_SETTINGS -> openWriteSettings(context)
            SpecialPermission.DND_ACCESS -> openNotificationPolicy(context)
            SpecialPermission.NOTIFICATION_ACCESS -> openNotificationAccessSettings(context)
            SpecialPermission.ACCESSIBILITY -> openAccessibilitySettings(context)
            // Shizuku: request the permission in-app when the server is already
            // running (one tap, no detour); otherwise open the Shizuku app.
            SpecialPermission.SHIZUKU -> ElevatedAccessShortcuts.openShizuku(context)
            // Root: trigger the root manager's allow/deny grant dialog directly
            // (Magisk/KernelSU/APatch) instead of opening app info — one tap to
            // grant, exactly how Tasker/Termux request root. Once granted, every
            // permission the app needs is granted automatically through the
            // elevated shell. If the prompt is denied or times out, fall back to
            // the root manager app so the user is never left with a dead tap.
            SpecialPermission.ROOT -> ElevatedAccessShortcuts.requestRootAccess(context) { granted ->
                if (granted) {
                    Thread {
                        RootPermissionGranter.requestAndGrantAll(context.applicationContext)
                    }.start()
                } else {
                    ElevatedAccessShortcuts.openRootManager(context)
                }
            }
            // Elevated actions run through root, Shizuku, or a system app; prefer
            // an in-app Shizuku grant when available, otherwise the root dialog.
            SpecialPermission.ELEVATED -> {
                if (PrivilegedRunner.isShizukuRunning()) {
                    ElevatedAccessShortcuts.openShizuku(context)
                } else {
                    ElevatedAccessShortcuts.requestRootAccess(context) { granted ->
                        if (granted) {
                            Thread {
                                RootPermissionGranter.requestAndGrantAll(context.applicationContext)
                            }.start()
                        } else {
                            ElevatedAccessShortcuts.openRootManager(context)
                        }
                    }
                }
            }
            SpecialPermission.BLUETOOTH -> openBluetoothSettings(context)
        }
    }

    fun openWriteSettings(context: Context) = PermissionStatus.openWriteSettings(context)

    fun openNotificationPolicy(context: Context) = PermissionStatus.openNotificationPolicy(context)

    fun openAccessibilitySettings(context: Context) =
        PermissionStatus.openAccessibilitySettings(context)

    fun openBluetoothSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openNotificationAccessSettings(context: Context) =
        PermissionStatus.openNotificationAccessSettings(context)
}

/** Shared stream type options used in ActionConfigEditor and EndBehaviorEditor. */
internal val STREAM_OPTIONS = listOf(
    "MUSIC" to R.string.stream_music,
    "RING" to R.string.stream_ring,
    "NOTIFICATION" to R.string.stream_notification,
    "ALARM" to R.string.stream_alarm,
    "VOICE_CALL" to R.string.stream_voice_call,
    "SYSTEM" to R.string.stream_system,
    "DTMF" to R.string.stream_dtmf,
    "ACCESSIBILITY" to R.string.stream_accessibility
)

// region Previews

@Preview(name = "SelectChip", showBackground = true)
@Composable
private fun SelectChipPreview() {
    MaterialTheme {
        Column {
            SelectChip(selected = true, onClick = {}, label = "Selected")
            SelectChip(selected = false, onClick = {}, label = "Unselected")
        }
    }
}

@Preview(name = "SelectChip – Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SelectChipDarkPreview() {
    MaterialTheme {
        Column {
            SelectChip(selected = true, onClick = {}, label = "Selected")
            SelectChip(selected = false, onClick = {}, label = "Unselected")
        }
    }
}

@Preview(name = "OptionChips", showBackground = true)
@Composable
private fun OptionChipsPreview() {
    MaterialTheme {
        OptionChips(
            options = listOf("ON", "OFF", "AUTO"),
            labels = mapOf("ON" to "On", "OFF" to "Off", "AUTO" to "Auto"),
            selected = "ON",
            onSelect = {}
        )
    }
}

@Preview(name = "SliderRow", showBackground = true)
@Composable
private fun SliderRowPreview() {
    MaterialTheme {
        SliderRow(label = "Volume", value = 0.5f, valueRange = 0f..1f, onValueChange = {})
    }
}

@Preview(name = "ItemHeader", showBackground = true)
@Composable
private fun ItemHeaderPreview() {
    MaterialTheme { ItemHeader(text = "Section Title") }
}

@Preview(name = "CategoryAccordion", showBackground = true)
@Composable
private fun CategoryAccordionPreview() {
    MaterialTheme {
        CategoryAccordion(
            tabs = listOf("Display" to Icons.Filled.DisplaySettings),
            expandedIndex = 0,
            onExpandedChange = {},
            content = { Text("Content here", modifier = Modifier.padding(16.dp)) }
        )
    }
}

// endregion
