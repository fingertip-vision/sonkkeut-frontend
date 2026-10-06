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
rootProject.name = "Sonkkeut"
include(":app")
include(":ui-tooling-probe")
include(":sonkkeut-native")
project(":sonkkeut-native").projectDir = file("../sonkkeut-ai/android/sonkkeut-native")
