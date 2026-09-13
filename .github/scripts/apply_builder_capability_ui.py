from pathlib import Path
import re


def require_sub(text: str, pattern: str, replacement: str, expected: int, label: str, flags: int = 0) -> str:
    updated, count = re.subn(pattern, replacement, text, flags=flags)
    if count != expected:
        raise SystemExit(f"{label}: expected {expected} replacement(s), found {count}")
    return updated


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


screen_path = Path("feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt")
screen = screen_path.read_text()

state_pattern = re.escape(
    "    // One reactive capability-engine snapshot is composed with Android/ROM\n"
    "    // compatibility. Options with no executable backend are not rendered in\n"
    "    // picker, browse, common or search paths.\n"
    "    val capabilitySnapshot by viewModel.capabilitySnapshot.collectAsStateWithLifecycle()\n"
    "    val supportedActions = remember(context, capabilitySnapshot) {\n"
    "        CompatibilityGate.supportedActionOptions(context, capabilitySnapshot)\n"
    "    }\n"
    "    val supportedTriggers = remember(context, capabilitySnapshot) {\n"
    "        CompatibilityGate.supportedTriggerOptions(context, capabilitySnapshot)\n"
    "    }\n"
)
state_replacement = (
    "    // Compose one live capability snapshot with Android/ROM compatibility.\n"
    "    // Unsupported entries stay hidden, while grantable or temporarily unavailable\n"
    "    // entries remain discoverable and are rendered as locked rows below.\n"
    "    val capabilitySnapshot by viewModel.capabilitySnapshot.collectAsStateWithLifecycle()\n"
    "    val actionOptionStates = remember(context, capabilitySnapshot) {\n"
    "        CompatibilityGate.actionOptionStates(context, capabilitySnapshot)\n"
    "            .filter { it.availability != BuilderOptionAvailability.UNSUPPORTED }\n"
    "    }\n"
    "    val triggerOptionStates = remember(context, capabilitySnapshot) {\n"
    "        CompatibilityGate.triggerOptionStates(context, capabilitySnapshot)\n"
    "            .filter { it.availability != BuilderOptionAvailability.UNSUPPORTED }\n"
    "    }\n"
    "    val supportedActions = remember(actionOptionStates) { actionOptionStates.map { it.option } }\n"
    "    val actionAvailabilityByType = remember(actionOptionStates) {\n"
    "        actionOptionStates.associate { it.option.actionType to it.availability }\n"
    "    }\n"
    "    val supportedTriggers = remember(triggerOptionStates) { triggerOptionStates.map { it.type } }\n"
    "    val triggerAvailabilityByType = remember(triggerOptionStates) {\n"
    "        triggerOptionStates.associate { it.type to it.availability }\n"
    "    }\n"
)
screen = require_sub(screen, state_pattern, state_replacement, 1, "capability state block")

screen = replace_once(
    screen,
    "    val stringLocationFixFailed = stringResource(R.string.location_fix_failed)\n",
    "    val stringLocationFixFailed = stringResource(R.string.location_fix_failed)\n"
    "    val stringPermissionRequired = stringResource(R.string.permission_denied_hint)\n",
    "permission snackbar string",
)

helpers = (
    "    fun requestGrantForAction(type: ActionType) {\n"
    "        val runtime = PermissionCatalog.runtimePermissionsFor(type)\n"
    "        val special = PermissionCatalog.specialPermissionFor(type)\n"
    "        when {\n"
    "            runtime.isNotEmpty() -> requestPermissions(runtime.toTypedArray())\n"
    "            special != null -> explainSpecialPermission(special)\n"
    "            else -> scope.launch { snackbarHostState.showSnackbar(stringPermissionRequired) }\n"
    "        }\n"
    "    }\n\n"
    "    fun requestGrantForTrigger(type: TriggerType) {\n"
    "        val runtime = PermissionCatalog.runtimePermissionsFor(type)\n"
    "        val special = PermissionCatalog.specialPermissionFor(type)\n"
    "        when {\n"
    "            runtime.isNotEmpty() -> requestPermissions(runtime.toTypedArray())\n"
    "            special != null -> explainSpecialPermission(special)\n"
    "            else -> scope.launch { snackbarHostState.showSnackbar(stringPermissionRequired) }\n"
    "        }\n"
    "    }\n\n"
)
screen = replace_once(screen, "    Scaffold(\n", helpers + "    Scaffold(\n", "grant helpers")

trigger_pattern = r"(?P<indent>\s+)alternatingIndex = optionIndex,\n(?P=indent)onSelect = \{"
trigger_repl = (
    r"\g<indent>alternatingIndex = optionIndex,\n"
    r"\g<indent>availability = triggerAvailabilityByType[type] ?: BuilderOptionAvailability.READY,\n"
    r"\g<indent>onBlockedClick = { requestGrantForTrigger(type) },\n"
    r"\g<indent>onSelect = {"
)
screen = require_sub(screen, trigger_pattern, trigger_repl, 2, "trigger picker rows")

action_pattern = r"(?P<indent>\s+)alternatingIndex = optionIndex,\n(?P=indent)onToggle = \{"
action_repl = (
    r"\g<indent>alternatingIndex = optionIndex,\n"
    r"\g<indent>availability = actionAvailabilityByType[option.actionType] ?: BuilderOptionAvailability.READY,\n"
    r"\g<indent>onBlockedClick = { requestGrantForAction(option.actionType) },\n"
    r"\g<indent>onToggle = {"
)
screen = require_sub(screen, action_pattern, action_repl, 2, "action picker rows")
screen_path.write_text(screen)

components_path = Path("feature/automation-builder/src/main/java/com/nexaflow/feature/builder/BuilderComponents.kt")
components = components_path.read_text()

components = replace_once(
    components,
    "@Composable\nfun ActionOptionRow(\n"
    "    option: ActionOption,\n"
    "    checked: Boolean,\n"
    "    onToggle: () -> Unit,\n"
    "    modifier: Modifier = Modifier,\n"
    "    alternatingIndex: Int? = null\n"
    ") {\n",
    "@Composable\ninternal fun ActionOptionRow(\n"
    "    option: ActionOption,\n"
    "    checked: Boolean,\n"
    "    onToggle: () -> Unit,\n"
    "    availability: BuilderOptionAvailability = BuilderOptionAvailability.READY,\n"
    "    onBlockedClick: () -> Unit = {},\n"
    "    modifier: Modifier = Modifier,\n"
    "    alternatingIndex: Int? = null\n"
    ") {\n",
    "action row signature",
)
components = replace_once(
    components,
    "        onToggle = onToggle,\n"
    "        modifier = modifier,\n"
    "        alternatingIndex = alternatingIndex\n"
    "    )\n"
    "}\n\n"
    "/**\n"
    " * A condition choice",
    "        onToggle = onToggle,\n"
    "        availability = availability,\n"
    "        onBlockedClick = onBlockedClick,\n"
    "        modifier = modifier,\n"
    "        alternatingIndex = alternatingIndex\n"
    "    )\n"
    "}\n\n"
    "/**\n"
    " * A condition choice",
    "action row forwarding",
)
components = replace_once(
    components,
    "@Composable\nfun TriggerOptionRow(\n"
    "    type: TriggerType,\n"
    "    checked: Boolean,\n"
    "    onSelect: () -> Unit,\n"
    "    modifier: Modifier = Modifier,\n"
    "    alternatingIndex: Int? = null\n"
    ") {\n",
    "@Composable\ninternal fun TriggerOptionRow(\n"
    "    type: TriggerType,\n"
    "    checked: Boolean,\n"
    "    onSelect: () -> Unit,\n"
    "    availability: BuilderOptionAvailability = BuilderOptionAvailability.READY,\n"
    "    onBlockedClick: () -> Unit = {},\n"
    "    modifier: Modifier = Modifier,\n"
    "    alternatingIndex: Int? = null\n"
    ") {\n",
    "trigger row signature",
)
components = replace_once(
    components,
    "        onToggle = onSelect,\n"
    "        modifier = modifier,\n"
    "        alternatingIndex = alternatingIndex\n"
    "    )\n"
    "}\n\n"
    "@Composable\n"
    "private fun CatalogOptionRow(",
    "        onToggle = onSelect,\n"
    "        availability = availability,\n"
    "        onBlockedClick = onBlockedClick,\n"
    "        modifier = modifier,\n"
    "        alternatingIndex = alternatingIndex\n"
    "    )\n"
    "}\n\n"
    "@Composable\n"
    "private fun CatalogOptionRow(",
    "trigger row forwarding",
)

prefix, catalog = components.split("@Composable\nprivate fun CatalogOptionRow(", 1)
catalog = "@Composable\nprivate fun CatalogOptionRow(" + catalog
catalog = replace_once(
    catalog,
    "    checked: Boolean,\n"
    "    onToggle: () -> Unit,\n"
    "    modifier: Modifier,\n"
    "    alternatingIndex: Int?\n"
    ") {\n"
    "    val rowSurface = alternatingIndex?.let { alternatingSurfaceColor(it) }\n",
    "    checked: Boolean,\n"
    "    onToggle: () -> Unit,\n"
    "    availability: BuilderOptionAvailability,\n"
    "    onBlockedClick: () -> Unit,\n"
    "    modifier: Modifier,\n"
    "    alternatingIndex: Int?\n"
    ") {\n"
    "    val rowSurface = alternatingIndex?.let { alternatingSurfaceColor(it) }\n"
    "    val isReady = availability == BuilderOptionAvailability.READY\n"
    "    val isGrantable = availability == BuilderOptionAvailability.PERMISSION_REQUIRED\n"
    "    val lockedMessage = when (availability) {\n"
    "        BuilderOptionAvailability.READY -> null\n"
    "        BuilderOptionAvailability.PERMISSION_REQUIRED -> stringResource(R.string.permission_denied_hint)\n"
    "        BuilderOptionAvailability.UNAVAILABLE,\n"
    "        BuilderOptionAvailability.UNSUPPORTED -> stringResource(R.string.elevated_status_unavailable)\n"
    "    }\n",
    "catalog state",
)
catalog = replace_once(
    catalog,
    "            .clickable(onClick = onToggle)\n",
    "            .clickable(\n"
    "                enabled = isReady || isGrantable,\n"
    "                onClick = if (isReady) onToggle else onBlockedClick\n"
    "            )\n",
    "catalog click policy",
)
catalog = replace_once(
    catalog,
    "            Text(\n"
    "                text = subtitle,\n"
    "                style = MaterialTheme.typography.bodySmall,\n"
    "                color = MaterialTheme.colorScheme.secondary\n"
    "            )\n"
    "        }\n"
    "        Checkbox(\n"
    "            checked = checked,\n"
    "            onCheckedChange = { onToggle() }\n"
    "        )\n"
    "    }\n"
    "}\n",
    "            Text(\n"
    "                text = subtitle,\n"
    "                style = MaterialTheme.typography.bodySmall,\n"
    "                color = MaterialTheme.colorScheme.secondary\n"
    "            )\n"
    "            lockedMessage?.let { message ->\n"
    "                Text(\n"
    "                    text = message,\n"
    "                    style = MaterialTheme.typography.labelSmall,\n"
    "                    color = if (isGrantable) NexaFlowTheme.colors.warning else MaterialTheme.colorScheme.secondary\n"
    "                )\n"
    "            }\n"
    "        }\n"
    "        if (isReady) {\n"
    "            Checkbox(\n"
    "                checked = checked,\n"
    "                onCheckedChange = { onToggle() }\n"
    "            )\n"
    "        } else {\n"
    "            StatusPill(\n"
    "                text = stringResource(\n"
    "                    if (isGrantable) R.string.elevated_status_available else R.string.elevated_status_unavailable\n"
    "                ),\n"
    "                background = if (isGrantable) {\n"
    "                    NexaFlowTheme.colors.warningContainer\n"
    "                } else {\n"
    "                    MaterialTheme.colorScheme.surfaceContainerHighest\n"
    "                },\n"
    "                contentColor = if (isGrantable) {\n"
    "                    NexaFlowTheme.colors.warning\n"
    "                } else {\n"
    "                    MaterialTheme.colorScheme.secondary\n"
    "                }\n"
    "            )\n"
    "        }\n"
    "    }\n"
    "}\n",
    "catalog locked rendering",
)
components_path.write_text(prefix + catalog)

changelog_path = Path("CHANGELOG.md")
changelog = changelog_path.read_text()
anchor = (
    "- **Shizuku is now the preferred compatibility provider for shared elevated capabilities.**\n"
    "  The legacy compatibility selector follows the same least-privilege ordering as the modern\n"
    "  capability resolver, while Root remains exclusive for `ROOT_SHELL`.\n"
)
expanded = anchor + (
    "- **Builder capability discovery now keeps grantable features visible without making them executable.**\n"
    "  The trigger and action catalogues distinguish ready, permission-required, temporarily unavailable,\n"
    "  and unsupported capabilities from the same live snapshot. Grantable rows stay discoverable with an\n"
    "  explicit locked state and route into the existing permission flow; unsupported rows remain hidden.\n"
    "- **Release automation now gates version tags on the latest green `main` commit.**\n"
    "  A changelog-backed release candidate is tagged only after Android CI succeeds for the current head,\n"
    "  then the tag build is dispatched explicitly so production-signing, certificate, APK/AAB, alignment,\n"
    "  and release checks cannot be skipped by GitHub token recursion protections.\n"
)
changelog = replace_once(changelog, anchor, expanded, "v3.69 changelog")
changelog_path.write_text(changelog)
