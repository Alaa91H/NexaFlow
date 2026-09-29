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

FORBIDDEN_CORE_PATTERNS = (
    r"\bContext\b",
    r"androidx\.compose",
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
)

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

    for path in (STATE_FILE, TEST_FILE, SHEET_FILE, BUILDER_FILE):
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

    # The previous gate stopped at the pure state machine and allowed T12 to be
    # declared complete without any Compose host. Product closure requires the
    # unified modal shell and both trigger/action discovery paths to use it.
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
        if builder.count("NodeConfiguratorSheet(") < 2:
            problems.append(
                "AutomationBuilderScreen must route both trigger and action discovery "
                "through NodeConfiguratorSheet"
            )
        if "showTriggerConfigurator" not in builder or "showActionConfigurator" not in builder:
            problems.append("builder is missing modal configurator ownership state")

    if problems:
        print("CANONICAL_CONFIGURATOR: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_CONFIGURATOR: OK — pure schema state plus a real unified "
        "ModalBottomSheet host wired into both product discovery paths"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
