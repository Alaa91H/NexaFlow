#!/usr/bin/env python3
"""Enforce the AI credential boundary introduced by T12."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

RUNTIME_FILES = [
    ROOT / "app/src/main/java/com/nexaflow/app/di/AiRuntimeModule.kt",
    ROOT / "feature/settings/src/main/java/com/nexaflow/feature/settings/AgentSettingsViewModel.kt",
]
PREFERENCES = ROOT / "core/datastore/src/main/java/com/nexaflow/core/datastore/AiProviderPreferences.kt"
CREDENTIAL_STORE = ROOT / "core/ai-runtime/src/main/java/com/nexaflow/core/airuntime/AiCredentialStore.kt"

errors: list[str] = []

for path in RUNTIME_FILES:
    source = path.read_text(encoding="utf-8")
    for banned in (
        "secureStorage.get(",
        "secureStorage.put(",
        "secureStorage.remove(",
        "providerApiKeyStorageKey(",
        "OpenAiCompatibleProvider.API_KEY_STORAGE_KEY",
    ):
        if banned in source:
            errors.append(f"{path.relative_to(ROOT)} bypasses AiCredentialStore: {banned}")

preferences = PREFERENCES.read_text(encoding="utf-8")
profile_match = re.search(
    r"data class AiProviderProfileSettings\((.*?)\n\)",
    preferences,
    re.S,
)
if not profile_match:
    errors.append("AiProviderProfileSettings declaration not found")
else:
    profile = profile_match.group(1).lower()
    for forbidden_field in ("apikey", "api_key", "secretvalue", "credentialvalue"):
        if forbidden_field in profile:
            errors.append(
                f"AiProviderProfileSettings must remain metadata-only: {forbidden_field}"
            )

encoded_profile_region = preferences[
    preferences.find("private fun encodeProfiles"):
    preferences.find("private fun validateProfile")
].lower()
for forbidden_field in ('"apikey"', '"api_key"', '"secret"', '"credentialvalue"'):
    if forbidden_field in encoded_profile_region:
        errors.append(f"AI profile serialization contains secret-like field: {forbidden_field}")

store = CREDENTIAL_STORE.read_text(encoding="utf-8")
for required in (
    "interface AiCredentialStore",
    "AiCredentialReference",
    "AiCredentialReferences",
):
    if required not in store:
        errors.append(f"AI credential boundary missing {required}")

if errors:
    print("\n".join(f"ERROR: {item}" for item in errors))
    raise SystemExit(1)

print("AI secret boundary OK")
