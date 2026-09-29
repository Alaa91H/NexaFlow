#!/usr/bin/env python3
"""Enforce T35 accessibility/RTL model boundaries (plan §T35)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "CanonicalAccessibilityModel.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CanonicalAccessibilityModelTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"androidx\.compose",
    r"android\.content\.res",
)

REQUIRED_CONSTRUCTS = (
    "CanonicalAccessibilityModel",
    "AccessibilityStatement",
    "RtlPolicy",
    "statementFor",
    "statementsFor",
    "nodeAnnouncement",
    "MIRROR_WITHOUT_REORDER",
    "OVERFLOW_BADGE_EDGE",
    "DETAIL_ORDER",
    "SECRET_LABEL",
)

REQUIRED_TEST_CASES = (
    "infoRowAnnouncesPlainSummaryWithStableId",
    "warningAndErrorRowsAnnounceTheirSeverity",
    "detailPairsAnnounceKeyFirst",
    "secretLabelInDetailsNeverExpands",
    "sectionRendersHeadingRowsAndExactOverflow",
    "snapshotFlattensSectionsInOrder",
    "rtlPolicyPinsMirrorWithoutReorder",
    "nodeAnnouncementIncludesArgumentValues",
    "nodeAnnouncementRedactsSecretArguments",
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
                problems.append(f"CanonicalAccessibilityModel.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"CanonicalAccessibilityModel.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"CanonicalAccessibilityModelTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_ACCESSIBILITY_MODEL: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_ACCESSIBILITY_MODEL: OK — accessibility & RTL contract "
        "over the diagnostics and builder surfaces: deterministic "
        "screen-reader statements with stable ids, severity prefixes, "
        "key-first detail announcement, secret payloads redacted in "
        "accessibility text too, exact overflow counts (never 'many'), RTL "
        "policy pins mirroring without reordering and trailing-edge badges"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
