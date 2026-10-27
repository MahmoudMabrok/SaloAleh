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
        // Mushaf Imad from GitHub Packages (fork with dynamic feature + asset pack)
        maven {
            url = uri("https://maven.pkg.github.com/MahmoudMabrok/mushaf-imad-android")
            content {
                includeGroup("com.github.YahiaRagae")
            }
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "MahmoudMabrok"
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "SaloAleh"
include(":app")
include(":dhikr-model")
include(":mushaf")
include(":mushaf-assets")
