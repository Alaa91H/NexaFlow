import importlib.util
import json
import os
import pathlib
import stat
import sys
import tempfile
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "nexaflow_agent.py"
SPEC = importlib.util.spec_from_file_location("nexaflow_agent", MODULE_PATH)
assert SPEC and SPEC.loader
bridge = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = bridge
SPEC.loader.exec_module(bridge)


class PairingPayloadTest(unittest.TestCase):
    def test_parses_bounded_payload(self):
        payload = bridge.PairingPayload.parse(
            json.dumps(
                {
                    "challengeId": "challenge-1",
                    "challengeSecret": "secret-1",
                    "port": 8766,
                    "pairingPath": "/api/v1/auth/pair/complete",
                    "sessionPath": "/api/v1/auth/session",
                    "mcpPath": "/mcp",
                }
            )
        )
        self.assertEqual("challenge-1", payload.challenge_id)
        self.assertEqual(8766, payload.port)
        self.assertNotIn("secret-1", repr(payload))

    def test_rejects_absolute_endpoint_in_payload(self):
        with self.assertRaises(bridge.BridgeError):
            bridge.PairingPayload.parse(
                json.dumps(
                    {
                        "challengeId": "challenge-1",
                        "challengeSecret": "secret-1",
                        "mcpPath": "https://evil.example/mcp",
                    }
                )
            )


class EndpointPolicyTest(unittest.TestCase):
    def test_private_http_is_allowed(self):
        self.assertEqual(
            "http://192.168.1.5:8766",
            bridge.validate_base_url("http://192.168.1.5:8766"),
        )

    def test_public_http_is_rejected(self):
        with self.assertRaises(bridge.BridgeError):
            bridge.validate_base_url("http://8.8.8.8:8766")

    def test_https_public_endpoint_is_allowed(self):
        self.assertEqual(
            "https://example.com:8766",
            bridge.validate_base_url("https://example.com:8766"),
        )


class AdbParserTest(unittest.TestCase):
    def test_devices_parser_keeps_state_and_details(self):
        devices = bridge.parse_adb_devices(
            "List of devices attached\n"
            "abc123 device product:marble model:POCO_F5\n"
            "offline123 offline\n"
        )
        self.assertEqual(["abc123", "offline123"], [d.serial for d in devices])
        self.assertEqual("device", devices[0].state)
        self.assertIn("product:marble", devices[0].detail)


class McpHeaderTest(unittest.TestCase):
    def test_legacy_message_does_not_invent_modern_headers(self):
        headers = bridge.build_mcp_headers(
            {"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}},
            "access",
        )
        self.assertNotIn("MCP-Protocol-Version", headers)
        self.assertEqual("Bearer access", headers["Authorization"])

    def test_modern_tool_call_sets_routing_headers(self):
        headers = bridge.build_mcp_headers(
            {
                "jsonrpc": "2.0",
                "id": "1",
                "method": "tools/call",
                "params": {
                    "name": "nexaflow.list_tasks",
                    "_meta": {
                        "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                        "io.modelcontextprotocol/clientCapabilities": {},
                    },
                },
            },
            "access",
        )
        self.assertEqual("2026-07-28", headers["MCP-Protocol-Version"])
        self.assertEqual("tools/call", headers["Mcp-Method"])
        self.assertEqual("nexaflow.list_tasks", headers["Mcp-Name"])


class ProfileStoreTest(unittest.TestCase):
    def test_round_trip_never_places_secret_in_repr(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "profiles.json"
            store = bridge.ProfileStore(path)
            profile = bridge.Profile(
                name="default",
                agent_id="agent-1",
                transport="adb",
                refresh_token="refresh-secret",
                serial="device-1",
            )
            store.put(profile)
            loaded = store.get("default")
            self.assertEqual("refresh-secret", loaded.refresh_token)
            self.assertNotIn("refresh-secret", repr(loaded))
            if os.name != "nt":
                mode = stat.S_IMODE(path.stat().st_mode)
                self.assertEqual(stat.S_IRUSR | stat.S_IWUSR, mode)


if __name__ == "__main__":
    unittest.main()
