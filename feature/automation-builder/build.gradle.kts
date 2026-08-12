plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nexaflow.feature.builder"
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
    buildFeatures {
        compose = true
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

composeCompiler {
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
    reportsDestination = layout.buildDirectory.dir("compose-reports")
}

dependencies {
    implementation(libs.androidx.core.core.ktx)
    implementation(platform(libs.androidx.compose.compose.bom))
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.compose.ui.ui.graphics)
    implementation(libs.androidx.compose.ui.ui.tooling.preview)
    implementation(libs.androidx.compose.material3.material3)
    implementation(libs.androidx.navigation.navigation.compose)
    implementation(libs.androidx.hilt.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.lifecycle.viewmodel.ktx)
    debugImplementation(libs.androidx.compose.ui.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.ui.test.manifest)
    implementation(libs.com.google.dagger.hilt.android)
    ksp(libs.com.google.dagger.hilt.compiler)
    implementation(project(":domain"))
    implementation(project(":core:ui-components"))
    implementation(project(":core:plugin-sdk"))
    // Notification action buttons reuse the core model + PendingIntent builder.
    implementation(project(":core:execution"))
    // Real root/Shizuku detection + elevated command execution.
    implementation(project(":core:rom-integration"))
    implementation(libs.dev.rikka.shizuku.api)
    // LocationAccess (silent location toggle + single-shot fix) for the
    // location trigger editor.
    implementation(project(":core:automation-engine"))
    // Embedded Google Maps picker: display + search + point + marker + radius
    // circle. The key is optional (NEXAFLOW_MAPS_API_KEY gradle property);
    // without it the screen shows a setup hint instead of a blank map.
    implementation(libs.com.google.android.gms.play.services.maps)
    testImplementation(libs.junit.junit)
    // Compose UI tests running under Robolectric (semantics assertions on the
    // live badge states). ui-test-manifest is already a debugImplementation.
    testImplementation(libs.androidx.compose.ui.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.org.robolectric.robolectric)
}

