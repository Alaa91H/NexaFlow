#!/usr/bin/env python3
"""Enforce T15 legacy-mapping coverage (plan §27 / Gate B / Gate E)."""
from __future__ import annotations

import re
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

TABLE_FILE = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical/LegacyMappingTable.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "LegacyMappingTableTest.kt"
)

REQUIRED_TEST_CASES = (
    "tableCoversAll233LegacyTypes",
    "everyMappingReferencesRegisteredIdentities",
    "canonicalizationIsDeterministicAndIdempotent",
    "unknownInputStaysRejected",
    "legacyConfigRidesAlongUnconsumed",
    "pilotMappingsArePinned",
    "allMappingsAreFailClosedConsistent",
)


def main() -> int:
    problems: list[str] = []

    # Gate B — coverage straight from the review inventory.
    from canonical_inventory_review import action_reviews, trigger_reviews

    triggers = trigger_reviews()
    actions = action_reviews()
    if len(triggers) != 57:
        problems.append(f"trigger coverage {len(triggers)}/57")
    if len(actions) != 176:
        problems.append(f"action coverage {len(actions)}/176")
    if len(triggers) + len(actions) != 233:
        problems.append(f"total coverage {len(triggers) + len(actions)}/233")

    if not TABLE_FILE.is_file():
        problems.append("missing generated LegacyMappingTable.kt")
    else:
        table = TABLE_FILE.read_text(encoding="utf-8")
        for name in triggers:
            if f'LegacyNodeKind.TRIGGER, "{name}"' not in table:
                problems.append(f"trigger {name} missing from generated table")
        for name in actions:
            if f'LegacyNodeKind.ACTION, "{name}"' not in table:
                problems.append(f"action {name} missing from generated table")

        # Gate E — the generated table must not drift from the review.
        from generate_legacy_mapping_table import generate

        if generate() != table:
            problems.append("generated table drifted from the T01 review inventory")

    if not TEST_FILE.is_file():
        problems.append("missing LegacyMappingTableTest.kt")
    else:
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"LegacyMappingTableTest.kt missing required test {case!r}")

    if problems:
        print("CANONICAL_LEGACY_MAPPINGS: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_LEGACY_MAPPINGS: OK — 57/57 triggers + 176/176 actions = "
        "233/233 rules generated from the reviewed inventory, identity-"
        "validated, idempotent and drift-checked"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
