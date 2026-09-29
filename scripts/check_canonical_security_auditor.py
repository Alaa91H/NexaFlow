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
EXPORT_AUDIT_FILE = ROOT / "scripts/audit_exported_components.py"
EXPORT_AUDIT_TEST = ROOT / "scripts/tests/test_exported_components.py"
REDACTOR_FILE = ROOT / (
    "core/logging/src/main/java/com/nexaflow/core/logging/RedactingLogStore.kt"
)
REDACTOR_TEST = ROOT / (
    "core/logging/src/test/java/com/nexaflow/core/logging/RedactingLogStoreTest.kt"
)
HTTP_POLICY_FILE = ROOT / (
    "core/execution/src/main/java/com/nexaflow/core/execution/handler/"
    "HttpUrlPolicy.kt"
)
HTTP_POLICY_TEST = ROOT / (
    "core/execution/src/test/java/com/nexaflow/core/execution/handler/"
    "HttpUrlPolicyTest.kt"
)
PLUGIN_BACKEND_FILE = ROOT / (
    "core/execution/src/main/java/com/nexaflow/core/execution/capability/"
    "PluginCapabilityBackend.kt"
)
PLUGIN_BACKEND_TEST = ROOT / (
    "core/execution/src/test/java/com/nexaflow/core/execution/capability/"
    "PluginCapabilityBackendTest.kt"
)
ACTION_SCHEMA_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/catalog/ActionNodeSchemas.kt"
)
MAPPER_SECURITY_TEST = ROOT / (
    "data/src/test/java/com/nexaflow/data/mapper/AutomationMapperTest.kt"
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

    for path in (
        EXPORT_AUDIT_FILE,
        EXPORT_AUDIT_TEST,
        REDACTOR_FILE,
        REDACTOR_TEST,
        HTTP_POLICY_FILE,
        HTTP_POLICY_TEST,
        PLUGIN_BACKEND_FILE,
        PLUGIN_BACKEND_TEST,
        ACTION_SCHEMA_FILE,
        MAPPER_SECURITY_TEST,
    ):
        if not path.is_file():
            problems.append(f"missing product security wiring {path.relative_to(ROOT)}")

    if EXPORT_AUDIT_FILE.is_file():
        exports = EXPORT_AUDIT_FILE.read_text(encoding="utf-8")
        for token in (
            "PUBLIC =",
            "GATES =",
            "REVIEWED_NO_PERMISSION",
            "Merged release manifest missing",
        ):
            if token not in exports:
                problems.append(f"exported-component audit missing {token!r}")

    if REDACTOR_FILE.is_file():
        redactor = REDACTOR_FILE.read_text(encoding="utf-8")
        for token in (
            "object SecretRedactor",
            "Bearer",
            "password",
            "authorization",
            "[REDACTED]",
            "class RedactingLogStore",
        ):
            if token not in redactor:
                problems.append(f"logging redaction boundary missing {token!r}")

    if REDACTOR_TEST.is_file():
        redactor_test = REDACTOR_TEST.read_text(encoding="utf-8")
        if "redactsCredentialsBeforeWritingTimelineAndErrors" not in redactor_test:
            problems.append("logging redaction regression test is missing")

    if HTTP_POLICY_FILE.is_file():
        http_policy = HTTP_POLICY_FILE.read_text(encoding="utf-8")
        for token in (
            'require(uri.scheme.equals("https", true))',
            "PRIVATE_NETWORK_DENIED",
            "addresses.none(::isLocal)",
            "isAnyLocalAddress",
            "isMulticastAddress",
        ):
            if token not in http_policy:
                problems.append(f"HTTP SSRF policy missing {token!r}")

    if HTTP_POLICY_TEST.is_file():
        http_tests = HTTP_POLICY_TEST.read_text(encoding="utf-8")
        for case in (
            "mixedAnswersAreRejectedInEitherOrder",
            "unresolvedFailsClosed",
            "localRangesAndOptIn",
            "httpsOnlyRejectsDowngradesIndependentOfCase",
        ):
            if f"fun {case}" not in http_tests:
                problems.append(f"HTTP security tests missing {case!r}")

    if PLUGIN_BACKEND_FILE.is_file():
        plugin_backend = PLUGIN_BACKEND_FILE.read_text(encoding="utf-8")
        for token in (
            "PluginCanonicalContract.checkInvocation",
            "PluginRiskPolicy.requiresHighRiskApproval",
            "trustGranted",
            "lifecycleActive",
        ):
            if token not in plugin_backend:
                problems.append(f"plugin security boundary missing {token!r}")

    if PLUGIN_BACKEND_TEST.is_file():
        plugin_tests = PLUGIN_BACKEND_TEST.read_text(encoding="utf-8")
        for case in (
            "rejectsHighRiskPluginWithoutExplicitHighRiskApprovalBeforeDiscoveryOrFire",
            "rejectsReferenceThatDoesNotMatchPersistedActionBeforeFiringPlugin",
        ):
            if f"fun {case}" not in plugin_tests:
                problems.append(f"plugin security tests missing {case!r}")

    if ACTION_SCHEMA_FILE.is_file():
        action_schema = ACTION_SCHEMA_FILE.read_text(encoding="utf-8")
        for snippet in (
            'ActionType.ADVANCED_SHIZUKU,\n        ActionType.ADVANCED_ROOT -> schema(',
            'secretField("command", required = true)',
            'secretField("body")',
            'secretField("headers")',
            'secretField("auth_token")',
            'secretField("bundleJson")',
        ):
            if snippet not in action_schema:
                problems.append(f"sensitive action schema missing {snippet!r}")

    if MAPPER_SECURITY_TEST.is_file():
        mapper_tests = MAPPER_SECURITY_TEST.read_text(encoding="utf-8")
        if "privilegedAndHttpSecretsStayOutOfV3AndRoundTripThroughFallback" not in mapper_tests:
            problems.append("V3 secret persistence regression test is missing")

    if problems:
        print("CANONICAL_SECURITY_AUDITOR: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_SECURITY_AUDITOR: OK — canonical workflow audit is backed "
        "by product security boundaries: reviewed exported components, common "
        "log redaction, HTTPS/SSRF destination policy, plugin trust/lifecycle "
        "approval, and secret-reference persistence for privileged commands, "
        "HTTP payloads and plugin configuration; destructive operations still "
        "fail closed on capability/idempotency violations"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
