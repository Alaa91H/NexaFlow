#!/usr/bin/env python3
"""Vendor-neutrality gate: NexaFlow code, resources, and docs must be
product-neutral.

The engine reasons about build *capability tiers* (see
core/rom-integration RomFamily), never about commercial products. Detection
evidence — version properties, brand lists, vendor component names, and real
device setting-key prefixes — is protocol surface and lives only in the
allowlisted protocol files below. Anywhere else, a vendor/product name in
shipped code, resources, or docs fails the gate.

Usage:
    python3 scripts/check_vendor_neutrality.py
    python3 scripts/check_vendor_neutrality.py --self-test
"""

from __future__ import annotations

import argparse
import io
import re
import sys
from pathlib import Path

# Windows consoles default to legacy code pages; the gate may print arbitrary
# file content, so force UTF-8 output.
if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent

# Files where protocol evidence legitimately lives (device fingerprints,
# vendor package names, real setting-key prefixes).
PROTOCOL_ALLOWLIST = {
    "core/rom-integration/src/main/java/com/nexaflow/core/rom/RomDetectionMatrix.kt",
    "core/rom-integration/src/main/java/com/nexaflow/core/rom/RomSettingSchema.kt",
    "core/rom-integration/src/main/java/com/nexaflow/core/rom/RomSettingCatalog.kt",
    "core/rom-integration/src/main/java/com/nexaflow/core/rom/OemCompat.kt",
    "core/rom-integration/src/main/java/com/nexaflow/core/rom/SystemController.kt",
    "core/automation-engine/src/main/java/com/nexaflow/core/engine/AppForegroundRules.kt",
    "core/rom-integration/src/test/java/com/nexaflow/core/rom/RomDetectionMatrixTest.kt",
    "core/rom-integration/src/test/java/com/nexaflow/core/rom/RomDetectorTest.kt",
    "core/rom-integration/src/test/java/com/nexaflow/core/rom/CustomSettingsBridgeTest.kt",
    "core/rom-integration/src/test/java/com/nexaflow/core/rom/RomSettingSchemaTest.kt",
    "docs/ROM_DETECTION_MATRIX.md",
    "docs/RESEARCH_2026.md",  # cited external research sources
    "docs/research/android-automation-expansion-evidence.md",  # external source citations
    "docs/ux/options-simplification-plan-ar.md",  # cites external design references
    "docs/superpowers/plans/2026-08-05-project-improvement.md",  # historical planning record
    "docs/superpowers/plans/2026-08-07-exit-options-icons-permissions.md",  # historical planning record
}

SCAN_DIRS = ["core", "feature", "domain", "data", "app", "wear", "docs", "README.md"]
SCAN_SUFFIXES = {".kt", ".java", ".xml", ".md"}

# Commercial product / vendor names. Matched as whole phrases, case-sensitive
# for the branded spellings users would recognize.
FORBIDDEN_PATTERNS = [
    r"Evolution X", r"EvolutionX", r"\bEvolver\b",
    r"\bLineageOS\b", r"\bLineage\b", r"\bcrDroid\b", r"\bcrdroid\b",
    r"\bArrowOS\b", r"\bPixelOS\b", r"\bPixelExperience\b", r"\bDerpFest\b",
    r"\bSuperiorOS\b", r"\bParanoid Android\b", r"\bGrapheneOS\b",
    r"\bProject Elixir\b",
    r"\bMIUI\b", r"\bHyperOS\b", r"\bOne UI\b", r"\bColorOS\b",
    r"\bOxygenOS\b", r"\bRealme UI\b", r"\bRealmeUI\b", r"\bOriginOS\b",
    r"\bFuntouch\b", r"\bEMUI\b", r"\bHarmonyOS\b", r"\bZenUI\b",
    r"\bNothing OS\b", r"\bNothingOS\b",
    r"\bSamsung\b", r"\bGalaxy\b", r"\bXiaomi\b", r"\bRedmi\b", r"\bPOCO\b",
    r"\bOPPO\b", r"\bOnePlus\b", r"\bHuawei\b", r"\bHonor\b", r"\bHONOR\b",
    r"\bRealme\b", r"\bVivo\b", r"\biQOO\b", r"\bASUS\b", r"\bZenfone\b",
    r"\bMotorola\b", r"\bMoto G\b", r"\bSony\b", r"\bXperia\b",
    r"\bNothing Phone\b", r"\bGoogle Pixel\b", r"\bPixel \d",
    r"\bwhyred\b", r"\bmarble\b",
    r"\bEVO_SET_SETTING\b", r"\bEVO_QS_TILES\b", r"\bEVO_STATUS_BAR\b",
    r"\bEVO_LOCKSCREEN\b", r"\bEVO_NAVIGATION\b", r"\bEVO_THEME\b",
    r"\bEVO_AMBIENT_AOD\b", r"\bEVO_NOTIFICATIONS\b", r"\bEVO_BATCH\b",
    r"SYSTEM_OPEN_GALAXY_STORE",
]
COMPILED = re.compile("|".join(FORBIDDEN_PATTERNS))


def iter_files() -> list[Path]:
    files: list[Path] = []
    for entry in SCAN_DIRS:
        p = ROOT / entry
        if p.is_file():
            files.append(p)
            continue
        for suffix in SCAN_SUFFIXES:
            files.extend(x for x in p.rglob(f"*{suffix}") if x.is_file())
    return [f for f in files if "build" not in f.relative_to(ROOT).parts]


def scan(files: list[Path]) -> list[tuple[str, int, str]]:
    hits: list[tuple[str, int, str]] = []
    for f in files:
        rel = f.relative_to(ROOT).as_posix()
        if rel in PROTOCOL_ALLOWLIST:
            continue
        try:
            text = f.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for i, line in enumerate(text.splitlines(), 1):
            # @JsonNames("EVO_*") lines are the backward-compat alias surface
            # for saved automations; they are contracts, not product mentions.
            if "JsonNames(" in line:
                continue
            m = COMPILED.search(line)
            if m:
                hits.append((rel, i, f"{m.group(0)} :: {line.strip()[:110]}"))
    return hits


def self_test() -> int:
    """Each forbidden class must be detected; allowlisted files ignored."""
    scratch = ROOT / "build" / "vendor-gate-selftest"
    scratch.mkdir(parents=True, exist_ok=True)
    samples = {
        "a.kt": 'val rom = "Evolution X" // vendor name in code\n',
        "b.xml": '<string name="x">Samsung Galaxy Store</string>\n',
        "c.md": "# Uses MIUI-only commands\n",
    }
    name_expect = {"a.kt": "Evolution X", "b.xml": "Samsung", "c.md": "MIUI"}
    ok = True
    for name, content in samples.items():
        f = scratch / name
        f.write_text(content, encoding="utf-8")
        found = scan([f])
        if not found or name_expect[name] not in found[0][2]:
            print(f"SELF-TEST FAIL: {name} not flagged")
            ok = False
        f.unlink()
    # Allowlist honored: RomDetectionMatrix.kt must never be flagged.
    matrix = ROOT / "core/rom-integration/src/main/java/com/nexaflow/core/rom/RomDetectionMatrix.kt"
    if scan([matrix]):
        print("SELF-TEST FAIL: protocol allowlist not honored")
        ok = False
    scratch.rmdir()
    print("SELF-TEST OK" if ok else "SELF-TEST FAILED")
    return 0 if ok else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return self_test()

    hits = scan(iter_files())
    if hits:
        print(f"VENDOR_NEUTRALITY_PROBLEMS: {len(hits)}")
        for rel, line, detail in hits[:40]:
            print(f"  {rel}:{line}: {detail}")
        if len(hits) > 40:
            print(f"  ... and {len(hits) - 40} more")
        print(
            "\nVendor/product names belong only in protocol-allowlisted files\n"
            "(detection matrix, schema, protocol catalogs/tests). Use neutral\n"
            "capability-tier naming (RomFamily) everywhere else."
        )
        return 1
    print("VENDOR_NEUTRALITY_PROBLEMS: 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
