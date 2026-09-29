#!/usr/bin/env python3
"""Enforce T34 canonical security auditor boundaries (plan §T34)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AUDITOR_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "CanonicalSecurityAuditor.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CanonicalSecurityAuditorTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"System\.currentTimeMillis",
    r"kotlin\.random\.Random",
)

REQUIRED_CONSTRUCTS = (
    "CanonicalSecurityAuditor",
    "SecurityPolicy",
    "SecurityFinding",
    "SecurityAuditReport",
    "FindingCode",
    "SECRET_IN_OBSERVATION",
    "FORBIDDEN_URI_SCHEME",
    "UNCLASSIFIED_OPERATION",
    "DESTRUCTIVE_WITHOUT_CAPABILITIES",
    "DESTRUCTIVE_DECLARED_IDEMPOTENT",
    "EXPRESSION_IN_DESTRUCTIVE_PAYLOAD",
    "ALLOWED_URI_SCHEMES",
    "FORBIDDEN_URI_SCHEMES",
    "audit",
)

REQUIRED_TEST_CASES = (
    "secretsOnActionsAreSanctioned",
    "secretsOnObservationsAreRefused",
    "allowedUriSchemesPass",
    "forbiddenUriSchemesAreRefused",
    "unclassifiedOperationsFailClosed",
    "destructiveWithoutCapabilitiesIsRefused",
    "destructiveDeclaredIdempotentIsRefused",
    "conditionallyIdempotentDestructivePasses",
    "expressionPayloadOnDestructiveWriteIsRefused",
    "auditIsDeterministicAndFindingsSortStably",
    "nestedBranchesAreAuditedRecursively",
)


def main() -> int:
    problems: list[str] = []

    if not AUDITOR_FILE.is_file():
        problems.append(f"missing {AUDITOR_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if AUDITOR_FILE.is_file():
        source = AUDITOR_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"CanonicalSecurityAuditor.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"CanonicalSecurityAuditor.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"CanonicalSecurityAuditorTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_SECURITY_AUDITOR: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_SECURITY_AUDITOR: OK — defense-in-depth security audit "
        "over composed canonical workflows: secrets refused on observations "
        "(sanctioned on actions), URI schemes allowlisted with file/"
        "javascript/data refused, unclassified operations fail closed, "
        "destructive operations require capabilities and may never be "
        "declared IDEMPOTENT, expression payloads on destructive writes "
        "refused; deterministic with stably sorted findings"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
