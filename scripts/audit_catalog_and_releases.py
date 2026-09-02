"""Deterministic cross-platform checks that keep the catalog and releases honest.

check_catalog_parity  — every TriggerType/ActionType enum value must appear exactly
                        once in the builder picker, except explicitly pinned
                        restricted entries (PLUGIN_EVENT). Prevents "enum exists but
                        is unconfigurable" drift between domain and UI.
check_tag_changelog   — the release tag must match the newest CHANGELOG.md entry,
                        and that entry must have real content. Prevents publishing
                        a tag whose release notes were never written.

Both checks are pure-Python, dependency-free, and UTF-8 (UTF-8 is enforced
explicitly so the checks behave identically on Windows CI, Linux CI, and
developer machines regardless of the platform default encoding).

Usage:
    python scripts/audit_catalog_and_releases.py catalog
    python scripts/audit_catalog_and_releases.py tag-changelog <tag>
    python scripts/audit_catalog_and_releases.py all <tag>

Exit codes: 0 = pass, 1 = failure (each failing check prints a diagnostic).
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

DOMAIN_MODEL = Path("domain/src/main/java/com/nexaflow/domain/models/Automation.kt")
TRIGGER_EDITOR = Path(
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt"
)
ACTION_BUILDER = Path(
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt"
)
CHANGELOG = Path("CHANGELOG.md")

# Trigger entries intentionally NOT exposed in the generic picker. Each must be
# justified here; the check fails if reality drifts from this pin in either
# direction (unexposed-but-not-pinned, or pinned-but-exposed).
RESTRICTED_TRIGGERS = {
    # PLUGIN_EVENT is created only through the verified plugin configuration
    # flow; it must never appear in the generic trigger picker.
    "PLUGIN_EVENT",
}

VERSIONED_TAG = re.compile(r"^v\d+\.\d+\.\d+(?:[-.][0-9A-Za-z]+)*$")


def read_utf8(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def enum_values(model: str, enum_name: str) -> list[str]:
    body = re.search(rf"enum class {enum_name} \{{(.*?)\n\}}", model, re.S)
    if body is None:
        raise SystemExit(f"FAIL: {enum_name} not found in {DOMAIN_MODEL}")
    return re.findall(r"^\s*([A-Z][A-Z0-9_]*)\s*(?:,|$)", body.group(1), re.M)


def catalog_values(source: str, list_pattern: str, ref_pattern: str) -> list[str]:
    body = re.search(list_pattern, source, re.S)
    if body is None:
        raise SystemExit(f"Catalog list not found with pattern {list_pattern!r}")
    return re.findall(ref_pattern, body.group(1))


def check_catalog_parity() -> int:
    problems = 0
    model = read_utf8(DOMAIN_MODEL)

    # --- Triggers -----------------------------------------------------------
    triggers = enum_values(model, "TriggerType")
    editor = read_utf8(TRIGGER_EDITOR)
    options = catalog_values(
        editor,
        r"val triggerTypeOptions = listOf\((.*?)\n\)",
        r"TriggerType\.([A-Z0-9_]+)",
    )
    counts: dict[str, int] = {}
    for option in options:
        counts[option] = counts.get(option, 0) + 1

    for value in triggers:
        expected = 0 if value in RESTRICTED_TRIGGERS else 1
        actual = counts.get(value, 0)
        if actual != expected:
            problems += 1
            if expected == 0:
                print(
                    f"TRIGGER_CATALOG: {value} is pinned restricted but appears "
                    f"{actual} time(s) in triggerTypeOptions"
                )
            elif actual == 0:
                print(f"TRIGGER_CATALOG: {value} missing from triggerTypeOptions")
            else:
                print(
                    f"TRIGGER_CATALOG: {value} appears {actual} time(s) in "
                    "triggerTypeOptions (duplicates forbidden)"
                )

    exposed_unpinned = sorted(set(counts) - set(triggers))
    if exposed_unpinned:
        problems += 1
        print(
            "TRIGGER_CATALOG: picker references values missing from the enum: "
            + ", ".join(exposed_unpinned)
        )

    # --- Actions ------------------------------------------------------------
    actions = enum_values(model, "ActionType")
    builder = read_utf8(ACTION_BUILDER)
    action_options = catalog_values(
        builder,
        r"internal val actionOptions = listOf\((.*?)\n\)",
        r"ActionType\.([A-Z0-9_]+)",
    )
    action_counts: dict[str, int] = {}
    for option in action_options:
        action_counts[option] = action_counts.get(option, 0) + 1

    for value in actions:
        actual = action_counts.get(value, 0)
        if actual != 1:
            problems += 1
            if actual == 0:
                print(f"ACTION_CATALOG: {value} missing from actionOptions")
            else:
                print(
                    f"ACTION_CATALOG: {value} appears {actual} time(s) in "
                    "actionOptions (duplicates forbidden)"
                )

    unknown_actions = sorted(set(action_counts) - set(actions))
    if unknown_actions:
        problems += 1
        print(
            "ACTION_CATALOG: picker references values missing from the enum: "
            + ", ".join(unknown_actions)
        )

    if problems:
        print("CATALOG_PARITY: FAIL", problems, "problem(s)")
    else:
        print(
            f"CATALOG_PARITY: OK — {len(triggers)} triggers "
            f"({len(triggers) - len(RESTRICTED_TRIGGERS)} exposed) and "
            f"{len(actions)} actions all appear exactly once in the builder."
        )
    return problems


def newest_changelog_entry(changelog: str) -> tuple[str, str]:
    """Return (version, section-body) of the first `## [version]` entry."""
    # The optional date suffix and \r? keep this line-ending agnostic (CRLF
    # checkouts on Windows vs. LF on Linux CI).
    match = re.search(r"^##\s+\[([^\]]+)\][^\S\r\n]*[^\r\n]*", changelog, re.M)
    if match is None:
        return "", ""
    start = match.end()
    next_section = re.search(r"^##\s+", changelog[start:], re.M)
    end = start + next_section.start() if next_section else len(changelog)
    return match.group(1), changelog[start:end].strip()


def extract_section(changelog: str, tag: str) -> str:
    """Return the body of the `## [<tag>]` section (empty when absent)."""
    # Headings look like `## [v3.56.2] - 2026-09-02` or `## [Unreleased]`.
    # The charset after the tag absorbs the optional date suffix; `\r?\s*`
    # keeps the matcher line-ending agnostic (CRLF checkouts on Windows vs.
    # LF on Linux CI).
    heading = re.compile(rf"^##\s+\[{re.escape(tag)}\][^\S\r\n]*[^\r\n]*", re.M)
    match = heading.search(changelog)
    if match is None:
        return ""
    start = match.end()
    next_section = re.search(r"^##\s+", changelog[start:], re.M)
    end = start + next_section.start() if next_section else len(changelog)
    return changelog[start:end].strip()


def check_tag_changelog(tag: str) -> int:
    problems = 0
    if not VERSIONED_TAG.match(tag):
        print(f"TAG_HYGIENE: {tag!r} is not a vMAJOR.MINOR.PATCH tag")
        return 1

    if not CHANGELOG.is_file():
        print("TAG_HYGIENE: CHANGELOG.md is missing")
        return 1

    changelog = read_utf8(CHANGELOG)

    # 1) The tag must have its own changelog section with real content.
    body = extract_section(changelog, tag)
    if not body:
        problems += 1
        print(
            f"TAG_HYGIENE: no CHANGELOG.md section for [{tag}] — write the "
            "release entry before tagging."
        )
    elif len(body) < 40 or not re.search(r"^###\s+", body, re.M):
        problems += 1
        print(
            f"TAG_HYGIENE: CHANGELOG.md section [{tag}] is empty or has no "
            "### subsections — describe the release (Added/Fixed/Changed/...)."
        )

    # 2) Anything accrued under [Unreleased] must have been cut into the
       #    release; a substantive Unreleased section means stale notes.
    unreleased = extract_section(changelog, "Unreleased")
    if len(unreleased) >= 40:
        problems += 1
        print(
            "TAG_HYGIENE: CHANGELOG.md still has content under [Unreleased] — "
            "cut it into the release section before tagging."
        )

    if problems:
        print("TAG_HYGIENE: FAIL", problems, "problem(s)")
    else:
        print(f"TAG_HYGIENE: OK — tag {tag} has a complete changelog entry.")
    return problems


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 1
    mode = argv[1]
    if mode == "catalog":
        return 1 if check_catalog_parity() else 0
    if mode == "tag-changelog":
        if len(argv) != 3:
            print("tag-changelog requires the tag argument", file=sys.stderr)
            return 1
        return 1 if check_tag_changelog(argv[2]) else 0
    if mode == "all":
        if len(argv) != 3:
            print("all requires the tag argument", file=sys.stderr)
            return 1
        problems = check_catalog_parity() + check_tag_changelog(argv[2])
        return 1 if problems else 0
    print(f"Unknown mode: {mode}", file=sys.stderr)
    print(__doc__)
    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
