#!/usr/bin/env python3
"""Enforce T13 central-summary boundaries (plan §13)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN_CANONICAL = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"

ENGINE_FILE = DOMAIN_CANONICAL / "WorkflowSummaryEngine.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "WorkflowSummaryEngineTest.kt"
)

# The summary engine is pure data formatting: no UI, no Android, no legacy
# enums, no locale-specific copy baked into the domain.
FORBIDDEN_PATTERNS = (
    r"\bContext\b",
    r"androidx\.compose",
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"getString\(R\.",
)

REQUIRED_CONSTRUCTS = (
    "WorkflowDraftNode",
    "WorkflowDraftSemantics",
    "WorkflowSummary",
    "WorkflowSummaryEngine",
    "summarizeNode",
    "summarizeCompact",
)

REQUIRED_TEST_CASES = (
    "triggerAndActionLinesRenderThroughTheSharedFormatter",
    "twoNetworkConditionsRenderWithTheirDeclaredLogic",
    "semanticsRequireAtLeastOneDeclaredDimension",
    "compactPreviewAddsAnOverflowCounter",
    "duplicateNodeIdsAreRejected",
    "renderingIsDeterministic",
)


def main() -> int:
    problems: list[str] = []

    if not ENGINE_FILE.is_file():
        problems.append(f"missing {ENGINE_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if ENGINE_FILE.is_file():
        source = ENGINE_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"WorkflowSummaryEngine.kt missing required T13 construct {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"WorkflowSummaryEngine.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"WorkflowSummaryEngineTest.kt missing required test {case!r}"
                )

    if problems:
        print("CANONICAL_SUMMARY_ENGINE: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_SUMMARY_ENGINE: OK — "
        "one deterministic workflow summary for builder/history/import/"
        "diagnostics with explicit semantics, enforced with mandatory T13 "
        "unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
