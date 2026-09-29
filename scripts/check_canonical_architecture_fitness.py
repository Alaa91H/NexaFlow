#!/usr/bin/env python3
"""Enforce T37 architecture fitness gates over the canonical platform (plan §T37)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

CANONICAL_MAIN = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"
CANONICAL_WORKFLOW = ROOT / "domain/src/main/java/com/nexaflow/domain/workflow"
PLUGINSDK_MAIN = ROOT / "core/plugin-sdk/src/main/java/com/nexaflow/core/pluginsdk"
CI_WORKFLOW = ROOT / ".github/workflows/nexaflow-ci.yml"

# Gate scripts that must exist and stay wired into CI (the T26+ platform
# family; earlier-phase gates like baseline/ADRs are covered by T01–T25
# checks and stay wired separately).
REQUIRED_GATES = (
    "check_canonical_persistence_policy",
    "check_canonical_runtime_cutover",
    "check_canonical_migration_rollout",
    "check_canonical_consolidation_optimizer",
    "check_canonical_diagnostics_model",
    "check_canonical_plugin_sdk",
    "check_canonical_fault_injection",
    "check_canonical_performance_budget",
    "check_canonical_security_auditor",
    "check_canonical_accessibility_model",
    "check_canonical_device_matrix",
)

# Forbidden anywhere in the canonical packages: legacy type system leaks.
CANONICAL_FORBIDDEN = (
    r"\benum\s+class\s+TriggerType\b",
    r"\benum\s+class\s+ActionType\b",
    r"\bdata\s+class\s+\w+Config\b.*Map\s*<\s*String\s*,\s*String\s*>",
)

# Dependency direction: canonical packages must not depend upward on the
# app/data layers or on Android framework types.
CANONICAL_FORBIDDEN_IMPORTS = (
    r"import\s+android\.app\.",
    r"import\s+android\.content\.",
    r"import\s+android\.os\.",
    r"import\s+android\.provider\.",
    r"import\s+androidx\.compose\.",
    r"import\s+com\.nexaflow\.data\.",
    r"import\s+com\.nexaflow\.app\.",
    r"import\s+com\.nexaflow\.core\.",
)

# The plugin SDK is a pure protocol module: no Android framework, no domain.
PLUGINSDK_CONTRACT_FORBIDDEN = (
    r"import\s+android\.",
    r"import\s+com\.nexaflow\.domain\.",
    r"import\s+com\.nexaflow\.core\.(?!pluginsdk)",
)

# The T27/T28 persistence+migration contracts must stay clock-free (the
# runner owns time). Other workflow files (e.g. RetryExecutor) may use
# randomness for backoff by design.
WORKFLOW_POLICY_FILES = (
    "WorkflowPersistencePolicy.kt",
    "WorkflowMigrationOrchestrator.kt",
)
WORKFLOW_FORBIDDEN = (
    r"System\.currentTimeMillis",
    r"kotlin\.random\.Random",
    r"java\.util\.Date",
    r"java\.time\.Clock",
)


def iter_kotlin(directory: Path):
    for path in sorted(directory.rglob("*.kt")):
        if "build" in path.parts:
            continue
        yield path


def main() -> int:
    problems: list[str] = []

    # 1. The canonical packages must not import upward or touch Android.
    for directory in (CANONICAL_MAIN, CANONICAL_WORKFLOW):
        for path in iter_kotlin(directory):
            source = path.read_text(encoding="utf-8", errors="replace")
            for pattern in CANONICAL_FORBIDDEN_IMPORTS:
                if re.search(pattern, source):
                    problems.append(
                        f"{path.relative_to(ROOT)} imports forbidden dependency {pattern!r}"
                    )
            for pattern in CANONICAL_FORBIDDEN:
                if re.search(pattern, source):
                    problems.append(
                        f"{path.relative_to(ROOT)} matches forbidden legacy pattern {pattern!r}"
                    )

    # 2. The persistence/migration policy files must be clock- and random-free.
    for name in WORKFLOW_POLICY_FILES:
        path = CANONICAL_WORKFLOW / name
        if not path.is_file():
            problems.append(f"missing {path.relative_to(ROOT)}")
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        for pattern in WORKFLOW_FORBIDDEN:
            if re.search(pattern, source):
                problems.append(
                    f"{path.relative_to(ROOT)} matches nondeterminism pattern {pattern!r}"
                )

    # 3. The plugin SDK canonical contract stays a pure protocol surface.
    contract = PLUGINSDK_MAIN / "PluginCanonicalContract.kt"
    if contract.is_file():
        source = contract.read_text(encoding="utf-8", errors="replace")
        for pattern in PLUGINSDK_CONTRACT_FORBIDDEN:
            if re.search(pattern, source):
                problems.append(
                    f"{contract.relative_to(ROOT)} matches forbidden import {pattern!r}"
                )
    else:
        problems.append("missing PluginCanonicalContract.kt")

    # 4. Every canonical gate exists and is wired into CI.
    for gate in REQUIRED_GATES:
        gate_path = ROOT / "scripts" / f"{gate}.py"
        if not gate_path.is_file():
            problems.append(f"missing gate script scripts/{gate}.py")
        if CI_WORKFLOW.is_file():
            ci_source = CI_WORKFLOW.read_text(encoding="utf-8")
            if gate.replace("_", "-") not in ci_source and gate not in ci_source:
                problems.append(f"gate {gate} not wired into CI workflow")

    # 5. Every gate script ships with a unittest in scripts/tests.
    for gate in REQUIRED_GATES:
        test_path = ROOT / "scripts" / "tests" / f"test_{gate}.py"
        if not test_path.is_file():
            problems.append(f"missing gate test scripts/tests/test_{gate}.py")

    if problems:
        print("CANONICAL_ARCHITECTURE_FITNESS: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_ARCHITECTURE_FITNESS: OK — dependency direction holds "
        "(canonical packages import no app/data/Android), the plugin SDK "
        "contract stays a pure protocol surface, workflow persistence stays "
        "clock- and random-free, and every canonical gate ships with a "
        "unittest and is wired into CI"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
