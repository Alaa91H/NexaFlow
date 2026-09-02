"""Render a professional GitHub Release body from CHANGELOG.md.

Extracts the section for a given version tag, rewrites relative doc links into
absolute GitHub URLs, appends the standing quality-evidence table and install
guidance, and prints the result to stdout. The CI release step pipes this into
`gh release create --notes-file`.

Usage:
    python scripts/generate_release_notes.py <tag> [> body.md]

Exit codes:
    0 - notes rendered
    1 - tag missing from the changelog (release notes are never invented)
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

CHANGELOG = Path("CHANGELOG.md")
REPO = "Alaa91H/NexaFlow"
GITHUB_BASE = f"https://github.com/{REPO}"

QUALITY_TABLE = """
## Quality evidence

Every release candidate passes the complete CI gate before publication:

- Android Lint (zero-tolerance: UnusedResources, MissingTranslation, ExtraTranslation,
  UnusedIds, CheckResult, HardcodedText, TypographyDashes) and Detekt static analysis.
- Catalog parity gates: every `TriggerType` and `ActionType` enum value must exist
  exactly once in the builder picker (restricted entries are pinned explicitly).
- Full unit-test suite, including Room migration, scheduler, lifecycle, and recovery
  regression coverage.
- Release build with R8 full shrinking, APK signature verification (v2/v3 schemes and
  certificate-fingerprint match against the production keystore), 16 KB page-size
  alignment check, zipalign verification, bundletool AAB validation, and Gradle
  dependency verification (SHA-256 checksums for every artifact).
- Tag hygiene: the tag must match the newest `CHANGELOG.md` entry, so the notes below
  are the actual, reviewed change record for this release.

## Install

- Download `NexaFlow-<version>.apk` from the assets below and install it.
- Updates install over the existing app without data loss when both builds share the
  same signing certificate (production releases do).
- Pre-release tags (`alpha` / `beta` / `rc`) are marked as pre-releases automatically.

## Documentation

- [Architecture](https://github.com/{repo}/blob/main/docs/ARCHITECTURE.md)
- [Master plan 2026](https://github.com/{repo}/blob/main/docs/ROADMAP_2026.md)
- [Strict task lifecycle](https://github.com/{repo}/blob/main/docs/STRICT_TASK_LIFECYCLE.md)
- [Plugin SDK](https://github.com/{repo}/blob/main/docs/PLUGIN_SDK.md)
""".format(repo=REPO)


def extract_section(changelog: str, tag: str) -> str:
    """Return the changelog body for `tag` (the first section after its heading)."""
    # Headings look like `## [v3.56.2] - 2026-09-02` or `## [Unreleased]`.
    # [^\S\r\n]*\r?$ keeps the matcher line-ending agnostic (CRLF checkouts on
    # Windows vs. LF on Linux CI) while absorbing the optional date suffix.
    heading = re.compile(rf"^##\s+\[{re.escape(tag)}\][^\S\r\n]*[^\r\n]*\r?$", re.M)
    match = heading.search(changelog)
    if match is None:
        return ""
    start = match.end()
    next_section = re.search(r"^##\s+", changelog[start:], re.M)
    end = start + next_section.start() if next_section else len(changelog)
    return changelog[start:end].strip()


def absolutize_links(section: str) -> str:
    """Rewrite relative markdown links (e.g. docs/X.md) into absolute GitHub URLs.

    Only [label](target) forms are rewritten — the preceding `]` is part of the
    pattern — so bare URLs inside parentheses or backticks are never touched.
    """

    def repl(m: re.Match[str]) -> str:
        target = m.group(1)
        if target.startswith(("http://", "https://", "#", "mailto:")):
            return m.group(0)
        return f"]({GITHUB_BASE}/blob/main/{target})"

    return re.sub(r"\]\(([^)\s]+)\)", repl, section)


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: generate_release_notes.py <tag>", file=sys.stderr)
        return 1
    # The changelog headings carry the tag verbatim (e.g. `## [v3.56.2]`), so the
    # tag is used as-is with no prefix normalization.
    tag = argv[1]
    changelog = CHANGELOG.read_text(encoding="utf-8")
    section = extract_section(changelog, tag)
    if not section:
        print(f"No CHANGELOG.md section found for [{tag}] — release notes must be "
              "written before tagging.", file=sys.stderr)
        return 1
    body = absolutize_links(section) + "\n" + QUALITY_TABLE
    print(body.strip())
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
