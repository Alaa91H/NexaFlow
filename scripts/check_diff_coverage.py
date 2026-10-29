#!/usr/bin/env python3
"""Diff coverage gate (NFA-04): changed production lines must be tested.

`coverageGate` enforces a per-module floor, which lets a large module absorb a
completely untested feature. This gate closes that hole: it parses the unified
diff against a base commit, keeps the added/modified lines of production Kotlin
sources, and checks them against the JaCoCo XML the module already produces.

Only executable lines appear in a JaCoCo report, so comments, blank lines and
declarations without bytecode are ignored instead of counted as uncovered.
Files in modules with no coverage report are reported and skipped — they are
already visible in `coverageGate` as report-only modules.

Usage:
    python3 scripts/check_diff_coverage.py --base origin/main
    python3 scripts/check_diff_coverage.py --diff-file pr.diff --threshold 0.8
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

DEFAULT_THRESHOLD = 0.80
PRODUCTION_SOURCE = re.compile(r"^(?P<module>[^/]+(?:/[^/]+)*)/src/main/(?:java|kotlin)/(?P<package>.+)/(?P<name>[^/]+\.kt)$")
HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(?P<start>\d+)(?:,(?P<count>\d+))? @@")


@dataclass(frozen=True)
class ChangedFile:
    path: str
    added_lines: tuple[int, ...]


def parse_diff(diff_text: str) -> list[ChangedFile]:
    """Extracts added line numbers per file from a unified diff."""
    files: list[ChangedFile] = []
    current: str | None = None
    added: list[int] = []
    next_line = 0

    def flush() -> None:
        nonlocal current, added
        if current is not None:
            files.append(ChangedFile(current, tuple(sorted(set(added)))))
        current = None
        added = []

    for raw in diff_text.splitlines():
        if raw.startswith("+++ "):
            flush()
            target = raw[4:].strip()
            current = None if target == "/dev/null" else target.removeprefix("b/")
            continue
        if raw.startswith("--- "):
            continue
        match = HUNK.match(raw)
        if match:
            next_line = int(match.group("start"))
            continue
        if current is None:
            continue
        if raw.startswith("+"):
            added.append(next_line)
            next_line += 1
        elif raw.startswith("-"):
            continue
        else:
            next_line += 1
    flush()
    return [entry for entry in files if entry.added_lines]


def git_diff(base: str, repo: Path) -> str:
    merge_base = subprocess.run(
        ["git", "merge-base", base, "HEAD"],
        cwd=repo,
        check=False,
        capture_output=True,
        text=True,
    )
    base_ref = merge_base.stdout.strip() if merge_base.returncode == 0 else base
    result = subprocess.run(
        ["git", "diff", "--unified=0", "--no-color", base_ref, "HEAD", "--", "*.kt"],
        cwd=repo,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout


def load_report(report: Path) -> dict[tuple[str, str], dict[int, tuple[int, int]]]:
    """Maps (package path, file name) to {line: (missed instructions, covered instructions)}."""
    root = ET.parse(report).getroot()
    sources: dict[tuple[str, str], dict[int, tuple[int, int]]] = {}
    for package in root.iter("package"):
        package_path = package.get("name", "")
        for source in package.findall("sourcefile"):
            key = (package_path, source.get("name", ""))
            lines: dict[int, tuple[int, int]] = {}
            for line in source.findall("line"):
                try:
                    lines[int(line.get("nr", ""))] = (
                        int(line.get("mi", "0")),
                        int(line.get("ci", "0")),
                    )
                except ValueError:
                    continue
            sources[key] = lines
    return sources


def evaluate(
    changed: list[ChangedFile],
    repo: Path,
    threshold: float,
) -> tuple[int, int, list[str], list[str]]:
    """Returns (covered, measured, failures, notes) over changed production lines."""
    covered = 0
    measured = 0
    failures: list[str] = []
    notes: list[str] = []
    reports: dict[str, dict[tuple[str, str], dict[int, tuple[int, int]]] | None] = {}

    for entry in changed:
        match = PRODUCTION_SOURCE.match(entry.path)
        if not match:
            continue
        module = match.group("module")
        package = match.group("package")
        name = match.group("name")
        if module not in reports:
            report = repo / module / "build/coverage/report.xml"
            reports[module] = load_report(report) if report.exists() else None
        sources = reports[module]
        if sources is None:
            notes.append(f"{entry.path}: no coverage report for :{module.replace('/', ':')} (skipped)")
            continue
        lines = sources.get((package, name))
        if lines is None:
            notes.append(f"{entry.path}: not instrumented by the coverage report (skipped)")
            continue
        file_covered = 0
        file_measured = 0
        for line in entry.added_lines:
            counter = lines.get(line)
            if counter is None:
                continue
            missed, hit = counter
            if missed == 0 and hit == 0:
                continue
            file_measured += 1
            if hit > 0:
                file_covered += 1
        covered += file_covered
        measured += file_measured
        if file_measured:
            ratio = file_covered / file_measured
            status = "ok" if ratio >= threshold else "LOW"
            print(
                f"DIFF_COVERAGE: {entry.path} {ratio * 100:.1f}% "
                f"({file_covered}/{file_measured}) [{status}]"
            )
            if ratio < threshold:
                failures.append(
                    f"{entry.path}: {ratio * 100:.1f}% of changed executable lines covered"
                )
        else:
            notes.append(f"{entry.path}: no executable changed lines")
    return covered, measured, failures, notes


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=".", help="Repository root (default: .)")
    parser.add_argument("--base", default=os.environ.get("DIFF_BASE", "origin/main"))
    parser.add_argument("--diff-file", help="Read a unified diff instead of invoking git")
    parser.add_argument("--threshold", type=float, default=DEFAULT_THRESHOLD)
    parser.add_argument(
        "--fail-on-empty",
        action="store_true",
        help="Fail when the diff touches production sources but none are measurable.",
    )
    args = parser.parse_args(argv)

    repo = Path(args.repo).resolve()
    diff_text = Path(args.diff_file).read_text() if args.diff_file else git_diff(args.base, repo)
    changed = parse_diff(diff_text)
    covered, measured, failures, notes = evaluate(changed, repo, args.threshold)

    for note in notes:
        print(f"DIFF_COVERAGE: {note}")
    if measured == 0:
        if args.fail_on_empty and any(PRODUCTION_SOURCE.match(e.path) for e in changed):
            print("DIFF_COVERAGE: FAIL — changed production files have no measured lines")
            return 1
        print("DIFF_COVERAGE: no measurable changed lines — pass")
        return 0
    ratio = covered / measured
    print(
        f"DIFF_COVERAGE: {ratio * 100:.1f}% of changed executable lines covered "
        f"({covered}/{measured}), threshold {args.threshold * 100:.0f}%"
    )
    if failures:
        for failure in failures:
            print(f"DIFF_COVERAGE: FAIL — {failure}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
