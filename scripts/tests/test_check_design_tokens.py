#!/usr/bin/env python3
"""Self-test for the design-token gate (scripts/check_design_tokens.py).

The NexaFlow One design system replaces the per-screen hardcoded palette with
a token layer in `core/ui-components/.../ui/theme/`. A token layer only holds
if nothing else hardcodes a value, so this suite pins every layer that must
keep that class of drift out:

  1. SCANNER   - a raw `Color(0x...)`, a raw `Color(r, g, b)` and a raw
                 `RoundedCornerShape(n.dp)` in shipped Kotlin/Java are flagged.
  2. SCOPE     - the theme packages are exempt; every other shipped source,
                 including the `app` module, is not.
  3. TESTS     - test sources are not production sources and are never
                 flagged.
  4. BASELINE  - a matching anchor suppresses a hit; a drifted anchor is
                 reported as stale.
  5. TREE      - the real repository tree passes the gate end-to-end, and
                 every committed baseline entry is still anchored.

The gate script must remain importable standalone: these tests add
``scripts/`` to sys.path and drive ``collect``/``load_baseline``/``main``
directly against synthetic module trees (tmp_path), mirroring the approach of
test_check_hardcoded_text.py. The final test shells out so the wire format
(exit codes and the OK line) is pinned too.

Run:  python3 -m pytest scripts/tests/test_check_design_tokens.py -v
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

import check_design_tokens as gate  # noqa: E402


def write_source(root: Path, rel: str, text: str) -> Path:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


class ScannerCatchesRawValues(unittest.TestCase):
    """A raw color or radius in a shipped source must always be caught."""

    def test_hex_color_is_flagged(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            write_source(
                tmp,
                "feature/demo/src/main/java/demo/Screen.kt",
                "val accent = Color(0xFF3B5BFF)\n",
            )
            violations, _ = gate.collect(tmp)
            self.assertIn(
                ("feature/demo/src/main/java/demo/Screen.kt", "color:0xFF3B5BFF"),
                set(violations),
            )

    def test_rgb_color_is_flagged(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            write_source(
                tmp,
                "feature/demo/src/main/java/demo/Screen.kt",
                "val accent = Color(12, 34, 56)\n",
            )
            violations, _ = gate.collect(tmp)
            self.assertIn(
                ("feature/demo/src/main/java/demo/Screen.kt", "color:rgb(12,34,56)"),
                set(violations),
            )

    def test_radius_is_flagged(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            write_source(
                tmp,
                "feature/demo/src/main/java/demo/Screen.kt",
                "val shape = RoundedCornerShape(12.dp)\n",
            )
            violations, _ = gate.collect(tmp)
            self.assertIn(
                ("feature/demo/src/main/java/demo/Screen.kt", "radius:12.dp"),
                set(violations),
            )


class ScopeIsHonored(unittest.TestCase):
    """The theme packages are the only place a raw value may live."""

    def test_shared_theme_package_is_exempt(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            write_source(
                tmp,
                gate.THEME_SCOPES[0] + "PaletteFixture.kt",
                "val accent = Color(0xFF3B5BFF)\n",
            )
            violations, scoped = gate.collect(tmp)
            self.assertEqual(violations, [])
            self.assertIn(
                (gate.THEME_SCOPES[0] + "PaletteFixture.kt", "color:0xFF3B5BFF"),
                set(scoped),
            )

    def test_app_theme_package_is_exempt(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            write_source(
                tmp,
                gate.THEME_SCOPES[1] + "AccentFixture.kt",
                "val accent = Color(0xFF00D6B4)\n",
            )
            violations, scoped = gate.collect(tmp)
            self.assertEqual(violations, [])
            self.assertIn(
                (gate.THEME_SCOPES[1] + "AccentFixture.kt", "color:0xFF00D6B4"),
                set(scoped),
            )

    def test_app_module_outside_the_theme_package_is_flagged(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            write_source(
                tmp,
                "app/src/main/java/app/Main.kt",
                "val accent = Color(0xFF00D6B4)\n",
            )
            violations, _ = gate.collect(tmp)
            self.assertIn(
                ("app/src/main/java/app/Main.kt", "color:0xFF00D6B4"),
                set(violations),
            )

    def test_test_sources_are_not_production_sources(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            write_source(
                tmp,
                "core/ui-components/src/test/java/T.kt",
                "val t = Color(0xFF000000)\n",
            )
            violations, _ = gate.collect(tmp)
            self.assertEqual(violations, [])


class BaselineBehavior(unittest.TestCase):
    """A pinned anchor suppresses a hit; a drifted anchor is reported."""

    def test_pinned_anchor_is_suppressed_and_drift_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            write_source(
                tmp,
                "feature/demo/src/main/java/demo/Screen.kt",
                "val accent = Color(0xFF3B5BFF)\n",
            )
            baseline = tmp / "scripts" / "design_token_baseline.txt"
            baseline.parent.mkdir(parents=True, exist_ok=True)
            baseline.write_text(
                "# pinned\n"
                "feature/demo/src/main/java/demo/Screen.kt|color:0xFF3B5BFF\n"
                "feature/demo/src/main/java/demo/Gone.kt|color:0xFF123456\n",
                encoding="utf-8",
            )
            pinned = gate.load_baseline(baseline)
            violations, _ = gate.collect(tmp)
            observed = set(violations)
            self.assertNotIn(
                ("feature/demo/src/main/java/demo/Screen.kt", "color:0xFF3B5BFF"),
                observed - pinned,
            )
            self.assertIn(
                ("feature/demo/src/main/java/demo/Gone.kt", "color:0xFF123456"),
                pinned - observed,
            )


class ShippedTreePasses(unittest.TestCase):
    """The committed tree and the committed baseline must agree."""

    def test_repository_tree_passes_the_gate(self) -> None:
        proc = subprocess.run(
            [sys.executable, "scripts/check_design_tokens.py"],
            capture_output=True,
            text=True,
            timeout=180,
            encoding="utf-8",
            errors="replace",
            cwd=str(SCRIPTS.parent),
        )
        self.assertEqual(proc.returncode, 0, proc.stdout + proc.stderr)
        self.assertIn("OK: no unpinned color or radius literals", proc.stdout)

    def test_committed_baseline_entries_are_anchored(self) -> None:
        # Every committed anchor must still hit, so the list cannot rot
        # silently as code moves.
        violations, _ = gate.collect(SCRIPTS.parent)
        observed = set(violations)
        pinned = gate.load_baseline(gate.BASELINE)
        self.assertTrue(pinned, "the committed baseline must not be empty")
        self.assertEqual(
            sorted(pinned - observed),
            [],
            "committed baseline anchors drifted and must be re-reviewed",
        )


if __name__ == "__main__":
    unittest.main()
