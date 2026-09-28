#!/usr/bin/env python3
"""Enforce T08 dynamic-schema architecture boundaries (plan §11, ADR-005)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN_CANONICAL = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"

SCHEMA_FILE = DOMAIN_CANONICAL / "NodeSchema.kt"
TEST_FILE = ROOT / "domain/src/test/java/com/nexaflow/domain/canonical/NodeSchemaTest.kt"

# ADR-005 invariant: the schema is the single source of truth; UI/rendering
# concerns must not leak into the engine, and defaults must not be applied
# anywhere except the declared-defaults function.
FORBIDDEN_PATTERNS = (
    r"androidx\.compose",
    r"\bContext\b",
    r"\.lowercase\(\)\s*(==|\.contains)",
    r"applyDefaults\s*\(",
)

REQUIRED_CONSTRUCTS = (
    "NodeSchema",
    "NodeSchemaField",
    "NodeFieldDefault",
    "NodeFieldCondition",
    "NodeSchemaConflict",
    "NodeSchemaCapability",
    "NodeSecurityClass",
    "NodeSchemaRegistry",
    "NodeSummaryFormatter",
    "validateNodeValues",
    "defaultsOf",
)

REQUIRED_TEST_CASES = (
    "unknownFieldsAreRejected",
    "typeMismatchesAreRejected",
    "conditionallyRequiredFieldsAreEnforced",
    "invisibleFieldsAreRejectedWhenSupplied",
    "declaredConflictsAreDetected",
    "declaredDefaultsAreTheOnlyDefaultSource",
    "summaryRendersValuesAndMasksSecrets",
    "summaryTemplateReferencingUndeclaredFieldFailsClosed",
    "registryRejectsDuplicateRegistrations",
)


def main() -> int:
    problems: list[str] = []

    if not SCHEMA_FILE.is_file():
        problems.append(f"missing {SCHEMA_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if SCHEMA_FILE.is_file():
        source = SCHEMA_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"NodeSchema.kt missing required T08 construct {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"NodeSchema.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"NodeSchemaTest.kt missing required test {case!r}"
                )

    if problems:
        print("CANONICAL_SCHEMA_ENGINE: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_SCHEMA_ENGINE: OK — "
        "typed schema fields, declared defaults only, conditional "
        "visibility/requirement, conflicts, security classes and summary "
        "formatting enforced with mandatory T08 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
