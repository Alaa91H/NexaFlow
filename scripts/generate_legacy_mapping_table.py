#!/usr/bin/env python3
"""Generate the T15 typed Kotlin mapping table from the T01 review inventory.

Single source of truth: scripts/canonical_inventory_review.py (237/237
REVIEWED). Trigger reviews map to ObserveNode skeletons via
core.predicate.<name>; action reviews map to InvokeNode skeletons via
core.operation.<name>. The generated rules intentionally consume no config
keys: every legacy config entry rides along untouched (lossless) until the
per-family phases (T17+) upgrade values with schema type information.

Run with --check in CI to fail on drift between the review and the
generated table. Regenerate after an explicitly reviewed T01 change.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from canonical_inventory_review import action_reviews, trigger_reviews  # noqa: E402

TARGET_FILE = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical/LegacyMappingTable.kt"

HEADER = """package com.nexaflow.domain.canonical

/**
 * T15 — generated legacy mapping table: {TRIGGERS} triggers + {ACTIONS}
 * actions = {TOTAL} rules.
 *
 * Source of truth: scripts/canonical_inventory_review.py (T01 semantic
 * review, 237/237 REVIEWED). Regenerate with:
 *
 *     python3 scripts/generate_legacy_mapping_table.py
 *
 * Do not edit by hand. Mapping semantics change through the reviewed T01
 * inventory only, then regenerate; CI fails on drift between the two.
 *
 * Every rule consumes no config keys: legacy payloads are carried through
 * verbatim in [LegacyAdapterOutcome.Canonicalized.preservedConfig] until a
 * family phase upgrades values with schema type information. This is what
 * keeps the migration lossless and idempotent.
 */
@Suppress("LargeClass") // one reviewed 237-entry table; splitting it would hide the parity contract
object LegacyMappingTable {

    private data class Entry(
        val observe: Boolean,
        val target: TargetId,
        val operation: OperationId?,
        val predicate: PredicateId?,
    )

    private val entries: Map<Pair<LegacyNodeKind, String>, Entry> = mapOf(
"""

FOOTER = """    )

    /** All {TOTAL} rules in deterministic (kind, legacyType) order. */
    fun all(): List<LegacyMappingRule> = entries.map { (key, entry) ->
        GeneratedLegacyMappingRule(
            legacyType = key.second,
            kind = key.first,
            entry = entry,
        )
    }.sortedWith(compareBy({ it.kind }, { it.legacyType }))

    fun ruleCount(): Int = entries.size

    fun triggerCount(): Int = entries.keys.count { it.first == LegacyNodeKind.TRIGGER }

    fun actionCount(): Int = entries.keys.count { it.first == LegacyNodeKind.ACTION }

    /**
     * The generated rule: builds the node skeleton (observe or invoke) with
     * the reviewed stable identities and no fabricated configuration.
     */
    private class GeneratedLegacyMappingRule(
        override val legacyType: String,
        override val kind: LegacyNodeKind,
        private val entry: Entry,
    ) : LegacyMappingRule {
        override val consumedKeys: Set<String> = emptySet()

        override fun canonicalize(input: LegacyNodeInput): CanonicalNode {
            val nodeId = CanonicalNodeId("legacy." + legacyType.lowercase())
            return if (entry.observe) {
                ObserveNode(
                    id = nodeId,
                    target = entry.target,
                    predicate = entry.predicate
                        ?: error("trigger mapping $legacyType has no predicate"),
                )
            } else {
                InvokeNode(
                    id = nodeId,
                    target = entry.target,
                    operation = entry.operation
                        ?: error("action mapping $legacyType has no operation"),
                )
            }
        }
    }
}
"""


def snake(name: str) -> str:
    return name.lower()


def generate() -> str:
    triggers = trigger_reviews()
    actions = action_reviews()
    if len(triggers) != 57:
        raise SystemExit(f"expected 57 trigger reviews, found {len(triggers)}")
    if len(actions) != 180:
        raise SystemExit(f"expected 180 action reviews, found {len(actions)}")

    lines: list[str] = []
    for name in sorted(triggers):
        review = triggers[name]
        operation = review.canonicalOperation
        if not operation.startswith("MATCH_"):
            raise SystemExit(f"trigger {name} has non-predicate operation {operation}")
        lines.append(
            f'        Pair(LegacyNodeKind.TRIGGER, "{name}") to Entry(\n'
            f'            observe = true,\n'
            f'            target = TargetId("{review.canonicalTarget}"),\n'
            f"            operation = null,\n"
            f'            predicate = PredicateId("core.predicate.{snake(operation)}"),\n'
            f"        ),"
        )
    for name in sorted(actions):
        review = actions[name]
        operation = review.canonicalOperation
        if operation.startswith("MATCH_"):
            raise SystemExit(f"action {name} has predicate operation {operation}")
        lines.append(
            f'        Pair(LegacyNodeKind.ACTION, "{name}") to Entry(\n'
            f"            observe = false,\n"
            f'            target = TargetId("{review.canonicalTarget}"),\n'
            f'            operation = OperationId("core.operation.{snake(operation)}"),\n'
            f"            predicate = null,\n"
            f"        ),"
        )

    body = "\n".join(lines)
    header = (
        HEADER.replace("{TRIGGERS}", "57")
        .replace("{ACTIONS}", "180")
        .replace("{TOTAL}", "237")
    )
    footer = FOOTER.replace("{TOTAL}", "237")
    return header + body + "\n" + footer


def main() -> int:
    generated = generate()
    if "--check" in sys.argv:
        current = TARGET_FILE.read_text(encoding="utf-8") if TARGET_FILE.is_file() else ""
        if current != generated:
            print(
                "LEGACY_MAPPING_TABLE: DRIFT — regenerate with "
                "python3 scripts/generate_legacy_mapping_table.py"
            )
            return 1
        print("LEGACY_MAPPING_TABLE: OK — generated table matches the T01 review")
        return 0

    TARGET_FILE.write_text(generated, encoding="utf-8", newline="\n")
    print(f"LEGACY_MAPPING_TABLE: wrote {TARGET_FILE.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
