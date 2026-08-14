#!/usr/bin/env python3
"""End-to-end tests for scripts/auto_fix.py on a miniature resource tree.

Runs the real script (subprocess) against a throwaway module whose resources
deliberately carry every defect the fixer understands, so each of the five
cases is proven on actual file bytes:

  1. ORPHAN    - a key defined but referenced nowhere -> deleted from EVERY
                 locale file (default + values-ar).
  2. EXTRA     - a key present only in a locale -> deleted.
  3. MISSING   - a default key absent from a locale -> backfilled with the
                 default value + a TODO(translate) comment.
  4. DUPLICATE - a key defined twice in one file -> first occurrence kept,
                 later ones removed.
  5. --no-backfill - balance performs deletions only; missing keys are never
                 inserted (and the tree is then NOT parity-clean).

Run:  python3 -m pytest scripts/tests/test_auto_fix.py -v
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parents[1]
FIXER = SCRIPTS / "auto_fix.py"

DEFAULT_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="used_a">A</string>
    <string name="used_b">B</string>
    <string name="zz_orphan">Orphan</string>
    <string name="zz_dup">Dup one</string>
    <string name="zz_dup">Dup two</string>
    <string name="zz_missing">Missing in ar</string>
</resources>
"""

AR_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="used_a">ألف</string>
    <string name="zz_orphan">يتيم</string>
    <string name="zz_extra">زائد</string>
</resources>
"""

COLORS_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="used_color">#FF0000</color>
    <color name="zz_color_orphan">#00FF00</color>
    <item type="color" name="used_item_color">#0000FF</item>
</resources>
"""

NIGHT_COLORS_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="used_color">#FF0000</color>
    <color name="zz_color_orphan">#111111</color>
</resources>
"""

DIMENS_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <dimen name="used_dimen">16dp</dimen>
    <dimen name="zz_dimen_orphan">8dp</dimen>
</resources>
"""

STYLES_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="UsedStyle">
        <item name="android:colorAccent">@color/used_color</item>
    </style>
    <style name="zz_style_orphan" />
</resources>
"""

# zz_dup and zz_missing are referenced on purpose: only zz_orphan may be an
# orphan, otherwise the orphan pass would eat the duplicate and missing cases
# before they can be observed. used_* keys keep the color/dimen/style orphans
# isolated - only zz_color_orphan / zz_dimen_orphan / zz_style_orphan are dead.
MAIN_KT = """package com.x
import com.x.R
val a = R.string.used_a
val b = R.string.used_b
val c = R.string.zz_dup
val d = R.string.zz_missing
val e = R.color.used_color
val f = R.color.used_item_color
val g = R.dimen.used_dimen
val h = R.style.UsedStyle
"""


@pytest.fixture
def tree(tmp_path: Path) -> Path:
    """A miniature module carrying every defect: orphan, extra, missing, dup,
    plus orphaned color/dimen/style keys in qualifier value files."""
    res = tmp_path / "src" / "main" / "res"
    (res / "values").mkdir(parents=True)
    (res / "values-ar").mkdir(parents=True)
    (res / "values-night").mkdir(parents=True)
    (res / "values" / "strings.xml").write_text(DEFAULT_XML, encoding="utf-8")
    (res / "values-ar" / "strings.xml").write_text(AR_XML, encoding="utf-8")
    (res / "values" / "colors.xml").write_text(COLORS_XML, encoding="utf-8")
    (res / "values-night" / "colors.xml").write_text(NIGHT_COLORS_XML, encoding="utf-8")
    (res / "values" / "dimens.xml").write_text(DIMENS_XML, encoding="utf-8")
    (res / "values" / "styles.xml").write_text(STYLES_XML, encoding="utf-8")
    (tmp_path / "src" / "main" / "java" / "com" / "x").mkdir(parents=True)
    (tmp_path / "src" / "main" / "java" / "com" / "x" / "Main.kt").write_text(
        MAIN_KT, encoding="utf-8"
    )
    return tmp_path


def run_fixer(cwd: Path, *extra: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(FIXER), *extra],
        cwd=str(cwd),
        capture_output=True,
        text=True,
        timeout=120,
    )


def read_xml(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def default_xml(tree: Path) -> Path:
    return tree / "src" / "main" / "res" / "values" / "strings.xml"


def ar_xml(tree: Path) -> Path:
    return tree / "src" / "main" / "res" / "values-ar" / "strings.xml"


# --- case 1: orphan -----------------------------------------------------------


def test_check_reports_orphan_extra_and_missing(tree: Path) -> None:
    proc = run_fixer(tree, "--check")
    assert proc.returncode == 1, proc.stdout + proc.stderr
    # Orphan appears for BOTH the default file and the locale.
    assert "remove-orphan: zz_orphan" in proc.stdout
    assert "remove-extra: zz_extra" in proc.stdout
    assert "backfill: used_b" in proc.stdout
    assert "backfill: zz_missing" in proc.stdout
    assert "AUTO_FIX:" in proc.stdout


def test_apply_removes_orphan_from_every_locale(tree: Path) -> None:
    proc = run_fixer(tree)
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "zz_orphan" not in read_xml(default_xml(tree))
    assert "zz_orphan" not in read_xml(ar_xml(tree))


# --- case 2: extra -------------------------------------------------------------


def test_apply_removes_locale_only_extra(tree: Path) -> None:
    run_fixer(tree)
    assert "zz_extra" not in read_xml(ar_xml(tree))


# --- case 3: missing -----------------------------------------------------------


def test_apply_backfills_missing_keys_with_todo_comment(tree: Path) -> None:
    run_fixer(tree)
    ar = read_xml(ar_xml(tree))
    assert "used_b" in ar
    assert "zz_missing" in ar
    assert "TODO(translate)" in ar
    # Backfilled values carry the default locale's text.
    assert "Missing in ar" in ar


# --- case 4: duplicate ---------------------------------------------------------


def test_apply_keeps_first_duplicate_occurrence(tree: Path) -> None:
    run_fixer(tree)
    default = read_xml(default_xml(tree))
    assert default.count("name=\"zz_dup\"") == 1
    assert "Dup one" in default
    assert "Dup two" not in default


# --- case 5: orphaned color/dimen/style value files ----------------------------


def test_check_reports_orphan_value_keys(tree: Path) -> None:
    proc = run_fixer(tree, "--check")
    assert proc.returncode == 1, proc.stdout + proc.stderr
    assert "remove-orphan: zz_color_orphan" in proc.stdout
    assert "remove-orphan: zz_dimen_orphan" in proc.stdout
    assert "remove-orphan: zz_style_orphan" in proc.stdout


def test_apply_removes_orphan_color_from_every_qualifier(tree: Path) -> None:
    run_fixer(tree)
    # Both the default and the values-night override carry the orphan color.
    assert "zz_color_orphan" not in read_xml(tree / "src" / "main" / "res" / "values" / "colors.xml")
    assert "zz_color_orphan" not in read_xml(tree / "src" / "main" / "res" / "values-night" / "colors.xml")


def test_apply_removes_orphan_dimen_and_style(tree: Path) -> None:
    run_fixer(tree)
    dimens = read_xml(tree / "src" / "main" / "res" / "values" / "dimens.xml")
    styles = read_xml(tree / "src" / "main" / "res" / "values" / "styles.xml")
    assert "zz_dimen_orphan" not in dimens
    assert "zz_style_orphan" not in styles


def test_apply_keeps_referenced_value_keys_and_style_items(tree: Path) -> None:
    run_fixer(tree)
    colors = read_xml(tree / "src" / "main" / "res" / "values" / "colors.xml")
    night = read_xml(tree / "src" / "main" / "res" / "values-night" / "colors.xml")
    dimens = read_xml(tree / "src" / "main" / "res" / "values" / "dimens.xml")
    styles = read_xml(tree / "src" / "main" / "res" / "values" / "styles.xml")
    assert "used_color" in colors and "used_color" in night
    assert "used_item_color" in colors
    assert "used_dimen" in dimens
    # The style survives with its android:colorAccent item intact.
    assert "UsedStyle" in styles
    assert "android:colorAccent" in styles


def test_no_backfill_never_touches_value_files(tree: Path) -> None:
    # --no-backfill only governs string backfill; value orphan removal is a
    # deletion-only pass in both modes and must still run.
    proc = run_fixer(tree, "--no-backfill")
    assert proc.returncode == 0, proc.stdout + proc.stderr
    colors = read_xml(tree / "src" / "main" / "res" / "values" / "colors.xml")
    assert "zz_color_orphan" not in colors
    assert "used_color" in colors


# --- case 6: --no-backfill -----------------------------------------------------


def test_no_backfill_deletes_but_never_inserts(tree: Path) -> None:
    proc = run_fixer(tree, "--no-backfill")
    assert proc.returncode == 0, proc.stdout + proc.stderr
    ar = read_xml(ar_xml(tree))
    # Deletions still happen: orphan + locale-only extra.
    assert "zz_orphan" not in ar
    assert "zz_extra" not in ar
    # But missing keys are NOT inserted, and no TODO marker appears.
    assert "used_b" not in ar
    assert "zz_missing" not in ar
    assert "TODO(translate)" not in ar
    # The default file still resolved the duplicate.
    assert read_xml(default_xml(tree)).count("name=\"zz_dup\"") == 1
    # Without backfill the tree is not parity-clean: check mode still demands
    # the missing keys.
    assert run_fixer(tree, "--check").returncode == 1


# --- clean tree ---------------------------------------------------------------


def test_check_passes_when_tree_is_clean(tree: Path) -> None:
    assert run_fixer(tree).returncode == 0
    proc = run_fixer(tree, "--check")
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "AUTO_FIX: OK" in proc.stdout
