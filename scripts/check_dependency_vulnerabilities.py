#!/usr/bin/env python3
"""Scan the generated SBOM for known dependency vulnerabilities via OSV (NFA-08).

Input is the CycloneDX document produced by ``scripts/generate_sbom.py``; every
``pkg:maven/<group>/<name>@<version>`` component becomes one OSV query. Matches
are enriched with the per-advisory record so the severity comes from the
advisory itself rather than from a guess.

Policy:
  * ``--fail-on HIGH,CRITICAL`` (default) fails the run for unallowlisted
    advisories at or above the given levels.
  * Every allowlist entry in ``config/osv-allowlist.json`` must carry a reason
    and an expiry date; an expired entry is itself a failure, so a temporary
    waiver can never become permanent by accident.
  * Network or API failures fail the run (exit 2). A vulnerability gate that
    silently passes because the API was unreachable would be worse than none.

Usage:
    python3 scripts/check_dependency_vulnerabilities.py --sbom sbom.cdx.json
    python3 scripts/check_dependency_vulnerabilities.py --offline --sbom sbom.cdx.json
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ALLOWLIST = Path("config/osv-allowlist.json")
OSV_BATCH_URL = "https://api.osv.dev/v1/querybatch"
OSV_VULN_URL = "https://api.osv.dev/v1/vulns/"
BATCH_SIZE = 200
LEVELS = ("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN")
LEVEL_ORDER = {level: index for index, level in enumerate(LEVELS)}
CVSS_RANGES = ((9.0, "CRITICAL"), (7.0, "HIGH"), (4.0, "MEDIUM"), (0.1, "LOW"))


def parse_purl(purl: str) -> tuple[str, str, str] | None:
    """Return (group, name, version) for a Maven purl, else None."""
    if not purl.startswith("pkg:maven/"):
        return None
    body = purl[len("pkg:maven/") :]
    if "@" not in body:
        return None
    coordinates, version = body.rsplit("@", 1)
    if "/" not in coordinates:
        return None
    group, name = coordinates.split("/", 1)
    group = urllib.parse.unquote(group)
    name = urllib.parse.unquote(name)
    version = urllib.parse.unquote(version)
    if not group or not name or not version:
        return None
    return group, name, version


def severity_from_record(record: dict) -> str:
    """Best-effort severity for an OSV advisory record."""
    database_specific = record.get("database_specific") or {}
    for key in ("severity", "cvss_severity"):
        value = str(database_specific.get(key, "")).strip().upper()
        if value in LEVELS:
            return value
    ecosystem_specific = record.get("ecosystem_specific") or {}
    value = str(ecosystem_specific.get("severity", "")).strip().upper()
    if value in LEVELS:
        return value
    for entry in record.get("severity") or []:
        score = str(entry.get("score", "")).strip()
        try:
            numeric = float(score)
        except ValueError:
            continue
        for threshold, level in CVSS_RANGES:
            if numeric >= threshold:
                return level
    return "UNKNOWN"


def allowlist_entries(path: Path, today: dt.date) -> tuple[dict[str, dict], list[str]]:
    """Validate and index the allowlist; returns (by-advisory index, errors)."""
    errors: list[str] = []
    if not path.is_file():
        return {}, []
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return {}, [f"{path.as_posix()}: invalid JSON ({exc})"]
    entries = payload.get("allow")
    if not isinstance(entries, list):
        return {}, [f"{path.as_posix()}: 'allow' must be a list"]
    index: dict[str, dict] = {}
    for position, entry in enumerate(entries, start=1):
        prefix = f"{path.as_posix()} allow[{position}]"
        if not isinstance(entry, dict):
            errors.append(f"{prefix}: must be an object")
            continue
        advisory_id = str(entry.get("id", "")).strip()
        reason = str(entry.get("reason", "")).strip()
        expires = str(entry.get("expires", "")).strip()
        if not advisory_id:
            errors.append(f"{prefix}: 'id' is required")
            continue
        if not reason:
            errors.append(f"{prefix}: 'reason' is required for {advisory_id}")
        try:
            expiry = dt.date.fromisoformat(expires)
        except ValueError:
            errors.append(f"{prefix}: 'expires' must be an ISO date for {advisory_id}")
            continue
        if expiry < today:
            errors.append(f"{prefix}: waiver for {advisory_id} expired on {expires}")
        packaged = str(entry.get("package", "*")).strip() or "*"
        index[advisory_id] = {"package": packaged, "reason": reason, "expires": expires}
    return index, errors


def build_queries(components: list[dict]) -> list[dict]:
    queries: list[dict] = []
    for component in components:
        parsed = parse_purl(str(component.get("purl", "")))
        if parsed is None:
            continue
        group, name, version = parsed
        queries.append(
            {
                "package": {"ecosystem": "Maven", "name": f"{group}:{name}"},
                "version": version,
            }
        )
    return queries


def _request(url: str, payload: dict | None, timeout: float, attempts: int = 3) -> dict:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        request = urllib.request.Request(
            url,
            data=data,
            headers={"Content-Type": "application/json", "User-Agent": "nexaflow-supply-chain-gate"},
            method="POST" if data is not None else "GET",
        )
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:  # noqa: S310
                return json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, json.JSONDecodeError) as exc:
            last_error = exc
            if attempt < attempts:
                time.sleep(float(attempt) * 2.0)
    raise RuntimeError(f"OSV request failed for {url}: {last_error}") from last_error


def query_osv(queries: list[dict], timeout: float) -> list[list[str]]:
    """Return advisory ids per query, in the same order as the input."""
    found: list[list[str]] = []
    for start in range(0, len(queries), BATCH_SIZE):
        chunk = queries[start : start + BATCH_SIZE]
        payload = _request(OSV_BATCH_URL, {"queries": chunk}, timeout)
        results = payload.get("results")
        if not isinstance(results, list) or len(results) != len(chunk):
            raise RuntimeError("OSV querybatch returned an unexpected payload shape")
        for result in results:
            ids = [str(vuln.get("id")) for vuln in (result or {}).get("vulns") or [] if vuln.get("id")]
            found.append(ids)
    return found


def collect_findings(queries: list[dict], results: list[list[str]], fetch_record, timeout: float) -> list[dict]:
    cache: dict[str, dict] = {}
    findings: list[dict] = []
    for query, advisory_ids in zip(queries, results):
        for advisory_id in advisory_ids:
            if advisory_id not in cache:
                cache[advisory_id] = fetch_record(advisory_id, timeout)
            record = cache[advisory_id]
            findings.append(
                {
                    "id": advisory_id,
                    "package": query["package"]["name"],
                    "version": query["version"],
                    "severity": severity_from_record(record),
                    "summary": str(record.get("summary", "")).strip(),
                    "aliases": [str(alias) for alias in record.get("aliases") or []],
                }
            )
    return findings


def evaluate(
    findings: list[dict],
    failure_levels: set[str],
    allowlist: dict[str, dict],
) -> tuple[list[dict], list[dict]]:
    blocking: list[dict] = []
    waived: list[dict] = []
    for finding in findings:
        if finding["severity"] not in failure_levels:
            continue
        waiver = allowlist.get(finding["id"])
        if waiver and waiver["package"] in {"*", finding["package"]}:
            waived.append({**finding, "reason": waiver["reason"], "expires": waiver["expires"]})
            continue
        blocking.append(finding)
    return blocking, waived


def format_levels(raw: str) -> set[str]:
    levels = {part.strip().upper() for part in raw.split(",") if part.strip()}
    unknown = sorted(level for level in levels if level not in LEVELS)
    if unknown:
        raise SystemExit(f"Unknown severity level(s): {', '.join(unknown)}. Use one of {', '.join(LEVELS)}.")
    return levels


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sbom", type=Path, required=True)
    parser.add_argument("--allowlist", type=Path, default=DEFAULT_ALLOWLIST)
    parser.add_argument("--fail-on", default="HIGH,CRITICAL")
    parser.add_argument("--report", type=Path, default=None)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--offline", action="store_true", help="validate inputs only, never call OSV")
    args = parser.parse_args(argv)

    sbom_path = args.sbom if args.sbom.is_absolute() else ROOT / args.sbom
    if not sbom_path.is_file():
        print(f"VULN_SCAN: SBOM not found: {sbom_path}", file=sys.stderr)
        return 2
    payload = json.loads(sbom_path.read_text(encoding="utf-8"))
    components = payload.get("components") or []
    if not components:
        print("VULN_SCAN: SBOM contains no components", file=sys.stderr)
        return 2

    failure_levels = format_levels(args.fail_on)
    allowlist_path = args.allowlist if args.allowlist.is_absolute() else ROOT / args.allowlist
    allowlist, allowlist_errors = allowlist_entries(allowlist_path, dt.date.today())
    if allowlist_errors:
        for error in allowlist_errors:
            print(f"VULN_SCAN: {error}", file=sys.stderr)
        return 1

    queries = build_queries(components)
    if args.offline:
        print(f"VULN_SCAN: offline validation passed for {len(queries)} Maven components")
        return 0

    try:
        results = query_osv(queries, args.timeout)
        findings = collect_findings(
            queries,
            results,
            lambda advisory_id, timeout: _request(OSV_VULN_URL + advisory_id, None, timeout),
            args.timeout,
        )
    except RuntimeError as exc:
        print(f"VULN_SCAN: {exc}", file=sys.stderr)
        return 2

    blocking, waived = evaluate(findings, failure_levels, allowlist)
    report = {
        "components_scanned": len(queries),
        "advisories": len(findings),
        "blocking": blocking,
        "waived": waived,
        "fail_on": sorted(failure_levels),
    }
    if args.report is not None:
        report_path = args.report if args.report.is_absolute() else ROOT / args.report
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")

    for finding in waived:
        print(f"VULN_SCAN: waived {finding['id']} ({finding['severity']}) {finding['package']}@{finding['version']}")
    if blocking:
        for finding in blocking:
            print(
                f"VULN_SCAN: FAIL {finding['id']} [{finding['severity']}] "
                f"{finding['package']}@{finding['version']} — {finding['summary']}",
                file=sys.stderr,
            )
        print(
            f"VULN_SCAN: {len(blocking)} advisory(ies) at or above {args.fail_on} without a waiver",
            file=sys.stderr,
        )
        return 1
    print(
        f"VULN_SCAN: OK — {len(queries)} components, {len(findings)} advisories, "
        f"0 blocking at {args.fail_on}, {len(waived)} waived"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
