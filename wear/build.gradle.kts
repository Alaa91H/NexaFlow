import com.nexaflow.build.gitVersion
import java.util.Properties
import org.gradle.jvm.toolchain.JavaLanguageVersion

val gitVer = gitVersion()

// Release signing — exact :app contract: CI env vars first, then the
// gitignored keystore/keystore.properties, else debug signing so ad-hoc
// watch builds stay installable. The production tag build must end up on
// the same key as the phone APK.
val wearKeystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val wearStorePath = providers.environmentVariable("NEXAFLOW_KEYSTORE_FILE")
    .orNull?.takeIf { it.isNotBlank() }
    ?: wearKeystoreProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }
val wearStoreFile = wearStorePath?.let { rootProject.file(it) }
val wearStorePassword = providers.environmentVariable("NEXAFLOW_KEYSTORE_PASSWORD")
    .orNull?.takeIf { it.isNotBlank() }
    ?: wearKeystoreProps.getProperty("storePassword")
val wearKeyAlias = providers.environmentVariable("NEXAFLOW_KEY_ALIAS")
    .orNull?.takeIf { it.isNotBlank() }
    ?: wearKeystoreProps.getProperty("keyAlias")
val wearKeyPassword = providers.environmentVariable("NEXAFLOW_KEY_PASSWORD")
    .orNull?.takeIf { it.isNotBlank() }
    ?: wearKeystoreProps.getProperty("keyPassword")
val wearSigningConfigured = wearStoreFile?.isFile == true &&
    !wearStorePassword.isNullOrBlank() &&
    !wearKeyAlias.isNullOrBlank() &&
    !wearKeyPassword.isNullOrBlank()

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 provides built-in Kotlin support; applying
    // org.jetbrains.kotlin.android on top of it is an error (and the legacy
    // wearApp embedding configuration no longer exists either).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nexaflow.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nexaflow.wear"
        // Wear OS 3 (API 30) minimum: covers all modern Wear OS devices with
        // stable Compose for Wear support. Wear OS 2.x devices are excluded
        // because they lack the Compose runtime required by the companion UI.
        minSdk = 30
        targetSdk = 37
        // Ride the phone release train: on the version-tag commit both apps
        // build from the same tree — the watch gets the phone's git-derived
        // versionCode plus 1 so paired-device ordering stays unambiguous.
        versionCode = gitVer.versionCode + 1
        versionName = gitVer.versionName
    }

    signingConfigs {
        create("release") {
            if (wearSigningConfigured) {
                storeFile = wearStoreFile
                storePassword = wearStorePassword
                keyAlias = wearKeyAlias
                keyPassword = wearKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with the project keystore when configured; otherwise fall
            // back to the debug keystore (mirrors :app).
            signingConfig = signingConfigs.findByName("release")?.takeIf {
                it.storeFile?.exists() == true
            } ?: signingConfigs.getByName("debug")
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
            all {
                it.javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                })
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.nio=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                )
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Wear OS core
    implementation(libs.com.google.android.gms.play.services.wearable)

    // Compose for Wear OS
    implementation(platform(libs.androidx.compose.compose.bom))
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.compose.ui.ui.tooling.preview)

    // Lifecycle
    implementation(libs.androidx.lifecycle.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.com.google.dagger.hilt.android)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.androidx.hilt.hilt.compiler)

    // kotlinx-serialization for Wear <-> Phone DTO
    implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.android)
    // await() bridging for Play Services Task APIs used by the Data Layer.
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.play.services)

    // Debug
    debugImplementation(libs.androidx.compose.ui.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.ui.test.manifest)

    // Tests
    testImplementation(libs.junit.junit)
    testImplementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.org.robolectric.robolectric)
    testImplementation(libs.org.mockito.kotlin.mockito.kotlin)
    testImplementation(libs.app.cash.turbine.turbine)
}
