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
    // Zero-tolerance for compiler warnings: any new deprecation, condition-is-
    // always-true, duplicate-branch or unused-import warning fails the build.
    // This prevents the exact regressions fixed in this changeset from
    // silently returning.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
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

// Coverage policy: every Android application/library module emits JaCoCo
// reports for visibility, but an 80% JVM unit-test gate is enforced only for
// modules whose behavior is primarily deterministic/JVM-testable today.
// UI shells, Android framework integrations, Wear, and hardware/privileged
// adapters remain report-only until connected/device coverage is part of the
// policy. This avoids "passing" coverage by excluding production code or
// writing meaningless JVM tests for behavior that only exists on Android.
val strictJvmCoverageThresholds = mapOf(
    ":data" to 0.80,
    ":domain" to 0.80,
    ":core:compatibility" to 0.80,
    ":core:logging" to 0.80,
    ":core:security" to 0.80,
    ":core:wear-protocol" to 0.80,
)

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
        // AGP 9 built-in Kotlin writes source classes below
        // intermediates/built_in_kotlinc/<variant>/compile<Variant>Kotlin,
        // while Java sources remain under intermediates/javac. Read those
        // pre-transform source outputs directly so JaCoCo sees the same class
        // identities that the unit-test agent instruments. Older AGP output
        // shapes remain as compatibility fallbacks.
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
                fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin")) {
                    include("**/*.class")
                    exclude(classExcludes)
                },
                fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
                    include("**/*.class")
                    exclude(classExcludes)
                },
                // Compatibility fallbacks for modules still exposing the older
                // AGP output layouts. These trees are ignored when absent.
                fileTree(layout.buildDirectory.dir("intermediates/runtime_library_classes_dir")) {
                    include("**/*.class")
                    exclude(classExcludes)
                },
                fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                    include("**/*.class")
                    exclude(classExcludes)
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
            val threshold = strictJvmCoverageThresholds[project.path]
            if (threshold == null) {
                println(
                    "COVERAGE_REPORT: ${project.path} " +
                        "${"%.1f".format(ratio * 100)}% (${covered}/${total}) — report-only"
                )
                return@doLast
            }
            if (ratio < threshold) {
                throw GradleException(
                    "coverage gate FAILED for ${project.path}: " +
                        "${"%.1f".format(ratio * 100)}% < ${"%.0f".format(threshold * 100)}% " +
                        "(covered=$covered missed=$missed)"
                )
            }
            println(
                "COVERAGE_GATE: ${project.path} " +
                    "${"%.1f".format(ratio * 100)}% >= " +
                    "${"%.0f".format(threshold * 100)}% (${covered}/${total})"
            )
        }
    }
}
