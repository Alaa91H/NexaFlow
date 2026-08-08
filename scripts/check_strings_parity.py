#!/usr/bin/env python3
"""CI gate: every values-<locale>/strings.xml must contain exactly the same
keys as values/strings.xml.

A missing or extra key in a translation file either breaks aapt at build time
(e.g. an apostrophe escaping issue) or silently renders a fallback string for
that locale. Either way it is a drift the build gate should catch before
pushing. Run from the repo root:

    python3 scripts/check_strings_parity.py            # check all modules
    python3 scripts/check_strings_parity.py <res-dir>  # check one module

Exit code 0 = all locales in parity; 1 = drift found (CI fails).
"""
import sys
import glob
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def keys_of(path: Path) -> set[str]:
    """Extract the set of <string name="..."> keys from a strings.xml."""
    tree = ET.parse(path)
    root = tree.getroot()
    return {item.get("name") for item in root if item.tag == "string"}


def apostrophe_issues(path: Path) -> list[str]:
    """Flag raw apostrophes inside string values (aapt build breakers).

    Android requires apostrophes inside string values to be escaped (\\') or
    the whole value wrapped in double quotes. A raw apostrophe is a hard aapt2
    failure at build time — exactly the class of break the Turkish/French
    drift caused. Parse every <string> element text and flag `'` characters
    that sit inside a value and are not part of an entity or escaped form.
    """
    tree = ET.parse(path)
    root = tree.getroot()
    issues: list[str] = []
    for item in root:
        if item.tag != "string":
            continue
        name = item.get("name", "?")
        # ET unescapes entities; \\' inside the XML becomes \\' here, so a
        # "raw" apostrophe is one not preceded by a backslash.
        text = item.text or ""
        for i, ch in enumerate(text):
            if ch == "'" and (i == 0 or text[i - 1] != "\\"):
                # Values wrapped in double quotes are legal with raw apostrophes.
                if text.startswith('"') and text.endswith('"'):
                    break
                issues.append(f"raw apostrophe in '{name}': {text[:40]!r}")
                break
    return issues


def check_res_dir(res_dir: Path) -> list[str]:
    """Compare every values-*/strings.xml against values/strings.xml."""
    base = res_dir / "values" / "strings.xml"
    if not base.exists():
        return []  # not a resource module
    expected = keys_of(base)
    errors: list[str] = []
    for strings_path in sorted(glob.glob(str(res_dir / "values" / "strings.xml"))) + sorted(
        glob.glob(str(res_dir / "values-*" / "strings.xml"))
    ):
        path = Path(strings_path)
        rel = path.relative_to(ROOT)
        # 1) Key parity against the default locale.
        if path.parent.name != "values":
            actual = keys_of(path)
            missing = expected - actual
            extra = actual - expected
            if missing:
                errors.append(
                    f"{rel}: missing {len(missing)} key(s): "
                    + ", ".join(sorted(missing)[:8])
                    + ("…" if len(missing) > 8 else "")
                )
            if extra:
                errors.append(
                    f"{rel}: extra {len(extra)} key(s): "
                    + ", ".join(sorted(extra)[:8])
                    + ("…" if len(extra) > 8 else "")
                )
        # 2) Raw apostrophes that would break aapt2 (all locales incl. default).
        for issue in apostrophe_issues(path):
            errors.append(f"{rel}: {issue}")
    return errors


def main() -> int:
    targets = sys.argv[1:] or ["**"]
    failures: list[str] = []
    checked = 0
    for target in targets:
        explicit = Path(target)
        if (explicit / "values" / "strings.xml").exists():
            # Explicit module res dir, e.g. feature/automation-builder/src/main/res
            res_dirs = [explicit]
        else:
            res_dirs = [
                Path(p).parent
                for p in glob.glob(str(ROOT / target / "src" / "main" / "res"), recursive=True)
            ]
        for res_dir in res_dirs:
            res_dir = res_dir.resolve()
            if not res_dir.exists():
                continue
            errors = check_res_dir(res_dir)
            checked += 1
            failures.extend(errors)

    print(f"Checked {checked} resource module(s).")
    if failures:
        print(f"FAIL: {len(failures)} string-parity issue(s):")
        for line in failures:
            print("  - " + line)
        return 1
    print("OK: every values-*/strings.xml matches values/strings.xml keys.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
