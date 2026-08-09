plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.nexaflow.core.database"
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
    sourceSets {
        getByName("test").assets.srcDirs(files("$projectDir/schemas"))
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
    implementation(libs.androidx.paging.paging.common)
    implementation(libs.androidx.room.room.paging)
    implementation(libs.androidx.room.room.runtime)
    ksp(libs.androidx.room.room.compiler)
    implementation(libs.androidx.room.room.ktx)
    // kotlinx.serialization for the Room JSON columns (triggers/actions/
    // constraints configs). The domain models are @Serializable; Gson remains a
    // test-only dependency to prove legacy Gson-shaped rows still parse.
    implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.json)
    implementation(project(":domain"))
    testImplementation(libs.junit.junit)
    testImplementation(libs.com.google.code.gson.gson)
    testImplementation(libs.androidx.room.room.testing)
    testImplementation(libs.androidx.sqlite.sqlite.framework)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.org.robolectric.robolectric)
}
