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

// P2-4: Resolve a fresh JDK 21 from Foojay so `./gradlew testDebugUnitTest`
// works on any machine without a pre-installed Java 21, mirroring CI's JDK 21
// runner. Robolectric's SDK 36/37 sandboxes rely on it.

// The Foojay resolver convention downloads a matching JDK at build time when none
// is already installed locally. Project-level application is handled by the
// root buildSrc plugin block; this workflow also pins the plugin version so that
// a broken Foojay release cannot silently change the downloaded JDK.

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

// Strict coverage gate: every Android application/library module enables JaCoCo
// for its unit-test variant, and the aggregate `coverageReport` task builds a
// per-module HTML/XML report under build/coverage/. The CI `coverage-gate` job
// reads those XML reports and fails when a module's covered-line ratio drops
// below 80% (see scripts/check_coverage.py) — a regression in test coverage is
// a build failure, not a suggestion.
subprojects {
    plugins.withId("com.android.application") { configureCoverage() }
    plugins.withId("com.android.library") { configureCoverage() }
}

fun Project.configureCoverage() {
    plugins.apply("jacoco")
    @Suppress("UnstableApiUsage")
    val androidExtension = extensions.getByType(com.android.build.api.dsl.CommonExtension::class.java)
    androidExtension.testOptions.apply {
        unitTests.all { test ->
            test.extensions.getByType(JacocoTaskExtension::class.java).isIncludeNoLocationClasses = false
            // Gradle 9.6 + AGP 9: the JaCoCo agent configuration is not yet
            // serializable into the configuration cache; tests + coverage run
            // with configuration-cache disabled for these tasks.
            test.notCompatibleWithConfigurationCache("jacoco agent serialization")
        }
    }
    val coverageTask = tasks.register("coverageReport", JacocoReport::class) {
        group = "verification"
        description = "Aggregates unit-test coverage for this module (debug variant)."
        dependsOn(tasks.matching { it.name.startsWith("testDebugUnitTest") })
        // Pure-resource modules have no test source set at all: their (absent)
        // report is treated as a pass by the gate below.
        onlyIf { file("src/test").exists() }
        reports {
            xml.required.set(true)
            xml.outputLocation.set(layout.buildDirectory.file("coverage/report.xml"))
            html.required.set(true)
            html.outputLocation.set(layout.buildDirectory.dir("coverage/html"))
        }
        // Class directories and sources are wired lazily: the stock jacoco
        // plugin writes exec data to build/jacoco/<task>.exec for every
        // JaCoCo-instrumented test task, AGP and JVM alike.
        executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")) {
            include("*.exec")
        })
        // Both AGP class-output shapes are covered: library modules expose
        // their classes under runtime_library_classes_dir, application modules
        // under the ASM-transformed tree (all Kotlin classes live there). The
        // common generated-code excludes keep the reports focused on product
        // code.
        val classExcludes = listOf(
            "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
            "**/*Test*.*", "**/*_Impl*.*", "**/*_Factory*.*", "**/Dagger*.*",
            "**/*Module_*.*", "**/*Hilt*.*", "**/*_HiltModules*.*", "**/di/*",
            "**/*_GeneratedInjector*.*", "**/*ComponentTreeDeps*.*",
            "**/hilt_aggregated_deps/*", "**/hilt_aggregated_deps/**",
            "**/dagger/**", "**/ui/theme/*", "**/*Preview*.*", "**/*Screen*.*", "**/*Activity*.*",
            // Hardware-bound implementations cannot execute on the JVM: the
            // Android Keystore provider is only exercised on a real device.
            "**/*KeystoreSecureStorage*.*"
        )
        classDirectories.setFrom(
            files(
                fileTree(layout.buildDirectory.dir("intermediates/runtime_library_classes_dir")) {
                    include("**/*.class")
                    exclude(classExcludes)
                },
                fileTree(layout.buildDirectory.dir("intermediates/classes")) {
                    include("**/*.class")
                    exclude(classExcludes + listOf(
                        // Only the hilt/javac outputs are unique under here;
                        // the ASM tree duplicates runtime classes.
                        "**/transformDebugClassesWithAsm/**",
                        "**/transformReleaseClassesWithAsm/**"
                    ))
                }
            )
        )
        sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    }
    tasks.register("coverageGate") {
        group = "verification"
        description = "Fails when this module's covered-line ratio is below the strict threshold."
        dependsOn(coverageTask)
        doLast {
            val report = layout.buildDirectory.file("coverage/report.xml").get().asFile
            if (!file("src/test").exists() || !report.exists()) {
                println("COVERAGE_GATE: ${project.path} no unit-test source set — skipped")
                return@doLast
            }
            val xml = report.readText()
            // JaCoCo writes <counter type="LINE" missed="N" covered="M"/>;
            // the last (root-level) counter aggregates the whole module. A
            // module with no executable Kotlin (pure-resource) yields no LINE
            // counter at all — that is a pass by definition, not a failure.
            val counter = Regex("<counter type=\"LINE\" missed=\"(\\d+)\" covered=\"(\\d+)\"/>")
                .findAll(xml).lastOrNull() ?: run {
                println("COVERAGE_GATE: ${project.path} no executable code — skipped")
                return@doLast
            }
            val (missed, covered) = counter.destructured
            val total = covered.toInt() + missed.toInt()
            if (total == 0) {
                throw GradleException("coverage report has zero measured lines for ${project.path}")
            }
            val ratio = covered.toInt().toDouble() / total
            val threshold = 0.80
            if (ratio < threshold) {
                throw GradleException(
                    "coverage gate FAILED for ${project.path}: " +
                        "${"%.1f".format(ratio * 100)}% < ${"%.0f".format(threshold * 100)}% " +
                        "(covered=$covered missed=$missed)"
                )
            }
            println(
                "COVERAGE_GATE: ${project.path} " +
                    "${"%.1f".format(ratio * 100)}% (${covered}/${total})"
            )
        }
    }
}
