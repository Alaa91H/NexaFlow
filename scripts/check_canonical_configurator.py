#!/usr/bin/env python3
"""Enforce T12 configurator product wiring (plan §12 / §T12)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN_CANONICAL = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"
STATE_FILE = DOMAIN_CANONICAL / "NodeConfiguratorState.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "NodeConfiguratorStateTest.kt"
)
SHEET_FILE = ROOT / (
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/"
    "NodeConfiguratorSheet.kt"
)
BUILDER_FILE = ROOT / (
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/"
    "AutomationBuilderScreen.kt"
)
BUILDER_ACTION_PRESENTATION_FILE = ROOT / (
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/"
    "BuilderActionPresentation.kt"
)
SCHEMA_EDITOR_FILE = ROOT / (
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/"
    "CanonicalSchemaFieldEditor.kt"
)
SCHEMA_BRIDGE_FILE = ROOT / (
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/"
    "CanonicalBuilderSchemaBridge.kt"
)
OPTION_CATALOG_FILE = ROOT / (
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/"
    "AutomationOptionCatalog.kt"
)
OPTION_CATALOG_TEST = ROOT / (
    "feature/automation-builder/src/test/java/com/nexaflow/feature/builder/"
    "AutomationOptionCatalogTest.kt"
)

FORBIDDEN_CORE_PATTERNS = (
    r"\bContext\b",
    r"androidx\.compose",
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
)

# Compatibility alias used by the gate's mutation tests.
FORBIDDEN_PATTERNS = FORBIDDEN_CORE_PATTERNS

REQUIRED_CONSTRUCTS = (
    "NodeConfiguratorState",
    "NodeConfiguratorTab",
    "SelectionState",
    "SelectionOption",
    "DisclosureState",
    "configuratorStateFor",
)

REQUIRED_TEST_CASES = (
    "tabsAreDerivedFromTheSchema",
    "standardSchemaHasNoAdvancedTab",
    "defaultsSeedTheDraft",
    "progressiveDisclosureRevealsAdvancedFields",
    "invisibleFieldsAreNotShown",
    "conditionalRequirementSurfacesAsMissing",
    "liveValidationFlagsConflicts",
    "settingAnUndeclaredValueFailsClosed",
    "selectionStateFiltersSearchesSortsAndCounts",
    "summaryRendersThroughTheSharedFormatter",
    "cleanDraftIsSubmittable",
    "stateTransitionsAreDeterministic",
)


def main() -> int:
    problems: list[str] = []

    for path in (
        STATE_FILE,
        TEST_FILE,
        SHEET_FILE,
        BUILDER_FILE,
        BUILDER_ACTION_PRESENTATION_FILE,
        SCHEMA_EDITOR_FILE,
        SCHEMA_BRIDGE_FILE,
        OPTION_CATALOG_FILE,
        OPTION_CATALOG_TEST,
    ):
        if not path.is_file():
            problems.append(f"missing {path.relative_to(ROOT)}")

    if STATE_FILE.is_file():
        source = STATE_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"NodeConfiguratorState.kt missing required T12 construct {token!r}"
                )
        for pattern in FORBIDDEN_CORE_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"NodeConfiguratorState.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"NodeConfiguratorStateTest.kt missing required test {case!r}"
                )

    # Configuration remains hosted in the shared sheet. Discovery itself is
    # inline in the builder, with separate trigger and action category lists.
    if SHEET_FILE.is_file():
        sheet = SHEET_FILE.read_text(encoding="utf-8")
        for token in (
            "fun NodeConfiguratorSheet",
            "ModalBottomSheet",
            "selected_count",
            "OutlinedTextField",
            "verticalScroll",
        ):
            if token not in sheet:
                problems.append(f"NodeConfiguratorSheet.kt missing product construct {token!r}")

    if BUILDER_FILE.is_file():
        builder = BUILDER_FILE.read_text(encoding="utf-8")
        if builder.count("CategoryAccordion(") < 2:
            problems.append("builder must show trigger and action discovery in separate category lists")

    if SCHEMA_EDITOR_FILE.is_file():
        editor = SCHEMA_EDITOR_FILE.read_text(encoding="utf-8")
        for token in (
            "fun CanonicalSchemaFieldEditor",
            "NodeConfiguratorState",
            "state.visibleFields()",
            "state.validationIssues()",
            "collectionElementKind",
        ):
            if token not in editor:
                problems.append(f"schema field renderer missing {token!r}")

    if SCHEMA_BRIDGE_FILE.is_file():
        bridge = SCHEMA_BRIDGE_FILE.read_text(encoding="utf-8")
        for token in (
            "CanonicalBuilderSchemaBridge",
            "editingBindingForAction",
            "PilotOpenFamily.openSettingsSchema",
        ):
            if token not in bridge:
                problems.append(f"schema bridge missing {token!r}")

    if OPTION_CATALOG_FILE.is_file():
        catalog = OPTION_CATALOG_FILE.read_text(encoding="utf-8")
        for token in (
            "enum class OptionTier",
            "BROWSE",
            "ADVANCED",
            "tierFor",
        ):
            if token not in catalog:
                problems.append(f"discovery catalog missing {token!r}")
        for token in ("COMMON", "commonTriggerOrder", "commonActionOrder"):
            if token in catalog:
                problems.append(f"discovery catalog must not restore the common tier: {token!r}")

    if OPTION_CATALOG_TEST.is_file():
        option_tests = OPTION_CATALOG_TEST.read_text(encoding="utf-8")
        for case in (
            "discovery tiers omit the common popular surface",
            "every legacy-compatible option has exactly one discovery tier",
            "high risk and raw automation surfaces are advanced",
        ):
            if case not in option_tests:
                problems.append(f"option catalog tests missing {case!r}")

    if BUILDER_FILE.is_file() and BUILDER_ACTION_PRESENTATION_FILE.is_file():
        builder = BUILDER_FILE.read_text(encoding="utf-8")
        builder_surface = builder + "\n" + BUILDER_ACTION_PRESENTATION_FILE.read_text(encoding="utf-8")
        for token in (
            "CanonicalBuilderSchemaBridge.editingBindingForAction",
            "CanonicalSchemaFieldEditor(",
            "triggerCategories",
            "actionCategories",
            "AutomationOptionCatalog.tierFor",
            "OptionTier.BROWSE",
            "OptionTier.ADVANCED",
            "showAdvancedTriggerOptions",
            "showAdvancedActionOptions",
        ):
            if token not in builder_surface:
                problems.append(f"builder discovery/schema wiring missing {token!r}")
        for token in ("AutomationOptionCatalog.commonTriggerOrder", "AutomationOptionCatalog.commonActionOrder"):
            if token in builder_surface:
                problems.append(f"builder must not use the removed common discovery tier: {token!r}")
        for state_name in ("expandedTriggerCategory", "expandedActionCategory"):
            if re.search(
                rf"{state_name}\\s+by\\s+rememberSaveable\\s*\\{{\\s*"
                r"mutableStateOf<Int\\?>\\(0\\)",
                builder,
            ):
                problems.append(
                    f"family-first picker must not auto-expand category zero: {state_name}"
                )

    if problems:
        print("CANONICAL_CONFIGURATOR: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_CONFIGURATOR: OK — shared configuration sheet, canonical schema "
        "renderer/bridge, separate inline trigger/action discovery, no common tier, "
        "and explicit advanced disclosure are regression-gated"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
