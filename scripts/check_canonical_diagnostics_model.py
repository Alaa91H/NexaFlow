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
RUN_EXPLAINER_FILE = ROOT / (
    "core/logging/src/main/java/com/nexaflow/core/logging/RunExplainer.kt"
)
TRACE_FILE = ROOT / (
    "core/logging/src/main/java/com/nexaflow/core/logging/ExecutionTraceEvent.kt"
)
DETAILS_VM_FILE = ROOT / (
    "feature/history/src/main/java/com/nexaflow/feature/history/"
    "ExecutionDetailsViewModel.kt"
)
DETAILS_SCREEN_FILE = ROOT / (
    "feature/history/src/main/java/com/nexaflow/feature/history/"
    "ExecutionDetailsScreen.kt"
)
RUN_EXPLAINER_TEST = ROOT / (
    "core/logging/src/test/java/com/nexaflow/core/logging/RunExplainerTest.kt"
)
DETAILS_TEST = ROOT / (
    "feature/history/src/test/java/com/nexaflow/feature/history/"
    "ExecutionDetailsExplanationTest.kt"
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

    # Product closure: T30 is not complete with a pure presentation model
    # alone. The real history/details surface must consume structured trace
    # reasons, correlate one exact run, and redact detail again before UI.
    for path in (
        RUN_EXPLAINER_FILE,
        TRACE_FILE,
        DETAILS_VM_FILE,
        DETAILS_SCREEN_FILE,
        RUN_EXPLAINER_TEST,
        DETAILS_TEST,
    ):
        if not path.is_file():
            problems.append(f"missing product diagnostics wiring {path.relative_to(ROOT)}")

    if TRACE_FILE.is_file():
        trace = TRACE_FILE.read_text(encoding="utf-8")
        for token in (
            "class TraceRecorder",
            "SecretRedactor.redact(stamped.detail)",
            "traceReasonCode",
            "traceDetail",
        ):
            if token not in trace:
                problems.append(f"structured trace boundary missing {token!r}")

    if RUN_EXPLAINER_FILE.is_file():
        explainer = RUN_EXPLAINER_FILE.read_text(encoding="utf-8")
        for token in (
            "object RunExplainer",
            "SecretRedactor.redact(event.detail)",
            "explainTimeline",
            "TraceReasons.CONSTRAINT_BLOCKED",
            "TraceReasons.CAPABILITY_BLOCKED",
            "TraceReasons.ACTION_FAILED",
            "TraceReasons.OUTCOME_UNCERTAIN",
        ):
            if token not in explainer:
                problems.append(f"run explainer missing {token!r}")

    if DETAILS_VM_FILE.is_file():
        details_vm = DETAILS_VM_FILE.read_text(encoding="utf-8")
        for token in (
            "RunExplainer.Explanation",
            "explanationForRecord",
            "RunExplainer.explainTimeline",
            "entry.traceRunId",
        ):
            if token not in details_vm:
                problems.append(f"execution details ViewModel missing {token!r}")

    if DETAILS_SCREEN_FILE.is_file():
        details_screen = DETAILS_SCREEN_FILE.read_text(encoding="utf-8")
        for token in (
            "RunExplanationCard",
            "R.string.why_run_title",
            "uiState.explanation",
        ):
            if token not in details_screen:
                problems.append(f"why-did-not-run product UI missing {token!r}")

    if RUN_EXPLAINER_TEST.is_file():
        explainer_tests = RUN_EXPLAINER_TEST.read_text(encoding="utf-8")
        for case in (
            "explanationRedactsDetailBeforeUiEvenForUntrustedTrace",
            "reportIsPrivacySafeAndStructured",
            "timelineTraceRetainsStructuredFieldsForExplainer",
        ):
            if f"fun {case}" not in explainer_tests:
                problems.append(f"RunExplainerTest missing {case!r}")

    if DETAILS_TEST.is_file():
        details_tests = DETAILS_TEST.read_text(encoding="utf-8")
        for case in (
            "skippedRunUsesStructuredTraceWithExactStartTime",
            "explicitRunIdCorrelatesTerminalTraceEvenWhenEventTimeDiffers",
            "nearbyTraceIsNotGuessedForAnotherRun",
            "explanationShownByDetailsScreenIsSecretSafe",
        ):
            if f"fun {case}" not in details_tests:
                problems.append(f"ExecutionDetailsExplanationTest missing {case!r}")

    if problems:
        print("CANONICAL_DIAGNOSTICS_MODEL: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_DIAGNOSTICS_MODEL: OK — canonical diagnostics model plus "
        "the real execution-details UI are wired to structured trace reasons; "
        "exact-run correlation avoids cross-run guesses, TraceRecorder and "
        "RunExplainer both redact details, and Why-did-not-run renders stable "
        "localized explanation/fix keys without exposing secrets"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
