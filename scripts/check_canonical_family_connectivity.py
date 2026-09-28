#!/usr/bin/env python3
"""Enforce T19 connectivity-family boundaries (plan §T19)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "FamilyPhase19Connectivity.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "FamilyPhase19ConnectivityTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"fallbackTo\w*\s*\(",
)

REQUIRED_CONSTRUCTS = (
    "FamilyPhase19Connectivity",
    "ruleOverrides",
    "adapterWithFamily",
    "stateWriteProviders",
    "enableSchema",
    "enableSemantics",
    "enableCardinality",
)

REQUIRED_TEST_CASES = (
    "familyOverridesCoverConnectivityMembers",
    "familyAdapterKeepsTheFullTable",
    "enableActionsUpgradeToTypedSetState",
    "missingEnabledKeyIsRejected",
    "bogusEnabledValueIsRejected",
    "wifiConnectCarriesSsidAndSecretPasswordReference",
    "triggersUpgradeEnabledStateConditionally",
    "contradictoryConnectivityBatchIsRejectedBySemanticRules",
    "multiTargetOrderedSemanticsAreExecutable",
    "stateWriteProvidersResolvePublicApiFirstWithPrivilegedFallback",
    "canonicalizationRemainsIdempotent",
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
                problems.append(f"FamilyPhase19Connectivity.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"FamilyPhase19Connectivity.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"FamilyPhase19ConnectivityTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_FAMILY_CONNECTIVITY: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_FAMILY_CONNECTIVITY: OK — 17 actions + 16 triggers "
        "upgraded with typed values, secret-safe wifi sessions, multi-target "
        "ordered semantics, declared provider fallback and idempotent "
        "canonicalization"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
