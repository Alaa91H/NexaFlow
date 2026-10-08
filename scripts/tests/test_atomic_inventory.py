"""Tests for the deterministic atomic trigger/action inventory generator."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "audit_atomic_inventory", ROOT / "scripts" / "audit_atomic_inventory.py"
)
assert SPEC and SPEC.loader
inventory = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(inventory)


class AtomicInventoryTest(unittest.TestCase):
    def test_enum_parser_preserves_declared_order_and_ignores_comments(self) -> None:
        source = "enum class Example {\n FIRST, // stable\n SECOND\n}"
        self.assertEqual(["FIRST", "SECOND"], inventory.enum_values(source, "Example"))

    def test_source_parsers_surface_new_enum_operation_and_field_values(self) -> None:
        enum_source = "enum class Example {\n FIRST,\n SECOND,\n THIRD\n}"
        self.assertEqual(["FIRST", "SECOND", "THIRD"], inventory.enum_values(enum_source, "Example"))

        operations = 'ActionType.DATA_SAMPLE to listOf("ONE", "TWO")'
        self.assertEqual({"DATA_SAMPLE": ["ONE", "TWO"]}, inventory.operation_map_from_source(operations))
        self.assertEqual(
            ["ONE", "TWO", "THREE"],
            inventory.operation_map_from_source(operations.replace('"TWO"', '"TWO", "THREE"'))["DATA_SAMPLE"],
        )

        schema = '''when (type) {
            TriggerType.SAMPLE -> schema(
                stringField("oldField"),
                integerField("newField")
            )
        }'''
        self.assertEqual(
            {"SAMPLE": ["oldField", "newField"]},
            inventory.schema_fields_from_source(schema, "TriggerType", ["SAMPLE"]),
        )

    def test_generated_matrices_cover_current_enums_and_are_deterministic(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            first = Path(temp) / "first"
            second = Path(temp) / "second"
            inventory.generate(ROOT, first)
            inventory.generate(ROOT, second)

            expected = {
                "triggers-matrix.csv",
                "actions-operations-matrix.csv",
                "field-runtime-parity.csv",
                "trigger-source-lifecycle.csv",
                "node-lifecycle.csv",
                "dependency-graph.csv",
                "architecture-findings.csv",
                "hotspots.csv",
                "special-plugin-flows.csv",
                "atomic-inventory.md",
            }
            self.assertEqual(expected, {path.name for path in first.iterdir()})
            for name in expected:
                self.assertEqual((first / name).read_bytes(), (second / name).read_bytes())

            trigger_rows = inventory.read_csv(first / "triggers-matrix.csv")
            action_rows = inventory.read_csv(first / "actions-operations-matrix.csv")
            field_rows = inventory.read_csv(first / "field-runtime-parity.csv")
            lifecycle_rows = inventory.read_csv(first / "trigger-source-lifecycle.csv")
            node_lifecycle_rows = inventory.read_csv(first / "node-lifecycle.csv")
            hotspot_rows = inventory.read_csv(first / "hotspots.csv")
            plugin_rows = inventory.read_csv(first / "special-plugin-flows.csv")
            graph_rows = inventory.read_csv(first / "dependency-graph.csv")
            architecture_rows = inventory.read_csv(first / "architecture-findings.csv")
            self.assertEqual(57, len(trigger_rows))
            self.assertEqual(180, len({row["legacy_type"] for row in action_rows}))
            self.assertEqual(213, len(action_rows))
            self.assertEqual(41, sum(row["sub_operation"] != "DEFAULT_ACTION" for row in action_rows))
            self.assertTrue(any(row["legacy_type"] == "PLUGIN_FIRE" for row in action_rows))
            self.assertTrue(
                all(row["schema_status"] in {
                    "SCHEMA_ARM_PRESENT", "SHARED_TOGGLE_SCHEMA", "EMPTY_SCHEMA_FALLBACK", "NO_MATCHED_SCHEMA_ARM"
                }
                    for row in trigger_rows + action_rows)
            )
            self.assertTrue(all(row["parity_status"] for row in field_rows))
            self.assertTrue(
                all(
                    row["runtime_consumer"] not in {"", "NO_STATIC_MATCH"}
                    for row in field_rows
                    if row["parity_status"] == "DECLARED_AND_RUNTIME_READ"
                )
            )
            self.assertTrue(all("schema_producer" in row for row in field_rows))
            self.assertTrue(all("runtime_consumer" in row for row in field_rows))
            self.assertTrue(all("runtime_owner_candidates" in row for row in field_rows))
            self.assertTrue(all("test_candidates" in row for row in field_rows))
            self.assertTrue(all("permission_api_backend_candidates" in row for row in field_rows))
            self.assertTrue(all("save_reload_round_trip" in row for row in field_rows))
            self.assertEqual(57 * 15, len(lifecycle_rows))
            self.assertEqual((57 + 213) * 15, len(node_lifecycle_rows))
            self.assertEqual(10, len(graph_rows))
            self.assertTrue(any(row["finding"] == "SHARED_RUNTIME_OWNER_DECLARATION" for row in architecture_rows))
            self.assertEqual(50, len(hotspot_rows))
            self.assertEqual(list(map(str, range(1, 51))), [row["rank"] for row in hotspot_rows])
            self.assertEqual(7, len(plugin_rows))


if __name__ == "__main__":
    unittest.main()
