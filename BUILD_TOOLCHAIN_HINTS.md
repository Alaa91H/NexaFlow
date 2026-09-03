# Build toolchain hints

## Why JDK 21 is needed for unit tests

The `:core:execution` and `:app` unit tests use Robolectric 4.17 with SDK 36/37
sandboxes, which require a Java 21 JVM. Both modules pin
`JavaLanguageVersion.of(21)` on their test tasks so CI and local machines get
consistent results.

## How JDK 21 is provided

- **CI**: `actions/setup-java` pre-installs JDK 21 (plus JDK 17 as the build
  JVM) in both the `lint` and `build` jobs, so toolchain detection is
  deterministic and the Foojay resolver has nothing to download.
- **Local / any machine**: the Foojay resolver convention
  (`org.gradle.toolchains.foojay-resolver-convention` in `settings.gradle.kts`)
  auto-provisions JDK 21 when it is not installed. The
  `init.d/foojay-trust-sealed.gradle` init script keeps Gradle's dependency
  verification happy with Foojay's self-referencing sealed POM.

## If a local build fails to find JDK 21

Either:

1. Install a JDK 21 (Temurin, Liberica, etc.) and point `JAVA_HOME` at it, or
2. Rely on the Foojay resolver (it downloads a JDK 21 on demand), or
3. Skip `testDebugUnitTest` and run `detekt` + `lintDebug` only.