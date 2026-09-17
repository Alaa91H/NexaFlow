# Build toolchain

Use the Gradle wrapper, Android SDK platform 37 and Java 17 or newer for the build. Recent Robolectric sandboxes require Java 21; app, execution and builder test tasks select that toolchain and the required Java module access flags. See the actual Gradle configuration and [required checks](docs/REQUIRED_CHECKS.md).

CI installs Java 17 and Java 21. Locally, install Java 21 or allow the configured Foojay resolver to provision it. Set `ANDROID_HOME` or an ignored `local.properties` SDK path. Toolchain availability is required for the test gate; skipping tests is not an equivalent validation result.

Keep checksum verification enabled. When adding a dependency, review its provenance and regenerate the required verification metadata; do not globally disable verification to fix resolution errors. Repository-local signing files and secrets stay out of Git.
