#!/usr/bin/env python3
"""End-to-end unit tests for scripts/check_resources.py orphan detection.

Proves the reference gate flags orphaned drawable / layout / dimen resources -
including a dimen defined ONLY in a locale folder (values-ar) - and that the
gate fails (non-zero exit) while those orphans exist and passes again once they
are removed. Also proves used resources of the same families are never flagged.

Runs against a throwaway Android-module tree in a temp dir; no repo files are
touched. Stdlib unittest only - no pytest required.

Run:  python3 scripts/tests/test_check_resources.py
      python3 -m unittest discover -s scripts/tests
"""
from __future__ import annotations

import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
GATE = SCRIPTS / "check_resources.py"

LINT_XML = """<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <issue id="UnusedResources" severity="error" />
    <issue id="MissingTranslation" severity="error" />
    <issue id="ExtraTranslation" severity="error" />
    <issue id="UnusedIds" severity="error" />
    <issue id="CheckResult" severity="error" />
    <issue id="HardcodedText" severity="error" />
    <issue id="TypographyDashes" severity="error" />
</lint>
"""


def make_tree(root: Path) -> None:
    """A minimal module whose ONLY defects are the four injected orphans."""
    res = root / "src" / "main" / "res"
    (res / "values").mkdir(parents=True)
    (res / "values-ar").mkdir(parents=True)
    (res / "drawable").mkdir(parents=True)
    (res / "layout").mkdir(parents=True)
    (root / "src" / "main" / "java" / "com" / "x").mkdir(parents=True)

    (res / "values" / "strings.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        '    <string name="used_string">Used</string>\n</resources>\n',
        encoding="utf-8",
    )
    (res / "values" / "dimens.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        '    <dimen name="used_dimen">16dp</dimen>\n'
        '    <dimen name="zz_orphan_dimen">12dp</dimen>\n</resources>\n',
        encoding="utf-8",
    )
    # Locale-only orphan dimen: defined ONLY in values-ar (localized dimens).
    (res / "values-ar" / "dimens.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        '    <dimen name="zz_orphan_dimen_ar">14dp</dimen>\n</resources>\n',
        encoding="utf-8",
    )
    (res / "drawable" / "used_drawable.xml").write_text(
        '<shape xmlns:android="http://schemas.android.com/apk/res/android">'
        '<solid android:color="#FFF"/></shape>\n',
        encoding="utf-8",
    )
    (res / "drawable" / "zz_orphan_drawable.xml").write_text(
        '<shape xmlns:android="http://schemas.android.com/apk/res/android">'
        '<solid android:color="#000"/></shape>\n',
        encoding="utf-8",
    )
    (res / "layout" / "main_layout.xml").write_text(
        '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"'
        ' android:orientation="vertical" android:layout_width="match_parent"'
        ' android:layout_height="match_parent"/>\n',
        encoding="utf-8",
    )
    (res / "layout" / "zz_orphan_layout.xml").write_text(
        '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"'
        ' android:layout_width="match_parent"'
        ' android:layout_height="match_parent"/>\n',
        encoding="utf-8",
    )
    (root / "src" / "main" / "java" / "com" / "x" / "Main.kt").write_text(
        "package com.x\n"
        "import com.x.R\n"
        "val a = R.layout.main_layout\n"
        "val b = R.drawable.used_drawable\n"
        "val c = R.dimen.used_dimen\n"
        "val d = R.string.used_string\n",
        encoding="utf-8",
    )
    (root / "lint.xml").write_text(LINT_XML, encoding="utf-8")


def run_gate(cwd: Path) -> subprocess.CompletedProcess:
    """Run the real gate script end-to-end from the fixture root."""
    return subprocess.run(
        [sys.executable, str(GATE)],
        cwd=str(cwd),
        capture_output=True,
        text=True,
        timeout=120,
    )


class OrphanDetectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        make_tree(self.root)

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def test_gate_fails_with_orphan_drawable_layout_dimen(self) -> None:
        proc = run_gate(self.root)
        self.assertNotEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("ORPHAN_RESOURCE: drawable:zz_orphan_drawable", proc.stdout)
        self.assertIn("ORPHAN_RESOURCE: layout:zz_orphan_layout", proc.stdout)
        self.assertIn("ORPHAN_RESOURCE: dimen:zz_orphan_dimen", proc.stdout)
        self.assertIn("ORPHAN_RESOURCE: dimen:zz_orphan_dimen_ar", proc.stdout)
        self.assertIn("RESOURCE_GATE: FAIL", proc.stdout)

    def test_used_resources_of_same_families_not_flagged(self) -> None:
        proc = run_gate(self.root)
        for name in ("used_drawable", "main_layout", "used_dimen", "used_string"):
            self.assertNotIn(name, proc.stdout, f"{name} must not be flagged")

    def test_gate_passes_after_orphans_removed(self) -> None:
        res = self.root / "src" / "main" / "res"
        (res / "drawable" / "zz_orphan_drawable.xml").unlink()
        (res / "layout" / "zz_orphan_layout.xml").unlink()
        for p in (res / "values" / "dimens.xml", res / "values-ar" / "dimens.xml"):
            content = p.read_text(encoding="utf-8")
            cleaned = re.sub(
                r'\s*<dimen name="zz_orphan_dimen[^"]*">.*?</dimen>',
                "",
                content,
                flags=re.DOTALL,
            )
            p.write_text(cleaned, encoding="utf-8")
        proc = run_gate(self.root)
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("ORPHAN_RESOURCES_FOUND: 0", proc.stdout)
        self.assertIn("RESOURCE_GATE: OK", proc.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
