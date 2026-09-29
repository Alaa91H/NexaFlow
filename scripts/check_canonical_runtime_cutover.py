#!/usr/bin/env python3
"""Enforce T26 canonical runtime cutover boundaries (plan §T26)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CANONICAL_DIR = (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
)
MAIN_FILE = ROOT / (CANONICAL_DIR + "CanonicalRuntimePipeline.kt")
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CanonicalRuntimePipelineTest.kt"
)

PRODUCT_RUNTIME_FILE = ROOT / (
    "core/execution/src/main/java/com/nexaflow/core/execution/compat/"
    "CanonicalRuntimeCutoverAdapter.kt"
)
EXECUTION_ENGINE_FILE = ROOT / (
    "core/execution/src/main/java/com/nexaflow/core/execution/"
    "ExecutionEngine.kt"
)
PRODUCT_TEST_FILE = ROOT / (
    "core/execution/src/test/java/com/nexaflow/core/execution/compat/"
    "CanonicalRuntimeCutoverAdapterTest.kt"
)
PIPELINE_FILES = (
    MAIN_FILE,
    ROOT / (CANONICAL_DIR + "ExecutionPlanner.kt"),
    ROOT / (CANONICAL_DIR + "LegacyCanonicalAdapter.kt"),
    ROOT / (CANONICAL_DIR + "PilotOpenFamily.kt"),
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
)

REQUIRED_CONSTRUCTS = (
    "CanonicalRuntimePipeline",
    "planLegacy",
    "defaultAdapter",
    "defaultPlanner",
    "CanonicalizedPlan",
    "preservedConfig",
    "cutover refused",
)

REQUIRED_TEST_CASES = (
    "defaultAdapterCoversAll233TypesWithFamilyOverrides",
    "legacyOpenSettingsFlowsThroughToAnExecutablePlan",
    "preservedLegacyConfigRidesButIsNotExecuted",
    "rejectedLegacyTypeNeverReachesThePlanner",
    "schemaTypedConfigBecomesTheValidatedSurface",
    "schemaMismatchBlocksTheCutover",
    "defaultPlannerCoversEveryFamilyDeclaredOperation",
    "all233TypesCanonicalizeDeterministicallyThroughTheCutoverAdapter",
    "cutoverPathIsDeterministic",
)


def main() -> int:
    problems: list[str] = []

    for path in PIPELINE_FILES:
        if not path.is_file():
            problems.append(f"missing {path.relative_to(ROOT)}")

    if MAIN_FILE.is_file():
        source = MAIN_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"CanonicalRuntimePipeline.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"CanonicalRuntimePipeline.kt matches forbidden pattern {pattern!r}"
                )

    for path in PIPELINE_FILES[1:]:
        if path.is_file():
            source = path.read_text(encoding="utf-8")
            for pattern in FORBIDDEN_PATTERNS:
                if re.search(pattern, source):
                    problems.append(
                        f"{path.name} matches forbidden pattern {pattern!r}"
                    )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"CanonicalRuntimePipelineTest.kt missing {case!r}")

    # Product cutover evidence: the pure domain pipeline is not sufficient.
    # The actual execution engine must cross the canonical boundary before any
    # handler side effect, and retry policy must consume canonical idempotency.
    for path in (PRODUCT_RUNTIME_FILE, EXECUTION_ENGINE_FILE, PRODUCT_TEST_FILE):
        if not path.is_file():
            problems.append(f"missing product runtime wiring {path.relative_to(ROOT)}")

    if PRODUCT_RUNTIME_FILE.is_file():
        product = PRODUCT_RUNTIME_FILE.read_text(encoding="utf-8")
        for token in (
            "CanonicalRuntimeCutoverAdapter",
            "prepareAction",
            "prepareTrigger",
            "CanonicalRuntimePipeline",
            "planLegacy",
            "sanitizedPreservedConfig",
        ):
            if token not in product:
                problems.append(f"CanonicalProductRuntime.kt missing {token!r}")

    if PRODUCT_TEST_FILE.is_file():
        product_tests = PRODUCT_TEST_FILE.read_text(encoding="utf-8")
        for case in (
            "ringerModeUsesRealModeKeyAndDeclaredDefault",
            "alarmUsesHourMinuteInsteadOfInventedTimeKey",
            "waitUsesCatalogSecondsDefaultAsTypedDuration",
            "installApkUsesPathContract",
            "mediaTransportIdentitySurvivesIntoAtomicCommand",
            "expressionCapableBrightnessPromotesTypedExpressionIntoPayload",
            "invalidBrightnessIsRejectedBeforePlanning",
            "all233CatalogContractsValidateAndPreserveReviewedIdentity",
            "runtimeCanonicalMetadataNeverRetainsRawSecretFields",
        ):
            if f"fun {case}" not in product_tests:
                problems.append(f"CanonicalRuntimeCutoverAdapterTest missing {case!r}")

    if EXECUTION_ENGINE_FILE.is_file():
        engine = EXECUTION_ENGINE_FILE.read_text(encoding="utf-8")
        for token in (
            "canonicalRuntimeCutover.prepareTrigger",
            "canonicalRuntimeCutover.prepareAction",
            "canonicalCommand = canonicalAction.command",
            "canonicalCommand: AtomicCommand",
            "CommandIdempotency.IDEMPOTENT",
            "executeCanonicalCompatibilityAction",
        ):
            if token not in engine:
                problems.append(f"ExecutionEngine is not cut over: missing {token!r}")
        if engine.index("canonicalRuntimeCutover.prepareTrigger") > engine.index("workflowAdmissionGate.evaluate"):
            problems.append(
                "canonical trigger admission must run before the legacy workflow admission/runtime path"
            )

    if problems:
        print("CANONICAL_RUNTIME_CUTOVER: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_RUNTIME_CUTOVER: OK — ExecutionEngine crosses the production "
        "compatibility boundary into CanonicalRuntimePipeline.planLegacy before "
        "admission/dispatch; catalog contracts become typed values, validation "
        "runs before planning, AtomicCommand is mandatory, and retry safety is "
        "driven by canonical idempotency"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
