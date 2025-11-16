pluginManagement { // plugin setup
    repositories { // plugin sources
        google { // Google plugins
            content { // allowed groups
                includeGroupByRegex("com\\.android.*") // Android plugins
                includeGroupByRegex("com\\.google.*")  // Google plugins
                includeGroupByRegex("androidx.*")      // AndroidX libs
            }
        }
        mavenCentral()        // central repo
        gradlePluginPortal()  // Gradle portal
    }
}

dependencyResolutionManagement { // dependency settings
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // block local repos
    repositories { // dependency sources
        google()             // Google libraries
        mavenCentral()       // Central repo
        maven("https://jitpack.io") // JitPack repo
    }
}

rootProject.name = "Super Reader" // project name
include(":app") // include app module
