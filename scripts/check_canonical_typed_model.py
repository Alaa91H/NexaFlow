#!/usr/bin/env python3
"""Enforce T04 canonical typed-model architecture boundaries."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CANONICAL_DIR = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"

CORE_MODEL_FILES = (
    CANONICAL_DIR / "CanonicalIds.kt",
    CANONICAL_DIR / "CanonicalValues.kt",
    CANONICAL_DIR / "CanonicalAst.kt",
)

REQUIRED_FILES = CORE_MODEL_FILES + (
    CANONICAL_DIR / "CanonicalIdentityRegistry.kt",
    CANONICAL_DIR / "LegacyStableIdMappings.kt",
)

FORBIDDEN_CORE_TOKENS = (
    "TriggerType",
    "ActionType",
    "AutomationNodeFamily",
    "androidx.compose",
    "com.nexaflow.core.execution",
    "com.nexaflow.feature.",
)

RAW_STRING_MAP = re.compile(
    r"(?:Mutable)?Map\s*<\s*String\s*,\s*String\s*>"
)


def main() -> int:
    problems: list[str] = []

    for path in REQUIRED_FILES:
        if not path.is_file():
            problems.append(f"missing canonical model file: {path.relative_to(ROOT)}")

    for path in CORE_MODEL_FILES:
        if not path.is_file():
            continue
        source = path.read_text(encoding="utf-8")
        # Architectural coupling is a code concern, not a documentation concern.
        # Strip Kotlin comments so ADR/KDoc references to legacy types do not
        # create false positives while actual imports/type usages still fail.
        code = re.sub(r"/\\*.*?\\*/", "", source, flags=re.S)
        code = re.sub(r"//.*", "", code)
        for token in FORBIDDEN_CORE_TOKENS:
            if token in code:
                problems.append(
                    f"{path.relative_to(ROOT)} couples canonical core to forbidden token {token!r}"
                )
        if RAW_STRING_MAP.search(code):
            problems.append(
                f"{path.relative_to(ROOT)} reintroduces Map<String, String> into canonical core"
            )

    ast = (CANONICAL_DIR / "CanonicalAst.kt")
    if ast.is_file():
        source = ast.read_text(encoding="utf-8")
        required = (
            "sealed interface CanonicalNode",
            "CanonicalWorkflowAst",
            "CanonicalPrimitive",
            "ObserveNode",
            "SetStateNode",
            "SetValueNode",
            "InvokeNode",
            "OpenNode",
            "SendNode",
            "TransformNode",
            "InputNode",
            "WaitNode",
            "SequenceNode",
            "BranchNode",
            "RestoreNode",
            "validateCanonicalAst",
        )
        for token in required:
            if token not in source:
                problems.append(f"CanonicalAst.kt missing required T04 construct {token!r}")

    values = CANONICAL_DIR / "CanonicalValues.kt"
    if values.is_file():
        source = values.read_text(encoding="utf-8")
        required = (
            "sealed interface CanonicalValue",
            "CanonicalValueKind",
            "SecretReferenceValue",
            "ExpressionValue",
            "CanonicalArguments",
        )
        for token in required:
            if token not in source:
                problems.append(f"CanonicalValues.kt missing required T04 construct {token!r}")

    if problems:
        print("CANONICAL_TYPED_MODEL: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_TYPED_MODEL: OK — typed value/AST boundaries enforced; "
        "no legacy enum/UI/execution coupling or raw Map<String,String> core contract"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
