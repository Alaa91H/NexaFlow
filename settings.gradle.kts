pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Supply-chain hardening: every resolved dependency (JAR/AAR/plugin) is
// checked against the SHA-256 checksums in gradle/verification-metadata.xml.
// Verification is enabled automatically by the file's presence. Regenerate
// after adding/upgrading dependencies with:
//   ./gradlew --write-verification-metadata sha256 :app:assembleDebug

rootProject.name = "NexaFlow"
include(":app")
include(":baseline-profile")
include(":core:common")
include(":core:security")
include(":core:logging")
include(":core:database")
include(":core:datastore")
include(":core:automation-engine")
include(":core:execution")
include(":core:capability-manager")
include(":core:rom-integration")
include(":core:compatibility")
include(":core:plugin-sdk")
include(":core:ui-components")
include(":domain")
include(":data")
include(":feature:dashboard")
include(":feature:automation-builder")
include(":feature:automations")
include(":feature:history")
include(":feature:icons")
include(":feature:themes")
include(":feature:widgets")
include(":feature:settings")
include(":sample-plugins:nfc-toggle")
