import com.nexaflow.build.encodeVersionCode

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
    // Always emit actionable failure diagnostics in CI. This keeps the
    // zero-warning/zero-failure policy strict while avoiding blind reruns that
    // only report a test name and line number.
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        // A deadlocked Robolectric/JVM test must never leave CI running
        // indefinitely. The timeout is per module Test task, not for the whole
        // multi-module verification build, so healthy suites keep their normal
        // runtime while one stuck module fails with actionable diagnostics.
        timeout.set(java.time.Duration.ofMinutes(10))
        // Bytecode instrumentation used by JaCoCo/Robolectric appends to the
        // bootstrap class path. Disable class-data sharing for test JVMs so
        // OpenJDK does not emit the otherwise harmless CDS warning.
        jvmArgs("-Xshare:off")
        testLogging {
            if (System.getenv("CI").equals("true", ignoreCase = true)) {
                events("started", "failed", "skipped")
            } else {
                events("failed")
            }
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }
}

subprojects {
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        config.setFrom(files("$detektConfigDir/detekt.yml"))
        baseline.set(file("$detektConfigDir/baselines/${project.name}.xml"))
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

tasks.register("detektBaseline") {
    group = "verification"
    description = "Regenerates reviewed Detekt baselines for all modules."
    dependsOn(subprojects.map { it.tasks.named("detektBaseline") })
}

tasks.register("verifyVersionCodeEncoding") {
    group = "verification"
    description = "Proves semantic versionCode ordering and Android range invariants."
    doLast {
        check(encodeVersionCode(3, 58, 9) < encodeVersionCode(3, 58, 10))
        check(encodeVersionCode(3, 58, 999) < encodeVersionCode(3, 59, 0))
        check(encodeVersionCode(3, 999, 999) < encodeVersionCode(4, 0, 0))
        check(encodeVersionCode(3, 90, 0, 99) < encodeVersionCode(3, 90, 1, 0))
        check(encodeVersionCode(3, 90, 0, 100) == encodeVersionCode(3, 90, 0, 99))
        check(encodeVersionCode(3, 90, 0, 10_000) < encodeVersionCode(3, 90, 1, 0))
        check(encodeVersionCode(20, 999, 999, 99) == 2_099_999_999)
        check(runCatching { encodeVersionCode(21, 0, 0) }.isFailure)
        check(runCatching { encodeVersionCode(3, 1000, 0) }.isFailure)
        check(runCatching { encodeVersionCode(3, 90, 1000) }.isFailure)
        check(runCatching { encodeVersionCode(3, 90, 0, -1) }.isFailure)
    }
}

tasks.named("detekt") {
    dependsOn("verifyVersionCodeEncoding")
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
            test.extensions.getByType(JacocoTaskExtension::class.java).apply {
                // Robolectric executes application classes through its SandboxClassLoader.
                // Those transformed classes may have no code-source location, so excluding
                // no-location classes makes JaCoCo report real Robolectric coverage as 0%.
                isIncludeNoLocationClasses = true
                // Keep JDK internals out of agent instrumentation when no-location classes
                // are enabled; they are not production code and can break newer JVMs.
                excludes = listOf("jdk.internal.*")
            }
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
    // Capture all Project-owned values during configuration. Gradle 9.6
    // deprecates Task.project access at execution time and Gradle 10 will
    // reject it, so the task action below must operate only on immutable
    // values/providers prepared here.
    val coverageModulePath = path
    val coverageTestSourceDir = layout.projectDirectory.dir("src/test")
    val coverageReportFile = layout.buildDirectory.file("coverage/report.xml")
    val coverageThreshold = strictJvmCoverageThresholds[coverageModulePath]

    tasks.register("coverageGate") {
        group = "verification"
        description = "Fails when this module's covered-line ratio is below the strict threshold."
        dependsOn(coverageTask)
        doLast {
            val report = coverageReportFile.get().asFile
            if (!coverageTestSourceDir.asFile.exists() || !report.exists()) {
                println("COVERAGE_GATE: $coverageModulePath no unit-test source set — skipped")
                return@doLast
            }
            val xml = report.readText()
            // JaCoCo writes <counter type="LINE" missed="N" covered="M"/>;
            // the last (root-level) counter aggregates the whole module. A
            // module with no executable Kotlin (pure-resource) yields no LINE
            // counter at all — that is a pass by definition, not a failure.
            val counter = Regex("<counter type=\"LINE\" missed=\"(\\d+)\" covered=\"(\\d+)\"/>")
                .findAll(xml).lastOrNull() ?: run {
                println("COVERAGE_GATE: $coverageModulePath no executable code — skipped")
                return@doLast
            }
            val (missed, covered) = counter.destructured
            val total = covered.toInt() + missed.toInt()
            if (total == 0) {
                throw GradleException("coverage report has zero measured lines for $coverageModulePath")
            }
            val ratio = covered.toInt().toDouble() / total
            if (coverageThreshold == null) {
                println(
                    "COVERAGE_REPORT: $coverageModulePath " +
                        "${"%.1f".format(ratio * 100)}% (${covered}/${total}) — report-only"
                )
                return@doLast
            }
            if (ratio < coverageThreshold) {
                throw GradleException(
                    "coverage gate FAILED for $coverageModulePath: " +
                        "${"%.1f".format(ratio * 100)}% < ${"%.0f".format(coverageThreshold * 100)}% " +
                        "(covered=$covered missed=$missed)"
                )
            }
            println(
                "COVERAGE_GATE: $coverageModulePath " +
                    "${"%.1f".format(ratio * 100)}% >= " +
                    "${"%.0f".format(coverageThreshold * 100)}% (${covered}/${total})"
            )
        }
    }
}
