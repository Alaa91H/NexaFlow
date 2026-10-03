#!/usr/bin/env python3
"""Validate the immutable atomic-overhaul baseline manifest."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docs" / "audit" / "atomic-overhaul-baseline.json"
PLAN = ROOT / "docs" / "superpowers" / "plans" / "2026-10-01-atomic-stability-ai-provider-overhaul.md"
SHA = re.compile(r"^[0-9a-f]{40}$")

data = json.loads(MANIFEST.read_text(encoding="utf-8"))
assert data["schemaVersion"] == 1
assert SHA.fullmatch(data["baselineCommit"])
assert SHA.fullmatch(data["baselineTree"])
assert data["repositoryFileCount"] > 0
assert data["kotlinFileCount"] > data["kotlinTestFileCount"] > 0

paths = set()
for item in data["criticalFiles"]:
    assert item["path"] not in paths
    paths.add(item["path"])
    assert SHA.fullmatch(item["blob"])
    assert item["bytes"] > 0

plan = PLAN.read_text(encoding="utf-8")
for task in range(30):
    token = f"T{task:02d}"
    assert token in plan, f"missing {token} from atomic execution plan"

print(f"Atomic baseline OK: {data['baselineTag']} {data['baselineCommit'][:12]}")
