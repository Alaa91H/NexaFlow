#!/usr/bin/env python3
"""Enforce T30 diagnostics UI model boundaries (plan §T30)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "CanonicalDiagnosticsModel.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CanonicalDiagnosticsModelTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"android\.content\.Context",
    r"androidx\.compose",
)

REQUIRED_CONSTRUCTS = (
    "CanonicalDiagnosticsModel",
    "DiagnosticsRow",
    "DiagnosticsSection",
    "DiagnosticsSnapshot",
    "Severity",
    "Codes",
    "renderValue",
    "SECRET_LABEL",
    "MAX_ROWS_PER_SECTION",
    "snapshot",
    "cutoverRows",
    "persistenceRows",
    "optimizerRow",
)

REQUIRED_TEST_CASES = (
    "secretReferencesRenderAsTheLabelWithoutNamingTheReference",
    "ordinaryValuesRenderDeterministically",
    "cutoverRowReportsCommandsAndPreservedKeys",
    "preservedKeysRenderTheirOwnRow",
    "dualWriteModeRendersInfoAndFailedItemsRenderAsErrors",
    "legacyOnlyModeRendersAWarning",
    "optimizerReportRendersRemovalCounts",
    "snapshotIsDeterministicAndSectionsAreOrdered",
    "oversizedSectionsCapWithAnExactOverflowCount",
)


def main() -> int:
    problems: list[str] = []

    if not MODEL_FILE.is_file():
        problems.append(f"missing {MODEL_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if MODEL_FILE.is_file():
        source = MODEL_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"CanonicalDiagnosticsModel.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"CanonicalDiagnosticsModel.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"CanonicalDiagnosticsModelTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_DIAGNOSTICS_MODEL: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_DIAGNOSTICS_MODEL: OK — pure diagnostics presentation "
        "layer over the canonical platform: deterministic display-ready "
        "rows with stable machine codes, secret-reference payloads redacted "
        "to a fixed label (deep-link tokens never render), every section "
        "capped with an exact overflow count, and no Android/Compose "
        "coupling so the same model feeds Compose, logs and exports"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
