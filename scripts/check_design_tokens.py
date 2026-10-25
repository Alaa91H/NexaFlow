#!/usr/bin/env python3
"""Design-token gate: color and shape literals live in the theme, nowhere else.

The NexaFlow One design system (docs/NEXAFLOW_ONE_DESIGN.md) replaces the
per-screen hardcoded palette with a token layer in
`core/ui-components/src/main/java/com/nexaflow/core/ui/theme/`. A token layer
only holds if nothing else hardcodes a value, so this gate enforces that:

  * Scanner: walks every ``src/main`` Kotlin/Java source in the repo and
    reports two classes of literal:
      - ``Color(0xAARRGGBB)`` / ``Color(red, green, blue)`` — a raw color
      - ``RoundedCornerShape(<n>.dp)`` — a raw radius
    Both are read as *values*, not as usages of the theme: a screen that
    hardcodes ``Color(0xFF3B5BFF)`` is exactly the drift the token layer
    exists to prevent.
  * Scope: the theme package is the only place a literal may be defined.
    Everything else — every ``feature/*`` screen, every ``core/*`` module,
    ``app``, ``wear`` — must reach the value through a token.
  * Baseline: a fixed, reviewable table of ``path|literal`` anchors with a
    one-line reason each. The pre-existing offenders are pinned here instead
    of being fixed inside the same change, because a mechanical sweep of 250+
    call sites is a separate review with its own risk. An anchor drifts
    (the literal moved or was removed) -> the gate fails and the anchor must
    be re-reviewed, so the list cannot rot silently.
  * Self-test: ``scripts/tests/test_check_design_tokens.py`` proves the
    scanner still catches a hardcoded color and radius, proves the theme
    scope is honored, proves the baseline actually suppresses its entries,
    and proves clean sources pass.

Run:  python3 scripts/check_design_tokens.py            (CI gate, exit 1 on hit)
      python3 scripts/check_design_tokens.py --self-test
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASELINE = ROOT / "scripts" / "design_token_baseline.txt"

# The only packages allowed to define raw color/radius values. Everything
# else consumes the tokens they export.
#
# `core/ui-components/.../ui/theme/` holds the shared palette, traits and
# gradient tokens every module reads. `app/.../ui/theme/` holds the
# app-level accent seeds that are layered on top of that shared palette.
THEME_SCOPES = (
    "core/ui-components/src/main/java/com/nexaflow/core/ui/theme/",
    "app/src/main/java/com/nexaflow/app/ui/theme/",
)

# `Color(0xAARRGGBB)` and the long/short hex forms Compose accepts.
COLOR_HEX_RE = re.compile(r"Color\(\s*0[xX]([0-9a-fA-F]{6,8})\s*\)")
# `Color(r, g, b)` — three integer arguments, no alpha.
COLOR_RGB_RE = re.compile(r"Color\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)")
# `RoundedCornerShape(12.dp)` — a raw radius in density-independent pixels.
RADIUS_RE = re.compile(r"RoundedCornerShape\(\s*(\d{1,3})(?:\.0)?\.dp\s*\)")


def iter_sources(root: Path):
    """Yield every shipped Kotlin/Java source under a ``src/main`` tree."""
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        if path.suffix not in (".kt", ".java"):
            continue
        parts = path.parts
        if "src" not in parts:
            continue
        try:
            src_index = parts.index("src")
        except ValueError:
            continue
        # Only the production source set; tests and androidTest may pin
        # literal colors on purpose (a contrast test needs a known pair).
        if parts[src_index + 1 : src_index + 2] != ("main",):
            continue
        yield path


def scan_text(text: str) -> list[str]:
    """Return the literals found in one source file, normalized."""
    found: list[str] = []
    for match in COLOR_HEX_RE.finditer(text):
        found.append(f"color:0x{match.group(1).upper()}")
    for match in COLOR_RGB_RE.finditer(text):
        found.append(f"color:rgb({match.group(1)},{match.group(2)},{match.group(3)})")
    for match in RADIUS_RE.finditer(text):
        found.append(f"radius:{match.group(1)}.dp")
    return found


def in_theme_scope(rel_path: str) -> bool:
    return any(rel_path.startswith(scope) for scope in THEME_SCOPES)


def collect(root: Path) -> tuple[list[tuple[str, str]], list[tuple[str, str]]]:
    """Return (violations, scoped) where violations are outside the theme."""
    violations: list[tuple[str, str]] = []
    scoped: list[tuple[str, str]] = []
    for path in iter_sources(root):
        rel = path.relative_to(root).as_posix()
        text = path.read_text(encoding="utf-8", errors="replace")
        for literal in scan_text(text):
            if in_theme_scope(rel):
                scoped.append((rel, literal))
            else:
                violations.append((rel, literal))
    return violations, scoped


def load_baseline(path: Path) -> set[tuple[str, str]]:
    """Read the pinned anchors. A missing file is an empty baseline."""
    if not path.exists():
        return set()
    anchors: set[tuple[str, str]] = set()
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "|" not in line:
            continue
        rel, literal = line.split("|", 1)
        anchors.add((rel.strip(), literal.strip()))
    return anchors


def check(root: Path = ROOT) -> int:
    violations, scoped = collect(root)
    baseline = load_baseline(BASELINE)

    observed = set(violations)
    new = sorted(observed - baseline)
    stale = sorted(baseline - observed)

    for scope in THEME_SCOPES:
        print(f"theme scope: {scope}")
    print(f"literals in theme scope: {len(scoped)}")
    print(f"violations outside theme scope: {len(violations)} ({len(baseline)} pinned)")
    print(f"NEW violations: {len(new)}")
    print(f"STALE anchors: {len(stale)}")

    failed = False

    if new:
        failed = True
        print()
        print("NEW design-token violations (not pinned in the baseline):")
        for rel, literal in new:
            print(f"  {rel} -> {literal}")
        print()
        print("Move the value into the theme package and consume the token, or pin it")
        print("in scripts/design_token_baseline.txt with a one-line reason.")

    if stale:
        failed = True
        print()
        print("STALE baseline anchors (the pinned literal is gone — re-review and remove):")
        for rel, literal in stale:
            print(f"  {rel} -> {literal}")

    if failed:
        return 1

    print()
    print("OK: no unpinned color or radius literals outside the theme package.")
    return 0


# --- self-test -----------------------------------------------------------------

_SELF_TEST_THEME = THEME_SCOPES[0] + "PaletteFixture.kt"


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def self_test(root: Path) -> int:
    """Prove the scanner, the scope and the baseline still behave."""
    import shutil
    import tempfile

    failures: list[str] = []

    def expect(condition: bool, message: str) -> None:
        if not condition:
            failures.append(message)

    tmp = Path(tempfile.mkdtemp(prefix="nexaflow-design-tokens-"))
    try:
        src = tmp / "feature" / "demo" / "src" / "main" / "java" / "demo"
        _write(src / "Screen.kt", "val c = Color(0xFF3B5BFF)\nval d = Color(12, 34, 56)\n")
        _write(src / "Other.kt", "val r = RoundedCornerShape(12.dp)\n")
        _write(tmp / (THEME_SCOPES[0] + "PaletteFixture.kt"), "val c = Color(0xFF3B5BFF)\n")
        _write(tmp / (THEME_SCOPES[1] + "AccentFixture.kt"), "val c = Color(0xFF00D6B4)\n")
        _write(tmp / "app" / "src" / "main" / "java" / "app" / "Main.kt", "val e = Color(0xFF00D6B4)\n")
        _write(
            tmp / "core" / "ui-components" / "src" / "test" / "java" / "T.kt",
            "val t = Color(0xFF000000)\n",
        )

        violations, scoped = collect(tmp)
        pairs = set(violations)
        scope_pairs = set(scoped)

        expect(
            ("feature/demo/src/main/java/demo/Screen.kt", "color:0xFF3B5BFF") in pairs,
            "hex color literal outside the theme scope was not reported",
        )
        expect(
            ("feature/demo/src/main/java/demo/Screen.kt", "color:rgb(12,34,56)") in pairs,
            "rgb color literal outside the theme scope was not reported",
        )
        expect(
            ("feature/demo/src/main/java/demo/Other.kt", "radius:12.dp") in pairs,
            "radius literal outside the theme scope was not reported",
        )
        expect(
            ("app/src/main/java/app/Main.kt", "color:0xFF00D6B4") in pairs,
            "app-module color literal outside the theme scope was not reported",
        )
        expect(
            (THEME_SCOPES[0] + "PaletteFixture.kt", "color:0xFF3B5BFF") in scope_pairs,
            "theme-scope literal was not classified as scoped",
        )
        expect(
            (THEME_SCOPES[1] + "AccentFixture.kt", "color:0xFF00D6B4") in scope_pairs,
            "app theme-scope literal was not classified as scoped",
        )
        expect(
            not any(rel.startswith("core/ui-components/src/test/") for rel, _ in pairs),
            "test sources must not be treated as production violations",
        )

        # The baseline suppresses exactly the anchors it lists, and a drifted
        # anchor is reported as stale.
        baseline_path = tmp / "scripts" / "design_token_baseline.txt"
        _write(
            baseline_path,
            "# pinned\n"
            "feature/demo/src/main/java/demo/Screen.kt|color:0xFF3B5BFF\n"
            "feature/demo/src/main/java/demo/Gone.kt|color:0xFF123456\n",
        )
        pinned = load_baseline(baseline_path)
        expect(
            pinned == {
                ("feature/demo/src/main/java/demo/Screen.kt", "color:0xFF3B5BFF"),
                ("feature/demo/src/main/java/demo/Gone.kt", "color:0xFF123456"),
            },
            "baseline loader produced the wrong anchor set",
        )
        observed = set(pairs)
        expect(
            (observed - pinned) != observed,
            "a pinned anchor must be suppressed by the baseline",
        )
        expect(
            ("feature/demo/src/main/java/demo/Gone.kt", "color:0xFF123456") in (pinned - observed),
            "a drifted anchor must be reported as stale",
        )

        # A clean tree must pass the gate.
        clean = Path(tempfile.mkdtemp(prefix="nexaflow-design-tokens-clean-"))
        try:
            _write(
                clean / "feature" / "demo" / "src" / "main" / "java" / "demo" / "Screen.kt",
                "val c = MaterialTheme.colorScheme.primary\n",
            )
            violations_clean, _ = collect(clean)
            expect(not violations_clean, "a token-driven source must not report a violation")
        finally:
            shutil.rmtree(clean, ignore_errors=True)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    if failures:
        print("SELF-TEST FAILED:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("SELF-TEST OK")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true", help="run the gate's self-test")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test(ROOT)
    return check(ROOT)


if __name__ == "__main__":
    raise SystemExit(main())
