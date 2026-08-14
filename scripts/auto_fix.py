#!/usr/bin/env python3
"""Auto-fix resource hygiene across every module's locale files.

Designed to run BEFORE the unified gate in CI (scripts/check_resources.py) so
the tree is already clean when the reference analysis and lint reports are
evaluated. Works on every module's `res/values*/strings.xml`.

Operations, applied to every module:

  1. ORPHANS    - keys defined anywhere but referenced nowhere in source are
                  deleted from EVERY file that defines them. Uses the exact
                  same cross-module reference analysis as check_resources.py
                  (imported, not reimplemented) so the fixer removes precisely
                  what the gate would flag - nothing more, nothing less.
                  Covers text keys (string/plurals/arrays in strings.xml) AND
                  color/dimen/style keys in colors.xml / dimens.xml /
                  styles.xml / themes.xml, including qualifier files
                  (values-night, values-v31, ...) and locale files
                  (values-ar, ...).
  2. DUPLICATES - a key defined twice in the same file keeps its first
                  occurrence; later ones are deleted. (AAPT2 normally rejects
                  in-file duplicates, so this is a safety net for generated or
                  hand-merged files.)
  3. BALANCE    - each locale's key set is aligned with the module's default
                  `values/strings.xml`: keys the locale has but the default
                  lacks (extra translations) are deleted, and keys the default
                  has but the locale lacks are inserted with the default
                  value, preceded by a `TODO(translate)` comment so they are
                  easy to find and translate later. This keeps
                  check_strings_parity.py green by construction.

Editing is element-aware (string / plurals / string-array / integer-array /
standalone <item name=...>) and preserves comments, formatting, CDATA and
line endings - only the offending elements are removed or inserted.

Modes:
  (default)     apply fixes in place; exit 0.
  --check       dry run: print every change without touching files; exit 1 if
                any change is needed. CI uses this - the tree must already be
                auto-fix-clean before the gate runs.
  --no-backfill balance performs deletions only; never inserts missing keys.
"""
from __future__ import annotations

import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import check_resources as gate  # noqa: E402  (exact gate semantics, reused)

ELEM = re.compile(
    r"<(?P<tag>string|plurals|string-array|integer-array|item|color|dimen|style)\b"
    r"(?P<attrs>[^>]*?)(?:>(?P<body>.*?)</(?P=tag)>|/>)",
    re.DOTALL,
)
NAME_ATTR = re.compile(r'\bname="([^"]+)"')
# A bare <item name=...> inside a <style> names a style attribute, NOT a
# resource key; only <item type="color"|type="dimen"> define resources.
ITEM_TYPE_ATTR = re.compile(r'\btype="(color|dimen)"', re.IGNORECASE)

FILL_COMMENT = "    <!-- TODO(translate): auto-filled by scripts/auto_fix.py -->"
FILL_INDENT = "    "


def eol_of(content: str) -> str:
    return "\r\n" if "\r\n" in content else "\n"


def keyed_elements(content: str) -> list:
    """(start, end, key) for every element that DEFINES a resource key.

    string/plurals/string-array/integer-array/color/dimen/style always count;
    bare `<item>` only when it carries `type="color"` or `type="dimen"`
    (style attributes named via <item name=...> inside a <style> are NOT
    resources)."""
    out = []
    for m in ELEM.finditer(content):
        tag = m.group("tag")
        attrs = m.group("attrs")
        if tag == "item" and not ITEM_TYPE_ATTR.search(attrs):
            continue
        nm = NAME_ATTR.search(attrs)
        if nm:
            out.append((m.start(), m.end(), nm.group(1)))
    return out


def default_element(default_content: str, key: str) -> str | None:
    """Raw text of the default file's element defining `key`, if present."""
    for start, end, k in keyed_elements(default_content):
        if k == key:
            return default_content[start:end].strip()
    return None


def expand_span(content: str, start: int, end: int) -> tuple:
    """Widen a removal span to the element's whole line: consume the leading
    indentation (same line) and the trailing line break, so removals do not
    leave indentation-only residue lines behind."""
    s = start
    while s > 0 and content[s - 1] in " \t":
        s -= 1
    e = end
    if content[e : e + 2] == "\r\n":
        e += 2
    elif e < len(content) and content[e] == "\n":
        e += 1
    return s, e


def rewrite(content: str, drop: set, backfill: list) -> tuple:
    """Remove `drop` keys (and later duplicates), insert `backfill` entries.

    backfill: list of (key, default_element_text) to insert before </resources>.
    Returns (new_content, removed_count, added_count).
    """
    spans, seen = [], set()
    for start, end, key in keyed_elements(content):
        if key in drop:
            spans.append(expand_span(content, start, end))
        elif key in seen:
            spans.append(expand_span(content, start, end))
        else:
            seen.add(key)
    removed = len(spans)
    parts, last = [], 0
    for start, end in sorted(spans):
        parts.append(content[last:start])
        last = end
    parts.append(content[last:])
    rebuilt = "".join(parts)

    added = 0
    if backfill:
        eol = eol_of(rebuilt)
        idx = rebuilt.rfind("</resources>")
        if idx != -1:
            inserts = []
            for key, elem in backfill:
                inserts.append(eol + FILL_COMMENT + eol + FILL_INDENT + elem + eol)
            rebuilt = rebuilt[:idx] + "".join(inserts) + rebuilt[idx:]
            added = len(backfill)
    return rebuilt, removed, added


VALUE_XML = ("colors.xml", "dimens.xml", "styles.xml", "themes.xml")


def module_files() -> dict:
    """module -> {"default": path, "locales": [path, ...], "values": [path, ...]}
    (sorted). `values` holds every non-string value file (colors/dimens/
    styles/themes), including qualifier variants like values-night."""
    mods: dict = {}
    for p in glob.glob("**/src/main/res/values*/strings.xml", recursive=True):
        p = gate.norm(p)
        if "/build/" in p:
            continue
        mod = gate.module_of(p)
        entry = mods.setdefault(mod, {"default": None, "locales": [], "values": []})
        if p.endswith("/values/strings.xml"):
            entry["default"] = p
        else:
            entry["locales"].append(p)
    for name in VALUE_XML:
        for p in glob.glob(f"**/src/main/res/values*/{name}", recursive=True):
            p = gate.norm(p)
            if "/build/" in p:
                continue
            mod = gate.module_of(p)
            entry = mods.setdefault(mod, {"default": None, "locales": [], "values": []})
            entry["values"].append(p)
    for entry in mods.values():
        entry["locales"].sort()
        entry["values"].sort()
    return mods


def orphans_by_module(used: set) -> dict:
    """module -> set of orphaned text keys (gate-identical analysis)."""
    orphans: dict = {}
    for (rtype, key), locations in gate.definitions().items():
        if rtype not in gate.STRING_TYPES or (rtype, key) in used:
            continue
        for _path, mod in locations:
            orphans.setdefault(mod, set()).add(key)
    return orphans


def orphans_by_value_path(used: set) -> dict:
    """path -> set of orphaned color/dimen/style keys defined in that file
    (every defining file is listed, so each gets its own cleanup)."""
    orphans: dict = {}
    for (rtype, key), locations in gate.definitions().items():
        if rtype not in ("color", "dimen", "style") or (rtype, key) in used:
            continue
        for path, _mod in locations:
            orphans.setdefault(path, set()).add(key)
    return orphans


def build_state() -> tuple:
    """Compute the (expensive) reference analysis + module map ONCE."""
    mods = module_files()
    used = gate.referenced_keys()
    orphans = orphans_by_module(used)
    value_orphans = orphans_by_value_path(used)
    defaults = {}
    for mod, entry in mods.items():
        if entry["default"] and os.path.isfile(entry["default"]):
            with open(entry["default"], encoding="utf-8", newline="") as f:
                defaults[mod] = f.read()
    return mods, orphans, defaults, value_orphans


def plan(mods: dict, orphans: dict, defaults: dict, value_orphans: dict) -> list:
    """List of (path, action, key) changes; empty when the tree is clean."""
    changes: list = []
    for mod, entry in mods.items():
        dpath = entry["default"]
        default_content = defaults.get(mod)
        # Value files: drop orphaned color/dimen/style keys only (no
        # backfill - a qualifier like values-night is an intentional
        # override, not a translation). Runs regardless of whether the
        # module has a default strings.xml.
        for vpath in entry["values"]:
            changes.extend(
                (vpath, "remove-orphan", k)
                for k in sorted(value_orphans.get(vpath, set()))
            )
        if not dpath or default_content is None:
            continue
        default_keys = set(gate.DEF.findall(default_content))
        drop = orphans.get(mod, set())
        # Default file: drop orphans + duplicates only.
        changes.extend((dpath, "remove-orphan", k) for k in sorted(drop))
        for lpath in entry["locales"]:
            with open(lpath, encoding="utf-8", newline="") as f:
                content = f.read()
            loc_keys = set(gate.DEF.findall(content))
            extra = loc_keys - default_keys
            missing = default_keys - loc_keys
            changes.extend((lpath, "remove-orphan", k) for k in sorted(drop & loc_keys))
            changes.extend((lpath, "remove-extra", k) for k in sorted(extra))
            changes.extend((lpath, "backfill", k) for k in sorted(missing))
    return changes


def main() -> int:
    check = "--check" in sys.argv
    no_backfill = "--no-backfill" in sys.argv
    mods, orphans, defaults, value_orphans = build_state()
    changes = plan(mods, orphans, defaults, value_orphans)

    if check:
        if not changes:
            print("AUTO_FIX: OK - no orphaned, duplicate, or unbalanced strings.")
            return 0
        for path, action, key in changes:
            print(f"{action}: {key} in {gate.norm(path)}")
        print(f"AUTO_FIX: {len(changes)} change(s) needed - run `python3 "
              "scripts/auto_fix.py` and commit the result.")
        return 1

    # Apply mode visits EVERY string file (default + every locale) so orphan
    # removal, extras removal, dedup and backfill all run unconditionally -
    # a file whose only defect is a duplicate must still be rewritten.
    applied = 0
    for mod, entry in mods.items():
        # Value files: drop orphaned color/dimen/style keys, no backfill.
        # Runs regardless of whether the module has a default strings.xml.
        for vpath in entry["values"]:
            with open(vpath, encoding="utf-8", newline="") as f:
                content = f.read()
            rebuilt, removed, added = rewrite(
                content, value_orphans.get(vpath, set()), []
            )
            if rebuilt != content:
                with open(vpath, "w", encoding="utf-8", newline="") as f:
                    f.write(rebuilt)
                applied += 1
                print(f"fixed {gate.norm(vpath)} (-{removed} +{added})")
        dpath = entry["default"]
        default_content = defaults.get(mod)
        if not dpath or default_content is None:
            continue
        default_keys = set(gate.DEF.findall(default_content))
        mod_orphans = orphans.get(mod, set())
        for path in [dpath] + entry["locales"]:
            with open(path, encoding="utf-8", newline="") as f:
                content = f.read()
            loc_keys = set(gate.DEF.findall(content))
            # Drop orphans everywhere; drop locale-only extras; never drop a
            # default key from the default file (extras set is empty there).
            drop = mod_orphans | (loc_keys - default_keys)
            backfill = []
            if not no_backfill and path != dpath:
                # Backfill only keys genuinely absent here and not being
                # dropped (the default snapshot may still list orphans).
                for key in sorted((default_keys - loc_keys) - drop):
                    elem = default_element(default_content, key)
                    if elem:
                        backfill.append((key, elem))
            rebuilt, removed, added = rewrite(content, drop, backfill)
            if rebuilt != content:
                with open(path, "w", encoding="utf-8", newline="") as f:
                    f.write(rebuilt)
                applied += 1
                print(f"fixed {gate.norm(path)} (-{removed} +{added})")
    if not applied:
        print("AUTO_FIX: nothing to do - tree is already clean.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
