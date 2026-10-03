#!/usr/bin/env python3
"""Fail closed on persistence policies that can silently destroy user data."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP_MODULE = ROOT / "app/src/main/java/com/nexaflow/app/di/AppModule.kt"
SCHEMA_DIR = ROOT / "core/database/schemas/com.nexaflow.core.database.AppDatabase"
MIGRATION_TEST = ROOT / "core/database/src/test/java/com/nexaflow/core/database/MigrationTest.kt"

source = APP_MODULE.read_text(encoding="utf-8")
for banned in (
    "fallbackToDestructiveMigration(",
    "fallbackToDestructiveMigrationOnDowngrade(",
):
    if banned in source:
        raise SystemExit(f"ERROR: destructive Room policy present: {banned}")

schemas = sorted(int(path.stem) for path in SCHEMA_DIR.glob("*.json"))
if not schemas:
    raise SystemExit("ERROR: no exported Room schemas found")
if schemas[-1] < 1:
    raise SystemExit("ERROR: invalid latest Room schema")

test_source = MIGRATION_TEST.read_text(encoding="utf-8")
match = re.search(r"historicalChainsReach22\(\).*?listOf\(([^)]*)\)", test_source, re.S)
if not match:
    raise SystemExit("ERROR: historical migration-chain test not found")
covered = {int(value) for value in re.findall(r"\d+", match.group(1))}
missing = set(schemas) - covered
if missing:
    raise SystemExit(f"ERROR: exported schemas missing from chain coverage: {sorted(missing)}")

print(f"Persistence safety OK: {len(schemas)} exported schemas, latest={schemas[-1]}")
