#!/usr/bin/env python3
"""Enforce T43 measured performance regression boundaries (plan §T43)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEDGER_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "CanonicalPerfRegression.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CanonicalPerfRegressionTest.kt"
)
CI_WORKFLOW = ROOT / ".github/workflows/nexaflow-ci.yml"

# The ledger must stay deterministic: no clocks, no randomness, no I/O.
FORBIDDEN_PATTERNS = (
    r"System\.currentTimeMillis",
    r"System\.nanoTime",
    r"kotlin\.random\.Random",
    r"java\.util\.Date",
    r"java\.time\.Clock",
    r"File\(",
)

REQUIRED_CONSTRUCTS = (
    "CanonicalPerfRegression",
    "MAX_DEPTH",
    "MAX_FAN_OUT",
    "Measurement",
    "Baseline",
    "Comparison",
    "Regressed",
    "WithinBaseline",
    "ProbeResult",
    "PROBES",
    "BASELINES",
    "buildProbeTree",
    "measure",
    "runProbe",
    "runAll",
)

REQUIRED_PROBES = (
    "maxDepthSpine",
    "wideFanOut",
    "optimizerChurn",
)

REQUIRED_TEST_CASES = (
    "measurementIsIdenticalAcrossRepeatedRuns",
    "probeTreeFormulaIsStable",
    "unknownProbesFailClosed",
    "everyShippedProbeIsWithinItsBaseline",
    "anActualRegressionIsTypedWithBaselineAndObservation",
    "optimizerChurnConvergesInBoundedPasses",
    "everyProbeHasABaselineAndEveryBaselineHasAProbe",
)


def main() -> int:
    problems: list[str] = []

    if not LEDGER_FILE.is_file():
        problems.append(f"missing {LEDGER_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if LEDGER_FILE.is_file():
        source = LEDGER_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"CanonicalPerfRegression.kt missing {token!r}")
        for probe in REQUIRED_PROBES:
            if probe not in source:
                problems.append(f"CanonicalPerfRegression.kt missing probe {probe!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"CanonicalPerfRegression.kt matches nondeterminism pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"CanonicalPerfRegressionTest.kt missing {case!r}")

    # The ledger runs through the CI unit-test suites; the suite pattern must
    # keep covering the canonical package (the gate pins the wiring).
    if CI_WORKFLOW.is_file():
        ci_source = CI_WORKFLOW.read_text(encoding="utf-8")
        if "testDebugUnitTest" not in ci_source:
            problems.append("CI does not run the Kotlin unit-test suites")
    else:
        problems.append("missing CI workflow")

    if problems:
        print("CANONICAL_PERF_REGRESSION: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_PERF_REGRESSION: OK — measured regression ledger: three "
        "worst-case probes (maxDepthSpine, wideFanOut, optimizerChurn) run "
        "through the pure rewrite/measure pipeline with exact, "
        "platform-independent work counters; the checked-in baseline binds "
        "with typed regressions naming baseline and observation; no clocks, "
        "no randomness, no machine variance"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
