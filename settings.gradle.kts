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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ComposeBreakout"
// :engine is the game itself — pure Kotlin, no Android — so its tests and the
// Auto Play bench run on any JVM in seconds. :app is the Compose shell.
include(":engine")
include(":app")
