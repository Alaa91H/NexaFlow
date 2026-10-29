#!/usr/bin/env python3
"""Module-boundary gate for the layered Gradle graph (NFA-07).

The rules follow the architecture the repository documents:

* ``feature/*`` is a UI layer. It may depend on ``:domain`` and ``core:*``
  capability modules, never on ``:data`` (persistence/backup implementations),
  ``:core:database`` (Room entities), ``:app`` or ``:wear``.
* ``core/*`` is infrastructure. It may depend on ``:domain`` and other
  ``core:*`` modules, never on a ``feature`` module or the app shells.
* ``:core:datastore`` stores preferences, so it must not reach into
  ``:core:ai-runtime`` (the runtime consumes the preferences, not the other
  way around).

Known, reviewed deviations live in ``config/module-boundaries.json`` with an
owner and a reason. They are printed as debt on every run; a *new* edge fails
the gate, so the graph can only improve.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def module_of(relative: Path) -> str:
    return ":" + relative.as_posix().replace("/", ":")


def discover_modules(root: Path) -> dict[str, Path]:
    modules: dict[str, Path] = {}
    for build_file in root.glob("*/build.gradle.kts"):
        modules[module_of(build_file.parent.relative_to(root))] = build_file
    for build_file in root.glob("*/*/build.gradle.kts"):
        modules[module_of(build_file.parent.relative_to(root))] = build_file
    return modules


def dependencies(build_file: Path) -> set[str]:
    text = build_file.read_text(encoding="utf-8")
    return set(re.findall(r'project\(\s*"(:[^"]+)"\s*\)', text))


def is_feature(module: str) -> bool:
    return module.startswith(":feature:")


def is_core(module: str) -> bool:
    return module.startswith(":core:")


def edge_is_forbidden(module: str, dependency: str) -> str | None:
    """Returns the rule name when the edge breaks a boundary, else None."""
    if is_feature(module):
        if dependency in {":app", ":wear"}:
            return "feature-to-shell"
        if dependency in {":data", ":core:database"}:
            return "feature-to-persistence"
    if is_core(module):
        if dependency.startswith(":feature:") or dependency in {":app", ":wear"}:
            return "core-to-upper-layer"
    if module == ":core:datastore" and dependency == ":core:ai-runtime":
        return "datastore-to-ai-runtime"
    return None


def load_allowlist(path: Path) -> set[tuple[str, str]]:
    if not path.exists():
        return set()
    data = json.loads(path.read_text(encoding="utf-8"))
    allowed = set()
    for entry in data.get("allowed", []):
        if not entry.get("from") or not entry.get("to") or not entry.get("reason"):
            raise SystemExit(f"module-boundaries: allowlist entry needs from/to/reason: {entry}")
        allowed.add((entry["from"], entry["to"]))
    return allowed


def audit(root: Path, allowlist: set[tuple[str, str]]) -> tuple[list[str], list[str]]:
    violations: list[str] = []
    debt: list[str] = []
    for module, build_file in sorted(discover_modules(root).items()):
        for dependency in sorted(dependencies(build_file)):
            rule = edge_is_forbidden(module, dependency)
            if rule is None:
                continue
            if (module, dependency) in allowlist:
                debt.append(f"{module} -> {dependency} ({rule})")
            else:
                violations.append(f"{module} -> {dependency} breaks {rule}")
    return violations, debt


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(ROOT))
    parser.add_argument(
        "--allowlist",
        default=str(ROOT / "config" / "module-boundaries.json"),
    )
    args = parser.parse_args(argv)
    root = Path(args.root)
    violations, debt = audit(root, load_allowlist(Path(args.allowlist)))
    for entry in debt:
        print(f"MODULE_BOUNDARY_DEBT: {entry}")
    for violation in violations:
        print(f"MODULE_BOUNDARY_VIOLATION: {violation}")
    print(f"MODULE_BOUNDARY_VIOLATIONS: {len(violations)} (allowlisted debt: {len(debt)})")
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main())
