plugins {
    // AGP 9.x includes built-in Kotlin support; no separate Kotlin plugin.
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.nexaflow.sample.nfctoggle"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nexaflow.sample.nfctoggle"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // ZERO runtime dependencies on purpose: this is the reference plugin that
    // proves the Locale protocol works with only the Android framework (and
    // org.json, which ships inside the OS). Test-only deps never ship in the APK.
    testImplementation(libs.junit.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.org.robolectric.robolectric)
}
