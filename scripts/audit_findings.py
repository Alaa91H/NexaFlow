#!/usr/bin/env python3
"""Lint and render the evidence-backed T02 atomic finding register."""
from __future__ import annotations

import argparse
import csv
import hashlib
import re
import subprocess
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CSV_PATH = Path("docs/audit/findings.csv")
MD_PATH = Path("docs/audit/findings.md")
CSV_FIELDS = [
    "finding_id", "classification", "severity", "area", "title", "evidence_refs",
    "observation", "impact", "prerequisite", "reproducer", "expected", "actual",
    "regression_test_target", "proposed_issue", "risk_class", "external_blocker", "historical_ref",
    "evidence_log_path", "evidence_log_sha256", "test_results",
]
CLASSIFICATIONS = {"CONFIRMED", "DOCUMENTED_GAP", "INVESTIGATE", "IMPLEMENTED_UNVERIFIED", "BLOCKED_EXTERNAL"}
SEVERITIES = {"P0", "P1", "P2", "P3", "INFO"}
EVIDENCE_REF = re.compile(r"^([0-9a-f]{40}):([A-Za-z0-9_./-]+):([1-9][0-9]*)$")


def current_head(root: Path) -> str:
    return subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=root, check=True,
        capture_output=True, text=True, encoding="utf-8",
    ).stdout.strip()


def validate_rows(rows: list[dict[str, str]], root: Path) -> list[str]:
    errors: list[str] = []
    seen: set[str] = set()
    for index, row in enumerate(rows, start=2):
        prefix = f"row {index}"
        missing = [field for field in CSV_FIELDS if field not in row]
        if missing:
            errors.append(f"{prefix}: missing columns: {', '.join(missing)}")
            continue
        row = {field: value or "" for field, value in row.items()}
        for field in CSV_FIELDS:
            if field not in {"external_blocker", "historical_ref", "evidence_log_path", "evidence_log_sha256"} and not (row[field] or "").strip():
                errors.append(f"{prefix}: {field} is required")
        finding_id = row["finding_id"].strip()
        if finding_id in seen:
            errors.append(f"{prefix}: duplicate finding_id {finding_id}")
        seen.add(finding_id)
        if row["classification"] not in CLASSIFICATIONS:
            errors.append(f"{prefix}: invalid classification {row['classification']!r}")
        if row["severity"] not in SEVERITIES:
            errors.append(f"{prefix}: invalid severity {row['severity']!r}")
        classification = row["classification"]
        if classification == "CONFIRMED":
            for field in ("reproducer", "expected", "actual", "regression_test_target", "proposed_issue"):
                if not row[field].strip() or row[field].strip() == "NOT_APPLICABLE":
                    errors.append(f"{prefix}: CONFIRMED finding requires {field}")
        if classification == "INVESTIGATE" and not re.search(r"pending|not yet|unresolved|unverified|investigate|remains unknown|not established", row["actual"], re.I):
            errors.append(f"{prefix}: INVESTIGATE actual must state what remains unverified")
        if classification == "BLOCKED_EXTERNAL" and not row["external_blocker"].strip():
            errors.append(f"{prefix}: BLOCKED_EXTERNAL finding requires external_blocker")
        if classification != "CONFIRMED" and row["evidence_log_path"].strip():
            errors.append(f"{prefix}: evidence_log_path is reserved for CONFIRMED findings")
        if classification != "BLOCKED_EXTERNAL" and row["external_blocker"].strip():
            errors.append(f"{prefix}: external_blocker is reserved for BLOCKED_EXTERNAL findings")
        if classification in {"DOCUMENTED_GAP", "IMPLEMENTED_UNVERIFIED", "BLOCKED_EXTERNAL"} and re.search(r"\b(PASS|passed|successfully certified)\b", row["test_results"], re.I):
            errors.append(f"{prefix}: test_results must not claim a passing verification for {classification}")

        refs = [part.strip() for part in row["evidence_refs"].split(";") if part.strip()]
        if not refs:
            errors.append(f"{prefix}: at least one SHA:path:line evidence reference is required")
        for ref in refs:
            match = EVIDENCE_REF.fullmatch(ref)
            if not match:
                errors.append(f"{prefix}: malformed evidence reference {ref!r}")
                continue
            sha, relative, line_text = match.groups()
            try:
                source = subprocess.run(
                    ["git", "show", f"{sha}:{relative}"], cwd=root,
                    check=True, capture_output=True, text=True, encoding="utf-8",
                ).stdout
            except subprocess.CalledProcessError:
                errors.append(f"{prefix}: evidence source does not exist at {sha}:{relative}")
                continue
            line_number = int(line_text)
            if line_number > len(source.splitlines()):
                errors.append(f"{prefix}: evidence line {ref!r} is outside file")
        if classification == "CONFIRMED":
            evidence_hash = row.get("evidence_log_sha256", "").strip()
            log_path = row.get("evidence_log_path", "").strip()
            if not evidence_hash or not re.fullmatch(r"[0-9a-f]{64}", evidence_hash):
                errors.append(f"{prefix}: CONFIRMED finding requires a SHA-256 evidence_log_sha256")
            if not log_path:
                errors.append(f"{prefix}: CONFIRMED finding requires evidence_log_path")
            else:
                log = (root / log_path).resolve()
                try:
                    log.relative_to(root.resolve())
                except ValueError:
                    errors.append(f"{prefix}: evidence log must be inside the repository")
                else:
                    if not log.is_file():
                        errors.append(f"{prefix}: evidence log does not exist: {log_path}")
                    elif evidence_hash and re.fullmatch(r"[0-9a-f]{64}", evidence_hash):
                        actual_hash = hashlib.sha256(log.read_bytes()).hexdigest()
                        if actual_hash != evidence_hash:
                            errors.append(f"{prefix}: evidence log SHA-256 mismatch for {log_path}")
                    else:
                        log_text = log.read_text(encoding="utf-8")
                        failed_cases = re.findall(r"^\s*FAIL\s+([A-Za-z0-9_.$-]+):", log_text, re.M)
                        if not failed_cases:
                            errors.append(f"{prefix}: CONFIRMED evidence log must list exact failed test cases")
                        if any(f"FAIL {case}:" not in row["actual"] for case in failed_cases):
                            errors.append(f"{prefix}: actual does not enumerate every failed test case from evidence")
    return errors


def render_report(rows: list[dict[str, str]], baseline_sha: str) -> str:
    counts = Counter(row["classification"] for row in rows)
    lines = [
        "# T02 atomic finding register", "",
        f"Audit evidence SHA(s): {baseline_sha}. This is a point-in-time source and evidence review; it does not certify device/OEM behavior.", "",
        "## Status counts", "",
        "| Classification | Count |", "|---|---:|",
    ]
    for status in sorted(CLASSIFICATIONS):
        lines.append(f"| {status} | {counts.get(status, 0)} |")
    lines.extend(["", "## Findings", ""])
    for row in rows:
        lines.extend([
            f"### {row['finding_id']} — {row['title']}", "",
            f"- Classification / severity: **{row['classification']} / {row['severity']}**; area: `{row['area']}`; risk: `{row['risk_class']}`.",
            f"- Evidence: `{row['evidence_refs']}`.",
            f"- Observation: {row['observation']}",
            f"- Impact: {row['impact']}",
            f"- Prerequisite: {row['prerequisite']}",
            f"- Minimal reproducer / verification: `{row['reproducer']}`",
            f"- Expected: {row['expected']}",
            f"- Actual: {row['actual']}",
            f"- Regression target: `{row['regression_test_target']}`; proposed task: {row['proposed_issue']}.",
            f"- Test results: `{row['test_results']}`",
            f"- External blocker: {row['external_blocker'] or 'None recorded.'}",
            f"- Historical clue (not treated as current proof): `{row['historical_ref'] or 'None'}`.", "",
        ])
    lines.extend([
        "## Audit coverage and limits", "",
        "The source review covers event identity/concurrency, cancellation and crash boundaries, clock and DST, permissions and locked boot, persistence, restore/import ownership, plugin trust, accessibility, performance, and localization. Related trigger/action family mappings remain traceable through the T01 inventories. Confidence labels distinguish current reproductions from code-level observations and missing external evidence.", "",
        "The register separates confirmed behavior, explicit gaps, investigations, unverified implementations, and external blockers. Absence of a physical device, provider account, or owner decision is recorded as a limitation; it is not converted into a software defect. A static source reference proves only the cited source shape, not dynamic behavior.", "",
        "All P0 candidates without a deterministic current reproducer remain labeled `INVESTIGATE`, `DOCUMENTED_GAP`, or `BLOCKED_EXTERNAL`; none is promoted to `CONFIRMED` by historical prose alone.", "",
    ])
    return "\n".join(lines)


def evidence_baseline(rows: list[dict[str, str]]) -> str:
    shas = sorted({
        match.group(1)
        for row in rows
        for ref in row.get("evidence_refs", "").split(";")
        if (match := EVIDENCE_REF.fullmatch(ref.strip()))
    })
    return ", ".join(f"`{sha}`" for sha in shas) if shas else "`UNAVAILABLE`"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="render findings.md from findings.csv")
    parser.add_argument("--check", action="store_true", help="lint evidence and require findings.md to match")
    args = parser.parse_args()
    if args.write and args.check:
        parser.error("choose either --write or --check")
    csv_path = ROOT / CSV_PATH
    md_path = ROOT / MD_PATH
    with csv_path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        rows = list(reader)
        headers = set(reader.fieldnames or [])
    if headers != set(CSV_FIELDS):
        missing = sorted(set(CSV_FIELDS) - headers)
        extra = sorted(headers - set(CSV_FIELDS))
        print(f"FINDINGS_AUDIT: CSV columns differ (missing={missing}, extra={extra})")
        return 1
    errors = validate_rows(rows, ROOT)
    if errors:
        for error in errors:
            print(f"FINDINGS_AUDIT: {error}")
        return 1
    rendered = render_report(rows, evidence_baseline(rows))
    if args.write:
        md_path.write_text(rendered, encoding="utf-8", newline="\n")
        print(f"FINDINGS_AUDIT: wrote {MD_PATH.as_posix()} ({len(rows)} findings)")
        return 0
    if not md_path.is_file() or md_path.read_text(encoding="utf-8") != rendered:
        print(f"FINDINGS_AUDIT: stale or missing {MD_PATH.as_posix()}; run python scripts/audit_findings.py --write")
        return 1
    print(f"FINDINGS_AUDIT: OK — {len(rows)} findings linted against exact source references")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
