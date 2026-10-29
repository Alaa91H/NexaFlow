"""Tests for the CycloneDX SBOM generator and the OSV gate helpers."""
from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
import datetime as dt
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("generate_sbom", ROOT / "scripts" / "generate_sbom.py")
assert SPEC and SPEC.loader
sbom = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sbom)

VULN_SPEC = importlib.util.spec_from_file_location(
    "check_dependency_vulnerabilities", ROOT / "scripts" / "check_dependency_vulnerabilities.py"
)
assert VULN_SPEC and VULN_SPEC.loader
vuln = importlib.util.module_from_spec(VULN_SPEC)
VULN_SPEC.loader.exec_module(vuln)

METADATA = """<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">
  <components>
    <component group="androidx.core" name="core" version="1.17.0">
      <artifact name="core-1.17.0.aar">
        <sha256 value="a{0}"/>
      </artifact>
      <artifact name="core-1.17.0.module">
        <sha256 value="b{0}"/>
      </artifact>
    </component>
    <component group="com.squareup.okhttp3" name="okhttp" version="5.2.1">
      <artifact name="okhttp-5.2.1.jar">
        <sha256 value="c{0}"/>
      </artifact>
    </component>
  </components>
</verification-metadata>
""".replace("{0}", "0" * 63)


class SbomTest(unittest.TestCase):
    def write_metadata(self) -> Path:
        handle = tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False, encoding="utf-8")
        handle.write(METADATA)
        handle.close()
        return Path(handle.name)

    def test_components_are_sorted_and_deduplicated(self) -> None:
        components = sbom.parse_components(self.write_metadata())
        self.assertEqual(
            [component["purl"] for component in components],
            [
                "pkg:maven/androidx.core/core@1.17.0",
                "pkg:maven/com.squareup.okhttp3/okhttp@5.2.1",
            ],
        )
        self.assertEqual(len(components[0]["hashes"]), 2)

    def test_output_is_byte_stable(self) -> None:
        components = sbom.parse_components(self.write_metadata())
        first = sbom.render(sbom.build_sbom(components, "v1.2.3"))
        second = sbom.render(sbom.build_sbom(components, "v1.2.3"))
        self.assertEqual(first, second)
        payload = json.loads(first)
        self.assertEqual(payload["bomFormat"], "CycloneDX")
        self.assertEqual(payload["specVersion"], "1.5")
        self.assertTrue(payload["serialNumber"].startswith("urn:uuid:"))

    def test_check_mode_detects_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "sbom.json"
            self.assertEqual(
                sbom.main(["--metadata", str(self.write_metadata()), "--output", str(output), "--version", "v1"]),
                0,
            )
            self.assertEqual(
                sbom.main(["--metadata", str(self.write_metadata()), "--check", str(output), "--version", "v1"]),
                0,
            )
            output.write_text("{}\n", encoding="utf-8")
            self.assertEqual(
                sbom.main(["--metadata", str(self.write_metadata()), "--check", str(output), "--version", "v1"]),
                1,
            )


class VulnerabilityGateTest(unittest.TestCase):
    def test_purl_parsing(self) -> None:
        self.assertEqual(
            vuln.parse_purl("pkg:maven/org.jetbrains.kotlin/kotlin-stdlib@2.4.10"),
            ("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.10"),
        )
        self.assertIsNone(vuln.parse_purl("pkg:npm/left-pad@1.0.0"))

    def test_severity_prefers_advisory_labels(self) -> None:
        self.assertEqual(vuln.severity_from_record({"database_specific": {"severity": "HIGH"}}), "HIGH")
        self.assertEqual(vuln.severity_from_record({"severity": [{"score": "9.8"}]}), "CRITICAL")
        self.assertEqual(vuln.severity_from_record({"severity": [{"score": "5.3"}]}), "MEDIUM")
        self.assertEqual(vuln.severity_from_record({}), "UNKNOWN")

    def test_expired_waiver_is_an_error(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "allow.json"
            path.write_text(
                json.dumps(
                    {
                        "allow": [
                            {
                                "id": "GHSA-xxxx",
                                "package": "*",
                                "reason": "transitive, not reachable",
                                "expires": "2020-01-01",
                            }
                        ]
                    }
                ),
                encoding="utf-8",
            )
            index, errors = vuln.allowlist_entries(path, dt.date(2026, 10, 28))
            self.assertTrue(any("expired" in error for error in errors))
            self.assertEqual(index["GHSA-xxxx"]["reason"], "transitive, not reachable")

    def test_waiver_requires_reason_and_expiry(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "allow.json"
            path.write_text(json.dumps({"allow": [{"id": "GHSA-xxxx"}]}), encoding="utf-8")
            _, errors = vuln.allowlist_entries(path, dt.date(2026, 10, 28))
            self.assertEqual(len(errors), 2)

    def test_evaluate_blocks_unwaived_high(self) -> None:
        finding = {
            "id": "GHSA-1",
            "package": "com.example:lib",
            "version": "1.0.0",
            "severity": "HIGH",
            "summary": "test",
            "aliases": [],
        }
        blocking, waived = vuln.evaluate([finding], {"HIGH", "CRITICAL"}, {})
        self.assertEqual(len(blocking), 1)
        self.assertEqual(waived, [])
        blocking, waived = vuln.evaluate(
            [finding], {"HIGH"}, {"GHSA-1": {"package": "com.example:lib", "reason": "r", "expires": "2099-01-01"}}
        )
        self.assertEqual(blocking, [])
        self.assertEqual(len(waived), 1)


if __name__ == "__main__":
    unittest.main()
