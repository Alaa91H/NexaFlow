plugins {
    // AGP 9.x includes built-in Kotlin support; no separate Kotlin plugin.
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nexaflow.core.pluginsdk"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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
    // Pure framework protocol module: android.os/content/app + org.json only.
    testImplementation(libs.junit.junit)
    testImplementation(libs.androidx.test.core)
    // Bundle round-trips exercise the real android.os.Bundle implementation, so
    // PluginConfigParserTest runs under Robolectric (android.jar stubs throw
    // "not mocked" in plain JVM tests).
    testImplementation(libs.org.robolectric.robolectric)
}
