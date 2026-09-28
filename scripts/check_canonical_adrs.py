#!/usr/bin/env python3
"""T02 architecture-decision gate for the canonical automation migration."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADR_DIR = ROOT / "docs/architecture/adr"

REQUIRED = {
    "ADR-001-canonical-workflow-ast.md": ("WorkflowInterpreter", "typed canonical"),
    "ADR-002-stable-ids-and-registries.md": ("stable IDs", "OperationRegistry"),
    "ADR-003-multi-selection-semantics.md": ("TargetSelectionMode", "BATCH"),
    "ADR-004-legacy-migration-strategy.md": ("no rewrite-on-read", "233/233"),
    "ADR-005-schema-driven-configuration-ui.md": ("NodeConfiguratorSheet", "Schema"),
    "ADR-006-workflow-compiler-integration.md": ("WorkflowDocumentCompiler", "side effects"),
    "ADR-007-capability-resolution.md": ("CapabilityRouter", "OperationRegistry"),
    "ADR-008-execution-planner.md": ("SemanticWorkflowPlanner", "WorkflowInterpreter"),
    "ADR-009-error-and-failure-semantics.md": ("UNKNOWN", "PENDING_USER_ACTION"),
    "ADR-010-idempotency-retry-verification.md": ("CapabilityIdempotency", "CapabilityRetrySafety"),
    "ADR-011-provider-architecture.md": ("provider", "conformance"),
    "ADR-012-diagnostics-and-execution-journal.md": ("execution journal", "why didn't it run"),
    "ADR-013-versioning-policy.md": ("Workflow schema version", "Migration version"),
    "ADR-014-security-and-secret-handling.md": ("No secrets", "Root/Shizuku"),
    "ADR-015-legacy-retirement-policy.md": ("233/233", "stable release cycle"),
}

BANNED = (
    "replace WorkflowInterpreter",
    "second workflow engine",
    "bypass CapabilityRouter",
)


def main() -> int:
    problems: list[str] = []
    readme = ADR_DIR / "README.md"
    if not readme.is_file():
        problems.append("ADR README.md is missing")
        index = ""
    else:
        index = readme.read_text(encoding="utf-8")

    found = {p.name for p in ADR_DIR.glob("ADR-*.md")} if ADR_DIR.is_dir() else set()
    expected = set(REQUIRED)
    if found != expected:
        problems.append(
            f"ADR file set mismatch: missing={sorted(expected-found)}, "
            f"unexpected={sorted(found-expected)}"
        )

    for name, anchors in REQUIRED.items():
        path = ADR_DIR / name
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        if "**Status:** Accepted" not in text:
            problems.append(f"{name}: status must be Accepted")
        for heading in ("## Context", "## Decision", "## Invariants", "## Consequences"):
            if heading not in text:
                problems.append(f"{name}: missing {heading}")
        for anchor in anchors:
            if anchor.lower() not in text.lower():
                problems.append(f"{name}: missing required architecture anchor {anchor!r}")
        for banned in BANNED:
            if banned.lower() in text.lower():
                problems.append(f"{name}: contains banned parallel-architecture decision {banned!r}")
        adr_number = name.split("-")[1]
        if f"ADR-{adr_number}" not in index:
            problems.append(f"README: ADR-{adr_number} is not indexed")

    if problems:
        print("CANONICAL_ADRS: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_ADRS: OK — 15/15 accepted ADRs present; "
        "required existing-runtime anchors and invariants are pinned"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
