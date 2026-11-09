pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only the Mushaf text library (MahmoudMabrok/mushaf-text-customizable).
        // Every other coordinate is excluded, so JitPack cannot satisfy a
        // dependency it was not added for.
        maven("https://jitpack.io") {
            content {
                includeModule("com.github.MahmoudMabrok", "mushaf-text-customizable")
            }
        }
    }
}

rootProject.name = "SaloAleh"
include(":app")
include(":dhikr-model")
