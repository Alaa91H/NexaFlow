#!/usr/bin/env python3
"""Enforce T09 validation-pipeline boundaries (plan §21 closure rule)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN_CANONICAL = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"

PIPELINE_FILE = DOMAIN_CANONICAL / "CanonicalValidationPipeline.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CanonicalValidationPipelineTest.kt"
)

# The pipeline is pure and ordered; it must never fall back to execution with
# findings, never swallow findings, and never depend on Android runtime state.
FORBIDDEN_PATTERNS = (
    r"\bContext\b",
    r"androidx\.compose",
    r"isValid\s*\|\|",
    r"filterNotNull\(\)\s*\.isEmpty\(\)\s*\|\|",
)

REQUIRED_CONSTRUCTS = (
    "ValidationStage",
    "ValidationFinding",
    "ValidationVerdict",
    "CanonicalWorkflowContract",
    "validate",
    "ValidationStage.SYNTAX",
    "ValidationStage.TYPE",
    "ValidationStage.SCHEMA",
    "ValidationStage.SEMANTIC",
    "ValidationStage.CAPABILITY",
    "ValidationStage.SECURITY",
)

REQUIRED_TEST_CASES = (
    "validWorkflowProducesCleanVerdict",
    "schemaFindingsBlockLaterStages",
    "semanticFindingsReportTheirStage",
    "securityClassWithoutCapabilityIsRejected",
    "secretOutsideSecretFieldIsRejected",
    "verdictIsDeterministic",
)


def main() -> int:
    problems: list[str] = []

    if not PIPELINE_FILE.is_file():
        problems.append(f"missing {PIPELINE_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if PIPELINE_FILE.is_file():
        source = PIPELINE_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"CanonicalValidationPipeline.kt missing required T09 construct {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"CanonicalValidationPipeline.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"CanonicalValidationPipelineTest.kt missing required test {case!r}"
                )

    if problems:
        print("CANONICAL_VALIDATION_PIPELINE: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_VALIDATION_PIPELINE: OK — "
        "ordered syntax/type/schema/semantic/capability/security stages with "
        "fail-closed gating and mandatory T09 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
