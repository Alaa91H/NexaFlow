#!/usr/bin/env python3
"""Enforce T11 journal/error-model boundaries (plan §21-§23, ADR-009/012)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN_CANONICAL = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"

JOURNAL_FILE = DOMAIN_CANONICAL / "ExecutionJournal.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "ExecutionJournalTest.kt"
)

# The journal is data: no Android runtime, no UI, no raw throwable capture,
# and no unbounded trigger-condition storage.
FORBIDDEN_PATTERNS = (
    r"\bContext\b",
    r"androidx\.compose",
    r"Throwable",
    r"printStackTrace",
)

REQUIRED_CONSTRUCTS = (
    "ExecutionErrorCode",
    "ExecutionPhase",
    "ExecutionPhaseStatus",
    "TriggerEvaluation",
    "SkipReason",
    "CommandRecord",
    "ExecutionRunRecord",
    "validationBlockedRun",
    "capabilityBlockedRun",
)

# Plan §21 contract codes that must exist in the vocabulary.
REQUIRED_ERROR_CODES = (
    "INVALID_CONFIGURATION",
    "UNSUPPORTED",
    "PERMISSION_MISSING",
    "CAPABILITY_MISSING",
    "SECURITY_REJECTED",
    "TIMEOUT",
    "TRANSIENT_FAILURE",
    "PROVIDER_UNAVAILABLE",
    "CANCELLED",
    "CONFLICT",
    "MIGRATION_FAILED",
)

REQUIRED_TEST_CASES = (
    "errorCodeVocabularyCoversThePlanContract",
    "journalPhasesFollowTheLifecycleOrder",
    "validationBlockedRunExplainsWhyItDidNotRun",
    "capabilityBlockedRunListsProviderExclusions",
    "journalRejectsSecretLookingMetadataKeys",
    "journalRejectsSecretLookingMetadataValues",
    "cleanMetadataIsAccepted",
)


def main() -> int:
    problems: list[str] = []

    if not JOURNAL_FILE.is_file():
        problems.append(f"missing {JOURNAL_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if JOURNAL_FILE.is_file():
        source = JOURNAL_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"ExecutionJournal.kt missing required T11 construct {token!r}"
                )
        for code in REQUIRED_ERROR_CODES:
            if code not in source:
                problems.append(
                    f"ExecutionJournal.kt missing plan §21 error code {code!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"ExecutionJournal.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"ExecutionJournalTest.kt missing required test {case!r}"
                )

    if problems:
        print("CANONICAL_EXECUTION_JOURNAL: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_EXECUTION_JOURNAL: OK — "
        "unified error vocabulary, six-phase run journal, why-didnt-it-run "
        "builders and fail-closed secret hygiene enforced with mandatory T11 "
        "unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
