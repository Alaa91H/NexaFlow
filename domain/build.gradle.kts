plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.androidx.core.core.ktx)
    implementation(libs.androidx.paging.paging.common)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    // Runtime for the @Serializable annotations on the domain models (used by
    // BackupManager and the execution-record mapper in :data).
    implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.core)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.json)
    // Only the @Immutable/@Stable annotations are used, so Compose consumers
    // (feature modules) can treat these cross-module models as stable and skip
    // needless recomposition. runtime-annotation is the annotation-only
    // artifact; 1.11.4 matches the compose-bom 2026.06.01 mapping used by the
    // UI modules (pinned explicitly so the transitive constraint propagates).
    implementation(libs.androidx.compose.runtime.runtime.annotation)
    implementation(libs.javax.inject.javax.inject)
    testImplementation(libs.junit.junit)
}
