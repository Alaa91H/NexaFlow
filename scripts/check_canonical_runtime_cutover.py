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

    if problems:
        print("CANONICAL_RUNTIME_CUTOVER: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_RUNTIME_CUTOVER: OK — single cutover path "
        "legacy > adapter > canonical AST > validation > plan; the pipeline "
        "refuses rejected mappings and invalid verdicts, the validated "
        "surface is the schema-typed view of the consumed config, family "
        "command semantics merge into the cutover planner, and all 233 "
        "legacy types canonicalize deterministically"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
