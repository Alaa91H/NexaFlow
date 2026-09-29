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
CONFIGURATOR_TEST = ROOT / (
    "feature/automation-builder/src/test/java/com/nexaflow/feature/builder/"
    "NodeConfiguratorAccessibilityTest.kt"
)
CATEGORY_LAYOUT_TEST = ROOT / (
    "feature/automation-builder/src/test/java/com/nexaflow/feature/builder/"
    "CategoryAccordionLayoutTest.kt"
)
CONSTRAINT_LAYOUT_TEST = ROOT / (
    "feature/automation-builder/src/test/java/com/nexaflow/feature/builder/"
    "ConstraintEditorCardLayoutTest.kt"
)
STRING_PARITY_GATE = ROOT / "scripts/check_strings_parity.py"
TRANSLATION_AUDIT = ROOT / "scripts/audit_translation_completeness.py"

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

    for path in (
        CONFIGURATOR_TEST,
        CATEGORY_LAYOUT_TEST,
        CONSTRAINT_LAYOUT_TEST,
        STRING_PARITY_GATE,
        TRANSLATION_AUDIT,
    ):
        if not path.is_file():
            problems.append(f"missing product accessibility wiring {path.relative_to(ROOT)}")

    if CONFIGURATOR_TEST.is_file():
        configurator_tests = CONFIGURATOR_TEST.read_text(encoding="utf-8")
        for token in (
            "LayoutDirection.Rtl",
            "fontScale = 2f",
            "boundsInRoot.height >= 48f",
            "unifiedConfiguratorKeepsReadableSemanticsAndTouchTargetInRtlLargeFont",
            "longLocalizedTitleAndValueRemainInSemanticsWithoutEllipsisReplacement",
        ):
            if token not in configurator_tests:
                problems.append(f"configurator accessibility test missing {token!r}")

    if CATEGORY_LAYOUT_TEST.is_file():
        category_tests = CATEGORY_LAYOUT_TEST.read_text(encoding="utf-8")
        for token in (
            "categoryTabs_doNotOverlapCatalogContent_onNarrowRtlLargeFontLayout",
            "animatedConditionCatalog_stacksDirectChildren_inNarrowRtlLargeFontLayout",
            "fontScale = 2f",
        ):
            if token not in category_tests:
                problems.append(f"category RTL/large-font coverage missing {token!r}")

    if CONSTRAINT_LAYOUT_TEST.is_file():
        constraint_tests = CONSTRAINT_LAYOUT_TEST.read_text(encoding="utf-8")
        if "chargingStateChoices_wrapWithoutTextOverlap_onNarrowRtlLargeFontLayout" not in constraint_tests:
            problems.append("constraint RTL/large-font regression test is missing")

    if STRING_PARITY_GATE.is_file():
        parity = STRING_PARITY_GATE.read_text(encoding="utf-8")
        if "values" not in parity or "strings.xml" not in parity:
            problems.append("string parity gate no longer audits localized string resources")

    if TRANSLATION_AUDIT.is_file():
        translations = TRANSLATION_AUDIT.read_text(encoding="utf-8")
        if "values-" not in translations:
            problems.append("translation completeness gate no longer scans locale resources")

    if problems:
        print("CANONICAL_ACCESSIBILITY_MODEL: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_ACCESSIBILITY_MODEL: OK — domain announcements are backed "
        "by product Compose regression coverage: unified configurator semantics "
        "and >=48dp touch target at 2x font, narrow RTL catalog/constraint "
        "layouts without overlap, full important-value semantics, secret-safe "
        "announcements, and string-parity/translation completeness gates"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
