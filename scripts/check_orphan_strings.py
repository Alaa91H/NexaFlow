"""Fail CI on unused string keys — cross-module reference aware.

Detects *orphaned* string keys: keys defined in any module's
`src/main/res/*/strings.xml` that no code or XML anywhere in the repo
references.

Cross-module by design: with AGP non-transitive R classes a module's R
contains its own resources plus its direct library dependencies', so e.g.
`feature/automations` legitimately resolves `R.string.action_*` keys that are
defined in `feature/automation-builder`. A per-module analysis would wrongly
flag those; this script collects references from the WHOLE tree (Kotlin/Java
`R.string|plurals|array.x` and XML `@string|plurals|array/x`) and only flags
keys with zero references anywhere.

A small explicit BANNED list is kept as a pin for the advanced-user feature
strings removed in 3.21.0 (execution channel, monitoring service, capability
center, ...): the reference analysis only catches a key that comes back
unreferenced, while BANNED also catches a full revival (string + code in the
same commit). Everything else is automatic — no manual maintenance.
"""
import glob
import re

# Explicit pins: removed advanced-user features. Never re-add, even with code.
BANNED = {
    # Execution channel (removed advanced picker)
    "execution_channel",
    "execution_channel_sub",
    "channel_detecting",
    "channel_none",
    # Monitoring service (removed advanced row)
    "monitoring_service",
    "monitoring_running",
    "monitoring_stopped",
    # Capability center (removed screen)
    "capability_center",
    "capability_center_sub",
    # Removed settings sections / categories
    "category_advanced",
    "section_integration",
    "section_services",
    # Removed builder hints / picker headings
    "pick_trigger_type",
    "tap_to_choose_icon",
    "exit_behavior_hint",
    "cooldown_hint",
    "cooldown_label",
    # Removed task-card / quick-tile extras
    "task_card_title",
    "tile_cancel",
}

KOTLIN_REF = re.compile(r"\bR\.(?:string|plurals|array)\.(\w+)")
XML_REF = re.compile(r"@(?:string|plurals|array)/(\w+)")
DEF = re.compile(r'name="([^"]+)"')


def norm(p: str) -> str:
    return p.replace("\\", "/")


def source_files() -> list:
    """All source files that can reference a string key (whole tree)."""
    found = []
    for pattern in ("**/src/**/*.kt", "**/src/**/*.java", "**/src/**/*.xml"):
        for p in glob.glob(pattern, recursive=True):
            p = norm(p)
            if "/build/" not in p:
                found.append(p)
    return found


def referenced_keys() -> set:
    used = set()
    for p in source_files():
        try:
            content = open(p, encoding="utf-8", errors="ignore").read()
        except OSError:
            continue
        for m in KOTLIN_REF.finditer(content):
            used.add(m.group(1))
        for m in XML_REF.finditer(content):
            used.add(m.group(1))
    return used


def definitions() -> dict:
    """key -> list of (path, module) defining it."""
    defs: dict = {}
    for p in glob.glob("**/src/main/res/*/strings.xml", recursive=True):
        p = norm(p)
        if "/build/" in p:
            continue
        module = p.split("/src/main/res")[0]
        try:
            content = open(p, encoding="utf-8").read()
        except OSError:
            continue
        for key in DEF.findall(content):
            defs.setdefault(key, []).append((p, module))
    return defs


def main() -> int:
    problems = 0

    # 1) Explicit pins (removed advanced-user features).
    for p in glob.glob("**/src/main/res/*/strings.xml", recursive=True):
        p = norm(p)
        if "/build/" in p:
            continue
        try:
            content = open(p, encoding="utf-8").read()
        except OSError:
            continue
        for key in DEF.findall(content):
            if key in BANNED:
                problems += 1
                print(f"FORBIDDEN_STRING: {key} in {p}")

    # 2) Cross-module reference analysis — any defined key with zero
    #    references anywhere in the repo is an orphan.
    used = referenced_keys()
    for key, locations in sorted(definitions().items()):
        if key in used:
            continue
        problems += 1
        modules = ", ".join(sorted({m for _, m in locations}))
        print(f"ORPHAN_STRING: {key} (defined in {modules})")

    if problems:
        print(
            "ORPHAN_STRINGS_FOUND:",
            problems,
            "- every string key must be referenced somewhere; delete orphan",
            "keys from every locale (or, for a deliberate unused resource,",
            "see scripts/check_orphan_strings.py).",
        )
    else:
        print("ORPHAN_STRINGS_FOUND: 0")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
