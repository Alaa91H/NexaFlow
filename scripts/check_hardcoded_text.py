#!/usr/bin/env python3
"""Hardcoded-text gate: no Arabic script in shipped Kotlin/Java sources.

Issue #4 shipped because two Arabic strings were hardcoded inside a Compose
screen; every other user-facing string in the app flows through localized
resources. This gate keeps it that way:

  * Scanner: strips comments, then flags any Arabic-script run in the
    remaining code of every ``src/main`` Kotlin/Java source. String literals,
    template text, and identifiers are all fair game - anything Arabic that
    survives comment stripping in shipped code is exactly the class of
    hardcoded text that leaked into v3.74.6.
  * Allow-list: a fixed, reviewable table of ``path:line`` anchors with a
    one-line reason each. Legitimate non-UI occurrences (locale names,
    measurement units, symbols) are pinned here instead of hiding inside the
    scanner. An anchor drifts (code moved, text changed) -> the gate fails and
    the anchor must be re-reviewed, so the list cannot rot silently.
  * Self-test: ``scripts/tests/test_check_hardcoded_text.py`` proves the
    scanner still catches the exact v3.74.6 leak strings, proves the
    allow-list actually suppresses its entries, and proves clean sources pass.

Run:  python3 scripts/check_hardcoded_text.py            (CI gate, exit 1 on hit)
      python3 scripts/check_hardcoded_text.py --self-test
"""
from __future__ import annotations

import argparse
import glob
import sys
from pathlib import Path

# Arabic block + supplements (base, Standard, Extended-A/B, presentation forms).
ARABIC_RE = None  # compiled lazily; see _arabic_re()


def _arabic_re():
    global ARABIC_RE
    if ARABIC_RE is None:
        import re

        ARABIC_RE = re.compile(
            "[\u0600-\u06ff\u0750-\u077f\u08a0-\u08ff\ufb50-\ufdff\ufe70-\ufeff]"
        )
    return ARABIC_RE


# --- comment stripping ----------------------------------------------------------

def strip_comments(text: str) -> str:
    """Blank /* */ and // comment spans, preserving strings and line structure.

    Comment CONTENT is dropped (Arabic in developer comments is fine); string
    and char literal contents are kept (that is where UI text leaks). Escapes
    and Kotlin triple-quoted strings are handled.
    """
    out: list[str] = []
    i, n = 0, len(text)
    NORMAL, LINE_C, BLOCK_C, STR, CHAR, TSTR = range(6)
    state = NORMAL
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state == NORMAL:
            if c == "/" and nxt == "/":
                state = LINE_C
                out.append("  ")
                i += 2
                continue
            if c == "/" and nxt == "*":
                state = BLOCK_C
                out.append("  ")
                i += 2
                continue
            if text.startswith('"""', i):
                state = TSTR
                out.append('"""')
                i += 3
                continue
            if c == '"':
                state = STR
                out.append(c)
                i += 1
                continue
            if c == "'":
                state = CHAR
                out.append(c)
                i += 1
                continue
            out.append(c)
            i += 1
        elif state == LINE_C:
            if c == "\n":
                state = NORMAL
                out.append(c)
            else:
                out.append(" " if c != "\t" else "\t")
            i += 1
        elif state == BLOCK_C:
            if c == "*" and nxt == "/":
                state = NORMAL
                out.append("  ")
                i += 2
            else:
                out.append("\n" if c == "\n" else " ")
                i += 1
        elif state == TSTR:
            if text.startswith('"""', i):
                state = NORMAL
                out.append('"""')
                i += 3
            else:
                if c == "\\":
                    out.append(text[i : i + 2] if nxt else "\\")
                    i += 2
                else:
                    out.append(c)
                    i += 1
        else:  # STR or CHAR
            if c == "\\" and nxt:
                out.append(text[i : i + 2])
                i += 2
                continue
            if (state == STR and c == '"') or (state == CHAR and c == "'"):
                state = NORMAL
            out.append(c)
            i += 1
    return "".join(out)


# --- allow-list -----------------------------------------------------------------
#
# path uses forward slashes and is repo-root relative; line is 1-based and must
# still carry Arabic after comment stripping. Every entry needs a reason. When
# a scanner hit is legitimate UI text, the correct fix is a string resource -
# never an entry here.

ALLOWLIST: list[dict[str, str]] = [
    {
        "path": "feature/settings/src/main/java/com/nexaflow/feature/settings/SettingsScreen.kt",
        "line": 721,
        "reason": "Native name of the Arabic language in the in-app language picker",
    },
]

# The exact strings that leaked to users in v3.74.6 (issue #4). The self-test
# re-injects them into a synthetic source file and demands a finding, so the
# scanner can never regress into missing this class again.
ISSUE_4_LEAKS = (
    "إجراءات إضافية عند الانتهاء",
    "اختياري: مهام تُنفَّذ بعد اكتمال التشغيل",
)


# --- scanning -------------------------------------------------------------------

def source_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for pattern in ("**/src/main/**/*.kt", "**/src/main/**/*.java"):
        files.extend(Path(p) for p in glob.glob(str(root / pattern), recursive=True))
    return sorted(
        p
        for p in files
        if "/build/" not in p.as_posix() and not p.as_posix().startswith(".git/")
    )


def scan(root: Path) -> list[str]:
    """Return human-readable findings for Arabic script in shipped code."""
    findings: list[str] = []
    anchors = {(e["path"], str(e["line"])) for e in ALLOWLIST}
    for path in source_files(root):
        rel = path.relative_to(root).as_posix()
        try:
            # utf-8-sig: a leading BOM (U+FEFF) sits inside an Arabic Unicode
            # range and would false-positive on the package line otherwise.
            text = path.read_text(encoding="utf-8-sig")
        except (UnicodeDecodeError, OSError) as exc:
            findings.append(f"{rel}:0: unreadable source ({exc})")
            continue
        code = strip_comments(text)
        for lineno, line in enumerate(code.splitlines(), start=1):
            if not _arabic_re().search(line):
                continue
            if (rel, str(lineno)) in anchors:
                continue
            snippet = line.strip()
            if len(snippet) > 100:
                snippet = snippet[:97] + "..."
            findings.append(f"{rel}:{lineno}: {snippet}")
    return findings


def verify_allowlist(root: Path) -> list[str]:
    """Every anchor must still hit; every hit must be anchored."""
    problems: list[str] = []
    active = {(e["path"], str(e["line"])) for e in ALLOWLIST}
    consumed: set[tuple[str, str]] = set()
    for path in source_files(root):
        rel = path.relative_to(root).as_posix()
        try:
            text = path.read_text(encoding="utf-8-sig")
        except (UnicodeDecodeError, OSError):
            continue
        code = strip_comments(text)
        for lineno, line in enumerate(code.splitlines(), start=1):
            if _arabic_re().search(line) and (rel, str(lineno)) in active:
                consumed.add((rel, str(lineno)))
    for entry in ALLOWLIST:
        key = (entry["path"], str(entry["line"]))
        if key not in consumed:
            problems.append(
                f"stale allow-list entry {key[0]}:{key[1]} ({entry['reason']}): "
                "no Arabic at that anchor anymore - remove or re-anchor it"
            )
        if not entry.get("reason", "").strip():
            problems.append(f"allow-list entry {key[0]}:{key[1]} has no reason")
    findings = scan(root)
    for f in findings:
        anchor = f.rsplit(":", 1)[0]
        if anchor not in {f"{p}:{n}" for p, n in consumed}:
            problems.append(f"unallowed finding: {f}")
    return problems


def main(argv: list[str] | None = None) -> int:
    # Windows consoles default to a legacy codepage; findings contain Arabic.
    if sys.stdout and hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--self-test", action="store_true", help="run the built-in leak regression"
    )
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[1]

    if args.self_test:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            mod = Path(tmp) / "m" / "src" / "main" / "java" / "com" / "x"
            mod.mkdir(parents=True)
            probe = mod / "Leak.kt"
            body = "\n".join(
                f'val leak{i} = "{s}"' for i, s in enumerate(ISSUE_4_LEAKS)
            )
            probe.write_text("package com.x\n" + body + "\n", encoding="utf-8")
            hits = scan(Path(tmp))
            if len(hits) != len(ISSUE_4_LEAKS) or not all(
                f"Leak.kt:{2 + i}" in hits[i] for i in range(len(ISSUE_4_LEAKS))
            ):
                print("SELF-TEST FAIL: an issue #4 leak string passed unnoticed")
                return 2
        print("SELF-TEST: OK - the v3.74.6 leak class is still caught")
        return 0

    findings = scan(root)
    problems = verify_allowlist(root)
    for f in findings:
        print(f"HARDCODED_TEXT: {f}")
    for p in problems:
        print(f"ALLOWLIST: {p}")
    if findings or problems:
        total = len(findings) + len(problems)
        print(
            f"\nHARDCODED_TEXT: {total} problem(s). User-facing strings must be "
            "string resources; extend ALLOWLIST only for genuine non-UI text."
        )
        return 1
    print(
        f"HARDCODED_TEXT: OK - {len(source_files(root))} shipped sources clean, "
        f"{len(ALLOWLIST)} allow-listed anchor(s) verified"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
