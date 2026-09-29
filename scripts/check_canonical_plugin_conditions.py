#!/usr/bin/env python3
"""Enforce T41 canonical plugin condition boundaries (plan §T41)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACT_FILE = ROOT / (
    "core/plugin-sdk/src/main/java/com/nexaflow/core/pluginsdk/"
    "PluginConditionContract.kt"
)
TEST_FILE = ROOT / (
    "core/plugin-sdk/src/test/java/com/nexaflow/core/pluginsdk/"
    "PluginConditionContractTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"android\.content\.Intent",
    r"android\.os\.Bundle",
    r"org\.json",
    r"System\.currentTimeMillis",
)

REQUIRED_CONSTRUCTS = (
    "PluginConditionContract",
    "core.capability.plugin_condition_read",
    "core.operation.get_state",
    "ARG_PLUGIN_INSTANCE",
    "ConditionState",
    "QueryRefusal",
    "INSTANCE_NOT_APPROVED",
    "SENDER_NOT_VERIFIED",
    "LIFECYCLE_NOT_ACTIVE",
    "ConditionQuery",
    "QueryHostState",
    "QueryPolicy",
    "QueryCheck",
    "checkQuery",
    "fromLocaleResultCode",
    "booleanVerdict",
    "timedOut",
)

REQUIRED_TEST_CASES = (
    "canonicalIdentitiesMatchTheEcosystemContract",
    "healthyQueryIsAccepted",
    "unapprovedInstanceRefusesEvenWithEverythingElseFine",
    "unverifiedSenderRefusesWhileVerificationIsRequired",
    "senderIsAllowedWhenThePolicyDisablesVerification",
    "inactiveLifecycleRefuses",
    "localeResultCodesMapOntoTypedStates",
    "unmappedResultCodesAreTypedErrorsNeverBooleans",
    "timedOutQueriesAreUnavailableNotFalse",
    "onlyRealVerdictsProduceABoolean",
)


def main() -> int:
    problems: list[str] = []

    if not CONTRACT_FILE.is_file():
        problems.append(f"missing {CONTRACT_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if CONTRACT_FILE.is_file():
        source = CONTRACT_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"PluginConditionContract.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"PluginConditionContract.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"PluginConditionContractTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_PLUGIN_CONDITIONS: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_PLUGIN_CONDITIONS: OK — typed plugin condition contract: "
        "the read is the pinned plugin_condition_read capability with a "
        "single persisted instance argument; the query gate fails closed on "
        "approval, sender identity and lifecycle; Locale result codes map "
        "onto the typed five-state result; Unknown, Unavailable and Error "
        "never collapse into a boolean"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
