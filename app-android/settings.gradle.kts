pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        id("com.android.application") version "9.4.1"
        id("com.android.library") version "9.4.1"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "OneOS"
include(":app", ":qube-android-lib")
project(":qube-android-lib").projectDir = file("../vendor/QubeVM/qube-android-lib")
