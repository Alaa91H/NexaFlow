#!/usr/bin/env python3
"""Validate the accepted ADR contract for canonical automation T02."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADR_DIR = ROOT / "docs/architecture/adr"

REQUIRED = {
    1: "ADR-001-canonical-workflow-ast.md",
    2: "ADR-002-stable-ids-and-registry.md",
    3: "ADR-003-multi-selection-semantics.md",
    4: "ADR-004-legacy-migration-strategy.md",
    5: "ADR-005-schema-driven-ui.md",
    6: "ADR-006-workflow-compiler.md",
    7: "ADR-007-capability-resolver.md",
    8: "ADR-008-execution-planner.md",
    9: "ADR-009-error-and-failure-semantics.md",
    10: "ADR-010-idempotency-and-retry-policy.md",
    11: "ADR-011-provider-architecture.md",
    12: "ADR-012-diagnostics-and-execution-journal.md",
    13: "ADR-013-versioning-policy.md",
    14: "ADR-014-security-and-secret-handling.md",
    15: "ADR-015-legacy-removal-policy.md",
}

MUST_CONTAIN = {
    1: ("typed canonical workflow", "UX families are presentation only"),
    2: ("stable string IDs", "Duplicate IDs fail"),
    3: ("TargetSelectionMode", "ConditionLogic", "EventLogic", "ExecutionMode"),
    4: ("Migration is deterministic and idempotent", "Migration never performs opportunistic consolidation"),
    5: ("source of truth for configuration UI", "No hidden runtime default"),
    6: ("Parse -> Normalize -> Type Check", "Invalid input never reaches side-effect execution"),
    7: ("capability resolver selects", "never silently"),
    8: ("atomic commands", "Parallel execution occurs only"),
    9: ("structured error model", "Errors never expose secrets"),
    10: ("NON_IDEMPOTENT commands are not automatically retried",),
    11: ("conformance test kit", "cannot redefine the semantic meaning"),
    12: ("Why didn't it run?", "Secrets and sensitive payloads are redacted or omitted"),
    13: ("WorkflowSchemaVersion", "ExecutionPlanVersion"),
    14: ("Secrets are never logged", "High-risk operations"),
    15: ("237/237 golden migration tests pass", "At least one stable release cycle"),
}

FORBIDDEN_COMBINATIONS = (
    (
        4,
        "Migration never performs opportunistic consolidation",
        8,
        "Compile canonical intent into explicit atomic commands",
        "Migration and execution planning must remain separate concerns",
    ),
    (
        1,
        "UX families are presentation only",
        5,
        "The canonical schema is the source of truth",
        "UX taxonomy must not become runtime identity",
    ),
)


def main() -> int:
    problems: list[str] = []

    if not ADR_DIR.is_dir():
        problems.append("ADR directory is missing")
    else:
        actual = sorted(p.name for p in ADR_DIR.glob("ADR-*.md"))
        expected = sorted(REQUIRED.values())
        if actual != expected:
            missing = sorted(set(expected) - set(actual))
            extra = sorted(set(actual) - set(expected))
            problems.append(f"ADR set mismatch: missing={missing}, extra={extra}")

    contents: dict[int, str] = {}
    for number, filename in REQUIRED.items():
        path = ADR_DIR / filename
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        contents[number] = text
        if "**Status:** Accepted" not in text:
            problems.append(f"{filename}: status is not Accepted")
        for heading in ("## Context", "## Decision", "## Invariants", "## Consequences"):
            if heading not in text:
                problems.append(f"{filename}: missing {heading}")
        for phrase in MUST_CONTAIN.get(number, ()):
            if phrase.lower() not in text.lower():
                problems.append(f"{filename}: missing binding phrase {phrase!r}")

    # Cross-ADR consistency checks: both halves of each architectural boundary
    # must remain present. This intentionally catches accidental weakening.
    for left_num, left_phrase, right_num, right_phrase, description in FORBIDDEN_COMBINATIONS:
        left = contents.get(left_num, "")
        right = contents.get(right_num, "")
        if left_phrase.lower() not in left.lower() or right_phrase.lower() not in right.lower():
            problems.append(f"cross-ADR invariant weakened: {description}")

    readme = ADR_DIR / "README.md"
    if not readme.is_file():
        problems.append("ADR README is missing")
    else:
        text = readme.read_text(encoding="utf-8")
        for number in REQUIRED:
            if f"ADR-{number:03d}" not in text:
                problems.append(f"ADR README missing ADR-{number:03d}")

    if problems:
        print("CANONICAL_ADRS: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print("CANONICAL_ADRS: OK — 15/15 accepted decisions and cross-ADR invariants verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
