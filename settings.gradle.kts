pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.openrs2.org/repository/openrs2-snapshots")
    }
}

rootProject.name = "kached"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    "js5",
    "downloader"
)
