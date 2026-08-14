#!/usr/bin/env python3
"""Function-level unit tests for check_resources.check_lint_config (Check 4).

Unlike the end-to-end suite (test_check_resources_pytest.py) which shells out to
the gate script, these tests import check_resources and call check_lint_config()
directly against five lint.xml shapes, asserting the returned problem count AND
the printed diagnostics:

  1. clean         — all seven zero-tolerance pins, severity=error -> 0, OK
  2. missing       — no lint.xml present in the tree            -> 1, unenforced
  3. severity=ignore — a pin explicitly silenced                -> 2 (not pinned
                     + silenced-bypass), each with its own message
  4. nested ignore — <ignore path=.../> inside a pinned issue   -> 1, bypass
  5. missing pin   — a pin dropped entirely from the file       -> 1, not pinned

Run:  python3 -m pytest scripts/tests/test_check_lint_config.py -v
"""
from __future__ import annotations

import sys
from pathlib import Path

import pytest

SCRIPTS = Path(__file__).resolve().parents[1]
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

import check_resources  # noqa: E402

PINS = [
    "UnusedResources",
    "MissingTranslation",
    "ExtraTranslation",
    "UnusedIds",
    "CheckResult",
    "HardcodedText",
    "TypographyDashes",
]


def _lint_xml(severities: dict[str, str]) -> str:
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        "<lint>",
    ]
    for pin in PINS:
        lines.append(f'    <issue id="{pin}" severity="{severities[pin]}" />')
    lines.append("</lint>")
    return "\n".join(lines) + "\n"


CLEAN = _lint_xml({pin: "error" for pin in PINS})

# A pin dropped entirely from the file.
MISSING_PIN = CLEAN.replace(
    '    <issue id="HardcodedText" severity="error" />\n', ""
)

# A pin explicitly silenced (severity="ignore").
SEVERITY_IGNORE = CLEAN.replace(
    '    <issue id="TypographyDashes" severity="error" />',
    '    <issue id="TypographyDashes" severity="ignore" />',
)

# A pinned issue carrying a path-scoped ignore-list.
NESTED_IGNORE = CLEAN.replace(
    '    <issue id="CheckResult" severity="error" />',
    '    <issue id="CheckResult" severity="error">\n'
    '        <ignore path="**/res/values*/"/>'
    "\n    </issue>",
)

# --- the five cases -----------------------------------------------------------

CASES = [
    pytest.param(
        "clean",
        CLEAN,
        0,
        ["LINT_CONFIG: OK", "all zero-tolerance pins present"],
        id="clean",
    ),
    pytest.param(
        "missing",
        None,  # no file written
        1,
        [
            "LINT_CONFIG: no lint.xml found",
            "zero-tolerance pins unenforced",
        ],
        id="missing-file",
    ),
    pytest.param(
        "severity-ignore",
        SEVERITY_IGNORE,
        2,
        [
            "TypographyDashes not pinned as severity=error",
            'TypographyDashes silenced via severity="ignore"',
        ],
        id="severity-ignore",
    ),
    pytest.param(
        "nested-ignore",
        NESTED_IGNORE,
        1,
        ["CheckResult has an <ignore path=.../> entry"],
        id="nested-ignore",
    ),
    pytest.param(
        "missing-pin",
        MISSING_PIN,
        1,
        ["HardcodedText not pinned as severity=error"],
        id="missing-pin",
    ),
]


@pytest.mark.parametrize(
    ("case_name", "lint_xml", "expected_problems", "expected_outputs"),
    CASES,
)
def test_check_lint_config(
    case_name: str,
    lint_xml: str | None,
    expected_problems: int,
    expected_outputs: list[str],
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    if lint_xml is not None:
        (tmp_path / "lint.xml").write_text(lint_xml, encoding="utf-8")
    monkeypatch.chdir(tmp_path)
    problems = check_resources.check_lint_config()
    out = capsys.readouterr().out
    assert problems == expected_problems, (
        f"[{case_name}] expected {expected_problems} problems, got {problems};\n"
        f"output:\n{out}"
    )
    for expected in expected_outputs:
        assert expected in out, f"[{case_name}] missing {expected!r} in:\n{out}"
