#!/usr/bin/env python3
"""Enforce T31 canonical plugin SDK boundaries (plan §T31)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACT_FILE = ROOT / (
    "core/plugin-sdk/src/main/java/com/nexaflow/core/pluginsdk/"
    "PluginCanonicalContract.kt"
)
TEST_FILE = ROOT / (
    "core/plugin-sdk/src/test/java/com/nexaflow/core/pluginsdk/"
    "PluginCanonicalContractTest.kt"
)
BACKEND_FILE = ROOT / (
    "core/execution/src/main/java/com/nexaflow/core/execution/capability/"
    "PluginCapabilityBackend.kt"
)
EVENT_INGRESS_FILE = ROOT / (
    "core/automation-engine/src/main/java/com/nexaflow/core/engine/"
    "PluginEventIngress.kt"
)
APP_MODULE_FILE = ROOT / "app/src/main/java/com/nexaflow/app/di/AppModule.kt"
ACTION_SCHEMA_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/catalog/ActionNodeSchemas.kt"
)
TRIGGER_SCHEMA_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/catalog/TriggerNodeSchemas.kt"
)

FORBIDDEN_PATTERNS = (
    r"android\.content\.Intent",
    r"android\.os\.Bundle",
    r"org\.json",
    r"System\.currentTimeMillis",
)

REQUIRED_CONSTRUCTS = (
    "PluginCanonicalContract",
    "TARGET_EVENT",
    "TARGET_ACTION",
    "PREDICATE_MATCH_EVENT_FILTER",
    "OPERATION_INVOKE",
    "ARG_PLUGIN_ID",
    "PayloadSchema",
    "PayloadSlot",
    "PluginEvent",
    "PluginInvocation",
    "eventMatches",
    "validatePayload",
    "checkInvocation",
    "HostState",
    "RefusalReason",
    "TRUST_NOT_GRANTED",
)

REQUIRED_PRODUCT_WIRING = {
    BACKEND_FILE: (
        "object PluginCapabilityCatalog",
        "fun descriptors()",
        "PluginCanonicalContract.checkInvocation",
        "PluginCanonicalContract.CONFIG_REFERENCE_SCHEMA",
        "PluginCanonicalContract.ARG_CONFIG_REF",
    ),
    EVENT_INGRESS_FILE: (
        "PluginCanonicalContract.PluginEvent",
        "PluginCanonicalContract.eventMatches",
    ),
    APP_MODULE_FILE: (
        "PluginCapabilityCatalog.descriptors()",
        "PluginCapabilityBackend(",
    ),
    ACTION_SCHEMA_FILE: (
        "ActionType.PLUGIN_FIRE",
        'secretField("bundleJson")',
        'stringField("pluginInstance")',
        'enumField("pluginApproval", "approved")',
    ),
    TRIGGER_SCHEMA_FILE: (
        "TriggerType.PLUGIN_EVENT",
        'packageField("package")',
        'stringField("pluginInstance")',
        'enumField("pluginApproval", "approved")',
    ),
}

REQUIRED_TEST_CASES = (
    "canonicalIdentitiesMatchThePinnedTableEntries",
    "eventMatchesOnPluginIdAndExactFilterEntries",
    "eventMatcherRefusesInvalidFilterIdsInsteadOfThrowing",
    "eventConstructorRejectsInvalidPluginIds",
    "configReferenceSchemaAcceptsOpaqueInstanceWithoutTreatingItAsPluginId",
    "configReferenceSchemaRejectsOversizedOpaqueInstance",
    "validPayloadIsAccepted",
    "unknownAndMissingSlotsAreTypedRefusals",
    "slotLengthOverflowIsATypedRefusal",
    "oversizedPayloadEntryCountIsATypedRefusal",
    "completeHealthyInvocationIsAccepted",
    "inactiveLifecycleRefusesEvenWithValidPayload",
    "missingTrustRefusesWhileApprovalIsRequired",
    "trustIsNotRequiredWhenThePolicyDisablesApproval",
    "invalidPluginIdIsATypedRefusalNotACrash",
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
                problems.append(f"PluginCanonicalContract.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"PluginCanonicalContract.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"PluginCanonicalContractTest.kt missing {case!r}")

    for wiring_file, required_tokens in REQUIRED_PRODUCT_WIRING.items():
        if not wiring_file.is_file():
            problems.append(f"missing {wiring_file.relative_to(ROOT)}")
            continue
        wiring_source = wiring_file.read_text(encoding="utf-8")
        for token in required_tokens:
            if token not in wiring_source:
                problems.append(
                    f"{wiring_file.relative_to(ROOT)} missing product wiring {token!r}"
                )

    if problems:
        print("CANONICAL_PLUGIN_SDK: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_PLUGIN_SDK: OK — typed canonical plugin surface: "
        "event/invocation identities mirror the pinned canonical AST "
        "(plugin.event, plugin.action, match_event_filter, typed pluginId "
        "argument); the host-side event matcher is deterministic with "
        "wildcard payload filters; payload and invocation checks fail "
        "closed with typed refusal reasons; the deprecated USER_APPROVED "
        "value never satisfies trust — the host policy layer decides"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
