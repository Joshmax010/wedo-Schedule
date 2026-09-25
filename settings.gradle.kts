pluginManagement {
    repositories {
        // GitHub runners resolve directly from upstream; local builds keep the China mirrors first.
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            gradlePluginPortal()
            mavenCentral()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
        // compose-markdown (com.github.jeziellago) 只在 JitPack 发布
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "wedoSchedule"
include(":app")
