// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    // P2-8: static analysis gate (Detekt).
    id("dev.detekt") version "2.0.0-alpha.6" apply false
}

// Centralized Detekt configuration: every module applies the plugin and picks
// up the single curated config file, and the root `detekt` task aggregates all
// of them so one `./gradlew detekt` gates the whole codebase.
val detektConfigDir = rootProject.file("config/detekt")
subprojects {
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        config.setFrom(files("$detektConfigDir/detekt.yml"))
        buildUponDefaultConfig.set(true)
        ignoreFailures.set(false)
        parallel.set(true)
    }
}

tasks.register("detekt") {
    group = "verification"
    description = "Runs Detekt on every module (aggregate gate)."
    dependsOn(subprojects.map { it.tasks.named("detekt") })
}

// Zero-tolerance for unused resources: every Android module (application or
// library) uses the single root lint.xml where UnusedResources is an error, so
// one `./gradlew lintDebug` gates the whole codebase on the real Lint
// analysis (cross-module aware). Any new orphaned string/resource fails CI.
fun Project.configureUnusedResourcesGate() {
    // AGP 9 keeps the lint DSL nested inside the `android` extension (the
    // top-level `lint` extension was removed), so resolve it from there.
    val android = extensions.getByName("android")
    val lintDsl: com.android.build.api.dsl.Lint = when (android) {
        is com.android.build.api.dsl.ApplicationExtension -> android.lint
        is com.android.build.api.dsl.LibraryExtension -> android.lint
        else -> error("Unexpected android extension type: ${android::class.java.name}")
    }
    lintDsl.lintConfig = rootProject.file("lint.xml")
}

subprojects {
    plugins.withId("com.android.application") { configureUnusedResourcesGate() }
    plugins.withId("com.android.library") { configureUnusedResourcesGate() }
}
