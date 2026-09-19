#!/usr/bin/env python3
"""Self-test for the hardcoded-text gate (scripts/check_hardcoded_text.py).

Issue #4 shipped because two Arabic strings were hardcoded in a Compose
screen. This suite pins every layer that must keep that class of defect out:

  1. SCANNER   - Arabic script in shipped Kotlin/Java (string literals AND
                 template text) is flagged, on the exact v3.74.6 leak strings.
  2. COMMENTS  - Arabic in // and /* */ comments (single and multi-line) is
                 stripped and never flagged; strings survive stripping.
  3. ALLOWLIST - a matching anchor suppresses a hit; a drifted anchor is
                 reported as a stale allow-list entry; entries without a
                 reason are rejected.
  4. TREE      - the real repository tree passes the gate end-to-end.

The gate script must remain importable standalone: these tests add
``scripts/`` to sys.path and drive ``scan``/``verify_allowlist``/``main``
directly against synthetic module trees (tmp_path), mirroring the approach of
test_check_lint_config.py. The final test shells out so the wire format
(exit codes, HARDCODED_TEXT / ALLOWLIST prefixes) is pinned too.

Run:  python3 -m pytest scripts/tests/test_check_hardcoded_text.py -v
"""
from __future__ import annotations

import io
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

import check_hardcoded_text as gate  # noqa: E402


LEAK_TITLE = "إجراءات إضافية عند الانتهاء"
LEAK_DESC = "اختياري: مهام تُنفَّذ بعد اكتمال التشغيل"


def make_module(tmp: Path, rel: str, text: str) -> Path:
    src = tmp / rel
    src.parent.mkdir(parents=True, exist_ok=True)
    src.write_text(text, encoding="utf-8")
    return src


class ScannerCatchesTheLeakClass(unittest.TestCase):
    """The exact strings that reached users in v3.74.6 must always be caught."""

    def _scan_single(self, text: str) -> list[str]:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            make_module(
                tmp,
                "m/src/main/java/com/x/Screen.kt",
                "package com.x\n" + text + "\n",
            )
            return gate.scan(tmp)

    def test_string_literal_leak_is_flagged(self) -> None:
        hits = self._scan_single(f'val label = "{LEAK_TITLE}"')
        self.assertEqual(len(hits), 1, hits)
        self.assertIn("Screen.kt:2", hits[0])
        self.assertIn(LEAK_TITLE, hits[0])

    def test_both_v3746_leak_strings_are_flagged(self) -> None:
        hits = self._scan_single(
            f'val a = "{LEAK_TITLE}"\nval b = "{LEAK_DESC}"'
        )
        self.assertEqual(len(hits), 2, hits)

    def test_template_text_leak_is_flagged(self) -> None:
        hits = self._scan_single(f'val msg = "حالة: $value"')
        self.assertEqual(len(hits), 1, hits)

    def test_triple_quoted_leak_is_flagged(self) -> None:
        hits = self._scan_single(f'val doc = """\n{LEAK_TITLE}\n"""')
        self.assertEqual(len(hits), 1, hits)

    def test_english_code_passes_clean(self) -> None:
        self.assertEqual(
            self._scan_single(
                'val label = "Extra actions when done"\n'
                "val n = 42 // لاحظ: تعليق عربي لا يُحتسب\n"
            ),
            [],
        )


class CommentStripping(unittest.TestCase):
    """Arabic inside comments is developer-facing only and must never block."""

    STRIPPERS = {
        "line": "val ok = 1 // تعليق عربي هنا\n",
        "block_single": "/* تعليق عربي هنا */\nval ok = 1\n",
        "block_multi": "/* سطر أول عربي\nسطر ثانٍ عربي */\nval ok = 1\n",
        "kdoc": "/**\n * توثيق عربي\n */\nval ok = 1\n",
    }

    def test_arabic_comments_are_never_flagged(self) -> None:
        for name, text in self.STRIPPERS.items():
            with self.subTest(comment=name):
                with tempfile.TemporaryDirectory() as tmp_name:
                    tmp = Path(tmp_name)
                    make_module(tmp, "m/src/main/java/C.kt", text)
                    self.assertEqual(gate.scan(tmp), [], name)

    def test_comment_masking_preserves_lines_and_strings(self) -> None:
        stripped = gate.strip_comments(
            'val a = 1 // تعليق\nval b = "نص عربي"\n/* حجب\nمتعدد */\nval c = 2\n'
        )
        self.assertEqual(len(stripped.splitlines()), 5)
        self.assertIn("نص عربي", stripped)  # string content survives
        self.assertNotIn("تعليق", stripped)
        self.assertNotIn("حجب", stripped)
        self.assertIn("val c = 2", stripped)

    def test_string_heuristics_do_not_eat_code(self) -> None:
        # Apostrophe inside a string must not open a char-literal state, and
        # escaped quotes must not close the string early.
        stripped = gate.strip_comments('val s = "it\'s النص" // تعليق\n')
        self.assertIn("النص", stripped)
        self.assertNotIn("تعليق", stripped)


class AllowlistBehaviour(unittest.TestCase):
    """Anchors suppress exactly their hit; drift and missing reasons fail."""

    def test_matching_anchor_suppresses_hit(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            make_module(tmp, "m/src/main/java/U.kt", 'val unit = "كم/س"\n')
            original = list(gate.ALLOWLIST)
            gate.ALLOWLIST[:] = [
                {"path": "m/src/main/java/U.kt", "line": 1, "reason": "unit"}
            ]
            try:
                self.assertEqual(gate.scan(tmp), [])
                self.assertEqual(gate.verify_allowlist(tmp), [])
            finally:
                gate.ALLOWLIST[:] = original

    def test_drifted_anchor_is_reported_as_stale(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            make_module(tmp, "m/src/main/java/U.kt", 'val unit = "كم/س"\n')
            original = list(gate.ALLOWLIST)
            gate.ALLOWLIST[:] = [
                {"path": "m/src/main/java/U.kt", "line": 9, "reason": "moved"}
            ]
            try:
                problems = gate.verify_allowlist(tmp)
                # Both signals are expected: the drifted anchor is stale AND
                # the still-present Arabic line is now unallowed.
                self.assertTrue(
                    any("stale allow-list entry" in p for p in problems), problems
                )
                self.assertTrue(
                    any("unallowed finding" in p for p in problems), problems
                )
            finally:
                gate.ALLOWLIST[:] = original

    def test_entry_without_reason_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            make_module(tmp, "m/src/main/java/U.kt", 'val unit = "كم/س"\n')
            original = list(gate.ALLOWLIST)
            gate.ALLOWLIST[:] = [
                {"path": "m/src/main/java/U.kt", "line": 1, "reason": "  "}
            ]
            try:
                problems = gate.verify_allowlist(tmp)
                self.assertTrue(
                    any("has no reason" in p for p in problems), problems
                )
            finally:
                gate.ALLOWLIST[:] = original

    def test_unallowed_hit_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_name:
            tmp = Path(tmp_name)
            make_module(tmp, "m/src/main/java/U.kt", 'val unit = "كم/س"\n')
            original = list(gate.ALLOWLIST)
            gate.ALLOWLIST[:] = []  # no anchors -> the hit must surface
            try:
                problems = gate.verify_allowlist(tmp)
                self.assertEqual(len(problems), 1, problems)
                self.assertIn("unallowed finding", problems[0])
            finally:
                gate.ALLOWLIST[:] = original


class BuiltInSelfTest(unittest.TestCase):
    """The --self-test regression re-injects the v3.74.6 leak string."""

    def test_self_test_passes(self) -> None:
        buf = io.StringIO()
        with redirect_stdout(buf):
            code = gate.main(["--self-test"])
        self.assertEqual(code, 0, buf.getvalue())
        self.assertIn("SELF-TEST: OK", buf.getvalue())


class RealTreePasses(unittest.TestCase):
    """End-to-end: the repository tree and its allow-list are consistent."""

    def test_gate_exit_code_zero_on_repo(self) -> None:
        proc = subprocess.run(
            [sys.executable, str(SCRIPTS / "check_hardcoded_text.py")],
            capture_output=True,
            text=True,
            timeout=120,
            encoding="utf-8",
            errors="replace",
            cwd=str(SCRIPTS.parent),
        )
        self.assertEqual(
            proc.returncode, 0, proc.stdout + proc.stderr
        )
        self.assertIn("HARDCODED_TEXT: OK", proc.stdout)

    def test_shipped_allowlist_entries_are_anchored(self) -> None:
        # Directly pins the committed ALLOWLIST: every entry must still hit,
        # so the list cannot silently rot as code moves.
        self.assertEqual(gate.verify_allowlist(SCRIPTS.parent), [])


if __name__ == "__main__":
    unittest.main()
