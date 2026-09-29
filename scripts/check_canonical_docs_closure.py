#!/usr/bin/env python3
"""Enforce T42 documentation closure gates (plan §T42)."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GUIDE = ROOT / "docs/canonical-automation.md"
DOC_INDEX = ROOT / "docs/README.md"
VALIDATION = ROOT / "docs/VALIDATION.md"
CHANGELOG = ROOT / "CHANGELOG.md"
INVENTORY = ROOT / "docs/architecture/canonical-automation-inventory.md"

REQUIRED_GUIDE_SECTIONS = (
    "# Canonical automation platform",
    "## What the platform is",
    "## The contracts that surround execution",
    "## Standing guarantees",
    "## Verification (the gates)",
    "## Status",
)

REQUIRED_GUIDE_PHRASES = (
    # The single runtime path is documented.
    "legacy input",
    "cutover",
    # The standing guarantees are pinned in the guide.
    "No generic shell",
    "Secrets are never exposed",
    "Unknown is not false",
    "retired by containment, not deletion",
    # Verification is described honestly: JVM evidence is not device evidence.
    "testDebugUnitTest",
    "deterministic",
)

REQUIRED_GUIDE_LINKS = (
    "canonical-automation-inventory.md",
    "VALIDATION.md",
)

REQUIRED_CONTRACT_ROWS = (
    "T05", "T09", "T10", "T15", "T26", "T27", "T29", "T30", "T31", "T34",
    "T35", "T41",
)

REQUIRED_INDEX_LINK = "| [canonical-automation.md](canonical-automation.md) | Current guide / evidence |"


def main() -> int:
    problems: list[str] = []

    if not GUIDE.is_file():
        problems.append(f"missing {GUIDE.relative_to(ROOT)}")
    else:
        guide = GUIDE.read_text(encoding="utf-8")
        for heading in REQUIRED_GUIDE_SECTIONS:
            if heading not in guide:
                problems.append(f"canonical guide missing section {heading!r}")
        for phrase in REQUIRED_GUIDE_PHRASES:
            if phrase.lower() not in guide.lower():
                problems.append(f"canonical guide missing phrase {phrase!r}")
        for link in REQUIRED_GUIDE_LINKS:
            if link not in guide:
                problems.append(f"canonical guide missing link {link!r}")
        for row in REQUIRED_CONTRACT_ROWS:
            if row not in guide:
                problems.append(f"canonical guide missing contract row {row!r}")

    if not DOC_INDEX.is_file():
        problems.append("missing docs/README.md documentation index")
    elif REQUIRED_INDEX_LINK not in DOC_INDEX.read_text(encoding="utf-8"):
        problems.append("documentation index does not list docs/canonical-automation.md")

    if VALIDATION.is_file():
        validation = VALIDATION.read_text(encoding="utf-8")
        for phrase in (
            "Canonical platform gates",
            "check_canonical_final_audit.py",
            "do not infer device results from JVM tests",
        ):
            if phrase not in validation:
                problems.append(f"VALIDATION.md missing {phrase!r}")
    else:
        problems.append("missing docs/VALIDATION.md")

    if CHANGELOG.is_file():
        changelog = CHANGELOG.read_text(encoding="utf-8")
        if "## [Unreleased]" not in changelog:
            problems.append("CHANGELOG.md missing an [Unreleased] section")
        if "Canonical automation platform" not in changelog:
            problems.append("CHANGELOG.md missing the canonical platform entry")
    else:
        problems.append("missing CHANGELOG.md")

    if INVENTORY.is_file():
        inventory = INVENTORY.read_text(encoding="utf-8")
        if "retirement ledger" not in inventory:
            problems.append("inventory missing the retirement ledger")
    else:
        problems.append("missing canonical automation inventory doc")

    if problems:
        print("CANONICAL_DOCS_CLOSURE: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_DOCS_CLOSURE: OK — documentation closed: the canonical "
        "platform has a current reference guide linked from the docs index, "
        "the changelog records the platform milestone under Unreleased, "
        "VALIDATION records the local gate evidence with the explicit "
        "JVM-vs-device boundary, and the inventory keeps the migration "
        "record with the retirement ledger"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
