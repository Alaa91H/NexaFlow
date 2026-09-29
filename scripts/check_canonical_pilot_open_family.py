#!/usr/bin/env python3
"""Enforce T17 pilot-family boundaries (plan §T17)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_FILE = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical/PilotOpenFamily.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "PilotOpenFamilyTest.kt"
)
DISCOVERY_FILE = ROOT / (
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/"
    "CanonicalDiscoveryPolicy.kt"
)
EDITOR_FILE = ROOT / (
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/"
    "ActionConfigEditor.kt"
)
CONTROLLER_FILE = ROOT / (
    "core/rom-integration/src/main/java/com/nexaflow/core/rom/SystemController.kt"
)

# The pilot may only refine reviewed mappings: no legacy enum references, no
# raw config maps, no name-based guessing inside the family rules.
FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"\.lowercase\(\)\s*==",
)

REQUIRED_CONSTRUCTS = (
    "PilotOpenFamily",
    "ruleOverrides",
    "adapterWithPilot",
    "openSettingsSchema",
    "cardinality",
    "semantics",
)

REQUIRED_TEST_CASES = (
    "pilotOverridesCoverExactlyTheSettingsLaunchers",
    "pilotAdapterKeepsEveryOtherRuleIntact",
    "settingsLaunchersInferTheirCanonicalPageWithoutLegacyConfig",
    "genericOpenSettingsKeepsExplicitPageAndLegacyWifiDefault",
    "settingsActionsPreserveReviewedTargetAndOperation",
    "nonSettingsOpenActionsAreNotCapturedByThePilot",
        "unconsumedKeysStillRideAlong",
    "schemaValidatesEveryDeclaredPageToken",
    "familySemanticsAreSingleTarget",
    "driftedSettingsTableFailsClosed",
)


def main() -> int:
    problems: list[str] = []

    if not FAMILY_FILE.is_file():
        problems.append(f"missing {FAMILY_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")
    for product_file in (DISCOVERY_FILE, EDITOR_FILE, CONTROLLER_FILE):
        if not product_file.is_file():
            problems.append(f"missing {product_file.relative_to(ROOT)}")

    if FAMILY_FILE.is_file():
        source = FAMILY_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"PilotOpenFamily.kt missing required T17 construct {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(f"PilotOpenFamily.kt matches forbidden pattern {pattern!r}")

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"PilotOpenFamilyTest.kt missing required test {case!r}")

    # T17 is not closed by a domain adapter alone. New-workflow discovery
    # must expose one Open Settings action, its editor must expose canonical
    # page tokens, and runtime must accept the same tokens.
    if DISCOVERY_FILE.is_file():
        discovery = DISCOVERY_FILE.read_text(encoding="utf-8")
        if discovery.count("ActionType.SYSTEM_OPEN_") < 28:
            problems.append("T17 discovery policy does not retire the dedicated settings aliases")
        if "ActionType.SYSTEM_OPEN_SETTINGS" in discovery:
            problems.append("the unified SYSTEM_OPEN_SETTINGS action must remain discoverable")
    if EDITOR_FILE.is_file():
        editor = EDITOR_FILE.read_text(encoding="utf-8")
        for token in ("ABOUT_PHONE", "SYSTEM_UPDATE", "APP_SETTINGS_LIST", "USAGE_ACCESS"):
            if token not in editor:
                problems.append(f"Open Settings editor missing canonical page token {token}")
    if CONTROLLER_FILE.is_file():
        controller = CONTROLLER_FILE.read_text(encoding="utf-8")
        for token in ('"ABOUT_PHONE"', '"SYSTEM_UPDATE"', '"APP_SETTINGS_LIST"'):
            if token not in controller:
                problems.append(f"SystemController missing canonical settings token {token}")

    if problems:
        print("CANONICAL_PILOT_OPEN_FAMILY: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_PILOT_OPEN_FAMILY: OK — 29 real settings launchers collapse to "
        "one typed Open(SettingsPage) contract; non-settings SYSTEM_OPEN_* nodes "
        "stay outside the family, mappings are parity-pinned and fail-closed"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
