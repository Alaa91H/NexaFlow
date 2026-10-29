"""Tests for the changed-line coverage gate (NFA-04)."""
from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "check_diff_coverage", ROOT / "scripts" / "check_diff_coverage.py"
)
assert SPEC and SPEC.loader
diff_cov = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = diff_cov
SPEC.loader.exec_module(diff_cov)

DIFF = """diff --git a/core/security/src/main/java/com/nexaflow/core/security/Foo.kt b/core/security/src/main/java/com/nexaflow/core/security/Foo.kt
--- a/core/security/src/main/java/com/nexaflow/core/security/Foo.kt
+++ b/core/security/src/main/java/com/nexaflow/core/security/Foo.kt
@@ -1,2 +1,4 @@
 package com.nexaflow.core.security
+fun covered() = 1
+fun untested() = 2
+fun skippedByCompiler() = 3
"""

REPORT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<report name="core:security">
  <package name="com/nexaflow/core/security">
    <sourcefile name="Foo.kt">
      <line nr="2" mi="0" ci="4"/>
      <line nr="3" mi="6" ci="0"/>
    </sourcefile>
  </package>
  <counter type="LINE" missed="1" covered="1"/>
</report>
"""


class ParseDiffTest(unittest.TestCase):
    def test_extracts_added_line_numbers_per_file(self):
        parsed = diff_cov.parse_diff(DIFF)
        self.assertEqual(len(parsed), 1)
        self.assertEqual(parsed[0].path, "core/security/src/main/java/com/nexaflow/core/security/Foo.kt")
        self.assertEqual(parsed[0].added_lines, (2, 3, 4))

    def test_ignores_removed_and_context_lines(self):
        diff = (
            "+++ b/a/B.kt\n"
            "@@ -10,0 +11,2 @@\n"
            "-old\n"
            "+new\n"
            " context\n"
            "+newer\n"
        )
        self.assertEqual(diff_cov.parse_diff(diff)[0].added_lines, (11, 13))

    def test_deleted_file_is_skipped(self):
        diff = "+++ /dev/null\n@@ -1 +0,0 @@\n+gone\n"
        self.assertEqual(diff_cov.parse_diff(diff), [])


class EvaluateTest(unittest.TestCase):
    def _repo(self, directory: str) -> Path:
        repo = Path(directory)
        report = repo / "core/security/build/coverage/report.xml"
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(REPORT)
        return repo

    def test_uncovered_changed_line_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = self._repo(directory)
            covered, measured, failures, _ = diff_cov.evaluate(
                diff_cov.parse_diff(DIFF), repo, 0.80
            )
        self.assertEqual((covered, measured), (1, 2))
        self.assertEqual(len(failures), 1)

    def test_fully_covered_change_passes(self):
        diff = DIFF.replace("+fun untested() = 2\n", "").replace("+fun skippedByCompiler() = 3\n", "")
        with tempfile.TemporaryDirectory() as directory:
            repo = self._repo(directory)
            covered, measured, failures, _ = diff_cov.evaluate(diff_cov.parse_diff(diff), repo, 0.80)
        self.assertEqual((covered, measured), (1, 1))
        self.assertEqual(failures, [])

    def test_missing_report_is_skipped_not_failed(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            covered, measured, failures, notes = diff_cov.evaluate(
                diff_cov.parse_diff(DIFF), repo, 0.80
            )
        self.assertEqual((covered, measured, failures), (0, 0, []))
        self.assertTrue(any("no coverage report" in note for note in notes))

    def test_non_production_paths_are_ignored(self):
        diff = (
            "+++ b/app/src/test/java/com/nexaflow/app/FooTest.kt\n"
            "@@ -1,0 +1,1 @@\n"
            "+class FooTest\n"
        )
        with tempfile.TemporaryDirectory() as directory:
            covered, measured, failures, _ = diff_cov.evaluate(
                diff_cov.parse_diff(diff), Path(directory), 0.80
            )
        self.assertEqual((covered, measured, failures), (0, 0, []))


class CliTest(unittest.TestCase):
    def test_cli_reads_diff_file(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            report = repo / "core/security/build/coverage/report.xml"
            report.parent.mkdir(parents=True, exist_ok=True)
            report.write_text(REPORT)
            diff_file = repo / "change.diff"
            diff_file.write_text(DIFF)
            exit_code = diff_cov.main(
                ["--repo", str(repo), "--diff-file", str(diff_file), "--threshold", "0.8"]
            )
        self.assertEqual(exit_code, 1)

    def test_cli_empty_diff_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            diff_file = Path(directory) / "empty.diff"
            diff_file.write_text("")
            exit_code = diff_cov.main(["--diff-file", str(diff_file)])
        self.assertEqual(exit_code, 0)


if __name__ == "__main__":
    unittest.main()
