plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nexaflow.data"
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
    implementation(libs.javax.inject.javax.inject)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    // kotlinx.serialization JSON runtime for BackupManager + execution records.
    // R8 keep rules for the generated serializers live in app/proguard-rules.pro
    // (kotlinx is NOT safe to shrink without them).
    implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.json)
    implementation(project(":domain"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:security"))
    implementation(project(":core:plugin-sdk"))
    testImplementation(libs.junit.junit)
}
