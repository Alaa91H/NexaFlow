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
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

// Centralized Detekt configuration: every module applies the plugin and picks
// up the single curated config file, and the root `detekt` task aggregates all
// of them so one `./gradlew detekt` gates the whole codebase.
val detektConfigDir = rootProject.file("config/detekt")
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(files("$detektConfigDir/detekt.yml"))
        buildUponDefaultConfig = true
        ignoreFailures = false
        parallel = true
    }
}

tasks.register("detekt") {
    group = "verification"
    description = "Runs Detekt on every module (aggregate gate)."
    dependsOn(subprojects.map { it.tasks.named("detekt") })
}
