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

rootProject.name = "NexaFlow"
include(":app")
include(":core:database")
include(":core:datastore")
include(":core:automation-engine")
include(":core:execution")
include(":core:capability-manager")
include(":core:rom-integration")
include(":core:ui-components")
include(":domain")
include(":data")
include(":feature:dashboard")
include(":feature:automation-builder")
include(":feature:automations")
include(":feature:profiles")
include(":feature:history")
include(":feature:icons")
include(":feature:themes")
include(":feature:widgets")
include(":feature:settings")
