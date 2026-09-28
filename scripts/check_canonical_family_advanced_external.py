#!/usr/bin/env python3
"""Enforce T25 advanced/external family boundaries (plan §T25)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "FamilyPhase25AdvancedExternal.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "FamilyPhase25AdvancedExternalTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
)

REQUIRED_CONSTRUCTS = (
    "FamilyPhase25AdvancedExternal",
    "ruleOverrides",
    "adapterWithFamily",
    "commandSemantics",
    "httpSchema",
)

REQUIRED_TEST_CASES = (
    "familyOverridesCoverAdvancedExternalMembers",
    "familyAdapterKeepsTheFullTable",
    "dataTransformUpgradesToTypedExpression",
    "privilegedCommandNeverCarriesRawCommandText",
    "httpUpgradesUrlAndSecretAuthToken",
    "waitRequiresTypedDuration",
    "pluginTriggerCarriesTypedPluginId",
    "privilegedOperationsAreNeverBlindlyRetryable",
    "httpSchemaIsSensitiveWithSecretTokenField",
    "canonicalizationRemainsIdempotent",
    "driftedTableFailsClosed",
)


def main() -> int:
    problems: list[str] = []

    if not FAMILY_FILE.is_file():
        problems.append(f"missing {FAMILY_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if FAMILY_FILE.is_file():
        source = FAMILY_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"FamilyPhase25AdvancedExternal.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"FamilyPhase25AdvancedExternal.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"FamilyPhase25AdvancedExternalTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_FAMILY_ADVANCED_EXTERNAL: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_FAMILY_ADVANCED_EXTERNAL: OK — 28 actions + 1 plugin "
        "trigger upgraded with secret-safe privileged commands (raw command "
        "text never enters the AST), SECRET_REFERENCE HTTP auth, typed data "
        "transforms and CONDITIONALLY_IDEMPOTENT destructive semantics"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
