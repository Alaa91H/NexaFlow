plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.nexaflow.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nexaflow.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "3.2.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign release builds with the debug keystore so CI can produce an
            // installable APK without exposing production signing keys.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")

    // Shizuku provider (referenced from the manifest for ROM integration)
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Project Modules
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:logging"))
    implementation(project(":core:automation-engine"))
    implementation(project(":core:execution"))
    implementation(project(":core:capability-manager"))
    implementation(project(":core:rom-integration"))
    implementation(project(":core:ui-components"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:automation-builder"))
    implementation(project(":feature:automations"))
    implementation(project(":feature:history"))
    implementation(project(":feature:icons"))
    implementation(project(":feature:themes"))
    implementation(project(":feature:widgets"))
    implementation(project(":feature:settings"))
}
