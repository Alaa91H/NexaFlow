// Startup macrobenchmark module (P0-3 second half: "Baseline Profiles +
// Macrobenchmark"). A self-instrumenting `com.android.test` module that
// measures cold-start timing against the app under test (:app) so the team
// can prove the baseline profile's benefit and catch startup regressions.
//
// Device-only by nature — benchmarks cannot run in device-less CI, so the
// module is excluded from the CI gate (nothing invokes its assemble tasks);
// it compiles cleanly with the rest of the build and runs on demand:
//
//   ./gradlew :macrobenchmark:connectedDebugAndroidTest
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The module benchmarks :app; AGP links them for the benchmark run.
        targetProjectPath = ":app"
    }

    // Test modules ship only a debug variant; `assembleDebug` at the root
    // resolves cleanly on device-less CI, and no R8 runs over test code.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator.uiautomator)
}
