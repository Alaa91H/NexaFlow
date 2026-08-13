"""Fail CI when Android Lint's UnusedResources analysis finds any dead resource.

Lint severity for UnusedResources is set to `error` in the root lint.xml (wired
into every module by the build.gradle.kts convention), so `./gradlew lintDebug`
already fails the build. This script is the reporting half of the gate: it runs
after lintDebug (`if: always()`) and prints every unused resource with its file
and message, and exits non-zero if any were found — so the failure is explicit
and readable even if a module's lint config ever gets mis-wired.

XML is authoritative: lint-results-debug.xml lists each finding as a multi-line
`<issue id="UnusedResources" ...>` element, one per module report.
"""
import glob
import re

# Matches an issue element whose id attribute equals UnusedResources
# (multiline-safe: the id may be on its own line inside the element).
ISSUE_BLOCK = re.compile(r"<issue\b(?P<attrs>[^>]*?)>", re.DOTALL)
ID_ATTR = re.compile(r'\bid="([^"]+)"')
MSG_ATTR = re.compile(r'\bmessage="([^"]*)"')
LOC_ATTR = re.compile(r'\bfile="([^"]*)"')


def norm(p: str) -> str:
    return p.replace("\\", "/")


def main() -> int:
    problems = 0
    for path in sorted(glob.glob("**/build/reports/lint-results-debug.xml", recursive=True)):
        path = norm(path)
        try:
            content = open(path, encoding="utf-8", errors="ignore").read()
        except OSError:
            continue
        for block in ISSUE_BLOCK.finditer(content):
            attrs = block.group("attrs")
            if ID_ATTR.search(attrs) and ID_ATTR.search(attrs).group(1) == "UnusedResources":
                problems += 1
                msg = MSG_ATTR.search(attrs)
                loc = LOC_ATTR.search(attrs)
                print(
                    "UNUSED_RESOURCE:",
                    msg.group(1) if msg else "(no message)",
                    "|",
                    loc.group(1) if loc else path,
                )
    if problems:
        print(
            "UNUSED_RESOURCES_FOUND:",
            problems,
            "- Android Lint's UnusedResources must be clean; delete the dead",
            "resource(s) above from every module.",
        )
    else:
        print("UNUSED_RESOURCES_FOUND: 0")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
