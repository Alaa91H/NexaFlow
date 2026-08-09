plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.nexaflow.core.execution"
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
    implementation(libs.androidx.core.core.ktx)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    implementation(project(":domain"))
    implementation(project(":core:rom-integration"))
    implementation(project(":core:compatibility"))
    implementation(project(":core:datastore"))
    implementation(project(":core:common"))
    implementation(project(":core:logging"))
    implementation(project(":core:plugin-sdk"))
    testImplementation(libs.junit.junit)
    testImplementation(libs.androidx.paging.paging.common)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.org.robolectric.robolectric)
}
