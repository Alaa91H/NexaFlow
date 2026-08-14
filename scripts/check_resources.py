"""Unified CI resource gate — one entry point, four complementary checks.

1. BANNED keys      — explicit pins for advanced-user feature strings removed in
                      3.21.0 (execution channel, monitoring service, capability
                      center, ...). Never re-add, even together with code: the
                      reference analysis below only catches a key that returns
                      *unreferenced*, while BANNED also catches a full revival.
2. Orphan detection — cross-module reference analysis covering TEXT keys
                      (strings/plurals/arrays) AND FILE keys (drawables,
                      layouts, dimen, mipmaps). Any key defined in a module's
                      `src/main/res/` that no code or XML anywhere in the repo
                      references is an orphan.
                      Cross-module by design: with AGP non-transitive R classes a
                      module's R contains its own resources plus its direct
                      library dependencies', so e.g. `feature/automations`
                      legitimately resolves `R.string.action_*` keys defined in
                      `feature/automation-builder`. This script collects
                      references from the WHOLE tree and only flags keys with
                      zero references anywhere.
                      File keys are matched by name (basename minus extension,
                      handling `.9.png` nine-patches) against `R.drawable.*`,
                      `R.layout.*`, `R.mipmap.*` in Kotlin/Java and `@drawable/*`,
                      `@layout/*`, `@mipmap/*` in XML; dimen keys come from
                      `name=` attributes inside `values*/dimens.xml` matched
                      against                      `R.dimen.*` / `@dimen/*`; color keys come from `name=`
                      inside `<color>` (or `<item type="color">`) in
                      `values*/colors.xml` matched against `R.color.*` /
                      `@color/*`; style/theme keys come from `<style>` in
                      `values*/styles.xml` and `values*/themes.xml` matched
                      against `R.style.*` / `@style/*` (dot-form, R-field
                      underscore-form, and bare `parent="Name"` inheritance
                      all count). Resource files that reference each other
                      (e.g. an adaptive icon XML pointing at
                      `@drawable/ic_launcher_foreground`, a layout `<include>`,
                      a theme's `@color/...` item) count as references, so
                      only truly dead resources are flagged.
3. Lint reporting   — parses every module's `lint-results-debug.xml` for the
                      zero-tolerance issues: `UnusedResources`,
                      `MissingTranslation`, `ExtraTranslation`, `CheckResult`,
                      `HardcodedText` and `TypographyDashes`. Lint severity is
                      already `error` for all six (root lint.xml wired via the
                      build.gradle.kts convention), so `./gradlew lintDebug`
                      fails on its own; this pass makes the failure explicit and
                      readable, and still catches a mis-wired module.
4. Lint config pins — verifies every `lint.xml` in the tree (root + any
                      module files). The root `lint.xml` must still pin every
                      zero-tolerance issue id as `severity=error` (the six lint
                      issues above plus `UnusedIds`); module-level `lint.xml`
                      files may omit pins (they inherit the root config via the
                      build convention) but must NEVER silence one — neither
                      `severity="ignore"` nor a nested `<ignore path=.../>`
                      element — so a future edit that relaxes or bypasses the
                      config in any module fails the gate even before a lint
                      run.

Run this AFTER `./gradlew lintDebug` (check 3 needs the lint XML reports).
Exits non-zero if any check finds a problem.
"""
import glob
import os
import re

# --- Check 1: explicit pins (removed advanced-user features) -----------------
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

# --- Check 2: reference-based orphan detection --------------------------------
# Text keys (name= inside values files), file keys (drawable/layout/mipmap
# basenames), dimen keys, color keys and style/theme keys. Each match carries
# (rtype, name). Style names may contain dots (Theme.NexaFlow.Starting); XML
# references keep them, Kotlin R.style fields replace dots with underscores -
# the style branch normalizes both forms.
KOTLIN_REF = re.compile(
    r"\bR\.(string|plurals|array|drawable|layout|dimen|mipmap|color|style)\.(\w+)"
)
XML_REF = re.compile(
    r"@(string|plurals|array|drawable|layout|dimen|mipmap|color|style)/"
    r"([A-Za-z0-9_.]+)"
)
DEF = re.compile(r'name="([^"]+)"')
# Matches a <dimen> or <item> opening tag so dimen keys can be filtered from
# items that are not type="dimen" (colors, integers, ...).
DIMEN_TAG = re.compile(r"<(dimen|item)\b([^>]*)>")
# Matches a <color> or <item type="color"> opening tag for color keys.
COLOR_TAG = re.compile(r"<(color|item)\b([^>]*)>")
# Matches a <style> opening tag for style/theme keys.
STYLE_TAG = re.compile(r"<style\b([^>]*)>")
# Bare style parent references (parent="Name" without @): a defined style that
# is only consumed as a parent of another style is still referenced.
PARENT_ATTR = re.compile(r'\bparent="([^"]+)"')

STRING_TYPES = ("string", "plurals", "array")
FILE_RES_TYPES = ("drawable", "layout", "mipmap")

# --- Check 3: lint zero-tolerance issue reporting -----------------------------
# Issue ids the unified gate treats as errors, with the report label used for
# each. Matches an issue element's attrs (multiline-safe: the id may be on its
# own line inside the element).
LINT_ISSUES = {
    "UnusedResources": "UNUSED_RESOURCES_FOUND",
    "MissingTranslation": "MISSING_TRANSLATIONS_FOUND",
    "ExtraTranslation": "EXTRA_TRANSLATIONS_FOUND",
    "CheckResult": "CHECK_RESULTS_FOUND",
    "HardcodedText": "HARDCODED_TEXTS_FOUND",
    "TypographyDashes": "TYPOGRAPHY_DASHES_FOUND",
}
# Matches a full <issue ...>...</issue> element (or a self-closing
# <issue ... />). attrs = opening-tag attributes; body = nested children, where
# a path-scoped <ignore .../> list lives.
ISSUE_BLOCK = re.compile(
    r"<issue\b(?P<attrs>[^>]*?)(?:>(?P<body>.*?)</issue>|/>)", re.DOTALL
)
ID_ATTR = re.compile(r'\bid="([^"]+)"')
MSG_ATTR = re.compile(r'\bmessage="([^"]*)"')
LOC_ATTR = re.compile(r'\bfile="([^"]*)"')

# --- Check 4: lint.xml zero-tolerance config guard -----------------------------
# Issue ids the root lint.xml MUST keep pinned as severity=error.
REQUIRED_LINT_PINS = {
    "UnusedResources",
    "MissingTranslation",
    "ExtraTranslation",
    "UnusedIds",
    "CheckResult",
    "HardcodedText",
    "TypographyDashes",
}
SEV_ATTR = re.compile(r'\bseverity="([^"]+)"')


def norm(p: str) -> str:
    return p.replace("\\", "/")


MODULE_MARKER = "/src/main/res"


def module_of(p: str) -> str:
    """Module directory owning `p`: everything before the last
    `/src/main/res` marker. A module at the tree root (path starts with
    `src/main/res/` and has no parent dir) maps to `` so its default and
    locale files share one bucket - the split-based derivation silently
    returned the whole path there, splitting one module in two."""
    p = norm(p)
    idx = p.rfind(MODULE_MARKER)
    return p[:idx] if idx != -1 else ""

def source_files() -> list:
    """All source files that can reference a resource key (whole tree)."""
    found = []
    for pattern in ("**/src/**/*.kt", "**/src/**/*.java", "**/src/**/*.xml"):
        for p in glob.glob(pattern, recursive=True):
            p = norm(p)
            if "/build/" not in p:
                found.append(p)
    return found


def referenced_keys() -> set:
    """Set of (rtype, key) referenced anywhere in source."""
    used = set()
    for p in source_files():
        try:
            content = open(p, encoding="utf-8", errors="ignore").read()
        except OSError:
            continue
        for rtype, key in KOTLIN_REF.findall(content):
            used.add((rtype, key))
            # R.style.Theme_NexaFlow.Starting refers to the XML style named
            # Theme.NexaFlow.Starting (R fields replace dots with underscores).
            if rtype == "style":
                used.add((rtype, key.replace("_", ".")))
        for rtype, key in XML_REF.findall(content):
            used.add((rtype, key))
        # Bare <style parent="Name"> references (no @): inheritance alone
        # counts as a use. android:* and @-prefixed parents are external or
        # already captured by XML_REF.
        for pm in PARENT_ATTR.finditer(content):
            parent = pm.group(1)
            if parent and not parent.startswith("android:") and not parent.startswith("@"):
                used.add(("style", parent))
    return used


def resource_key(path: str) -> str:
    """Basename minus extension; strips the `.9` from nine-patch files."""
    name = norm(path).rsplit("/", 1)[-1]
    if name.endswith(".9.png"):
        return name[: -len(".9.png")]
    dot = name.rfind(".")
    return name[:dot] if dot > 0 else name


def definitions() -> dict:
    """(rtype, key) -> list of (path, module) defining it."""
    defs: dict = {}

    # Text keys: name= attributes inside values*/strings.xml.
    for p in glob.glob("**/src/main/res/values*/strings.xml", recursive=True):
        p = norm(p)
        if "/build/" in p:
            continue
        module = module_of(p)
        try:
            content = open(p, encoding="utf-8").read()
        except OSError:
            continue
        for key in DEF.findall(content):
            defs.setdefault(("string", key), []).append((p, module))

    # Dimen keys: name= inside <dimen> (or <item type="dimen">) tags.
    for p in glob.glob("**/src/main/res/values*/dimens.xml", recursive=True):
        p = norm(p)
        if "/build/" in p:
            continue
        module = module_of(p)
        try:
            content = open(p, encoding="utf-8").read()
        except OSError:
            continue
        for tag_match in DIMEN_TAG.finditer(content):
            tag, attrs = tag_match.group(1), tag_match.group(2)
            if tag == "item" and 'type="dimen"' not in attrs:
                continue
            nm = DEF.search(attrs)
            if nm:
                defs.setdefault(("dimen", nm.group(1)), []).append((p, module))

    # Color keys: name= inside <color> (or <item type="color">) tags in
    # values*/colors.xml (including values-night overrides).
    for p in glob.glob("**/src/main/res/values*/colors.xml", recursive=True):
        p = norm(p)
        if "/build/" in p:
            continue
        module = module_of(p)
        try:
            content = open(p, encoding="utf-8").read()
        except OSError:
            continue
        for tag_match in COLOR_TAG.finditer(content):
            tag, attrs = tag_match.group(1), tag_match.group(2)
            if tag == "item" and 'type="color"' not in attrs:
                continue
            nm = DEF.search(attrs)
            if nm:
                defs.setdefault(("color", nm.group(1)), []).append((p, module))

    # Style/theme keys: name= inside <style> tags in values*/styles.xml and
    # values*/themes.xml (themes are styles; both are the R.style type).
    for p in (
        glob.glob("**/src/main/res/values*/styles.xml", recursive=True)
        + glob.glob("**/src/main/res/values*/themes.xml", recursive=True)
    ):
        p = norm(p)
        if "/build/" in p:
            continue
        module = module_of(p)
        try:
            content = open(p, encoding="utf-8").read()
        except OSError:
            continue
        for tag_match in STYLE_TAG.finditer(content):
            nm = DEF.search(tag_match.group(1))
            if nm:
                defs.setdefault(("style", nm.group(1)), []).append((p, module))

    # File keys: basenames under drawable*/layout*/mipmap* dirs (any format,
    # including .xml, .png, .webp, .9.png).
    for rtype in FILE_RES_TYPES:
        for p in glob.glob(f"**/src/main/res/{rtype}*/**", recursive=True):
            if not os.path.isfile(p):
                continue
            p = norm(p)
            if "/build/" in p:
                continue
            module = module_of(p)
            defs.setdefault((rtype, resource_key(p)), []).append((p, module))

    return defs


def check_banned_and_orphans() -> int:
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
    for (rtype, key), locations in sorted(definitions().items()):
        if (rtype, key) in used:
            continue
        problems += 1
        modules = ", ".join(sorted({m for _, m in locations}))
        if rtype in STRING_TYPES:
            print(f"ORPHAN_STRING: {key} (defined in {modules})")
        else:
            print(f"ORPHAN_RESOURCE: {rtype}:{key} (defined in {modules})")

    if problems:
        print(
            "ORPHAN_RESOURCES_FOUND:",
            problems,
            "- every resource key must be referenced somewhere; delete orphan",
            "keys/files from every locale and qualifier (or, for a deliberate",
            "unused resource, see scripts/check_resources.py).",
        )
    else:
        print("ORPHAN_RESOURCES_FOUND: 0")
    return problems


def check_lint_config() -> int:
    """Check 4: every `lint.xml` in the tree is verified. The root file must
    keep every zero-tolerance issue pinned `severity=error`; any module-level
    file (e.g. `app/lint.xml`) may omit pins — modules inherit the root config
    via the build convention — but must never silence one, neither
    `severity="ignore"` nor a nested `<ignore path=.../>` element, so no
    module can suspend the zero-tolerance checks."""
    problems = 0
    lint_files = sorted(
        norm(p) for p in glob.glob("**/lint.xml", recursive=True)
        if "/build/" not in norm(p)
    )
    if not lint_files:
        print("LINT_CONFIG: no lint.xml found - zero-tolerance pins unenforced.")
        return 1
    root_seen = False
    for path in lint_files:
        is_root = path == "lint.xml"
        try:
            content = open(path, encoding="utf-8").read()
        except OSError:
            continue
        pinned = set()
        ignored = set()
        path_ignored = set()
        for block in ISSUE_BLOCK.finditer(content):
            attrs = block.group("attrs")
            idm = ID_ATTR.search(attrs)
            if not (idm and idm.group(1) in REQUIRED_LINT_PINS):
                continue
            issue_id = idm.group(1)
            sev = SEV_ATTR.search(attrs)
            if sev and sev.group(1) == "ignore":
                ignored.add(issue_id)
                continue
            body = block.group("body") or ""
            if "<ignore" in body:
                path_ignored.add(issue_id)
            if sev and sev.group(1) in ("error", "fatal"):
                pinned.add(issue_id)
        if is_root:
            root_seen = True
            for issue_id in sorted(REQUIRED_LINT_PINS - pinned):
                problems += 1
                print(
                    f"LINT_CONFIG: {issue_id} not pinned as severity=error "
                    "in lint.xml"
                )
        for issue_id in sorted(REQUIRED_LINT_PINS & ignored):
            problems += 1
            print(
                f"LINT_CONFIG: {issue_id} silenced via severity=\"ignore\" "
                f"in {path} - zero-tolerance bypass"
            )
        for issue_id in sorted(REQUIRED_LINT_PINS & path_ignored):
            problems += 1
            print(
                f"LINT_CONFIG: {issue_id} has an <ignore path=.../> entry "
                f"in {path} - zero-tolerance bypass"
            )
    if not root_seen:
        problems += 1
        print("LINT_CONFIG: root lint.xml missing - zero-tolerance pins unenforced.")
    if not problems:
        print(
            "LINT_CONFIG: OK - all zero-tolerance pins present in root lint.xml, "
            "none ignored in any module lint.xml."
        )
    return problems


def check_lint_issues() -> int:
    """Check 3: parse lint XML reports for the zero-tolerance resource issues."""
    problems = 0
    counts = {issue_id: 0 for issue_id in LINT_ISSUES}
    for path in sorted(
        glob.glob("**/build/reports/lint-results-debug.xml", recursive=True)
    ):
        path = norm(path)
        try:
            content = open(path, encoding="utf-8", errors="ignore").read()
        except OSError:
            continue
        for block in ISSUE_BLOCK.finditer(content):
            attrs = block.group("attrs")
            idm = ID_ATTR.search(attrs)
            if not (idm and idm.group(1) in LINT_ISSUES):
                continue
            issue_id = idm.group(1)
            counts[issue_id] += 1
            problems += 1
            msg = MSG_ATTR.search(attrs)
            loc = LOC_ATTR.search(attrs)
            print(
                LINT_ISSUES[issue_id] + ":",
                msg.group(1) if msg else "(no message)",
                "|",
                loc.group(1) if loc else path,
            )
    for issue_id, label in LINT_ISSUES.items():
        print(f"{label}: {counts[issue_id]}")
    if problems:
        print(
            "LINT_ISSUES_FOUND:",
            problems,
            "- Android Lint must be clean on UnusedResources, MissingTranslation,",
            "ExtraTranslation, CheckResult, HardcodedText and TypographyDashes;",
            "fix the issue(s) above in every module.",
        )
    else:
        print("LINT_ISSUES_FOUND: 0")
    return problems


def main() -> int:
    problems = 0
    problems += check_banned_and_orphans()
    problems += check_lint_config()
    problems += check_lint_issues()
    if problems:
        print(
            "RESOURCE_GATE: FAIL",
            f"({problems} problem(s)) - fix the items above before merging.",
        )
    else:
        print("RESOURCE_GATE: OK - no banned, orphaned, unused, or mistranslated resources.")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
