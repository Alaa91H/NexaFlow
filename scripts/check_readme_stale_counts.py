"""Prevent fixed implementation counts from going stale in the README."""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
PATTERNS = (
    re.compile(r"\b\d+\s+(?:trigger|action)(?:\s+enum)?\s+entries?\b", re.IGNORECASE),
    re.compile(r"\b\d+\s+(?:trigger|action)s?\b", re.IGNORECASE),
    re.compile(r"\b\d+\s+sensor\s+modes?\b", re.IGNORECASE),
    re.compile(r"\bRoom\s+database\s+schema\s+\d+\b", re.IGNORECASE),
)


def find_stale_count_claims(readme: str) -> list[str]:
    return [
        line.strip()
        for line in readme.splitlines()
        if any(pattern.search(line) for pattern in PATTERNS)
    ]


def main() -> int:
    readme = README.read_text(encoding="utf-8")
    claims = find_stale_count_claims(readme)
    if claims:
        print("README_STALE_COUNTS: FAIL")
        for claim in claims:
            print(f"- {claim}")
        return 1
    if "docs/CAPABILITY_CATALOG.md" not in readme:
        print("README_STALE_COUNTS: FAIL - generated capability catalog link is missing")
        return 1
    print("README_STALE_COUNTS: OK (catalog and schema counts are not hard-coded)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
