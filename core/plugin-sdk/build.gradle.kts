plugins {
    // AGP 9.x includes built-in Kotlin support; no separate Kotlin plugin.
    alias(libs.plugins.android.library)
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
        
            all {
                // Robolectric 4.17's SDK 36/37 sandboxes require Java 21; run the
                // unit-test JVM on a Java 21 Gradle toolchain (auto-provisioned by
                // Gradle) so tests exercise the real targetSdk (37).
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

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Discovery refresh is explicit and serialized; no background polling is introduced.
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    // Pure framework protocol module: android.os/content/app + org.json only.
    testImplementation(libs.junit.junit)
    testImplementation(libs.androidx.test.core)
    // Bundle round-trips exercise the real android.os.Bundle implementation, so
    // PluginConfigParserTest runs under Robolectric (android.jar stubs throw
    // "not mocked" in plain JVM tests).
    testImplementation(libs.org.robolectric.robolectric)
}
