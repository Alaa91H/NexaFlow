#!/usr/bin/env python3
"""Enforce T07 capability-resolver architecture boundaries (plan §7, ADR-007)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN_CANONICAL = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"

RESOLVER_FILE = DOMAIN_CANONICAL / "CapabilityGraph.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CapabilityGraphTest.kt"
)

# ADR-007 invariants that must never regress in the canonical resolver:
#  - no silent intent substitution on unsupported requests;
#  - no GlobalScope / time-dependent or random resolution (determinism).
FORBIDDEN_PATTERNS = (
    r"fallbackTo\w*\s*\(",
    r"GlobalScope",
    r"System\.currentTimeMillis\(\)",
    r" kotlin\.random\.Random\b",
)

REQUIRED_CONSTRUCTS = (
    "ProviderDescriptor",
    "ProviderCapability",
    "OperationCapabilityRequirements",
    "CapabilitySelectionPolicy",
    "CapabilityGraphSnapshot",
    "CanonicalCapabilityResolver",
    "CapabilityResolution",
    "CapabilityResolutionStatus",
    "CapabilityResolutionError",
    "ProviderExclusion",
)

REQUIRED_TEST_CASES = (
    "providerSelectionIsDeterministicAcrossRepeatedCalls",
    "privilegedProviderRequiresExplicitOptIn",
    "partialAvailabilityNeverUpgradesToExecutable",
    "unobservedBackendFailsClosed",
    "unsupportedProviderIsNeverSelectedAsSilentFallback",
    "unknownOperationIsUnsupportedNotGuessed",
    "pinnedBackendWinsOverRanking",
    "fallbackOrderFollowsRankingAfterSelection",
)


def main() -> int:
    problems: list[str] = []

    if not RESOLVER_FILE.is_file():
        problems.append(f"missing {RESOLVER_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if RESOLVER_FILE.is_file():
        source = RESOLVER_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"CapabilityGraph.kt missing required T07 construct {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"CapabilityGraph.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"CapabilityGraphTest.kt missing required test {case!r}"
                )

    if problems:
        print("CANONICAL_CAPABILITY_RESOLVER: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_CAPABILITY_RESOLVER: OK — "
        "deterministic provider selection, explicit policy gates, fail-closed "
        "unknowns and no silent intent substitution enforced with mandatory "
        "T07 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
