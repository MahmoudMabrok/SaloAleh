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
        // 0.3.0 from JitPack is the last artifact that passed release R8 with
        // this app's Koin 4.0.0 (GH Packages 0.2.0 references removed Koin 3 APIs).
        // feature/asset-pack-support still uses Koin 3.5.6 — do not switch until
        // a Koin-4 build with LineImageSource is published.
        maven("https://jitpack.io") {
            content {
                includeModule("com.github.YahiaRagae.mushaf-imad-android", "mushaf-ui")
                includeModule("com.github.YahiaRagae.mushaf-imad-android", "mushaf-core")
            }
        }
    }
}

rootProject.name = "SaloAleh"
include(":app")
include(":dhikr-model")
include(":mushaf")
// :mushaf-assets sources stay in the tree for a future PAD cutover, but the module is
// not included in the Gradle build until mushaf-ui can load via AssetPackManager
// (Koin-4 + LineImageSource). Including an unused asset-pack module breaks AGP.
// include(":mushaf-assets")
