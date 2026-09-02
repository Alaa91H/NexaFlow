plugins {
    `kotlin-dsl`
    // P2-4: auto-provision a JDK 21 for unit-test toolchains (Robolectric SDK 36/37)
    // on machines without a pre-installed Java 21 (local dev, off-CI). Mirrors CI's
    // JDK 21 runner so unit tests exhibit the same SDK 37 sandbox.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}
