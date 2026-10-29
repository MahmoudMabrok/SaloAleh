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
        // Only the Mushaf Imad artifacts. Every other coordinate is excluded, so
        // JitPack cannot satisfy a dependency it was not added for.
        // 0.4.x is MahmoudMabrok/mushaf-imad-android (based on upstream 0.3.0):
        // built on Koin 4.0.0 like this app, pluggable LineImageSource, and the page
        // images are NOT inside the AAR any more - they ship in :mushaf-assets.
        maven("https://jitpack.io") {
            content {
                includeModule("com.github.MahmoudMabrok.mushaf-imad-android", "mushaf-ui")
                includeModule("com.github.MahmoudMabrok.mushaf-imad-android", "mushaf-core")
            }
        }
    }
}

rootProject.name = "SaloAleh"
include(":app")
include(":dhikr-model")
include(":mushaf")
// Install-time Play Asset Delivery pack with the Mushaf page images
// (assets/quran-images). Kept out of the base module so app updates stay small.
include(":mushaf-assets")
