"""Tests for the evidence-backed atomic finding register linter."""
from __future__ import annotations

import importlib.util
import hashlib
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("audit_findings", ROOT / "scripts" / "audit_findings.py")
assert SPEC and SPEC.loader
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class AuditFindingsTest(unittest.TestCase):
    def row(self, **updates: str) -> dict[str, str]:
        row = {field: "documented evidence" for field in audit.CSV_FIELDS}
        row.update({
            "finding_id": "T02-001",
            "classification": "INVESTIGATE",
            "severity": "P1",
            "area": "persistence",
            "title": "Windows DataStore replacement behavior",
            "evidence_refs": f"{audit.current_head(ROOT)}:docs/OPEN_QUESTIONS.md:1",
            "observation": "The exact current runtime result is not yet reproduced.",
            "impact": "Windows-only persistence validation may fail.",
            "prerequisite": "Run the targeted task on the current Windows checkout.",
            "reproducer": ".\\gradlew.bat :core:datastore:testDebugUnitTest --console=plain",
            "expected": "The current targeted suite completes successfully.",
            "actual": "Historical failures are documented; current run is pending.",
            "regression_test_target": "core/datastore/src/test/java/com/nexaflow/core/datastore/DataStoreCorruptionHandlerTest.kt",
            "proposed_issue": "#126",
            "risk_class": "CROSS_PLATFORM_RELIABILITY",
            "test_results": "The Windows run remains pending; the linter checks recorded result consistency only.",
            "external_blocker": "",
            "historical_ref": "docs/evidence/baseline/p0-01-current-main-2026-10-05.md",
            "evidence_log_path": "",
            "evidence_log_sha256": "",
            "test_results": "No new Gradle run was performed by this linter test.",
        })
        row.update(updates)
        return row

    def test_valid_investigation_record_and_current_source_line(self) -> None:
        errors = audit.validate_rows([self.row()], ROOT)
        self.assertEqual([], errors)

    def test_invalid_status_and_missing_reproducer_are_rejected(self) -> None:
        row = self.row(classification="FOUND_BUG", reproducer="")
        errors = audit.validate_rows([row], ROOT)
        self.assertTrue(any("classification" in error for error in errors))
        self.assertTrue(any("reproducer" in error for error in errors))

    def test_nonexistent_source_line_is_rejected(self) -> None:
        row = self.row(evidence_refs=f"{audit.current_head(ROOT)}:docs/OPEN_QUESTIONS.md:999999")
        self.assertTrue(any("outside file" in error for error in audit.validate_rows([row], ROOT)))

    def test_confirmed_finding_requires_reproducer_and_regression_test(self) -> None:
        row = self.row(classification="CONFIRMED", reproducer="", regression_test_target="")
        errors = audit.validate_rows([row], ROOT)
        self.assertTrue(any("CONFIRMED" in error and "reproducer" in error for error in errors))
        self.assertTrue(any("CONFIRMED" in error and "regression_test_target" in error for error in errors))

    def test_duplicate_ids_are_rejected(self) -> None:
        self.assertTrue(any("duplicate finding_id" in error for error in audit.validate_rows(
            [self.row(), self.row()], ROOT
        )))

    def test_confirmed_requires_existing_hash_pinned_evidence_log(self) -> None:
        with tempfile.TemporaryDirectory(dir=ROOT) as temp_dir:
            temporary_root = Path(temp_dir)
            evidence = temporary_root / "test-report.xml"
            evidence.write_text("<testsuite failures='1'/>", encoding="utf-8")
            content_hash = hashlib.sha256(evidence.read_bytes()).hexdigest()
            row = self.row(
                classification="CONFIRMED",
                evidence_log_path="test-report.xml",
                evidence_log_sha256=content_hash,
            )
            self.assertEqual([], audit.validate_rows([row], temporary_root))
            row["evidence_log_sha256"] = "0" * 64
            self.assertTrue(any("SHA-256 mismatch" in error for error in audit.validate_rows([row], temporary_root)))

    def test_confirmed_rejects_evidence_log_outside_repository(self) -> None:
        row = self.row(classification="CONFIRMED", evidence_log_path="../outside.log", evidence_log_sha256="0" * 64)
        self.assertTrue(any("inside the repository" in error for error in audit.validate_rows([row], ROOT)))


if __name__ == "__main__":
    unittest.main()
