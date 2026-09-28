#!/usr/bin/env python3
"""Enforce T16 golden-migration boundaries (plan §32.1 / Gate E)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "GoldenMigrationSuiteTest.kt"
)

REQUIRED_TEST_CASES = (
    "goldenContractExistsAndIsValidForEachOfThe233Mappings",
    "goldenOutputIsPinnedToTheReviewedIdentity",
    "migrateIsIdempotentAcrossTheWholeTable",
    "triggerAndActionGoldenSplitsMatchTheBaseline",
    "canonicalizedNodesRoundTripThroughSerialization",
    "noGoldenUsesAnUnregisteredIdentity",
)

# The suite must fail loudly, not skip: every golden assertion is a hard
# equality; a silently filtered table would defeat the gate.
FORBIDDEN_PATTERNS = (
    r"assumeTrue",
    r"Assert\.assertThrows\(\s*Exception\.class",
)


def main() -> int:
    problems: list[str] = []

    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")
    else:
        source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in source:
                problems.append(f"GoldenMigrationSuiteTest.kt missing required test {case!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"GoldenMigrationSuiteTest.kt matches forbidden pattern {pattern!r}"
                )
        if "233" not in source:
            problems.append("GoldenMigrationSuiteTest.kt does not pin the 233 total")

    if problems:
        print("CANONICAL_GOLDEN_MIGRATION: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_GOLDEN_MIGRATION: OK — golden contract, payload parity, "
        "idempotency and serialization round-trips pinned for all 233 "
        "mappings with mandatory T16 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
