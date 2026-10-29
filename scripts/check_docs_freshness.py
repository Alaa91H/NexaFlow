#!/usr/bin/env python3
"""Docs freshness gate (NFA-11).

An audit document is only useful while its baseline is honest. This gate makes
that structural: every registered document must declare

    Audit-Status: active | historical
    Audit-Baseline: <40-hex commit>

An ``active`` document fails when its baseline is more than ``--max-commits``
commits behind HEAD, which forces the author to either re-run the audit or mark
it historical. A ``historical`` document is exempt from distance checks but must
still name the commit it describes, so no reader mistakes it for a current
claim.

Usage:
    python3 scripts/check_docs_freshness.py
    python3 scripts/check_docs_freshness.py --max-commits 25
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MAX_COMMITS = 25

# Documents that make reviewable claims about the current tree. Evidence logs
# under docs/evidence/ are deliberately excluded: they are immutable snapshots.
REGISTRY = (
    "docs/AUDIT.md",
    "docs/MASTER_PLAN_STATUS_2026-10-05.md",
    "docs/audit/findings.md",
    "docs/audit/FULL_AUDIT_REMEDIATION_PLAN_2026-10-28_AR.md",
    "docs/audit/production-readiness-baseline-ar.md",
)

STATUS_RE = re.compile(r"^Audit-Status:\s*(active|historical)\s*$", re.MULTILINE)
BASELINE_RE = re.compile(r"^Audit-Baseline:\s*`?([0-9a-f]{40})`?\s*$", re.MULTILINE)


def git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=ROOT, check=True, capture_output=True, text=True, encoding="utf-8"
    ).stdout.strip()


def commit_exists(sha: str) -> bool:
    return (
        subprocess.run(
            ["git", "cat-file", "-e", f"{sha}^{{commit}}"],
            cwd=ROOT,
            capture_output=True,
        ).returncode
        == 0
    )


def commits_behind(sha: str) -> int:
    return int(git("rev-list", "--count", f"{sha}..HEAD"))


def check_document(path: Path, max_commits: int) -> list[str]:
    name = path.relative_to(ROOT).as_posix()
    if not path.is_file():
        return [f"{name}: registered document is missing"]
    text = path.read_text(encoding="utf-8")
    status_match = STATUS_RE.search(text)
    baseline_match = BASELINE_RE.search(text)
    if status_match is None:
        return [f"{name}: missing 'Audit-Status: active|historical' line"]
    if baseline_match is None:
        return [f"{name}: missing 'Audit-Baseline: <40-hex commit>' line"]
    sha = baseline_match.group(1)
    if not commit_exists(sha):
        return [f"{name}: Audit-Baseline {sha} is not a commit in this repository"]
    if status_match.group(1) == "historical":
        return []
    distance = commits_behind(sha)
    if distance > max_commits:
        return [
            f"{name}: active audit baseline {sha[:12]} is {distance} commits behind HEAD "
            f"(limit {max_commits}); re-run the audit or change the status to historical"
        ]
    return []


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--max-commits", type=int, default=DEFAULT_MAX_COMMITS)
    parser.add_argument("--list", action="store_true", help="print the registered documents and exit")
    args = parser.parse_args(argv)

    if args.list:
        for name in REGISTRY:
            print(name)
        return 0

    try:
        head = git("rev-parse", "--short", "HEAD")
    except (OSError, subprocess.CalledProcessError):
        print("DOCS_FRESHNESS: not a git checkout; cannot evaluate baselines", file=sys.stderr)
        return 2

    problems: list[str] = []
    for name in REGISTRY:
        problems.extend(check_document(ROOT / name, args.max_commits))

    if problems:
        for problem in problems:
            print(f"DOCS_FRESHNESS: {problem}", file=sys.stderr)
        return 1
    print(f"DOCS_FRESHNESS: OK — {len(REGISTRY)} registered documents declare a valid baseline at {head}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
