plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.nexaflow.testfixture.locale"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nexaflow.testfixture.locale"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0-test-fixture"
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
}

// This module is intentionally self-contained. It is an installable external test APK and
// must never be added as an implementation dependency of NexaFlow production modules.
