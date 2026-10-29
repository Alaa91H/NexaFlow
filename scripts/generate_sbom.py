#!/usr/bin/env python3
"""Generate a CycloneDX SBOM for the Gradle dependency universe (NFA-08).

The source of truth is ``gradle/verification-metadata.xml``: every artifact the
build is allowed to resolve is pinned there with its SHA-256, so the SBOM
describes exactly the components the verified build can consume instead of a
best-effort resolution walk that needs a full Gradle run.

Output is deterministic (components sorted, stable serial number) so the SBOM
can be committed, diffed, and attached to a release without spurious churn.

Usage:
    python3 scripts/generate_sbom.py --output sbom.cdx.json
    python3 scripts/generate_sbom.py --output sbom.cdx.json --version v3.92.0
    python3 scripts/generate_sbom.py --check sbom.cdx.json
"""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_METADATA = Path("gradle/verification-metadata.xml")
DEFAULT_OUTPUT = Path("build/reports/sbom/nexaflow-sbom.cdx.json")
SPEC_VERSION = "1.5"
SERIAL_NAMESPACE = uuid.UUID("6f54d1a2-4b1c-4a8e-9f0d-2b6f0a1f0c11")


def _localname(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _git_version(root: Path) -> str:
    try:
        return subprocess.run(
            ["git", "describe", "--tags", "--always", "--dirty"],
            cwd=root,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        ).stdout.strip() or "0.0.0-dev"
    except (OSError, subprocess.CalledProcessError):
        return "0.0.0-dev"


def parse_components(metadata_path: Path) -> list[dict[str, object]]:
    """Return deduplicated components from Gradle verification metadata."""
    tree = ET.parse(metadata_path)
    components: dict[tuple[str, str, str], set[str]] = {}
    for element in tree.getroot().iter():
        if _localname(element.tag) != "component":
            continue
        group = (element.get("group") or "").strip()
        name = (element.get("name") or "").strip()
        version = (element.get("version") or "").strip()
        if not group or not name or not version:
            continue
        hashes = components.setdefault((group, name, version), set())
        for artifact in element:
            if _localname(artifact.tag) != "artifact":
                continue
            for child in artifact:
                if _localname(child.tag) != "sha256":
                    continue
                value = (child.get("value") or "").strip().lower()
                if len(value) == 64 and all(c in "0123456789abcdef" for c in value):
                    hashes.add(value)

    result: list[dict[str, object]] = []
    for (group, name, version) in sorted(components):
        purl = f"pkg:maven/{group}/{name}@{version}"
        entry: dict[str, object] = {
            "type": "library",
            "bom-ref": purl,
            "group": group,
            "name": name,
            "version": version,
            "purl": purl,
        }
        sha256 = sorted(components[(group, name, version)])
        if sha256:
            entry["hashes"] = [{"alg": "SHA-256", "content": value} for value in sha256]
        result.append(entry)
    return result


def parse_inventory(paths: list[Path]) -> list[dict[str, object]]:
    """Return components from one or more resolved-runtime inventories.

    Inventories come from ``:app:dependencyInventory`` (Gradle resolution) and
    describe only what ships, which is the correct scope for a CVE gate.
    """
    merged: dict[tuple[str, str, str], str] = {}
    for path in paths:
        payload = json.loads(path.read_text(encoding="utf-8"))
        for entry in payload.get("components") or []:
            group = str(entry.get("group", "")).strip()
            name = str(entry.get("name", "")).strip()
            version = str(entry.get("version", "")).strip()
            if not group or not name or not version:
                continue
            merged[(group, name, version)] = str(payload.get("variant", "runtime"))

    result: list[dict[str, object]] = []
    for (group, name, version) in sorted(merged):
        purl = f"pkg:maven/{group}/{name}@{version}"
        result.append(
            {
                "type": "library",
                "bom-ref": purl,
                "group": group,
                "name": name,
                "version": version,
                "purl": purl,
                "scope": "required",
            }
        )
    return result


def build_sbom(components: list[dict[str, object]], version: str) -> dict[str, object]:
    canonical = json.dumps(components, sort_keys=True, separators=(",", ":"))
    digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    serial = uuid.uuid5(SERIAL_NAMESPACE, f"nexaflow-sbom:{digest}")
    return {
        "bomFormat": "CycloneDX",
        "specVersion": SPEC_VERSION,
        "serialNumber": f"urn:uuid:{serial}",
        "version": 1,
        "metadata": {
            # Deliberately no timestamp: the document must stay byte-identical
            # between runs so --check can detect real dependency drift.
            "tools": {
                "components": [
                    {
                        "type": "application",
                        "name": "nexaflow-generate-sbom",
                        "version": "1",
                    }
                ]
            },
            "component": {
                "type": "application",
                "bom-ref": "pkg:generic/nexaflow@"
                + version.replace("+", "%2B").replace(" ", "%20"),
                "name": "NexaFlow",
                "version": version,
            },
        },
        "components": components,
    }


def render(sbom: dict[str, object]) -> str:
    return json.dumps(sbom, indent=2, ensure_ascii=False) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, default=DEFAULT_METADATA)
    parser.add_argument(
        "--inventory",
        type=Path,
        action="append",
        default=None,
        help="resolved-runtime inventory JSON from :app:dependencyInventory (repeatable); "
        "when supplied, only shipped components enter the SBOM",
    )
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--version", default=None, help="component version (default: git describe)")
    parser.add_argument(
        "--check",
        type=Path,
        default=None,
        help="verify an existing SBOM matches the current verification metadata",
    )
    args = parser.parse_args(argv)

    version = args.version or _git_version(ROOT)
    if args.inventory:
        inventories = [path if path.is_absolute() else ROOT / path for path in args.inventory]
        missing = [path for path in inventories if not path.is_file()]
        if missing:
            for path in missing:
                print(f"SBOM: inventory not found: {path}", file=sys.stderr)
            return 2
        sbom = build_sbom(parse_inventory(inventories), version)
    else:
        metadata_path = args.metadata if args.metadata.is_absolute() else ROOT / args.metadata
        if not metadata_path.is_file():
            print(f"SBOM: verification metadata not found: {metadata_path}", file=sys.stderr)
            return 2
        sbom = build_sbom(parse_components(metadata_path), version)
    rendered = render(sbom)

    if args.check is not None:
        check_path = args.check if args.check.is_absolute() else ROOT / args.check
        if not check_path.is_file():
            print(f"SBOM: {check_path} is missing; regenerate it.", file=sys.stderr)
            return 1
        if check_path.read_text(encoding="utf-8") != rendered:
            print(
                f"SBOM: {check_path} is stale; run python3 scripts/generate_sbom.py "
                f"--output {args.check.as_posix()}",
                file=sys.stderr,
            )
            return 1
        print(f"SBOM: OK — {len(sbom['components'])} components match {check_path.as_posix()}")
        return 0

    output = args.output if args.output.is_absolute() else ROOT / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(rendered, encoding="utf-8", newline="\n")
    print(f"SBOM: wrote {output.as_posix()} ({len(sbom['components'])} components, version {version})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
