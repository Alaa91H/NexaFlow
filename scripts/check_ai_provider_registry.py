#!/usr/bin/env python3
"""Enforce the canonical AI provider registry boundary."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRODUCTION_ROOTS = [
    ROOT / "app/src/main",
    ROOT / "core/ai-runtime/src/main",
    ROOT / "feature/settings/src/main",
]
CATALOG_FILE = (
    ROOT
    / "core/ai-runtime/src/main/java/com/nexaflow/core/airuntime/AiProviderCatalog.kt"
)
SETTINGS_FILE = (
    ROOT
    / "feature/settings/src/main/java/com/nexaflow/feature/settings/AgentSettingsScreen.kt"
)
FACTORY_FILE = (
    ROOT
    / "app/src/main/java/com/nexaflow/app/ai/AiProfileAdapterFactory.kt"
)

errors = []

for source_root in PRODUCTION_ROOTS:
    for path in source_root.rglob("*.kt"):
        if path == CATALOG_FILE:
            continue
        text = path.read_text(encoding="utf-8")
        if "AiProviderCatalog." in text or "import com.nexaflow.core.airuntime.AiProviderCatalog" in text:
            errors.append(
                "production AI provider metadata bypasses registry: "
                + str(path.relative_to(ROOT))
            )

catalog = CATALOG_FILE.read_text(encoding="utf-8")
for token in (
    "object AiProviderDefinitionRegistry",
    "val definitions: List<AiProviderDefinition>",
    "val presets: List<AiProviderPreset>",
    "fun resolveDialect(",
    "fun supportsDialect(",
    "fun supportsAuth(",
):
    if token not in catalog:
        errors.append("canonical provider registry missing " + repr(token))

settings = SETTINGS_FILE.read_text(encoding="utf-8")
for token in (
    "ProviderRegistry.presets",
    "ProviderRegistry.preset(",
):
    if token not in settings:
        errors.append("AI settings is not registry-driven: missing " + repr(token))

factory = FACTORY_FILE.read_text(encoding="utf-8")
if "AiProviderDefinitionRegistry.resolveDialect(" not in factory:
    errors.append("AI profile adapter factory bypasses canonical dialect resolution")

if errors:
    for error in errors:
        print("ERROR:", error)
    raise SystemExit(1)

print("AI provider registry boundary OK")
