// Baseline Profile generator module (P0-3).
//
// Generates a Baseline Profile from the app's Critical User Journeys so the
// release APK can pre-compile the hot startup/rendering paths. The generated
// file is checked in at app/src/main/baseline-prof.txt (AGP bundles it into
// release builds automatically), which keeps device-less CI green — the
// device-dependent generation task below only runs when a developer invokes it
// explicitly with a connected emulator/device.
//
// Regenerate on a device (rooted or API 33+):
//   ./gradlew :baseline-profile:connectedAndroidTest
// then copy build/outputs/.../BaselineProfileGenerator_generate-baseline-prof.txt
// (or the merged profile printed by the task) over app/src/main/baseline-prof.txt.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nexaflow.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    // Keep both variants so plain `assembleDebug`/`assembleRelease` at the root
    // resolve cleanly on device-less CI; the module ships no runtime code.
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    androidTestImplementation(libs.androidx.benchmark.benchmark.macro.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator.uiautomator)
}
