package com.nexaflow.feature.builder

import android.app.Activity
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.PhonePaused
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.nexaflow.core.engine.LocationAccess
import com.nexaflow.core.engine.CanonicalBatteryTriggerDefinition
import com.nexaflow.core.execution.NotificationActionButton
import com.nexaflow.core.execution.TriggerMatchPolicy
import com.nexaflow.core.execution.compat.CommandRequirementCatalog
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginConfigParser
import com.nexaflow.core.rom.ElevatedAccessShortcuts
import com.nexaflow.core.rom.NetworkModePolicy
import com.nexaflow.core.rom.RootPermissionGranter
import com.nexaflow.core.ui.IconBadge
import com.nexaflow.core.ui.NexaFlowAnimatedVisibility
import com.nexaflow.core.ui.NexaFlowCard
import com.nexaflow.core.ui.NexaFlowFloatingActionButton
import com.nexaflow.core.ui.NexaFlowIcons
import com.nexaflow.core.ui.nexaFlowEffectsSpec
import com.nexaflow.core.ui.nexaFlowSpatialSpec
import com.nexaflow.core.ui.NexaFlowTopBar
import com.nexaflow.core.ui.SectionHeader
import com.nexaflow.core.ui.SettingRow
import com.nexaflow.core.ui.iconVector
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.PluginInfo
import com.nexaflow.domain.models.RoutineTemplateCatalog
import com.nexaflow.domain.models.EndBehaviorCatalog
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.core.execution.canonical.CanonicalDelayDefinition
import com.nexaflow.domain.canonical.DurationValue
import com.nexaflow.domain.canonical.WaitNode
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

private const val TAG = "AutomationBuilder"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationBuilderScreen(
    navController: NavController,
    automationId: String? = null,
    templateId: String? = null,
    savedStateHandle: SavedStateHandle? = null
) {
    val viewModel: AutomationBuilderViewModel = hiltViewModel()
    val context = LocalContext.current
    // Search labels must observe configuration changes as they are localized.
    val configuration = LocalConfiguration.current
    val configurationContext = remember(context, configuration) {
        context.createConfigurationContext(configuration)
    }
    // Compose one live capability snapshot with Android/ROM compatibility.
    // Unsupported entries stay hidden, while grantable or temporarily unavailable
    // entries remain discoverable and are rendered as locked rows below.
    val capabilitySnapshot by viewModel.capabilitySnapshot.collectAsStateWithLifecycle()
    var permissionRefreshTick by remember { mutableStateOf(0) }
    val actionOptionStates = remember(context, capabilitySnapshot, permissionRefreshTick) {
        CompatibilityGate.actionOptionStates(context, capabilitySnapshot)
            .filter { it.availability != BuilderOptionAvailability.UNSUPPORTED }
    }
    val triggerOptionStates = remember(context, capabilitySnapshot, permissionRefreshTick) {
        CompatibilityGate.triggerOptionStates(context, capabilitySnapshot)
            .filter { it.availability != BuilderOptionAvailability.UNSUPPORTED }
    }
    val supportedActions = remember(actionOptionStates) { actionOptionStates.map { it.option } }
    val actionAvailabilityByType = remember(actionOptionStates) {
        actionOptionStates.associate { it.option.actionType to it.availability }
    }
    val supportedTriggers = remember(triggerOptionStates, viewModel.executableCanonicalTriggers) {
        triggerOptionStates.map { it.type } + viewModel.executableCanonicalTriggers.mapNotNull { contract ->
            if (contract.definitionId == CanonicalBatteryTriggerDefinition.ID) TriggerType.BATTERY else null
        }.filterNot { type -> type in triggerOptionStates.map { it.type } }
    }
    val triggerAvailabilityByType = remember(triggerOptionStates) {
        triggerOptionStates.associate { it.type to it.availability }
    }
    val availableTemplates = remember(capabilitySnapshot) {
        RoutineTemplateCatalog.availableTemplates(
            snapshot = capabilitySnapshot,
            actionRequirement = CommandRequirementCatalog::requirementFor,
            triggerRequirement = CommandRequirementCatalog::requirementFor
        )
    }
    val availableTemplateIds = remember(availableTemplates) {
        availableTemplates.mapTo(linkedSetOf()) { it.id }
    }
    val variables by viewModel.variables.collectAsStateWithLifecycle()
    // Saved tasks available for notification action buttons (run from a notification).
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    // %VARIABLE chips offered in text fields: user globals first, then the most
    // useful device-context built-ins (full set still works when typed by hand).
    val availableVariables = remember(variables) {
        variables.map { it.name } + listOf(
            "DATE", "TIME", "DATETIME", "BATTERY", "CHARGING", "WIFI", "BLUETOOTH",
            "RINGER", "SCREEN", "AIRPLANE", "NETWORK", "BRIGHTNESS", "BRAND", "MODEL", "SDK"
        )
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val stringNextNeedsTrigger = stringResource(R.string.next_needs_trigger)
    val stringNextNeedsAction = stringResource(R.string.next_needs_action)
    val stringSavedSuccessfully = stringResource(R.string.saved_successfully)
    val stringSaveFailed = stringResource(R.string.save_failed)
    val stringDefaultTaskName = stringResource(R.string.builder_title)
    val stringLocationFixFailed = stringResource(R.string.location_fix_failed)
    val stringPermissionRequired = stringResource(R.string.permission_denied_hint)
    // P2-11: the editable draft survives rotation AND process death via
    // rememberSaveable (custom savers serialize the immutable drafts to Bundle).
    var name by rememberSaveable { mutableStateOf("") }
    // Guided creation: 0 = when, 1 = do, 2 = review and save.
    // The data model is unchanged; only the order in which decisions are shown changes.
    var step by rememberSaveable { mutableStateOf(0) }
    val triggers = rememberSaveable(saver = TriggerDraftListSaver) { mutableStateListOf<TriggerDraft>() }
    // How multiple triggers combine. NEW tasks default to ALL: users expect
    // every configured condition to hold before the task runs (the dominant
    // support request). Legacy stored tasks keep their own value, and ANY
    // remains one tap away in the selector. Shown once two or more triggers
    // exist — with a single trigger the choice is meaningless.
    var triggerMatchName by rememberSaveable { mutableStateOf(TriggerMatchMode.ALL.name) }
    val triggerMatch = TriggerMatchMode.entries.firstOrNull { it.name == triggerMatchName } ?: TriggerMatchMode.ANY
    val constraints = rememberSaveable(saver = ConstraintDraftListSaver) { mutableStateListOf<ConstraintDraft>() }
    var showConstraintPicker by remember { mutableStateOf(false) }
    // A freshly picked constraint opens its editor; loaded ones stay collapsed.
    var lastAddedConstraint by remember { mutableStateOf(-1) }
    var selectedIconIndex by rememberSaveable { mutableStateOf(0) }
    // Accent color chosen in the icon picker (ARGB Long, persisted in the
    // automation's iconColor column). Defaults to Google blue.
    var selectedIconColor by rememberSaveable { mutableStateOf(0xFF0B57D0L) }
    var appPickerTarget by remember { mutableStateOf<String?>(null) }
    var bluetoothPickerTarget by remember { mutableStateOf<Int?>(null) }
    var calendarPickerTarget by remember { mutableStateOf<Int?>(null) }
    // Family-first discovery: opening a configurator shows the small family
    // surface only. Concrete legacy-compatible options are revealed only
    // after the user chooses a family (or types a search query).
    var expandedTriggerCategory by rememberSaveable { mutableStateOf<Int?>(0) }
    var expandedActionCategory by rememberSaveable { mutableStateOf<Int?>(0) }
    var triggerSearchQuery by rememberSaveable { mutableStateOf("") }
    var actionSearchQuery by rememberSaveable { mutableStateOf("") }
    var showAdvancedTriggerOptions by rememberSaveable { mutableStateOf(false) }
    var showAdvancedActionOptions by rememberSaveable { mutableStateOf(false) }
    // Fixed multi-select catalogues. A choice is only materialised as a card
    // after the user presses the dedicated Add button below its catalogue.
    val selectedTriggerTypes = rememberSaveable(saver = TriggerTypeSelectionSaver) {
        mutableStateListOf<TriggerType>()
    }
    val selectedActionTypes = rememberSaveable(saver = ActionTypeSelectionSaver) {
        mutableStateListOf<ActionType>()
    }
    // Expansion belongs to the section, not individual card-local state: one
    // selected trigger and one selected execution may be open at any moment.
    var expandedTriggerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var expandedActionCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val actionDrafts = rememberSaveable(saver = ActionDraftListSaver) { mutableStateListOf<ActionDraft>() }
    val canonicalActionNodes = remember { mutableStateListOf<CanonicalWorkflowNode>() }
    val canonicalTriggerNodes = remember { mutableStateListOf<CanonicalWorkflowNode>() }
    // Real drag-and-drop reorder state — one per reorderable list so
    // dragging in one section never disturbs the others (↕️ handle).
    val actionDrag = remember { TaskDragState<ActionDraft>() }
    val triggerDrag = remember { TaskDragState<TriggerDraft>() }
    val constraintDrag = remember { TaskDragState<ConstraintDraft>() }
    val exitActionConfigs = rememberSaveable(saver = ActionConfigMapSaver) { mutableStateMapOf<ActionType, Map<String, String>>() }
    val selectedExitActions = rememberSaveable(saver = ActionOptionListSaver) { mutableStateListOf<ActionOption>() }
    var appliedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedTemplateId by rememberSaveable(templateId) { mutableStateOf(templateId) }
    var showStarterRoutineChooser by rememberSaveable { mutableStateOf(false) }

    // ── External plugins (Locale protocol) ─────────────────────────
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    var pluginPickerTarget by remember { mutableStateOf<String?>(null) }
    var pluginLauncherActionId by remember { mutableStateOf<String?>(null) }
    var pluginLauncherPackage by remember { mutableStateOf<String?>(null) }
    var pluginLauncherReceiver by remember { mutableStateOf<String?>(null) }
    var pluginLauncherEditActivity by remember { mutableStateOf<String?>(null) }
    var pluginLauncherHighRiskApproved by remember { mutableStateOf(false) }
    val pluginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val actionId = pluginLauncherActionId
        val pkg = pluginLauncherPackage
        val receiver = pluginLauncherReceiver
        val editActivity = pluginLauncherEditActivity
        val highRiskApproved = pluginLauncherHighRiskApproved
        pluginLauncherActionId = null
        pluginLauncherPackage = null
        pluginLauncherReceiver = null
        pluginLauncherEditActivity = null
        pluginLauncherHighRiskApproved = false
        val bundle: Bundle? = result.data?.getBundleExtra(LocaleContract.EXTRA_BUNDLE)
        val blurb = result.data?.getStringExtra(LocaleContract.EXTRA_STRING_BLURB)
            ?: result.data?.getStringExtra(LocaleContract.EXTRA_BLURB).orEmpty()
        val draftIndex = actionId?.let { id -> actionDrafts.indexOfFirst { it.id == id } } ?: -1
        if (result.resultCode == Activity.RESULT_OK && pkg != null && receiver != null && bundle != null && blurb.isNotBlank() && draftIndex >= 0) {
            // Prefer the JSON convention; fall back to legacy flat extras.
            val configMap = PluginConfigParser.fromBundle(bundle).ifEmpty {
                PluginConfigParser.flattenBundle(bundle)
            }
            actionDrafts[draftIndex] = actionDrafts[draftIndex].copy(
                config = buildMap {
                    put("package", pkg)
                    put("receiver", receiver)
                    put("blurb", blurb)
                    put("bundleJson", PluginConfigParser.toJson(configMap))
                    // CapabilityRequest carries only this opaque reference; the
                    // backend reloads the persisted action config by workflow id.
                    put("pluginInstance", "plugin:${UUID.randomUUID()}")
                    // The user just completed the external configuration Activity.
                    put("pluginApproval", "approved")
                    if (highRiskApproved) put("pluginHighRiskApproval", "approved")
                    editActivity?.takeIf { it.isNotBlank() }?.let { put("editActivity", it) }
                }
            )
        } else if (draftIndex >= 0 && actionDrafts[draftIndex].config.isEmpty()) {
            // A cancelled initial plugin setup removes only its own stub card.
            actionDrafts.removeAt(draftIndex)
        }
    }
    val stringPluginNoEdit = stringResource(R.string.plugin_no_edit)
    fun configurePlugin(
        actionId: String,
        packageName: String?,
        receiver: String?,
        editActivityClass: String?,
        config: Map<String, String>,
        highRiskApproved: Boolean = false
    ) {
        val pkg = packageName ?: return
        val rec = receiver ?: return
        pluginLauncherActionId = actionId
        val editActivity = editActivityClass?.takeIf { it.isNotBlank() }
            ?: config["editActivity"]?.takeIf { it.isNotBlank() }
        pluginLauncherPackage = pkg
        pluginLauncherReceiver = rec
        pluginLauncherEditActivity = editActivity
        pluginLauncherHighRiskApproved = highRiskApproved ||
            config["pluginHighRiskApproval"] == "approved"
        val intent = Intent(LocaleContract.ACTION_EDIT_SETTING).apply {
            if (editActivity != null) {
                component = ComponentName(pkg, editActivity)
            } else {
                // Legacy persisted entry: no stable edit component was stored.
                // Keep previous package-scoped behaviour only for reconfiguration;
                // newly discovered plug-ins always use an explicit component.
                `package` = pkg
            }
            putExtra(LocaleContract.EXTRA_STRING_BREADCRUMB, "NexaFlow")
            putExtra(
                LocaleContract.EXTRA_HOST_CAPABILITIES,
                LocaleContract.HOST_CAPABILITY_SETTING_OUTPUT_VARIABLES
            )
        }
        // Reconfiguring: hand the saved bundle back so the plugin can pre-fill.
        val savedJson = config["bundleJson"]
        if (!savedJson.isNullOrBlank()) {
            runCatching {
                intent.putExtra(
                    LocaleContract.EXTRA_BUNDLE,
                    PluginConfigParser.toBundle(PluginConfigParser.parseJson(savedJson))
                )
            }
        }
        try {
            pluginLauncher.launch(intent)
        } catch (_: Throwable) {
            pluginLauncherActionId = null
            pluginLauncherPackage = null
            pluginLauncherReceiver = null
            pluginLauncherEditActivity = null
            pluginLauncherHighRiskApproved = false
            scope.launch { snackbarHostState.showSnackbar(stringPluginNoEdit) }
            // The plugin has no edit screen: never leave a stuck, unconfigured
            // action behind (the user can still add other plugins).
            val draftIndex = actionDrafts.indexOfFirst { it.id == actionId }
            if (draftIndex >= 0 && actionDrafts[draftIndex].config.isEmpty()) {
                actionDrafts.removeAt(draftIndex)
            }
        }
    }

    // A template fills a new editable draft once. It never overwrites an edit.
    LaunchedEffect(requestedTemplateId, automationId, availableTemplateIds) {
        val requestedId = requestedTemplateId
        if (automationId == null && requestedId != null && appliedTemplateId != requestedId) {
            RoutineTemplateCatalog.find(requestedId)
                ?.takeIf { it.id in availableTemplateIds }
                ?.let { template ->
                triggers.clear()
                template.triggers.forEach { triggers.add(TriggerDraft(it.type, it.config)) }
                actionDrafts.clear()
                template.actions.forEach { action ->
                    actionOptions.find { it.actionType == action.type }?.let { option ->
                        actionDrafts.add(
                            ActionDraft(
                                option = option,
                                config = action.config,
                                endBehavior = action.endBehavior
                            )
                        )
                    }
                }
                expandedTriggerIndex = null
                expandedActionCardId = null
                // A template is only a starting point. Prefill the editable name,
                // show the full review station, and persist it disabled on first save.
                name = configurationContext.getString(starterRoutineTitleRes(template.id))
                appliedTemplateId = requestedId
                step = if (triggers.isNotEmpty() && actionDrafts.isNotEmpty()) 2 else 0
            }
        }
    }

    // Edit mode: load the existing automation once and pre-fill the drafts.
    LaunchedEffect(automationId) {
        automationId?.let { viewModel.loadAutomation(it) }
    }
    val loadedAutomation by viewModel.loaded.collectAsStateWithLifecycle()
    LaunchedEffect(loadedAutomation) {
        val loaded = loadedAutomation ?: return@LaunchedEffect
        if (loaded.id != automationId) return@LaunchedEffect
        name = loaded.name
        selectedIconIndex = NexaFlowIcons.all.indexOfFirst { it.first == loaded.icon }.coerceAtLeast(0)
        selectedIconColor = loaded.iconColor
        triggers.clear()
        loaded.triggers.forEach { triggers.add(TriggerDraft(it.type, it.config)) }
        triggerMatchName = loaded.triggerMatch.name
        selectedTriggerTypes.clear()
        selectedActionTypes.clear()
        expandedTriggerIndex = null
        expandedActionCardId = null
        constraints.clear()
        loaded.constraints.forEach { constraints.add(ConstraintDraft(it.type, it.config)) }
        actionDrafts.clear()
        loaded.actions.forEach { action ->
            actionOptions.find { it.actionType == action.type }?.let { option ->
                val migratedEndBehavior = when {
                    action.endBehavior != null -> action.endBehavior
                    loaded.revertOnExit && EndBehaviorCatalog.supportsRevert(action.type) ->
                        EndBehavior(EndMode.REVERT)
                    else -> null
                }
                actionDrafts.add(
                    ActionDraft(
                        option = option,
                        config = action.config,
                        endBehavior = migratedEndBehavior
                    )
                )
            }
        }
        canonicalActionNodes.clear()
        loaded.canonicalNodes
            .filter { it.kind == com.nexaflow.domain.canonical.NodeSchemaKind.ACTION }
            .forEach(canonicalActionNodes::add)
        canonicalTriggerNodes.clear()
        loaded.canonicalNodes
            .filter { it.kind == com.nexaflow.domain.canonical.NodeSchemaKind.TRIGGER }
            .forEach(canonicalTriggerNodes::add)
        // Backward compatibility: the old global revert-on-exit toggle is now
        // expanded for every matching card independently, preserving duplicate
        // actions instead of merging their end behavior by type.
        selectedExitActions.clear()
        exitActionConfigs.clear()
        loaded.exitActions.forEach { action ->
            actionOptions.find { it.actionType == action.type }?.let { option ->
                selectedExitActions.add(option)
                exitActionConfigs[option.actionType] = action.config
            }
        }
    }
    /**
     * The builder's own savedStateHandle, stable for this destination: the
     * icon picker writes its result here, and the in-app map picker does the
     * same (plus the trigger index it was opened for). Reading it from the
     * navController instead would re-point at the top entry while a picker is
     * open and drop the result. The caller passes the entry's handle; fall
     * back to reading it once from the controller for standalone composition.
     */
    val stableSavedStateHandle = remember {
        savedStateHandle ?: navController.currentBackStackEntry?.savedStateHandle
    }

    /**
     * Opens the in-app map picker for a trigger. External maps apps are a
     * dead end for picking: modern Google Maps no longer handles ACTION_PICK
     * (and ACTION_VIEW never returns a point), so the builder now opens its
     * own OpenStreetMap screen where a crosshair + confirm button return the
     * exact coordinates via the shared savedStateHandle.
     */
    fun launchMapPicker(index: Int) {
        // Remember which trigger the picker was opened for. It lives in the
        // builder's own savedStateHandle (survives the navigation round-trip
        // and process death) so the returned point lands on the right row.
        stableSavedStateHandle?.set("map_picker_target", index)
        // Seed the embedded map with the trigger's current point + radius.
        val cfg = triggers.getOrNull(index)?.config.orEmpty()
        val lat = cfg["lat"]?.toDoubleOrNull() ?: 0.0
        val lng = cfg["lng"]?.toDoubleOrNull() ?: 0.0
        val radius = cfg["radius"]?.toIntOrNull() ?: 100
        stableSavedStateHandle?.set(
            "map_picker_init",
            String.format(Locale.US, "%f,%f,%d", lat, lng, radius)
        )
        navController.navigate("map_picker")
    }

    // ── "Use my current location": silently enable location (privileged),
    // grab a fix, fill the coordinates, then restore the previous mode. When no
    // elevated runtime exists, opens the system location settings and resumes
    // on return — the whole flow needs no permission dialogs beyond the one-time
    // runtime location permission.
    var pendingUseLocationIndex by remember { mutableStateOf<Int?>(null) }
    // Toggled by the permission/settings launchers so their callbacks can resume
    // the locate flow without a forward reference to the local function below.
    var resumeLocationFill by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) resumeLocationFill = true
    }
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Back from the system location settings: retry the pending fill.
        resumeLocationFill = true
    }
    fun locateAndFill(index: Int) {
        if (index !in triggers.indices) return
        if (!LocationAccess.hasLocationPermission(context)) {
            pendingUseLocationIndex = index
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        scope.launch {
            // Everything below must never crash the builder: location services,
            // elevated shells and the single-shot fix can all throw on odd ROM
            // states, and an uncaught exception here would FC the whole app.
            try {
                val wasEnabled = LocationAccess.isLocationEnabled(context)
                val previousMode = LocationAccess.currentLocationMode(context)
                val enabled = if (wasEnabled) true else LocationAccess.enableLocationSilently(context)
                if (!enabled) {
                    // No privileged path: one tap in the system settings screen.
                    pendingUseLocationIndex = index
                    runCatching {
                        locationSettingsLauncher.launch(
                            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )
                    }
                    return@launch
                }
                val fix = LocationAccess.getCurrentLocation(context, 12_000)
                if (fix != null && index in triggers.indices) {
                    triggers[index] = triggers[index].copy(
                        config = triggers[index].config +
                            ("lat" to fix.latitude.toString()) +
                            ("lng" to fix.longitude.toString()) +
                            ("source" to "current")
                    )
                } else {
                    scope.launch { snackbarHostState.showSnackbar(stringLocationFixFailed) }
                }
                if (!wasEnabled) LocationAccess.restoreLocationModeIfWeChanged(context, previousMode)
            } catch (t: Throwable) {
                Log.w(TAG, "Current-location fill failed", t)
                scope.launch { snackbarHostState.showSnackbar(stringLocationFixFailed) }
            }
        }
    }
    // Resumes a locate flow interrupted by the permission or settings screens.
    LaunchedEffect(resumeLocationFill) {
        if (resumeLocationFill) {
            resumeLocationFill = false
            pendingUseLocationIndex?.let { locateAndFill(it) }
        }
    }
    val scrollState = rememberScrollState()

    // Receive the icon picked in IconPickerScreen. The handle must stay bound
    // to THIS builder entry (see stableSavedStateHandle above).
    DisposableEffect(stableSavedStateHandle) {
        val handle = stableSavedStateHandle
        if (handle == null) {
            onDispose { }
        } else {
            val observer = Observer<Int> { index ->
                selectedIconIndex = index
            }
            handle.getLiveData<Int>("selected_icon").observeForever(observer)
            val colorObserver = Observer<Long> { color ->
                selectedIconColor = color
            }
            handle.getLiveData<Long>("selected_color").observeForever(colorObserver)
            val locationObserver = Observer<String> { value ->
                val coords = value.split(',')
                val lat = coords.getOrNull(0)?.toDoubleOrNull()
                val lng = coords.getOrNull(1)?.toDoubleOrNull()
                val index = handle.get<Int>("map_picker_target") ?: return@Observer
                if (lat != null && lng != null && index in triggers.indices) {
                    val radius = handle.get<String>("picked_radius")?.toIntOrNull()
                    triggers[index] = triggers[index].copy(
                        config = triggers[index].config +
                            ("lat" to lat.toString()) +
                            ("lng" to lng.toString()) +
                            (if (radius != null) mapOf("radius" to radius.toString()) else emptyMap()) +
                            ("source" to "selected")
                    )
                    handle.set("map_picker_target", null)
                }
            }
            handle.getLiveData<String>("picked_location").observeForever(locationObserver)
            onDispose {
                handle.getLiveData<Int>("selected_icon").removeObserver(observer)
                handle.getLiveData<Long>("selected_color").removeObserver(colorObserver)
                handle.getLiveData<String>("picked_location").removeObserver(locationObserver)
            }
        }
    }

    // Live elevated-permission status (root/Shizuku): re-probe the action
    // cards every time the screen resumes — e.g. after returning from the
    // Magisk/KernelSU grant dialog or the Shizuku grant screen — so the
    // colour-coded badge reflects the freshly granted state without leaving
    // and re-opening the task.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, stableSavedStateHandle) {
        val handle = stableSavedStateHandle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshTick++
                // Re-probe grantable capabilities (write settings, DND access)
                // and the memoized ROM/integration snapshot so a permission
                // granted in the system screen unlocks its locked builder row
                // immediately on return instead of after a process restart.
                com.nexaflow.core.rom.RomIntegrationManager.refresh(context)
                viewModel.refreshCapabilities()
                // Coming back from the picker: re-read the latest value from
                // the builder entry whenever a stable handle is available.
                handle?.get<Int>("selected_icon")?.let {
                    if (it in NexaFlowIcons.all.indices) selectedIconIndex = it
                }
                handle?.get<Long>("selected_color")?.let {
                    selectedIconColor = it
                }
                handle?.get<String>("picked_location")?.let { value ->
                    val coords = value.split(',')
                    val lat = coords.getOrNull(0)?.toDoubleOrNull()
                    val lng = coords.getOrNull(1)?.toDoubleOrNull()
                    val index = handle.get<Int>("map_picker_target")
                    if (lat != null && lng != null && index != null && index in triggers.indices) {
                        val radius = handle.get<String>("picked_radius")?.toIntOrNull()
                        triggers[index] = triggers[index].copy(
                            config = triggers[index].config +
                                ("lat" to lat.toString()) +
                                ("lng" to lng.toString()) +
                                (if (radius != null) mapOf("radius" to radius.toString()) else emptyMap()) +
                                ("source" to "selected")
                        )
                        handle.set("map_picker_target", null)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isEditing = automationId != null
    val canChooseStarterRoutine = !isEditing &&
        triggers.isEmpty() &&
        actionDrafts.isEmpty() &&
        availableTemplates.isNotEmpty()

    val stringPermissionDenied = stringResource(R.string.permission_denied_hint)
    // Permission request currently waiting for the user to confirm the
    // explain screen. The system dialog only opens after the user taps Continue.
    var pendingPermissions by remember { mutableStateOf<Array<String>?>(null) }
    // Special permission (settings-screen) request awaiting the explain screen.
    var pendingSpecialPermission by remember { mutableStateOf<SpecialPermission?>(null) }
    // Captures special requirements discovered while saving. A task containing
    // both a dangerous runtime permission (READ_PHONE_STATE) and a special
    // permission (exact alarm / elevated access) must not abandon the latter
    // after the Android runtime dialog closes.
    var specialPermissionsAfterRuntimeGrant by remember { mutableStateOf<List<SpecialPermission>>(emptyList()) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Surface denied permissions so the user knows why the feature may not work.
        if (grants.values.any { !it }) {
            specialPermissionsAfterRuntimeGrant = emptyList()
            scope.launch {
                snackbarHostState.showSnackbar(stringPermissionDenied)
            }
        } else {
            // Continue the same save flow with the highest-priority missing
            // special access. TIME requirements are collected first, therefore
            // an exact alarm request is never silently skipped for a task that
            // also changes the cellular network.
            val nextSpecial = specialPermissionsAfterRuntimeGrant.firstOrNull {
                !PermissionShortcuts.isGranted(context, it)
            }
            specialPermissionsAfterRuntimeGrant = emptyList()
            if (nextSpecial != null) pendingSpecialPermission = nextSpecial
            else ElevatedAccessShortcuts.requestBatteryOptimizationExemption(context)
        }
    }

    fun requestPermissions(permissions: Array<String>) {
        // Already-granted permissions need neither the explain screen nor the
        // system dialog, so skip straight past them on repeat visits.
        val allGranted = permissions.all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) return
        // Explain why the permission is needed BEFORE opening the system dialog.
        pendingPermissions = permissions
    }

    fun explainSpecialPermission(special: SpecialPermission) {
        // Explain why the permission is needed BEFORE opening its settings screen.
        pendingSpecialPermission = special
    }


    fun moveAction(from: Int, to: Int) {
        BuilderDraftOperations.move(actionDrafts, from, to)
    }

    fun moveTrigger(from: Int, to: Int) {
        val expanded = expandedTriggerIndex
        if (BuilderDraftOperations.move(triggers, from, to)) {
            expandedTriggerIndex = BuilderDraftOperations.movedExpandedIndex(expanded, from, to)
        }
    }

    fun moveConstraint(from: Int, to: Int) {
        BuilderDraftOperations.move(constraints, from, to)
    }

    fun showSnackbar(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    LaunchedEffect(saveError) {
        if (saveError != null) {
            showSnackbar(stringSaveFailed)
            viewModel.consumeSaveError()
        }
    }

    fun save(closeAfterSave: Boolean = true) {
        if (triggers.isNotEmpty() && canonicalTriggerNodes.isNotEmpty()) {
            showSnackbar(configurationContext.getString(R.string.canonical_mixed_trigger_error))
            return
        }
        if (triggers.isEmpty() && canonicalTriggerNodes.isEmpty()) {
            showSnackbar(stringNextNeedsTrigger)
            return
        }
        if (actionDrafts.isEmpty() && canonicalActionNodes.isEmpty()) {
            showSnackbar(stringNextNeedsAction)
            return
        }
        // The picker writes the selection into this entry's savedStateHandle.
        // Re-read it here as the source of truth: the LiveData observer may
        // miss the event under device recomposition timing, but the handle
        // itself always holds the latest pick.
        stableSavedStateHandle?.get<Int>("selected_icon")?.let {
            if (it in NexaFlowIcons.all.indices) selectedIconIndex = it
        }
        stableSavedStateHandle?.get<Long>("selected_color")?.let {
            selectedIconColor = it
        }
        val builtTriggers = triggers.map { draft ->
            Trigger(draft.type, draft.config)
        }
        val actions = actionDrafts.map { it.toAction() }
        val builtConstraints = constraints.map { Constraint(it.type, it.config) }
        val exitActions = selectedExitActions.map { Action(it.actionType, exitActionConfigs[it.actionType] ?: emptyMap()) }
        val saveJob = viewModel.saveAutomation(
            // A task can be created without making naming the first decision.
            // The default stays localized and users can still refine it in review.
            name = name.trim().ifBlank { stringDefaultTaskName },
            icon = NexaFlowIcons.all[selectedIconIndex].first,
            iconColor = selectedIconColor,
            triggers = builtTriggers,
            triggerMatch = triggerMatch,
            actions = actions,
            canonicalNodes = buildList {
                addAll(loadedAutomation?.canonicalNodes.orEmpty().filter {
                    it.kind != com.nexaflow.domain.canonical.NodeSchemaKind.ACTION &&
                        it.kind != com.nexaflow.domain.canonical.NodeSchemaKind.TRIGGER
                })
                addAll(canonicalActionNodes.mapIndexed { index, node -> node.copy(sequenceIndex = index) })
                addAll(canonicalTriggerNodes.mapIndexed { index, node -> node.copy(sequenceIndex = index) })
            },
            constraints = builtConstraints,
            exitActions = exitActions,
            // Unified end behavior: each action carries its own end behavior
            // (leave / restore / set value), so the global toggle stays off.
            // Runs are always immediate: the cooldown UI was removed entirely
            // and the engine gate is pinned to zero so every trigger fires at
            // once, no matter how often the event repeats.
            revertOnExit = false,
            cooldownSeconds = 0,
            maintenanceProfile = RoutineTemplateCatalog.find(appliedTemplateId)?.maintenanceProfile
                ?: loadedAutomation?.maintenanceProfile,
            startDisabled = !isEditing && appliedTemplateId != null
        )
        // Aggressive permission flow is requirement-aware: only grants that
        // still block this exact workflow are requested. A working Root or
        // Shizuku route therefore prevents redundant Android settings prompts.
        scope.launchAfterSave(saveJob) {
            var repairPlan = viewModel.freshPermissionRepairPlan(
                triggers = builtTriggers,
                actions = actions,
                exitActions = exitActions
            )
            val missingRuntime = repairPlan.runtimePermissions.filter {
                context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            // A verified elevated shell can grant a dangerous permission to
            // NexaFlow's own UID through `pm grant`. Do that first and use the
            // Android dialog only for permissions a ROM still leaves missing.
            val autoGrantAttempted = missingRuntime.isNotEmpty() &&
                withContext(Dispatchers.IO) { RootPermissionGranter.canAutoGrant() }
            val remainingRuntime = withContext(Dispatchers.IO) {
                if (autoGrantAttempted) {
                    RootPermissionGranter.grantRuntimePermissions(
                        context.applicationContext,
                        missingRuntime
                    ).remaining
                } else {
                    missingRuntime
                }
            }
            if (autoGrantAttempted) {
                // The elevated repair publishes an invalidation event; read a
                // fresh graph before deciding which special grant is still
                // necessary so satisfied alternative branches disappear.
                repairPlan = viewModel.freshPermissionRepairPlan(
                    triggers = builtTriggers,
                    actions = actions,
                    exitActions = exitActions
                )
            }
            val missingSpecial = repairPlan.specialPermissions
                .map { it.toUiSpecialPermission() }
                .filter { !PermissionShortcuts.isGranted(context, it) }
            val userPermissionFlowRequired = remainingRuntime.isNotEmpty() || missingSpecial.isNotEmpty()
            if (remainingRuntime.isNotEmpty()) {
                // The launcher callback continues directly to the first special
                // requirement rather than losing exact-alarm access after the
                // phone-state dialog succeeds.
                specialPermissionsAfterRuntimeGrant = missingSpecial
                requestPermissions(remainingRuntime.toTypedArray())
            } else {
                val firstMissingSpecial = missingSpecial.firstOrNull()
                if (firstMissingSpecial != null) {
                    explainSpecialPermission(firstMissingSpecial)
                } else {
                    // All runtime/special permissions satisfied: keep the monitoring
                    // service alive in the background by requesting the battery
                    // optimization exemption right away (system dialog, one tap).
                    ElevatedAccessShortcuts.requestBatteryOptimizationExemption(context)
                }
            }
            // The permission dialogs are owned by this composable. Popping the
            // builder immediately after save disposed it before either the exact
            // alarm or elevated-access dialog could be shown, leaving a task that
            // looked ready but could not run reliably. Stay until the user sees
            // the required next step; a later normal save can close the screen.
            if (closeAfterSave && !userPermissionFlowRequired) {
                navController.popBackStack()
            } else if (!closeAfterSave) {
                showSnackbar(stringSavedSuccessfully)
            }
        }
    }

    fun requestGrantForAction(type: ActionType) {
        val runtime = PermissionCatalog.runtimePermissionsFor(type)
        val special = PermissionCatalog.specialPermissionFor(type)
        when {
            runtime.isNotEmpty() -> requestPermissions(runtime.toTypedArray())
            special != null -> explainSpecialPermission(special)
            else -> scope.launch { snackbarHostState.showSnackbar(stringPermissionRequired) }
        }
    }

    fun requestGrantForTrigger(type: TriggerType) {
        val runtime = PermissionCatalog.runtimePermissionsFor(type)
        val special = PermissionCatalog.specialPermissionFor(type)
        when {
            runtime.isNotEmpty() -> requestPermissions(runtime.toTypedArray())
            special != null -> explainSpecialPermission(special)
            else -> scope.launch { snackbarHostState.showSnackbar(stringPermissionRequired) }
        }
    }

    Scaffold(
        topBar = {
            NexaFlowTopBar(
                title = if (isEditing) stringResource(R.string.edit_task_title) else stringResource(R.string.builder_title),
                onBack = {
                    // Walk through the guided creation flow before leaving it.
                    if (step > 0) step -= 1 else navController.popBackStack()
                },
                // No starter routines — clean, professional builder without templates
                actions = {}
            )
        },
        floatingActionButton = {
            BuilderBottomPrimaryAction(
                step = step,
                triggerCount = triggers.size,
                actionCount = actionDrafts.size,
                onAdvance = { nextStep -> step = nextStep },
                onSave = { save() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Progress bar above tabs — visual feedback for task creation progress.
            // M3 Expressive wavy linear bar: the 2026 determinate-progress language.
            val stepProgress by animateFloatAsState(
                targetValue = (step + 1) / 3f,
                animationSpec = nexaFlowSpatialSpec()
            )
            LinearWavyProgressIndicator(
                progress = { stepProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            // ── Top tabs: Triggers | Executions | When Task Ends ─────
            // Professional, easy navigation between the three builder sections.
            androidx.compose.material3.PrimaryTabRow(selectedTabIndex = step, containerColor = MaterialTheme.colorScheme.surface) {
                listOf(
                    R.string.section_when to Icons.Filled.Schedule,
                    R.string.section_actions to Icons.Filled.PlayArrow,
                    R.string.section_exit_behavior to Icons.Filled.Restore
                ).forEachIndexed { index, (titleRes, icon) ->
                    androidx.compose.material3.Tab(
                        selected = step == index,
                        onClick = { step = index },
                        text = { Text(stringResource(titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(icon, null) }
                    )
                }
            }

            // ── Name + icon belong to review, after the routine has meaning ─
            if (step == 2) NexaFlowCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(
                        icon = iconVector(NexaFlowIcons.all[selectedIconIndex].first),
                        containerColor = Color.White,
                        contentColor = Color(selectedIconColor),
                        size = 48,
                        modifier = Modifier.clickable {
                            // Preseed the picker with the current color so the
                            // palette opens on the task's own accent.
                            stableSavedStateHandle?.set("selected_color", selectedIconColor)
                            navController.navigate("icon_picker")
                        }
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(text = stringResource(R.string.name_hint)) },
                        singleLine = true
                    )
                }
            }


            // The transitionSpec lambda is not composable, so the specs are
            // read here in the composable body (transitionSpec captures them).
            val stepSpatial = nexaFlowSpatialSpec<IntOffset>()
            val stepEffects = nexaFlowEffectsSpec<Float>()
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    // Google 2026 directional step transition: content slides
                    // with the M3 Expressive spatial spring while fading.
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = stepSpatial,
                        initialOffsetX = { it / 3 * direction }
                    ) + fadeIn(animationSpec = stepEffects)) togetherWith
                        (slideOutHorizontally(
                            animationSpec = stepSpatial,
                            targetOffsetX = { -it / 3 * direction }
                        ) + fadeOut(animationSpec = stepEffects))
                },
                label = "wizardStep"
            ) { currentStep ->
                if (currentStep == 0) {
                // ── Step 1: trigger + its constraints ───────────────
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(text = stringResource(R.string.section_when))
                        val batteryTriggerContract = viewModel.executableCanonicalTriggers.firstOrNull {
                            it.definitionId == CanonicalBatteryTriggerDefinition.ID
                        }
                        if (batteryTriggerContract != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.canonical_battery_title), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.canonical_battery_subtitle), style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                    enabled = triggers.isEmpty(),
                                    onClick = {
                                    canonicalTriggerNodes.add(CanonicalBatteryTriggerDefinition.node(
                                        id = "native.trigger.${java.util.UUID.randomUUID().toString().replace("-", "")}",
                                        sequenceIndex = canonicalTriggerNodes.size,
                                ))
                                }) { Text(stringResource(R.string.canonical_add_battery)) }
                            }
                            canonicalTriggerNodes.forEachIndexed { index, node ->
                                val threshold = (node.arguments.firstOrNull { it.field == CanonicalBatteryTriggerDefinition.thresholdField }
                                    ?.value as? com.nexaflow.domain.canonical.IntegerValue)?.value?.toInt() ?: 20
                                NexaFlowCard {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("${stringResource(R.string.canonical_battery_threshold)}: $threshold%")
                                        androidx.compose.material3.Slider(
                                            value = threshold.toFloat(),
                                            onValueChange = { raw ->
                                                canonicalTriggerNodes[index] = CanonicalBatteryTriggerDefinition.node(
                                                    id = node.node.id.value,
                                                    sequenceIndex = index,
                                                    thresholdPercent = raw.toInt(),
                                                    direction = (node.arguments.getOrNull(1)?.value as? com.nexaflow.domain.canonical.EnumTokenValue)?.token ?: "BELOW",
                                                    chargerType = (node.arguments.getOrNull(2)?.value as? com.nexaflow.domain.canonical.EnumTokenValue)?.token ?: "ANY",
                                                    chargingState = (node.arguments.getOrNull(3)?.value as? com.nexaflow.domain.canonical.EnumTokenValue)?.token ?: "ANY",
                                                )
                                            },
                                            valueRange = 0f..100f,
                                            steps = 99,
                                        )
                                        val charger = (node.arguments.getOrNull(2)?.value as? com.nexaflow.domain.canonical.EnumTokenValue)?.token ?: "ANY"
                                        val chargingState = (node.arguments.getOrNull(3)?.value as? com.nexaflow.domain.canonical.EnumTokenValue)?.token ?: "ANY"
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = {
                                                canonicalTriggerNodes[index] = CanonicalBatteryTriggerDefinition.node(
                                                    id = node.node.id.value,
                                                    sequenceIndex = index,
                                                    thresholdPercent = threshold,
                                                    direction = if ((node.arguments.getOrNull(1)?.value as? com.nexaflow.domain.canonical.EnumTokenValue)?.token == "BELOW") "ABOVE" else "BELOW",
                                                    chargerType = (node.arguments.getOrNull(2)?.value as? com.nexaflow.domain.canonical.EnumTokenValue)?.token ?: "ANY",
                                                    chargingState = (node.arguments.getOrNull(3)?.value as? com.nexaflow.domain.canonical.EnumTokenValue)?.token ?: "ANY",
                                                )
                                            }) { Text(stringResource(R.string.canonical_battery_toggle_direction)) }
                                            TextButton(onClick = {
                                                val next = when (charger) { "ANY" -> "AC"; "AC" -> "USB"; "USB" -> "WIRELESS"; else -> "ANY" }
                                                canonicalTriggerNodes[index] = CanonicalBatteryTriggerDefinition.node(
                                                    id = node.node.id.value, sequenceIndex = index, thresholdPercent = threshold,
                                                    direction = (node.arguments.getOrNull(1)?.value as? com.nexaflow.domain.canonical.EnumTokenValue)?.token ?: "BELOW",
                                                    chargerType = next, chargingState = chargingState,
                                                )
                                            }) { Text(stringResource(R.string.canonical_battery_charger, charger)) }
                                            TextButton(onClick = {
                                                val next = when (chargingState) { "ANY" -> "CHARGING"; "CHARGING" -> "NOT_CHARGING"; else -> "ANY" }
                                                canonicalTriggerNodes[index] = CanonicalBatteryTriggerDefinition.node(
                                                    id = node.node.id.value, sequenceIndex = index, thresholdPercent = threshold,
                                                    direction = (node.arguments.getOrNull(1)?.value as? com.nexaflow.domain.canonical.EnumTokenValue)?.token ?: "BELOW",
                                                    chargerType = charger, chargingState = next,
                                                )
                                            }) { Text(stringResource(R.string.canonical_battery_charging_state, chargingState)) }
                                            TextButton(onClick = { canonicalTriggerNodes.removeAt(index) }) {
                                                Text(stringResource(R.string.remove))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (canonicalTriggerNodes.isEmpty()) NodeConfiguratorPanel(
                            title = stringResource(R.string.section_when),
                            searchQuery = triggerSearchQuery,
                            onSearchQueryChange = { triggerSearchQuery = it },
                            selectedCount = selectedTriggerTypes.size,
                            confirmLabel = stringResource(R.string.add_trigger),
                            confirmEnabled = selectedTriggerTypes.isNotEmpty(),
                            onConfirm = {
                                selectedTriggerTypes.forEach { type ->
                                    triggers.add(TriggerDraft(type, defaultTriggerConfig(type)))
                                }
                                expandedTriggerIndex = triggers.lastIndex.takeIf { it >= 0 }
                                selectedTriggerTypes.clear()
                            }
                        ) {
                            val visibleTriggers = if (triggerSearchQuery.isBlank()) {
                                supportedTriggers.filter { it != TriggerType.BATTERY }
                            } else {
                                supportedTriggers.filter { type -> type != TriggerType.BATTERY &&
                                    configurationContext.getString(type.labelRes())
                                        .contains(triggerSearchQuery, ignoreCase = true) ||
                                        configurationContext.getString(type.descRes())
                                            .contains(triggerSearchQuery, ignoreCase = true)
                                }
                            }
                            if (triggerSearchQuery.isNotBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    visibleTriggers.forEachIndexed { optionIndex, type ->
                                        TriggerOptionRow(
                                            type = type,
                                            checked = type in selectedTriggerTypes,
                                            alternatingIndex = optionIndex,
                                            availability = triggerAvailabilityByType[type]
                                                ?: BuilderOptionAvailability.READY,
                                            onBlockedClick = { requestGrantForTrigger(type) },
                                            onSelect = {
                                                if (type in selectedTriggerTypes) {
                                                    selectedTriggerTypes.remove(type)
                                                } else {
                                                    selectedTriggerTypes.add(type)
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.option_tier_all),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                CategoryAccordion(
                                    tabs = triggerCategories.map { category ->
                                        stringResource(category.headerRes) to category.icon()
                                    },
                                    expandedIndex = expandedTriggerCategory,
                                    onExpandedChange = { expandedTriggerCategory = it }
                                ) { categoryIndex ->
                                    val category = triggerCategories[categoryIndex]
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        visibleTriggers
                                            .filter {
                                                triggerCategoryOf[it] == category &&
                                                    AutomationOptionCatalog.tierFor(it) == OptionTier.BROWSE
                                            }
                                            .forEachIndexed { optionIndex, type ->
                                                TriggerOptionRow(
                                                    type = type,
                                                    checked = type in selectedTriggerTypes,
                                                    alternatingIndex = optionIndex,
                                                    availability = triggerAvailabilityByType[type]
                                                        ?: BuilderOptionAvailability.READY,
                                                    onBlockedClick = { requestGrantForTrigger(type) },
                                                    onSelect = {
                                                        if (type in selectedTriggerTypes) {
                                                            selectedTriggerTypes.remove(type)
                                                        } else {
                                                            selectedTriggerTypes.add(type)
                                                        }
                                                    }
                                                )
                                            }
                                    }
                                }

                                val advancedTriggers = visibleTriggers.filter {
                                    AutomationOptionCatalog.tierFor(it) == OptionTier.ADVANCED
                                }
                                if (advancedTriggers.isNotEmpty()) {
                                    AdvancedOptionsHeader(
                                        title = stringResource(R.string.option_tier_advanced),
                                        expanded = showAdvancedTriggerOptions,
                                        onToggle = {
                                            showAdvancedTriggerOptions = !showAdvancedTriggerOptions
                                        }
                                    )
                                    if (showAdvancedTriggerOptions) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            advancedTriggers.forEachIndexed { optionIndex, type ->
                                                TriggerOptionRow(
                                                    type = type,
                                                    checked = type in selectedTriggerTypes,
                                                    alternatingIndex = optionIndex,
                                                    availability = triggerAvailabilityByType[type]
                                                        ?: BuilderOptionAvailability.READY,
                                                    onBlockedClick = { requestGrantForTrigger(type) },
                                                    onSelect = {
                                                        if (type in selectedTriggerTypes) {
                                                            selectedTriggerTypes.remove(type)
                                                        } else {
                                                            selectedTriggerTypes.add(type)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    triggers.forEachIndexed { index, draft ->
                val triggerDragging = triggerDrag.draggedIndex == index
                TriggerEditorCard(
                    draft = draft,
                    index = index,
                    total = triggers.size,
                    modifier = Modifier.taskDragOffset(triggerDrag, draft, triggerDragging),
                    isDragging = triggerDragging,
                    onMoveUp = { moveTrigger(index, index - 1) },
                    onMoveDown = { moveTrigger(index, index + 1) },
                    onDragStart = { startDrag(triggerDrag, index) },
                    onDragDelta = { dragBy(triggerDrag, triggers, it) { f, t -> moveTrigger(f, t) } },
                    onDragEnd = { endDrag(triggerDrag) },
                    onConfigChange = { updated ->
                        triggers[index] = updated
                    },
                    onRemove = {
                        triggers.removeAt(index)
                        expandedTriggerIndex = when {
                            expandedTriggerIndex == index -> null
                            expandedTriggerIndex != null && expandedTriggerIndex!! > index -> expandedTriggerIndex!! - 1
                            else -> expandedTriggerIndex
                        }
                    },
                    expanded = expandedTriggerIndex == index,
                    onExpandedChange = { expanded ->
                        expandedTriggerIndex = if (expanded) index else null
                    },
                    onPickApp = { appPickerTarget = "trigger:$index" },
                    onPickBluetooth = { bluetoothPickerTarget = index },
                    onPickCalendar = { calendarPickerTarget = index },
                    onRequestPermission = { requestPermissions(it) },
                    onExplainSpecial = { explainSpecialPermission(it) },
                    refreshKey = permissionRefreshTick,
                    onPickFromMap = { launchMapPicker(index) },
                    onUseCurrentLocation = { locateAndFill(index) }
                )
            }

                // Combine rule: visible only when two or more triggers exist —
                // with one trigger the choice has no meaning.
                if (triggers.size > 1) {
                    TriggerMatchSelector(
                        selected = triggerMatch,
                        onSelect = { triggerMatchName = it.name },
                        allModeSemantics = triggerMatchBuiltWarning(triggers),
                        legacyReviewRequired =
                            loadedAutomation?.workflowVersion
                                ?.let { version ->
                                    version <
                                        Automation.OCCURRENCE_AWARE_TRIGGER_SEMANTICS_VERSION &&
                                        triggerMatch == TriggerMatchMode.ALL &&
                                        triggerMatchBuiltWarning(triggers) !=
                                            TriggerMatchPolicy.AllModeEventSemantics.NONE
                                } == true,
                    )
                }

                    }
                }
            } else if (currentStep == 1) {
                // ── Step 2: actions + end behavior ───────────────────
                NexaFlowCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {                        // ── THEN (actions) ──────────────────────────
                            SectionHeader(text = stringResource(R.string.section_actions))
                            NodeConfiguratorPanel(
                                title = stringResource(R.string.section_actions),
                                searchQuery = actionSearchQuery,
                                onSearchQueryChange = { actionSearchQuery = it },
                                selectedCount = selectedActionTypes.size,
                                confirmLabel = stringResource(R.string.add_action),
                                confirmEnabled = selectedActionTypes.isNotEmpty(),
                                onConfirm = {
                                    selectedActionTypes.forEach { type ->
                                        supportedActions.firstOrNull { it.actionType == type }?.let { option ->
                                            actionDrafts.add(ActionDraft(option = option))
                                        }
                                    }
                                    expandedActionCardId = actionDrafts.lastOrNull()?.id
                                    selectedActionTypes.clear()
                                }
                            ) {
                                val visibleActions = if (actionSearchQuery.isBlank()) {
                                    supportedActions
                                } else {
                                    supportedActions.filter { option ->
                                        configurationContext.getString(option.titleRes)
                                            .contains(actionSearchQuery, ignoreCase = true) ||
                                            configurationContext.getString(option.subtitleRes)
                                                .contains(actionSearchQuery, ignoreCase = true)
                                    }
                                }
                                if (actionSearchQuery.isNotBlank()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        visibleActions.forEachIndexed { optionIndex, option ->
                                            ActionOptionRow(
                                                option = option,
                                                checked = option.actionType in selectedActionTypes,
                                                alternatingIndex = optionIndex,
                                                availability = actionAvailabilityByType[option.actionType]
                                                    ?: BuilderOptionAvailability.READY,
                                                onBlockedClick = { requestGrantForAction(option.actionType) },
                                                onToggle = {
                                                    if (option.actionType in selectedActionTypes) {
                                                        selectedActionTypes.remove(option.actionType)
                                                    } else {
                                                        selectedActionTypes.add(option.actionType)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.option_tier_all),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    CategoryAccordion(
                                        tabs = actionCategories.map { category ->
                                            stringResource(category.headerRes) to category.icon()
                                        },
                                        expandedIndex = expandedActionCategory,
                                        onExpandedChange = { expandedActionCategory = it }
                                    ) { categoryIndex ->
                                        val category = actionCategories[categoryIndex]
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            optionsForActionCategory(category, visibleActions)
                                                .filter {
                                                    AutomationOptionCatalog.tierFor(it.actionType) ==
                                                        OptionTier.BROWSE
                                                }
                                                .forEachIndexed { optionIndex, option ->
                                                    ActionOptionRow(
                                                        option = option,
                                                        checked = option.actionType in selectedActionTypes,
                                                        alternatingIndex = optionIndex,
                                                        availability = actionAvailabilityByType[option.actionType]
                                                            ?: BuilderOptionAvailability.READY,
                                                        onBlockedClick = { requestGrantForAction(option.actionType) },
                                                        onToggle = {
                                                            if (option.actionType in selectedActionTypes) {
                                                                selectedActionTypes.remove(option.actionType)
                                                            } else {
                                                                selectedActionTypes.add(option.actionType)
                                                            }
                                                        }
                                                    )
                                                }
                                        }
                                    }

                                    val advancedActions = visibleActions.filter {
                                        AutomationOptionCatalog.tierFor(it.actionType) ==
                                            OptionTier.ADVANCED
                                    }
                                    if (advancedActions.isNotEmpty()) {
                                        AdvancedOptionsHeader(
                                            title = stringResource(R.string.option_tier_advanced),
                                            expanded = showAdvancedActionOptions,
                                            onToggle = {
                                                showAdvancedActionOptions = !showAdvancedActionOptions
                                            }
                                        )
                                        if (showAdvancedActionOptions) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                advancedActions.forEachIndexed { optionIndex, option ->
                                                    ActionOptionRow(
                                                        option = option,
                                                        checked = option.actionType in selectedActionTypes,
                                                        alternatingIndex = optionIndex,
                                                        availability = actionAvailabilityByType[option.actionType]
                                                            ?: BuilderOptionAvailability.READY,
                                                        onBlockedClick = { requestGrantForAction(option.actionType) },
                                                        onToggle = {
                                                            if (option.actionType in selectedActionTypes) {
                                                                selectedActionTypes.remove(option.actionType)
                                                            } else {
                                                                selectedActionTypes.add(option.actionType)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        actionDrafts.forEachIndexed { index, draft ->
                            key(draft.id) {
                                val actionDragging = actionDrag.draggedIndex == index
                                SelectedActionCard(
                                modifier = Modifier.taskDragOffset(actionDrag, draft, actionDragging),
                                isDragging = actionDragging,
                                onDragStart = { startDrag(actionDrag, index) },
                                onDragDelta = { dragBy(actionDrag, actionDrafts, it) { from, to -> moveAction(from, to) } },
                                onDragEnd = { endDrag(actionDrag) },
                                option = draft.option,
                                index = index,
                                total = actionDrafts.size,
                                config = draft.config,
                                onConfigChange = { config ->
                                    val current = actionDrafts.indexOfFirst { it.id == draft.id }
                                    if (current >= 0) actionDrafts[current] = actionDrafts[current].copy(config = config)
                                },
                                onMoveUp = { moveAction(index, index - 1) },
                                onMoveDown = { moveAction(index, index + 1) },
                                onRemove = {
                                    val current = actionDrafts.indexOfFirst { it.id == draft.id }
                                    if (current >= 0) actionDrafts.removeAt(current)
                                    if (expandedActionCardId == draft.id) expandedActionCardId = null
                                },
                                onPickApp = { appPickerTarget = "action:${draft.id}" },
                                onRequestPermission = { requestPermissions(it) },
                                onExplainSpecial = { explainSpecialPermission(it) },
                                refreshKey = permissionRefreshTick,
                                context = context,
                                availableVariables = availableVariables,
                                automations = automations,
                                onPluginConfigure = {
                                    if (draft.config["package"].isNullOrBlank() || draft.config["receiver"].isNullOrBlank()) {
                                        pluginPickerTarget = draft.id
                                    } else {
                                        configurePlugin(
                                            actionId = draft.id,
                                            packageName = draft.config["package"],
                                            receiver = draft.config["receiver"],
                                            editActivityClass = draft.config["editActivity"],
                                            config = draft.config
                                        )
                                    }
                                },
                                expanded = expandedActionCardId == draft.id,
                                onExpandedChange = { expanded ->
                                    expandedActionCardId = if (expanded) draft.id else null
                                }
                                )
                            }
                        }
                        val delayDefinition = viewModel.executableCanonicalActions.firstOrNull {
                            it.definitionId == CanonicalDelayDefinition.ID
                        }
                        if (delayDefinition != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = stringResource(R.string.canonical_actions_title), style = MaterialTheme.typography.titleSmall)
                                    Text(text = stringResource(R.string.canonical_actions_subtitle), style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = {
                                    canonicalActionNodes.add(
                                        CanonicalDelayDefinition.node(
                                            durationMs = 5_000L,
                                            nodeId = "native.action.${java.util.UUID.randomUUID().toString().replace("-", "")}",
                                            sequenceIndex = canonicalActionNodes.size,
                                        ),
                                    )
                                }) { Text(stringResource(R.string.canonical_add_delay)) }
                            }
                            canonicalActionNodes.forEachIndexed { index, nativeNode ->
                                val wait = nativeNode.node as? WaitNode
                                if (wait != null) {
                                    NexaFlowCard {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(text = nativeNode.schema.title, style = MaterialTheme.typography.titleSmall)
                                            Text(text = "${(wait.duration.milliseconds / 1_000.0)} s", style = MaterialTheme.typography.bodySmall)
                                            androidx.compose.material3.Slider(
                                                value = wait.duration.milliseconds.toFloat(),
                                                onValueChange = { raw ->
                                                    val updatedValue = DurationValue(raw.toLong())
                                                    canonicalActionNodes[index] = nativeNode.copy(
                                                        node = wait.copy(duration = updatedValue),
                                                        arguments = listOf(com.nexaflow.domain.canonical.NodeFieldValue(
                                                            CanonicalDelayDefinition.durationField, updatedValue,
                                                        )),
                                                    )
                                                },
                                                valueRange = 0f..CanonicalDelayDefinition.MAX_DURATION_MS.toFloat(),
                                                steps = 59,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(enabled = index > 0, onClick = {
                                                    BuilderDraftOperations.move(canonicalActionNodes, index, index - 1)
                                                }) { Text("↑") }
                                                TextButton(enabled = index < canonicalActionNodes.lastIndex, onClick = {
                                                    BuilderDraftOperations.move(canonicalActionNodes, index, index + 1)
                                                }) { Text("↓") }
                                                TextButton(onClick = { canonicalActionNodes.removeAt(index) }) {
                                                    Text(stringResource(R.string.remove_action))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            } else {
                // ── Step 3: When task ends — strictly exit behavior with circular icons
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (actionDrafts.isEmpty()) {
                        NexaFlowCard {
                            Text(
                                text = stringResource(R.string.end_behavior_add_first),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        val actionsWithEndOptions = actionDrafts.filter { draft ->
                            draft.option.actionType in EndBehaviorCatalog.toggleActions ||
                                draft.option.actionType in EndBehaviorCatalog.valueActions ||
                                draft.option.actionType in EndBehaviorCatalog.revertOnlyActions
                        }
                        if (actionsWithEndOptions.isEmpty()) {
                            NexaFlowCard {
                                Text(
                                    text = stringResource(R.string.end_behavior_none_supported),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else {
                            actionsWithEndOptions.forEach { draft ->
                                NexaFlowCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        IconBadge(
                                            icon = draft.option.icon,
                                            containerColor = Color.White,
                                            contentColor = Color(selectedIconColor)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(draft.option.titleRes),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = stringResource(draft.option.subtitleRes),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    EndBehaviorEditor(
                                        actionType = draft.option.actionType,
                                        behavior = draft.endBehavior,
                                        onBehaviorChange = { behavior ->
                                            val current = actionDrafts.indexOfFirst { it.id == draft.id }
                                            if (current >= 0) actionDrafts[current] = actionDrafts[current].copy(endBehavior = behavior)
                                        },
                                        showLabel = false
                                    )
                                }
                            }
                        }
                        // Extra exit actions (custom when task ends)
                        if (selectedExitActions.isNotEmpty() || actionsWithEndOptions.isNotEmpty()) {
                            NexaFlowCard {
                                SectionHeader(text = stringResource(R.string.exit_extra_section))
                                if (selectedExitActions.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.exit_extra_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    selectedExitActions.forEach { option ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            IconBadge(
                                                icon = option.icon,
                                                containerColor = Color.White,
                                                contentColor = Color(selectedIconColor)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(option.titleRes),
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = stringResource(option.subtitleRes),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = stringResource(R.string.section_constraints),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (constraints.isEmpty()) {
                                stringResource(R.string.constraints_empty_hint)
                            } else {
                                stringResource(R.string.section_constraints)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        SectionHeader(
                            text = stringResource(R.string.section_constraints),
                            trailing = {
                                IconButton(onClick = { showConstraintPicker = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.add_constraint)
                                    )
                                }
                            }
                        )
                        constraints.forEachIndexed { index, draft ->
                            val constraintDragging = constraintDrag.draggedIndex == index
                            ConstraintEditorCard(
                                draft = draft,
                                index = index,
                                total = constraints.size,
                                modifier = Modifier.taskDragOffset(constraintDrag, draft, constraintDragging),
                                isDragging = constraintDragging,
                                onMoveUp = { moveConstraint(index, index - 1) },
                                onMoveDown = { moveConstraint(index, index + 1) },
                                onDragStart = { startDrag(constraintDrag, index) },
                                onDragDelta = { dragBy(constraintDrag, constraints, it) { f, t -> moveConstraint(f, t) } },
                                onDragEnd = { endDrag(constraintDrag) },
                                initiallyExpanded = index == lastAddedConstraint,
                                onConfigChange = { constraints[index] = it },
                                onRemove = { constraints.removeAt(index) }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    if (showConstraintPicker) {
        ConstraintTypePickerDialog(
            onPick = { type ->
                constraints.add(ConstraintDraft(type, defaultConstraintConfig(type)))
                lastAddedConstraint = constraints.lastIndex
                showConstraintPicker = false
            },
            onDismiss = { showConstraintPicker = false }
        )
    }

    pluginPickerTarget?.let { actionId ->
        PluginPickerDialog(
            plugins = plugins,
            onRefresh = { viewModel.refreshPlugins() },
            onPick = { plugin, highRiskApproved ->
                pluginPickerTarget = null
                configurePlugin(
                    actionId = actionId,
                    packageName = plugin.packageName,
                    receiver = plugin.receiverClass,
                    editActivityClass = plugin.editActivityClass,
                    config = emptyMap(),
                    highRiskApproved = highRiskApproved
                )
            },
            onDismiss = {
                pluginPickerTarget = null
                // Dropping the picker without configuring removes only the
                // unconfigured plugin card that opened this dialog.
                val draftIndex = actionDrafts.indexOfFirst { it.id == actionId }
                if (draftIndex >= 0 && actionDrafts[draftIndex].config.isEmpty()) {
                    actionDrafts.removeAt(draftIndex)
                }
            }
        )
    }

    bluetoothPickerTarget?.let { index ->
        if (index in triggers.indices) {
            BluetoothDevicePickerDialog(
                onPick = { device ->
                    triggers[index] = triggers[index].copy(
                        config = mapOf(
                            "deviceName" to device.name,
                            "deviceAddress" to device.address,
                            "event" to (triggers[index].config["event"] ?: "CONNECTED")
                        )
                    )
                    bluetoothPickerTarget = null
                },
                onDismiss = { bluetoothPickerTarget = null },
                preSelectedAddress = triggers[index].config["deviceAddress"]
            )
        } else {
            bluetoothPickerTarget = null
        }
    }

    calendarPickerTarget?.let { index ->
        if (index in triggers.indices) {
            CalendarPickerDialog(
                onPick = { calendar ->
                    triggers[index] = triggers[index].copy(
                        config = mapOf(
                            "calendar" to calendar.name,
                            "contains" to (triggers[index].config["contains"] ?: ""),
                            "event" to (triggers[index].config["event"] ?: "EVENT_START"),
                            "beforeMinutes" to (triggers[index].config["beforeMinutes"] ?: "0")
                        )
                    )
                    calendarPickerTarget = null
                },
                onDismiss = { calendarPickerTarget = null },
                preSelectedName = triggers[index].config["calendar"]
            )
        } else {
            calendarPickerTarget = null
        }
    }

    appPickerTarget?.let { target ->
        val triggerIndex = target.removePrefix("trigger:").toIntOrNull()
        if (triggerIndex != null) {
            val triggerPackages = (triggers[triggerIndex].config["packages"] ?: triggers[triggerIndex].config["package"] ?: "")
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            AppPickerDialog(
                onPickSingle = { app ->
                    val merged = (triggerPackages + app.packageName).distinct()
                    val current = triggers[triggerIndex]
                    triggers[triggerIndex] = current.copy(
                        config = mapOf("packages" to merged.joinToString(","))
                    )
                    appPickerTarget = null
                },
                onPickMultiple = { apps ->
                    val current = triggers[triggerIndex]
                    triggers[triggerIndex] = current.copy(
                        config = mapOf("packages" to apps.joinToString(",") { it.packageName })
                    )
                    appPickerTarget = null
                },
                multiSelect = true,
                preSelectedPackages = triggerPackages,
                recentPackages = packagesUsedByOtherTasks(viewModel, excludeAutomationId = automationId),
                onDismiss = { appPickerTarget = null }
            )
        } else {
            val actionId = target.removePrefix("action:")
            val actionIndex = actionDrafts.indexOfFirst { it.id == actionId }
            val draft = actionDrafts.getOrNull(actionIndex)
            if (draft == null) {
                appPickerTarget = null
            } else {
                val isOpenApp = draft.option.actionType == ActionType.SYSTEM_OPEN_APP
                val isSinglePickAction = draft.option.actionType in setOf(
                    ActionType.APPLICATION_CLOSE_APP,
                    ActionType.APPLICATION_OPEN_APP_SETTINGS
                )
                val isMultiPickAction = draft.option.actionType in setOf(
                    ActionType.SYSTEM_BLOCK_NOTIFICATION,
                    ActionType.SYSTEM_CLEAR_APP_NOTIFICATIONS
                )
                when {
                    isOpenApp -> {
                        val pre = (draft.config["packages"] ?: draft.config["package"] ?: "")
                            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        AppPickerDialog(
                            onPickSingle = { app ->
                                val merged = (pre + app.packageName).distinct()
                                val current = actionDrafts.getOrNull(actionIndex)
                                if (current?.id == actionId) {
                                    actionDrafts[actionIndex] = current.copy(
                                        config = current.config + ("packages" to merged.joinToString(","))
                                    )
                                }
                                appPickerTarget = null
                            },
                            onPickMultiple = { packages ->
                                val current = actionDrafts.getOrNull(actionIndex)
                                if (current?.id == actionId) {
                                    actionDrafts[actionIndex] = current.copy(
                                        config = current.config + ("packages" to packages.joinToString(",") { it.packageName })
                                    )
                                }
                                appPickerTarget = null
                            },
                            multiSelect = true,
                            preSelectedPackages = pre,
                            recentPackages = packagesUsedByOtherTasks(viewModel, excludeAutomationId = automationId),
                            onDismiss = { appPickerTarget = null }
                        )
                    }
                    isSinglePickAction -> AppPickerDialog(
                        onPickSingle = { app ->
                            val current = actionDrafts.getOrNull(actionIndex)
                            if (current?.id == actionId) {
                                actionDrafts[actionIndex] = current.copy(
                                    config = current.config + ("package" to app.packageName)
                                )
                            }
                            appPickerTarget = null
                        },
                        onDismiss = { appPickerTarget = null }
                    )
                    isMultiPickAction -> {
                        val pre = (draft.config["packages"] ?: draft.config["package"] ?: "")
                            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        AppPickerDialog(
                            onPickSingle = { app ->
                                val merged = (pre + app.packageName).distinct()
                                val current = actionDrafts.getOrNull(actionIndex)
                                if (current?.id == actionId) {
                                    actionDrafts[actionIndex] = current.copy(
                                        config = current.config + ("packages" to merged.joinToString(","))
                                    )
                                }
                                appPickerTarget = null
                            },
                            onPickMultiple = { apps ->
                                val current = actionDrafts.getOrNull(actionIndex)
                                if (current?.id == actionId) {
                                    actionDrafts[actionIndex] = current.copy(
                                        config = current.config + ("packages" to apps.joinToString(",") { it.packageName })
                                    )
                                }
                                appPickerTarget = null
                            },
                            multiSelect = true,
                            preSelectedPackages = pre,
                            recentPackages = packagesUsedByOtherTasks(viewModel, excludeAutomationId = automationId),
                            onDismiss = { appPickerTarget = null }
                        )
                    }
                    else -> appPickerTarget = null
                }
            }
        }
    }

    // Explain screens shown before granting a permission.
    pendingPermissions?.let { permissions ->
        PermissionExplainDialog(
            info = remember(permissions) { permissionExplainInfo(permissions) },
            onContinue = {
                pendingPermissions = null
                permissionLauncher.launch(permissions)
            },
            onDismiss = { pendingPermissions = null }
        )
    }
    pendingSpecialPermission?.let { special ->
        PermissionExplainDialog(
            info = specialPermissionExplainInfo(special),
            onContinue = {
                pendingSpecialPermission = null
                PermissionShortcuts.openSpecial(context, special)
            },
            onDismiss = { pendingSpecialPermission = null }
        )
    }
}

/** Localized presentation label for a bundled, capability-filtered starter routine. */
