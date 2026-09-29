#!/usr/bin/env python3
"""Enforce T38 release-candidate readiness over the canonical platform (plan §T38)."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHANGELOG = ROOT / "CHANGELOG.md"
INVENTORY = ROOT / "docs/architecture/canonical-automation-inventory.md"
CI_WORKFLOW = ROOT / ".github/workflows/nexaflow-ci.yml"

# Every canonical gate that must pass for an RC (the whole family; running
# them all is the readiness definition — one aggregate, fail-closed verdict).
READINESS_GATES = (
    "check_canonical_baseline",
    "check_canonical_validation_pipeline",
    "check_canonical_legacy_adapter",
    "check_canonical_execution_planner",
    "check_canonical_configurator",
    "check_canonical_family_media_navigation",
    "check_canonical_family_connectivity",
    "check_canonical_family_display_sound",
    "check_canonical_family_applications",
    "check_canonical_family_communication",
    "check_canonical_family_power_sensors",
    "check_canonical_family_time_location",
    "check_canonical_family_advanced_external",
    "check_canonical_runtime_cutover",
    "check_canonical_persistence_policy",
    "check_canonical_migration_rollout",
    "check_canonical_consolidation_optimizer",
    "check_canonical_diagnostics_model",
    "check_canonical_plugin_sdk",
    "check_canonical_fault_injection",
    "check_canonical_performance_budget",
    "check_canonical_security_auditor",
    "check_canonical_accessibility_model",
    "check_canonical_device_matrix",
    "check_canonical_architecture_fitness",
    "check_canonical_legacy_retirement",
    "check_canonical_plugin_conditions",
    "check_canonical_docs_closure",
    "check_canonical_perf_regression",
    # NOTE: check_canonical_final_audit is deliberately NOT here — the final
    # audit is the umbrella that runs this readiness gate itself; adding it
    # would create an infinite mutual recursion between the two runners.
)

REQUIRED_INVENTORY_STATUSES = (
    "T26: **implemented**",
    "T27: **implemented**",
    "T28: **implemented**",
    "T29: **implemented**",
    "T30: **implemented**",
    "T31: **implemented**",
    "T32: **implemented**",
    "T33: **implemented**",
    "T34: **implemented**",
    "T35: **implemented**",
    "T36: **implemented**",
    "T37: **implemented**",
    "T38: **implemented**",
    "T39: **implemented**",
    "T40: **implemented**",
    "T41: **implemented**",
    "T42: **implemented**",
    "T43: **implemented**",
)


def run_gate(gate: str) -> tuple[str, int]:
    proc = subprocess.run(
        [sys.executable, str(ROOT / "scripts" / f"{gate}.py")],
        capture_output=True,
        text=True,
        timeout=120,
    )
    return gate, proc.returncode


def main() -> int:
    problems: list[str] = []

    # 1. Every readiness gate exists, is wired into CI, and passes now.
    for gate in READINESS_GATES:
        gate_path = ROOT / "scripts" / f"{gate}.py"
        if not gate_path.is_file():
            problems.append(f"missing gate script scripts/{gate}.py")
            continue
        ci_source = CI_WORKFLOW.read_text(encoding="utf-8") if CI_WORKFLOW.is_file() else ""
        if gate not in ci_source and gate.replace("_", "-") not in ci_source:
            problems.append(f"gate {gate} not wired into CI")

    # 2. Run every gate; a single failure blocks the RC.
    for gate in READINESS_GATES:
        if not (ROOT / "scripts" / f"{gate}.py").is_file():
            continue
        name, code = run_gate(gate)
        if code != 0:
            problems.append(f"gate {name} FAILED (exit {code})")

    # 3. The inventory must declare every T26+ phase implemented.
    if INVENTORY.is_file():
        inventory = INVENTORY.read_text(encoding="utf-8")
        for status in REQUIRED_INVENTORY_STATUSES:
            if status not in inventory:
                problems.append(f"inventory missing implementation status {status!r}")
    else:
        problems.append("missing canonical automation inventory doc")

    # 4. A changelog exists with an Unreleased section (release notes land
    #    there before the tag, satisfying the tag↔changelog hygiene audit).
    if CHANGELOG.is_file():
        changelog = CHANGELOG.read_text(encoding="utf-8")
        if "## [Unreleased]" not in changelog:
            problems.append("CHANGELOG.md missing an [Unreleased] section")
    else:
        problems.append("missing CHANGELOG.md")

    # 5. The working tree must be clean (nothing half-committed ships).
    try:
        status = subprocess.run(
            ["git", "status", "--porcelain"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=30,
        )
        if status.stdout.strip():
            problems.append(
                "working tree is not clean; commit or stash before tagging an RC"
            )
    except OSError as error:
        problems.append(f"could not check git status: {error}")

    if problems:
        print("CANONICAL_RELEASE_READINESS: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_RELEASE_READINESS: OK — release candidate ready: all 30 "
        "canonical gates pass on the current tree and are wired into CI, the "
        "inventory declares every T26+ phase implemented, the changelog "
        "carries an Unreleased section for release notes, and the working "
        "tree is clean"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
