#!/usr/bin/env python3
"""Micro-audit of translation completeness across every module and locale.

The existing check_strings_parity.py only proves that every locale file has
the same KEY NAMES as the default (English) file. It is blind to VALUES, so
a locale file can ship an exact English copy and pass CI forever. This is
the class of defect behind user reports of "part of the app is not
translated".

Layers audited here (per module, per locale: ar de es fr hi ja pt ru tr
zh-rCN):

  1. KEY PARITY        - locale keys == default keys (defense in depth)
  2. UNTRANSLATED COPY - locale value identical to the English value while
                         still containing Latin words (an obvious English
                         copy). Brand names / units / symbols that are
                         legitimately identical everywhere are pinned in
                         IDENTICAL_OK (exact strings only).
  3. SCRIPT LEAKAGE    - a file carrying the script of a DIFFERENT language
                         (e.g. Arabic glyphs inside the German file, or any
                         non-English script inside the default file) - the
                         signature of copy-paste between files.
  4. PLACEHOLDERS      - the multiset of format specifiers (%1$s, %s, %d,
                         %%, ...) must match the default exactly, or the
                         locale crashes or leaks at runtime.
  5. EMPTY VALUES      - translations that are blank or whitespace.

Exit code 0 only when every layer is clean. --self-test re-runs the
detector on known-bad fixtures and must stay green in CI.
"""
from __future__ import annotations

import argparse
import glob
import re
import sys
from collections import Counter
from pathlib import Path
from xml.etree import ElementTree

try:  # Windows consoles default to cp1252; keep reports readable.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # pragma: no cover
    pass

LOCALES = ("ar", "de", "es", "fr", "hi", "ja", "pt", "ru", "tr", "zh-rCN")

# Script ranges used for leakage detection. Languages without a distinct
# script (de/es/fr/pt/tr) are detected via layer 2 instead.
SCRIPT_RANGES = {
    "ar": (0x0600, 0x06FF),      # Arabic
    "ru": (0x0400, 0x04FF),      # Cyrillic
    "hi": (0x0900, 0x097F),      # Devanagari
    "ja": (0x3040, 0x30FF),      # Kana (hiragana + katakana)
    "zh-rCN": (0x4E00, 0x9FFF),  # CJK unified
}

LATIN_WORD_RE = re.compile(r"[A-Za-z]{2,}")
PLACEHOLDER_RE = re.compile(r"%(\d+\$)?[sdf]|%n\$|%%")

# Exact values that are legitimately identical to English in every locale
# (brands, technical terms, universal UI tokens). Extend ONLY with the full
# literal string; prefix/suffix games would hide real defects.
IDENTICAL_OK = {
    "NexaFlow",
    "Wi-Fi",
    "Bluetooth",
    "NFC",
    "USB",
    "GPS",
    "SIM",
    "PIN",
    "APK",
    "OK",
    "Shizuku",
    "GitHub",
    "NFC Tag",
    "Wi-Fi Direct",
    # Contact handles and literal examples (never localized).
    "@Alaa91H",
    "@Alaa91h",
    "alahus2591@gmail.com",
    "https://example.com",
    '{"key": "value"}',
    # Technical acronyms/terms identical in every supported language.
    "ADB",
    "URL",
    "VPN",
    "SMS",          # universal; ar/zh carry native forms already
    "SIM %1$d",
    "Android API",  # standard term in ja/ru/tr/zh developer surfaces
    # Product/feature names (brands or Android feature names kept in Latin
    # script by convention in all these locales).
    "Galaxy Store",
    "Telegram",
    "Tasker / Locale",
    "Root",         # standard Android term in every locale (ar: روت native)
    "Webhook",      # de/es/fr/hi/ja/pt/tr keep it; ar/ru/zh native already
    "Heads-up",
    "Heads-up, less boring, timeout",
    "QS Tiles",
    "Ambient & AOD",
    "Ambient / AOD",
}

# Words that ARE the correct word in a specific locale (same spelling as
# English), so an identical value there is a valid translation. Keyed by
# locale; every entry was verified as the real native word.
PER_LOCALE_IDENTICAL_OK: dict[str, set[str]] = {
    "de": {
        "App", "Apps", "Hotspot", "Minute", "Name", "Navigation", "Orange",
        "Pink", "Minimal", "Start", "System", "Sensor", "Text", "Update",
        "Updates", "Version %1$s", "Global", "Secure", "Timeout",
        "Screenshot", "Wearables", "Plugins", "ms", "Maximum: %1$d%%",
        "Radius: %1$d m",
    },
    "es": {
        "Color", "General", "Manual", "Global", "Total", "Variables",
        "Sensor", "ms", "%1$d min", "1 min", "5 min", "10 min",
        "Base: %1$d ms",
    },
    "fr": {
        "Action", "Actions (%1$d)", "Animations", "Communication",
        "Condition", "Conditions", "Contact", "Contacts", "Description",
        "Direction", "Global", "Latitude", "Longitude", "Message",
        "Minute", "Minutes", "Mode", "Navigation", "Notification",
        "Notifications", "Orange", "Portrait", "Total", "Version %1$s",
        "Volume", "Volume %1$d", "occurrences", "via %1$s", "ms",
        "%1$d min", "1 min", "5 min", "10 min",
    },
    "pt": {
        "Manual", "Global", "Total", "Latitude", "Longitude", "Namespace",
        "Volume", "Volume: %1$d", "Volume %1$d", "via %1$s", "ms",
        "%1$d min", "1 min", "5 min", "10 min", "Base: %1$d ms", "Sensor",
    },
    "tr": {
        "Alarm", "Minimal", "Test", "ms",
    },
}

# (module, key) pairs whose untranslated/script-leak copy is reviewed and
# legitimate (e.g. native language names inside a language picker, or the
# app name rendered in its own script). Every entry needs a reason.
REVIEWED = {
    # ("app", "language_name_ar"): "native name of Arabic in the picker",
}

# (module, key) pairs where a non-English script inside the DEFAULT file is
# reviewed (native language names, script samples).
DEFAULT_SCRIPT_OK = {
    # ("app", "language_name_ar"): "native language name",
}


def has_script(text: str, script: str) -> bool:
    lo, hi = SCRIPT_RANGES[script]
    return any(lo <= ord(ch) <= hi for ch in text)


def placeholder_bag(text: str) -> Counter:
    return Counter(m.group(0) for m in PLACEHOLDER_RE.finditer(text))


def parse_strings(path: Path) -> dict[str, str]:
    """name -> text for <string> and <string-array> elements."""
    out: dict[str, str] = {}
    try:
        root = ElementTree.parse(path).getroot()
    except ElementTree.ParseError:
        return out
    for el in root.iter("string"):
        name = el.get("name")
        if name is not None:
            out[name] = "".join(el.itertext())
    for el in root.iter("string-array"):
        name = el.get("name")
        if name is not None:
            items = ["".join(item.itertext()) for item in el.iter("item")]
            out[f"array:{name}"] = " | ".join(items)
    return out


def norm(p: str) -> str:
    return p.replace("\\", "/")


def module_files() -> dict[str, dict[str, Path]]:
    modules: dict[str, dict[str, Path]] = {}
    for p in glob.glob("**/src/main/res/values*/strings.xml", recursive=True):
        p = norm(p)
        if "/build/" in p or "/test/" in p or "/test-fixtures/" in p:
            continue
        mod = p.split("/src/main/res")[0]
        if p.endswith("/values/strings.xml"):
            modules.setdefault(mod, {})["default"] = Path(p)
        else:
            loc = p.split("/values-")[1].split("/")[0]
            modules.setdefault(mod, {})[loc] = Path(p)
    return modules


def audit() -> list[str]:
    problems: list[str] = []
    for mod, files in sorted(module_files().items()):
        default = files.get("default")
        if default is None:
            continue
        base = parse_strings(default)

        # Layer 3 on the default file itself: no non-English script allowed
        # unless reviewed (the hardcoded-text gate only covers Kotlin code).
        for key, value in base.items():
            for script in SCRIPT_RANGES:
                if has_script(value, script) and (mod, key) not in DEFAULT_SCRIPT_OK:
                    problems.append(
                        f"{mod} [default] {key}: non-English script "
                        f"({script}) in the English default: {value[:60]!r}"
                    )

        for loc in LOCALES:
            path = files.get(loc)
            if path is None:
                continue  # parity gate reports the missing file
            loc_map = parse_strings(path)

            # Layer 1: key parity (defense in depth).
            missing = set(base) - set(loc_map)
            extra = set(loc_map) - set(base)
            if missing:
                problems.append(f"{mod} [{loc}] missing {len(missing)} keys: {sorted(missing)[:6]}")
            if extra:
                problems.append(f"{mod} [{loc}] extra keys: {sorted(extra)[:6]}")

            for key, value in loc_map.items():
                if key not in base:
                    continue
                base_value = base[key]

                # Layer 5: empty translation.
                if not value.strip():
                    problems.append(f"{mod} [{loc}] {key}: empty translation")
                    continue

                # Layer 2: untranslated English copy.
                if (
                    value == base_value
                    and LATIN_WORD_RE.search(base_value)
                    and value not in IDENTICAL_OK
                    and value not in PER_LOCALE_IDENTICAL_OK.get(loc, set())
                    and (mod, key) not in REVIEWED
                ):
                    problems.append(
                        f"{mod} [{loc}] {key}: untranslated English copy: {value[:60]!r}"
                    )

                # Layer 3: foreign script inside a locale file.
                own = script = None
                for s in SCRIPT_RANGES:
                    if has_script(value, s):
                        if s == loc:
                            own = s
                        else:
                            script = s
                if script is not None and (mod, key) not in REVIEWED:
                    # Kana inside a zh file means Japanese text; the reverse
                    # (hanzi inside ja) is normal kanji usage, so the CJK
                    # range is only a leak signal when found with kana rules.
                    if not (loc == "ja" and script == "zh-rCN"):
                        problems.append(
                            f"{mod} [{loc}] {key}: foreign script ({script}) "
                            f"in the {loc} file: {value[:60]!r}"
                        )

                # Layer 4: placeholder multiset must match the default.
                if placeholder_bag(value) != placeholder_bag(base_value):
                    problems.append(
                        f"{mod} [{loc}] {key}: placeholder mismatch "
                        f"{dict(placeholder_bag(value))} != {dict(placeholder_bag(base_value))}"
                    )
    return problems


# --------------------------------------------------------------------------
# Self-test fixtures: the detector must flag every defect class it claims
# to catch, and stay silent on legitimate cases.
# --------------------------------------------------------------------------

FIXTURE_DEFAULT = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NexaFlow</string>
    <string name="enable">Enable</string>
    <string name="greeting">Hello, %1$s!</string>
    <string name="battery">Battery: %d%%</string>
    <string name="ok">OK</string>
    <string name="nfc">NFC</string>
</resources>
"""

FIXTURE_AR_GOOD = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NexaFlow</string>
    <string name="enable">تفعيل</string>
    <string name="greeting">مرحبًا يا %1$s!</string>
    <string name="battery">البطارية: %d%%</string>
    <string name="ok">OK</string>
    <string name="nfc">NFC</string>
</resources>
"""

# Untranslated copy of "Enable" + identical OK/NFC allowed.
FIXTURE_AR_COPY = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NexaFlow</string>
    <string name="enable">Enable</string>
    <string name="greeting">مرحبًا يا %1$s!</string>
    <string name="battery">البطارية: %d%%</string>
    <string name="ok">OK</string>
    <string name="nfc">NFC</string>
</resources>
"""

# Arabic glyphs inside the German file.
FIXTURE_DE_LEAK = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NexaFlow</string>
    <string name="enable">Aktivieren</string>
    <string name="greeting">Hallo %1$s!</string>
    <string name="battery">Akku: %d%%</string>
    <string name="ok">تفعيل</string>
    <string name="nfc">NFC</string>
</resources>
"""

# Missing a positional placeholder -> runtime leak.
FIXTURE_AR_PH = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NexaFlow</string>
    <string name="enable">تفعيل</string>
    <string name="greeting">مرحبًا!</string>
    <string name="battery">البطارية: %d%%</string>
    <string name="ok">OK</string>
    <string name="nfc">NFC</string>
</resources>
"""

# Blank translation.
FIXTURE_AR_EMPTY = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NexaFlow</string>
    <string name="enable"></string>
    <string name="greeting">مرحبًا يا %1$s!</string>
    <string name="battery">البطارية: %d%%</string>
    <string name="ok">OK</string>
    <string name="nfc">NFC</string>
</resources>
"""


def _run_on(tmpdir: str, default: str, ar: str | None, de: str | None) -> list[str]:
    root = Path(tmpdir) / "mod" / "src" / "main" / "res"
    (root / "values").mkdir(parents=True)
    (root / "values" / "strings.xml").write_text(default, encoding="utf-8")
    if ar is not None:
        (root / "values-ar").mkdir()
        (root / "values-ar" / "strings.xml").write_text(ar, encoding="utf-8")
    if de is not None:
        (root / "values-de").mkdir()
        (root / "values-de" / "strings.xml").write_text(de, encoding="utf-8")
    import os

    old = os.getcwd()
    os.chdir(tmpdir)
    try:
        return audit()
    finally:
        os.chdir(old)


def self_test() -> int:
    import tempfile

    failures: list[str] = []

    with tempfile.TemporaryDirectory() as d:
        p = _run_on(d, FIXTURE_DEFAULT, FIXTURE_AR_GOOD, FIXTURE_DE_LEAK)
        # de leak flagged; ar good file must be fully silent; OK/NFC allowed.
        if not any("[de]" in x and "foreign script" in x for x in p):
            failures.append(f"de leak not detected: {p}")
        if any("[ar]" in x for x in p):
            failures.append(f"good ar file flagged: {p}")

    with tempfile.TemporaryDirectory() as d:
        p = _run_on(d, FIXTURE_DEFAULT, FIXTURE_AR_COPY, None)
        if not any("[ar]" in x and "untranslated" in x for x in p):
            failures.append(f"untranslated copy not detected: {p}")

    with tempfile.TemporaryDirectory() as d:
        p = _run_on(d, FIXTURE_DEFAULT, FIXTURE_AR_PH, None)
        if not any("[ar]" in x and "placeholder mismatch" in x for x in p):
            failures.append(f"placeholder mismatch not detected: {p}")

    with tempfile.TemporaryDirectory() as d:
        p = _run_on(d, FIXTURE_DEFAULT, FIXTURE_AR_EMPTY, None)
        if not any("[ar]" in x and "empty translation" in x for x in p):
            failures.append(f"empty translation not detected: {p}")

    if failures:
        print("SELF-TEST FAILURES:")
        for f in failures:
            print(" -", f)
        return 1
    print("SELF-TEST OK: all defect classes detected, clean fixtures silent")
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true", help="run fixture tests and exit")
    args = parser.parse_args()
    if args.self_test:
        raise SystemExit(self_test())

    problems = audit()
    print(f"TRANSLATION_PROBLEMS: {len(problems)}")
    for p in problems:
        print(" -", p)
    raise SystemExit(1 if problems else 0)


if __name__ == "__main__":
    main()
