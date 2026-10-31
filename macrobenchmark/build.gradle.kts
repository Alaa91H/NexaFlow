// Startup macrobenchmark module (P0-3 second half: "Baseline Profiles +
// Macrobenchmark"). A self-instrumenting `com.android.test` module that
// measures cold-start timing against the app under test (:app) so the team
// can prove the baseline profile's benefit and catch startup regressions.
//
// Device-only by nature — benchmarks cannot run in device-less CI, so the
// module is excluded from the CI gate (nothing invokes its assemble tasks);
// it compiles cleanly with the rest of the build and runs on demand:
//
//   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
//
// (Requires an emulator/device; produces startup timings and compares
// `startup()` vs `startupWithBaselineProfile()`.)
plugins {
    id("com.android.test")
}

android {
    namespace = "com.nexaflow.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        // AndroidBenchmarkRunner (an AndroidJUnitRunner subclass) launches the
        // library's IsolationActivity: without it the planner microbenchmark
        // fails with ACTIVITY-MISSING, and macrobenchmark measurements lose
        // sustained performance mode.
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        // The module benchmarks :app; AGP links them for the benchmark run.
        targetProjectPath = ":app"
    }

    // Test modules run one variant per target build type. The default debug
    // variant stays for local debugging; CI benchmarks the `benchmark`
    // variant below so the measured app is not debuggable.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The benchmark variant is the one that measures :app's `benchmark`
    // variant, so the target is non-debuggable and R8-shrunk like production.
    // Library dependencies only ship debug/release, hence the fallback.
    buildTypes {
        create("benchmark") {
            // The benchmark library rejects a debuggable target, and a
            // self-instrumenting module is its own target for the planner
            // microbenchmark, so neither side may keep the debug flag.
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // Required by AGP for self-instrumenting benchmark modules.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.benchmark.benchmark.macro.junit4)
    implementation(libs.androidx.benchmark.benchmark.junit4)
    implementation(project(":domain"))
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator.uiautomator)
}
