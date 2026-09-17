# Contributing

Use the checked-in Gradle wrapper and the SDK/JDK requirements in [README](README.md). Preserve existing user work when integrating branches. Keep secrets, keystores, local SDK paths and generated build artifacts out of Git.

Every capability change needs an actual runtime path, editor configuration, compatibility/permission policy, useful error behavior and documentation. Catalog counts are not a substitute for implementation. Prefer bounded pure policies plus tests through the real caller. Add regression tests for the failure being fixed; hardware claims require device evidence.

Run the checks in [docs/REQUIRED_CHECKS.md](docs/REQUIRED_CHECKS.md). Update all locale key sets when adding strings and disclose untranslated fallback copy. Run resource hygiene checks before committing. Do not disable dependency verification, lint rules or security gates to obtain a green build.

Write changelog and release notes in English, distinguish verified behavior from planned work, and preserve historical reports as historical evidence. Follow [the release procedure](docs/RELEASING.md) for tags and artifacts.
