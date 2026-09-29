#!/usr/bin/env python3
"""Enforce the T40 final canonical audit (plan §T40).

One fail-closed verdict that closes the canonical automation migration
record. The audit discovers every canonical gate script (itself excluded,
so the check has no bootstrap paradox) and requires that:

  1. Every canonical gate ships a unittest in scripts/tests.
  2. Every canonical gate is wired into the CI workflow.
  3. Every canonical gate passes on the current tree (they are all run).
  4. The Kotlin unit tests run in CI (the canonical model suites included).
  5. The inventory carries a closure status for every phase T05 through
     T39 plus the T39 retirement ledger.

A single missing or failing element blocks the audit; partial completion
is never reported as done.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
TESTS = SCRIPTS / "tests"
CI_WORKFLOW = ROOT / ".github/workflows/nexaflow-ci.yml"
INVENTORY = ROOT / "docs/architecture/canonical-automation-inventory.md"

SELF = "check_canonical_final_audit"

FIRST_PHASE = 5
LAST_PHASE = 39


def canonical_gates() -> list[str]:
    return sorted(
        path.stem
        for path in SCRIPTS.glob("check_canonical_*.py")
        if path.stem != SELF
    )


def main() -> int:
    problems: list[str] = []

    gates = canonical_gates()
    if not gates:
        problems.append("no canonical gate scripts found")

    ci_source = (
        CI_WORKFLOW.read_text(encoding="utf-8")
        if CI_WORKFLOW.is_file()
        else ""
    )

    # 1+2. Every canonical gate is wired into CI and carries a unittest.
    for gate in gates:
        if not (TESTS / f"test_{gate}.py").is_file():
            problems.append(
                f"canonical gate {gate} has no unittest "
                f"scripts/tests/test_{gate}.py"
            )
        if gate not in ci_source and gate.replace("_", "-") not in ci_source:
            problems.append(f"canonical gate {gate} is not wired into CI")

    # 3. Every canonical gate passes on the current tree.
    for gate in gates:
        proc = subprocess.run(
            [sys.executable, str(SCRIPTS / f"{gate}.py")],
            capture_output=True,
            text=True,
            timeout=120,
        )
        if proc.returncode != 0:
            lines = (proc.stdout or proc.stderr).strip().splitlines()
            detail = lines[0] if lines else "no output"
            problems.append(f"canonical gate {gate} FAILED: {detail}")

    # 4. The Kotlin unit tests (canonical model suites included) run in CI.
    if "testDebugUnitTest" not in ci_source:
        problems.append("CI does not run the Kotlin unit-test suites")

    # 5. The inventory closes the migration record phase by phase.
    if INVENTORY.is_file():
        inventory = INVENTORY.read_text(encoding="utf-8")
        for phase in range(FIRST_PHASE, LAST_PHASE + 1):
            token = f"T{phase:02d}: **implemented**"
            if token not in inventory:
                problems.append(f"inventory missing closure status {token!r}")
        if "retirement ledger" not in inventory:
            problems.append("inventory missing the retirement ledger")
    else:
        problems.append("missing canonical automation inventory doc")

    if problems:
        print("CANONICAL_FINAL_AUDIT: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        f"CANONICAL_FINAL_AUDIT: OK — canonical migration audit complete: "
        f"all {len(gates)} gates ship a unittest, stay wired into CI and "
        f"pass on the current tree, the Kotlin suites run in CI, and the "
        f"inventory closes every phase T{FIRST_PHASE:02d}-T{LAST_PHASE:02d} "
        f"with the retirement ledger"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
