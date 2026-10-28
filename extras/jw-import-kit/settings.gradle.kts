pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.library" || requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
    repositories {
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "WedoJwImportKit"
include(":library", ":demo")
