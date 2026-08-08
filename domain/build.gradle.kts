plugins {
    id("com.android.library")
}

android {
    namespace = "com.nexaflow.domain"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    // Only the @Immutable/@Stable annotations are used, so Compose consumers
    // (feature modules) can treat these cross-module models as stable and skip
    // needless recomposition. runtime-annotation is the annotation-only
    // artifact; 1.11.4 matches the compose-bom 2026.06.01 mapping used by the
    // UI modules (pinned explicitly so the transitive constraint propagates).
    implementation("androidx.compose.runtime:runtime-annotation:1.11.4")
    implementation("javax.inject:javax.inject:1")
    testImplementation("junit:junit:4.13.2")
}
