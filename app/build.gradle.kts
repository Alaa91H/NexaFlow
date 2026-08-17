import com.nexaflow.build.gitVersion
import java.util.Properties
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val gitVer = gitVersion()

// Release signing: prefer the project keystore (keystore/keystore.properties,
// gitignored — carries the SAME key that signed the currently installed app,
// so updates install over it without data loss). Falls back to CI-provided
// env vars, then to the debug keystore so ad-hoc builds never break.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStoreFile = providers.environmentVariable("NEXAFLOW_KEYSTORE_FILE")
    .orNull?.takeIf { it.isNotBlank() }
    ?: keystoreProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }
val releaseStorePassword = providers.environmentVariable("NEXAFLOW_KEYSTORE_PASSWORD")
    .orNull?.takeIf { it.isNotBlank() }
    ?: keystoreProps.getProperty("storePassword")
val releaseKeyAlias = providers.environmentVariable("NEXAFLOW_KEY_ALIAS")
    .orNull?.takeIf { it.isNotBlank() }
    ?: keystoreProps.getProperty("keyAlias")
val releaseKeyPassword = providers.environmentVariable("NEXAFLOW_KEY_PASSWORD")
    .orNull?.takeIf { it.isNotBlank() }
    ?: keystoreProps.getProperty("keyPassword")

android {
    namespace = "com.nexaflow.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nexaflow.app"
        minSdk = 26
        targetSdk = 37
        versionCode = gitVer.versionCode
        versionName = gitVer.versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Ship only the locales the app actually translates: prunes the
        // bundled resources of every library for the ~40 unsupported locales
        // (smaller APK, consistent fallback). Keep in sync with
        // res/xml/locales_config.xml and the values-* string directories.
        resourceConfigurations += listOf(
            "en", "ar", "de", "es", "fr", "hi", "ja", "pt", "ru", "tr", "zh-rCN"
        )

        // Sentry DSN for crash reporting (P0-2). Populated from CI env; empty in
        // local builds so Sentry stays inert until the user opts in AND a DSN
        // is configured. Never commit a real DSN to source control.
        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"${providers.gradleProperty("NEXAFLOW_SENTRY_DSN").orElse("").get()}\""
        )
        // Google Maps API key for the embedded map picker. Optional: an empty
        // key renders a setup hint instead of a map. Resolved the same way as
        // the signing secrets: CI env var, then keystore.properties
        // (gitignored), then empty. Never commit a real key.
        val mapsApiKey = providers.environmentVariable("NEXAFLOW_MAPS_API_KEY")
            .orNull?.takeIf { it.isNotBlank() }
            ?: keystoreProps.getProperty("mapsApiKey")?.takeIf { it.isNotBlank() }
            .orEmpty()
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric needs the merged manifest + resources on the test
            // classpath to inspect providers and run Android framework code.
            isIncludeAndroidResources = true
            all {
                // The app targets SDK 37; Robolectric 4.17 supports it, but its
                // SDK 36/37 sandboxes need a Java 21 JVM. Run the unit-test JVM
                // on a Java 21 Gradle toolchain (auto-provisioned by Gradle) so
                // tests exercise the real targetSdk (37) instead of pinning
                // @Config(sdk=[35]). On JDK 16+ Robolectric also needs
                // --add-opens flags to reach OpenJDK internals.
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
                    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
                    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                    "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
                    "--add-opens=java.base/sun.security.action=ALL-UNNAMED",
                    "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                )            }
        }
    }
}

    signingConfigs {
        create("release") {
            // Only configure when a real keystore is available; the release
            // build type falls back to the debug config otherwise.
            val storeFileValue = releaseStoreFile?.let { file(it) }
            if (storeFileValue?.exists() == true && !releaseStorePassword.isNullOrBlank()
                && !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()
            ) {
                storeFile = storeFileValue
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8 full code shrinking + resource shrinking: smaller APK and
            // faster cold start for end users. Keep rules for the reflection
            // surfaces (Shizuku, Gson-serialized models) live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign with the project keystore when configured; otherwise fall
            // back to the debug keystore so CI/ad-hoc builds stay installable.
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

// Compose Compiler metrics: emits per-module skippability/stability reports to
// build/compose-metrics + build/compose-reports so the team can audit which
// composables skip recomposition and which classes are treated as unstable.
composeCompiler {
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
    reportsDestination = layout.buildDirectory.dir("compose-reports")
}

dependencies {
    implementation(libs.androidx.core.core.ktx)
    implementation(libs.androidx.core.core.splashscreen)
    implementation(libs.androidx.lifecycle.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.activity.compose)
    implementation(platform(libs.androidx.compose.compose.bom))
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.compose.ui.ui.graphics)
    implementation(libs.androidx.compose.ui.ui.tooling.preview)
    implementation(libs.androidx.compose.material3.material3)
    implementation(libs.androidx.compose.material3.window.size)
    testImplementation(libs.junit.junit)
    // Robolectric: the merged-manifest test asserts SentryInitProvider is
    // stripped (tools:node="remove") so a DSN-less build boots, and the
    // SentryReporter tests exercise the enable/disable paths on the JVM.
    testImplementation(libs.org.robolectric.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.ui.test.manifest)

    // Hilt
    implementation(libs.com.google.dagger.hilt.android)
    ksp(libs.com.google.dagger.hilt.compiler)

    // Navigation Compose
    implementation(libs.androidx.navigation.navigation.compose)
    implementation(libs.androidx.work.work.runtime.ktx)
    implementation(libs.androidx.hilt.hilt.work)
    ksp(libs.androidx.hilt.hilt.compiler)
    // Crash/ANR reporting, opt-in only (see PrivacyPreferences). The NDK
    // artifact is excluded: NexaFlow is pure Kotlin/Java, so native crash
    // handling is unnecessary and its .so files break 16 KB page alignment.
    implementation(libs.io.sentry.sentry.android) {
        exclude(group = "io.sentry", module = "sentry-android-ndk")
    }
    // Applies the bundled baseline profile (app/src/main/baseline-prof.txt) at
    // install time on API 29+, pre-compiling the startup/hot path (P0-3).
    implementation(libs.androidx.profileinstaller.profileinstaller)

    // Room
    implementation(libs.androidx.room.room.runtime)

    // Shizuku provider (referenced from the manifest for ROM integration)
    implementation(libs.dev.rikka.shizuku.provider)

    // Project Modules
    implementation(project(":core:database"))
    implementation(project(":core:security"))
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
