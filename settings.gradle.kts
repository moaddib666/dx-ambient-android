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
    }
}

rootProject.name = "DxAmbient"

include(":app")
include(":core-domain")
include(":core-data")
include(":core-playback")
include(":core-rendering")
include(":feature-scenes")
include(":feature-library")
include(":feature-settings")
include(":optional-youtube")
