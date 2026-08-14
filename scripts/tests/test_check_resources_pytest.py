#!/usr/bin/env python3
"""pytest suite for scripts/check_resources.py — the unified CI resource gate.

End-to-end tests (the real gate script runs via subprocess against throwaway
module trees in tmp_path — no repo files are touched). Coverage:

  1. Reference-based orphan detection for FILE resources: an orphaned
     drawable, an orphaned layout, and dimen orphans (default locale plus a
     dimen defined ONLY in values-ar) fail the gate with ORPHAN_RESOURCE
     lines; removing them turns it green; used resources of the same families
     are never flagged.
  2. Synthetic Android Lint reports: a `lint-results-debug.xml` carrying
     MissingTranslation + ExtraTranslation issues is reported with the
     MISSING_TRANSLATIONS_FOUND / EXTRA_TRANSLATIONS_FOUND labels and fails
     the gate; a clean report passes.
  3. lint.xml zero-tolerance pins guard: all seven pins present -> LINT_CONFIG:
     OK; a missing pin, an explicit severity="ignore", or a nested
     <ignore path=.../> list each fail the gate with the LINT_CONFIG
     zero-tolerance-bypass messages.
  4. Module-level lint.xml guard: a module file that silences a pinned issue
     (severity="ignore" or a nested <ignore path=.../>) fails the gate even
     though the root file is fully pinned; a module file carrying only
     non-pinned ignores passes.

Run:  python3 -m pytest scripts/tests/test_check_resources_pytest.py -v
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parents[1]
GATE = SCRIPTS / "check_resources.py"

# The root lint.xml must pin all seven zero-tolerance issues as severity=error.
LINT_XML_OK = """<?xml version="1.0" encoding="UTF-8"?>
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

# --- fixtures ---------------------------------------------------------------


@pytest.fixture
def tree(tmp_path: Path) -> Path:
    """A minimal clean module: one used string, no orphans, lint pinned."""
    res = tmp_path / "src" / "main" / "res"
    (res / "values").mkdir(parents=True)
    (res / "drawable").mkdir(parents=True)
    (res / "layout").mkdir(parents=True)
    (tmp_path / "src" / "main" / "java" / "com" / "x").mkdir(parents=True)

    (res / "values" / "strings.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        '    <string name="used_string">Used</string>\n</resources>\n',
        encoding="utf-8",
    )
    (res / "drawable" / "used_drawable.xml").write_text(
        '<shape xmlns:android="http://schemas.android.com/apk/res/android">'
        '<solid android:color="#FFF"/></shape>\n',
        encoding="utf-8",
    )
    (res / "layout" / "main_layout.xml").write_text(
        '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"'
        ' android:layout_width="match_parent"'
        ' android:layout_height="match_parent"/>\n',
        encoding="utf-8",
    )
    (tmp_path / "src" / "main" / "java" / "com" / "x" / "Main.kt").write_text(
        "package com.x\n"
        "import com.x.R\n"
        "val a = R.layout.main_layout\n"
        "val b = R.drawable.used_drawable\n"
        "val c = R.string.used_string\n",
        encoding="utf-8",
    )
    (tmp_path / "lint.xml").write_text(LINT_XML_OK, encoding="utf-8")
    return tmp_path


def inject_orphans(root: Path) -> None:
    """Add one orphan of each file family to the fixture tree."""
    res = root / "src" / "main" / "res"
    (res / "values-ar").mkdir(parents=True)
    (res / "drawable" / "zz_orphan_drawable.xml").write_text(
        '<shape xmlns:android="http://schemas.android.com/apk/res/android">'
        '<solid android:color="#000"/></shape>\n',
        encoding="utf-8",
    )
    (res / "layout" / "zz_orphan_layout.xml").write_text(
        '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"'
        ' android:layout_width="match_parent"'
        ' android:layout_height="match_parent"/>\n',
        encoding="utf-8",
    )
    (res / "values" / "dimens.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        '    <dimen name="used_dimen">16dp</dimen>\n'
        '    <dimen name="zz_orphan_dimen">12dp</dimen>\n</resources>\n',
        encoding="utf-8",
    )
    # Localized dimen defined ONLY in values-ar — must be flagged too.
    (res / "values-ar" / "dimens.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        '    <dimen name="zz_orphan_dimen_ar">14dp</dimen>\n</resources>\n',
        encoding="utf-8",
    )
    (root / "src" / "main" / "java" / "com" / "x" / "Main.kt").write_text(
        (root / "src" / "main" / "java" / "com" / "x" / "Main.kt").read_text(
            encoding="utf-8"
        )
        + "val d = R.dimen.used_dimen\n",
        encoding="utf-8",
    )


def remove_orphans(root: Path) -> None:
    """Delete the injected orphans so the tree is clean again."""
    res = root / "src" / "main" / "res"
    (res / "drawable" / "zz_orphan_drawable.xml").unlink()
    (res / "layout" / "zz_orphan_layout.xml").unlink()
    for dimens in (res / "values" / "dimens.xml", res / "values-ar" / "dimens.xml"):
        content = dimens.read_text(encoding="utf-8")
        cleaned = "\n".join(
            line
            for line in content.splitlines()
            if "zz_orphan_dimen" not in line
        )
        dimens.write_text(cleaned + "\n", encoding="utf-8")


def run_gate(cwd: Path) -> subprocess.CompletedProcess:
    """Run the real gate script end-to-end from the fixture root."""
    return subprocess.run(
        [sys.executable, str(GATE)],
        cwd=str(cwd),
        capture_output=True,
        text=True,
        timeout=120,
    )


# --- 1. orphan drawable / layout / dimen -------------------------------------


def test_orphan_drawable_layout_dimen_fail_the_gate(tree: Path) -> None:
    inject_orphans(tree)
    proc = run_gate(tree)
    assert proc.returncode != 0, proc.stdout + proc.stderr
    assert "ORPHAN_RESOURCE: drawable:zz_orphan_drawable" in proc.stdout
    assert "ORPHAN_RESOURCE: layout:zz_orphan_layout" in proc.stdout
    assert "ORPHAN_RESOURCE: dimen:zz_orphan_dimen" in proc.stdout
    assert "ORPHAN_RESOURCE: dimen:zz_orphan_dimen_ar" in proc.stdout
    assert "RESOURCE_GATE: FAIL" in proc.stdout


def test_used_resources_of_same_families_never_flagged(tree: Path) -> None:
    inject_orphans(tree)
    proc = run_gate(tree)
    for name in ("used_drawable", "main_layout", "used_dimen", "used_string"):
        assert name not in proc.stdout, f"{name} must not be flagged"


def test_gate_passes_once_orphans_are_removed(tree: Path) -> None:
    inject_orphans(tree)
    assert run_gate(tree).returncode != 0
    remove_orphans(tree)
    proc = run_gate(tree)
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "ORPHAN_RESOURCES_FOUND: 0" in proc.stdout
    assert "RESOURCE_GATE: OK" in proc.stdout


# --- 2. synthetic lint reports ------------------------------------------------

SYNTHETIC_LINT_ISSUES = """<?xml version="1.0" encoding="UTF-8"?>
<issues format="6" by="lint 8.8.2">
    <issue
        id="MissingTranslation"
        severity="error"
        message="`app_name` is not translated in ar, de"
        category="Correctness"
        priority="8"
        summary="Incomplete translation"
        explanation="If an application has more than one locale, ...">
        <location file="src/main/res/values-ar/strings.xml"/>
    </issue>
    <issue
        id="ExtraTranslation"
        severity="error"
        message="`legacy_key` is translated here but not in the default locale"
        category="Correctness"
        priority="8"
        summary="Extra translation"
        explanation="The string is translated in more locales than the default, ..."/>
</issues>
"""

SYNTHETIC_LINT_CLEAN = """<?xml version="1.0" encoding="UTF-8"?>
<issues format="6" by="lint 8.8.2">
</issues>
"""


def write_lint_report(root: Path, content: str) -> None:
    report = root / "build" / "reports"
    report.mkdir(parents=True)
    (report / "lint-results-debug.xml").write_text(content, encoding="utf-8")


def test_synthetic_lint_report_flags_missing_and_extra_translation(
    tree: Path,
) -> None:
    write_lint_report(tree, SYNTHETIC_LINT_ISSUES)
    proc = run_gate(tree)
    assert proc.returncode != 0, proc.stdout + proc.stderr
    assert "MISSING_TRANSLATIONS_FOUND: 1" in proc.stdout
    assert "EXTRA_TRANSLATIONS_FOUND: 1" in proc.stdout
    assert "LINT_ISSUES_FOUND: 2" in proc.stdout
    assert "RESOURCE_GATE: FAIL" in proc.stdout


def test_clean_lint_report_passes(tree: Path) -> None:
    write_lint_report(tree, SYNTHETIC_LINT_CLEAN)
    proc = run_gate(tree)
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "LINT_ISSUES_FOUND: 0" in proc.stdout
    assert "RESOURCE_GATE: OK" in proc.stdout


# --- 3. lint.xml zero-tolerance pins guard -----------------------------------

LINT_XML_MISSING_PIN = LINT_XML_OK.replace(
    '    <issue id="HardcodedText" severity="error" />\n', ""
)

LINT_XML_SEVERITY_IGNORE = LINT_XML_OK.replace(
    '    <issue id="TypographyDashes" severity="error" />',
    '    <issue id="TypographyDashes" severity="ignore" />',
)

LINT_XML_NESTED_IGNORE = LINT_XML_OK.replace(
    '    <issue id="CheckResult" severity="error" />',
    '    <issue id="CheckResult" severity="error">\n'
    '        <ignore path="**/res/values*/"/>'
    "\n    </issue>",
)


@pytest.mark.parametrize(
    ("lint_xml", "expected_line"),
    [
        # Positive: all seven pins present, none ignored.
        (LINT_XML_OK, None),
        # A pin is dropped entirely.
        (LINT_XML_MISSING_PIN, "HardcodedText not pinned as severity=error"),
        # A pin is explicitly silenced.
        (
            LINT_XML_SEVERITY_IGNORE,
            "TypographyDashes silenced via severity=\"ignore\"",
        ),
        # A pin is bypassed with a path-scoped ignore-list.
        (
            LINT_XML_NESTED_IGNORE,
            "CheckResult has an <ignore path=.../> entry",
        ),
    ],
    ids=["all-pins", "missing-pin", "severity-ignore", "nested-ignore"],
)
def test_config_guard(lint_xml: str, expected_line: str | None, tree: Path) -> None:
    (tree / "lint.xml").write_text(lint_xml, encoding="utf-8")
    proc = run_gate(tree)
    if expected_line is None:
        assert proc.returncode == 0, proc.stdout + proc.stderr
        assert "LINT_CONFIG: OK" in proc.stdout
        assert "RESOURCE_GATE: OK" in proc.stdout
    else:
        assert proc.returncode != 0, proc.stdout + proc.stderr
        assert expected_line in proc.stdout
        assert "RESOURCE_GATE: FAIL" in proc.stdout


# --- 4. module-level lint.xml guard -------------------------------------------

MODULE_LINT_SEVERITY_IGNORE = """<?xml version="1.0" encoding="utf-8"?>
<lint>
    <issue id="HardcodedText" severity="ignore" />
</lint>
"""

MODULE_LINT_NESTED_IGNORE = """<?xml version="1.0" encoding="utf-8"?>
<lint>
    <issue id="CheckResult" severity="error">
        <ignore path="**/res/values*/"/>
    </issue>
</lint>
"""

MODULE_LINT_UNPINNED_ONLY = """<?xml version="1.0" encoding="utf-8"?>
<lint>
    <issue id="ProtectedPermissions" severity="ignore" />
    <issue id="GradleDependency" severity="ignore" />
    <issue id="NewerVersionAvailable" severity="ignore" />
</lint>
"""


def _with_module_lint(tree: Path, content: str) -> Path:
    # A module-level lint.xml is enough for the guard — no module resources
    # needed (an unreferenced string would trip the orphan check instead).
    (tree / "app").mkdir(parents=True)
    (tree / "app" / "lint.xml").write_text(content, encoding="utf-8")
    return tree


def test_module_lint_severity_ignore_on_pin_fails(tree: Path) -> None:
    _with_module_lint(tree, MODULE_LINT_SEVERITY_IGNORE)
    proc = run_gate(tree)
    assert proc.returncode != 0, proc.stdout + proc.stderr
    assert (
        'HardcodedText silenced via severity="ignore" in app/lint.xml'
        in proc.stdout
    )
    assert "RESOURCE_GATE: FAIL" in proc.stdout


def test_module_lint_nested_ignore_on_pin_fails(tree: Path) -> None:
    _with_module_lint(tree, MODULE_LINT_NESTED_IGNORE)
    proc = run_gate(tree)
    assert proc.returncode != 0, proc.stdout + proc.stderr
    assert (
        "CheckResult has an <ignore path=.../> entry in app/lint.xml"
        in proc.stdout
    )
    assert "RESOURCE_GATE: FAIL" in proc.stdout


def test_module_lint_with_only_unpinned_ignores_passes(tree: Path) -> None:
    _with_module_lint(tree, MODULE_LINT_UNPINNED_ONLY)
    proc = run_gate(tree)
    assert proc.returncode == 0, proc.stdout + proc.stderr
    assert "LINT_CONFIG: OK" in proc.stdout
    assert "RESOURCE_GATE: OK" in proc.stdout
