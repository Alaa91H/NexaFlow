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

    def test_runtime_key_parser_is_scoped_to_node_arm(self) -> None:
        source = '''fun run(action: Action) = when (action.type) {
            ActionType.SYSTEM_OPEN_APP -> action.config["packages"]
            ActionType.APPLICATION_LAUNCH_APP -> action.config["package"]
            else -> ""
        }'''
        self.assertEqual({"package"}, inventory.node_arm_config_keys(source, "ACTION", "APPLICATION_LAUNCH_APP"))
        self.assertEqual({"packages"}, inventory.node_arm_config_keys(source, "ACTION", "SYSTEM_OPEN_APP"))

    def test_runtime_key_parser_follows_only_unique_invoked_helpers(self) -> None:
        source = '''fun run(action: Action) = when (action.type) {
            ActionType.APPLICATION_LAUNCH_APP -> launch(action)
            ActionType.SYSTEM_OPEN_APP -> unrelated(action)
            else -> ""
        }
        fun launch(action: Action) = common(action)
        fun unrelated(action: Action) = action.config["packages"]
        fun common(action: Action) = action.config["package"]
        '''
        self.assertEqual({"package"}, inventory.node_arm_config_keys(source, "ACTION", "APPLICATION_LAUNCH_APP"))
        self.assertEqual({"packages"}, inventory.node_arm_config_keys(source, "ACTION", "SYSTEM_OPEN_APP"))

    def test_data_field_effectiveness_is_operation_scoped(self) -> None:
        self.assertEqual("UNUSED_BY_OPERATION", inventory.data_operation_scope("DATA_HASH", "argument", "SHA-256"))
        self.assertEqual("EFFECTIVE_FOR_OPERATION", inventory.data_operation_scope("DATA_RANDOM", "min", "INTEGER"))
        self.assertEqual("UNUSED_BY_OPERATION", inventory.data_operation_scope("DATA_RANDOM", "min", "UUID"))

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
                all("DataActionsHandler.kt" in row["runtime_handler_candidates"]
                    for row in action_rows if row["legacy_type"].startswith("DATA_"))
            )
            self.assertTrue(
                all(row["schema_status"] in {
                    "SCHEMA_ARM_PRESENT", "SHARED_TOGGLE_SCHEMA", "EMPTY_SCHEMA_FALLBACK", "NO_MATCHED_SCHEMA_ARM"
                }
                    for row in trigger_rows + action_rows)
            )
            self.assertTrue(all(row["parity_status"] for row in field_rows))
            self.assertEqual(763, len(field_rows))
            self.assertFalse(any(row["schema_producer"] == "UNMAPPED" for row in field_rows))
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
            self.assertTrue(all("consumer_review" in row for row in field_rows))
            time_zone_fields = [row for row in field_rows if row["kind"] == "TRIGGER" and row["legacy_type"] == "TIME" and row["field"] in {"zonePolicy", "zoneId"}]
            self.assertEqual(2, len(time_zone_fields))
            self.assertTrue(all(row["parity_status"] == "DECLARED_AND_RUNTIME_READ" for row in time_zone_fields))
            connectivity_state = next(row for row in field_rows if row["kind"] == "TRIGGER" and row["legacy_type"] == "CONNECTIVITY" and row["field"] == "state")
            self.assertIn("TriggerStateEvaluator.kt", connectivity_state["runtime_consumer"])
            wifi_fields = [
                row for row in field_rows
                if row["kind"] == "TRIGGER"
                and row["legacy_type"] == "WIFI_CONNECTED"
                and row["field"] in {"validated", "captivePortal", "metered", "ssid", "bssid"}
            ]
            self.assertEqual(5, len(wifi_fields))
            self.assertTrue(all(row["parity_status"] == "DECLARED_AND_RUNTIME_READ" for row in wifi_fields))
            self.assertTrue(all("ConnectivityMonitor.kt" in row["runtime_consumer"] for row in wifi_fields))
            random_min = next(row for row in field_rows if row["kind"] == "ACTION" and row["legacy_type"] == "DATA_RANDOM" and row["field"] == "min" and row["sub_operation"] == "INTEGER")
            self.assertIn("DataTransforms.kt", random_min["runtime_consumer"])
            hash_argument = next(row for row in field_rows if row["kind"] == "ACTION" and row["legacy_type"] == "DATA_HASH" and row["field"] == "argument" and row["sub_operation"] == "SHA-256")
            self.assertEqual("DECLARED_UNUSED_BY_OPERATION", hash_argument["parity_status"])
            self.assertEqual("UNUSED_BY_OPERATION", hash_argument["operation_scope"])
            launch_packages = next(row for row in field_rows if row["kind"] == "ACTION" and row["legacy_type"] == "APPLICATION_LAUNCH_APP" and row["field"] == "packages")
            self.assertEqual("DECLARED_UNUSED_BY_HANDLER", launch_packages["parity_status"])
            reboot_mode = next(row for row in field_rows if row["kind"] == "ACTION" and row["legacy_type"] == "SYSTEM_REBOOT" and row["field"] == "mode")
            self.assertEqual("DECLARED_UNUSED_BY_HANDLER", reboot_mode["parity_status"])
            play_store_package = next(row for row in field_rows if row["kind"] == "ACTION" and row["legacy_type"] == "SYSTEM_OPEN_PLAY_STORE_APP" and row["field"] == "package")
            self.assertEqual("DECLARED_UNUSED_BY_HANDLER", play_store_package["parity_status"])
            encoding_end = next(row for row in field_rows if row["kind"] == "ACTION" and row["legacy_type"] == "DATA_ENCODING" and row["field"] == "end")
            self.assertEqual("DECLARED_UNUSED_BY_OPERATION", encoding_end["parity_status"])
            plugin_trigger = next(row for row in field_rows if row["kind"] == "TRIGGER" and row["legacy_type"] == "PLUGIN_EVENT" and row["field"] == "pluginEventId")
            self.assertEqual("DECLARED_AND_RUNTIME_READ", plugin_trigger["parity_status"])
            time_excluded_dates = next(row for row in field_rows if row["kind"] == "TRIGGER" and row["legacy_type"] == "TIME" and row["field"] == "excludedDates")
            self.assertEqual("DECLARED_AND_RUNTIME_READ", time_excluded_dates["parity_status"])
            legacy_plugin_alias = next(row for row in field_rows if row["kind"] == "TRIGGER" and row["legacy_type"] == "PLUGIN_EVENT" and row["field"] == "plugin_id")
            self.assertEqual("DECLARED_CANONICAL_COMPATIBILITY_ALIAS", legacy_plugin_alias["parity_status"])
            battery_below = next(row for row in field_rows if row["kind"] == "ACTION" and row["legacy_type"] == "BATTERY_ALERTS" and row["field"] == "below")
            self.assertEqual("DECLARED_UNUSED_BY_HANDLER", battery_below["parity_status"])
            plugin_blurb = next(row for row in field_rows if row["kind"] == "ACTION" and row["legacy_type"] == "PLUGIN_FIRE" and row["field"] == "blurb")
            self.assertEqual("DECLARED_UI_CONSUMED", plugin_blurb["parity_status"])
            plugin_risk = next(row for row in field_rows if row["kind"] == "ACTION" and row["legacy_type"] == "PLUGIN_FIRE" and row["field"] == "pluginHighRiskApproval")
            self.assertEqual("DECLARED_AND_RUNTIME_READ", plugin_risk["parity_status"])
            self.assertEqual(57 * 15, len(lifecycle_rows))
            self.assertEqual((57 + 213) * 15, len(node_lifecycle_rows))
            self.assertFalse(any(row["evidence_status"] == "SHARED_OWNER_CANDIDATE_REQUIRES_NODE_REVIEW" for row in node_lifecycle_rows))
            self.assertTrue(any(row["stage"] == "dispatch" and row["kind"] == "ACTION" and row["evidence_status"] == "STATIC_NODE_REFERENCE" for row in node_lifecycle_rows))
            connectivity_picker = next(row for row in node_lifecycle_rows if row["kind"] == "TRIGGER" and row["legacy_type"] == "CONNECTIVITY" and row["stage"] == "picker")
            self.assertEqual("NOT_MAPPED", connectivity_picker["evidence_status"])
            self.assertEqual("", connectivity_picker["candidate_source"])
            self.assertEqual(10, len(graph_rows))
            self.assertTrue(any(row["finding"] == "SHARED_RUNTIME_OWNER_DECLARATION" for row in architecture_rows))
            self.assertEqual(4, sum(row["finding"] == "DECLARED_FIELD_NOT_READ_BY_ACTION_HANDLER" for row in architecture_rows))
            self.assertEqual(50, len(hotspot_rows))
            self.assertEqual(list(map(str, range(1, 51))), [row["rank"] for row in hotspot_rows])
            self.assertEqual(7, len(plugin_rows))


if __name__ == "__main__":
    unittest.main()
