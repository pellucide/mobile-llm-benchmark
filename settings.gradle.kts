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

    // Version catalog is enabled by default in Gradle 7.4+
    // but we can explicitly reference it if needed
    //versionCatalogs {
        //create("libs") {
            //from(files("gradle/libs.versions.toml"))
        //}
    //}
}

rootProject.name = "SmallModelTest"
include(":app")
