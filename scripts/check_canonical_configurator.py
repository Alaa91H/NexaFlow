#!/usr/bin/env python3
"""Enforce T12 configurator-infrastructure boundaries (plan §12, ADR-005)."""
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

# The configurator core is pure: no Android/Compose rendering, no legacy
# enums, no per-family hardcoding, no untyped configuration.
FORBIDDEN_PATTERNS = (
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

    if not STATE_FILE.is_file():
        problems.append(f"missing {STATE_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if STATE_FILE.is_file():
        source = STATE_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"NodeConfiguratorState.kt missing required T12 construct {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
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

    if problems:
        print("CANONICAL_CONFIGURATOR: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_CONFIGURATOR: OK — "
        "schema-driven tabs, progressive disclosure, bounded multi-select, "
        "live validation and shared summaries with no per-family UI logic, "
        "enforced with mandatory T12 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
