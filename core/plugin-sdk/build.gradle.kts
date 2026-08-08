plugins {
    // AGP 9.x includes built-in Kotlin support; no separate Kotlin plugin.
    id("com.android.library")
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
    api("androidx.core:core-ktx:1.19.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    // Bundle round-trips exercise the real android.os.Bundle implementation, so
    // PluginConfigParserTest runs under Robolectric (android.jar stubs throw
    // "not mocked" in plain JVM tests).
    testImplementation("org.robolectric:robolectric:4.16.1")
}
