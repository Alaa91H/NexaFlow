#!/usr/bin/env python3
"""Enforce T44 canonical release closure (plan §T44).

The closing gate of the canonical plan. It pins the release decision as a
procedure, not an artifact: the repository ships a **verified candidate**
and the tag remains an explicit, reviewed act per docs/RELEASING.md.

Fail closed unless ALL of the following hold:

  1. Every canonical gate passes now via the T38 readiness aggregate
     (which itself runs every gate, checks the changelog and requires a
     clean tree).
  2. The release procedure still refuses to tag silently: RELEASING.md
     keeps the explicit-tagging and never-move-a-tag rules, and the CI
     tag-hygiene audit stays wired for version tags only.
  3. The evidence boundary stays documented: JVM gates never certify
     device behavior (VALIDATION.md and RELEASING.md both say so).
  4. The canonical guide and inventory record the closing milestones
     (T42 documentation closure, T43 measured regression ledger).
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RELEASING = ROOT / "docs/RELEASING.md"
VALIDATION = ROOT / "docs/VALIDATION.md"
CI_WORKFLOW = ROOT / ".github/workflows/nexaflow-ci.yml"
INVENTORY = ROOT / "docs/architecture/canonical-automation-inventory.md"
GUIDE = ROOT / "docs/canonical-automation.md"

REQUIRED_RELEASING_PHRASES = (
    "Release tags are created explicitly",
    "Never move an existing published tag",
    "does not prove a physical device matrix",
)

REQUIRED_VALIDATION_PHRASES = (
    "do not infer device results from JVM tests",
)

REQUIRED_CI_MARKERS = (
    # Tag hygiene must stay scoped to version tags, never run as a silent tag.
    "audit_catalog_and_releases.py tag-changelog",
    "startsWith(github.ref, 'refs/tags/')",
)

REQUIRED_INVENTORY_STATUSES = (
    "T42: **implemented**",
    "T43: **implemented**",
)


def main() -> int:
    problems: list[str] = []

    # 1. The whole readiness aggregate (every gate + changelog + clean tree).
    proc = subprocess.run(
        [sys.executable, str(ROOT / "scripts/check_canonical_release_readiness.py")],
        capture_output=True,
        text=True,
        timeout=300,
    )
    if proc.returncode != 0:
        lines = (proc.stdout or proc.stderr).strip().splitlines()
        for line in lines[:8]:
            problems.append(f"readiness: {line}")
        if not lines:
            problems.append("readiness aggregate failed with no output")

    # 2. The explicit-tagging rules stay pinned.
    if RELEASING.is_file():
        releasing = RELEASING.read_text(encoding="utf-8")
        for phrase in REQUIRED_RELEASING_PHRASES:
            if phrase not in releasing:
                problems.append(f"RELEASING.md missing {phrase!r}")
    else:
        problems.append("missing docs/RELEASING.md")

    if CI_WORKFLOW.is_file():
        ci_source = CI_WORKFLOW.read_text(encoding="utf-8")
        for marker in REQUIRED_CI_MARKERS:
            if marker not in ci_source:
                problems.append(f"CI missing tag-hygiene marker {marker!r}")
    else:
        problems.append("missing CI workflow")

    # 3. The evidence boundary stays documented.
    if VALIDATION.is_file():
        validation = VALIDATION.read_text(encoding="utf-8")
        for phrase in REQUIRED_VALIDATION_PHRASES:
            if phrase not in validation:
                problems.append(f"VALIDATION.md missing {phrase!r}")
    else:
        problems.append("missing docs/VALIDATION.md")

    # 4. The closing milestones are recorded in the migration record.
    if INVENTORY.is_file():
        inventory = INVENTORY.read_text(encoding="utf-8")
        for status in REQUIRED_INVENTORY_STATUSES:
            if status not in inventory:
                problems.append(f"inventory missing closing status {status!r}")
        if "retirement ledger" not in inventory:
            problems.append("inventory missing the retirement ledger")
    else:
        problems.append("missing canonical automation inventory doc")

    if not GUIDE.is_file():
        problems.append("missing docs/canonical-automation.md")

    if problems:
        print("CANONICAL_RELEASE_CLOSURE: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_RELEASE_CLOSURE: OK — release closure verified: every "
        "canonical gate passes on a clean tree, the changelog and validation "
        "record are release-ready, tagging stays an explicit reviewed act "
        "with hygiene scoped to version tags, and the JVM-vs-device "
        "evidence boundary is documented — the candidate is verified, the "
        "tag is a human decision"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
