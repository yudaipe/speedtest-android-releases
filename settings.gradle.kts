pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://maven.pkg.github.com/yudaipe/speedtest-common")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "yudaipe"
                password = System.getenv("GITHUB_TOKEN")
                    ?: (settings.providers.gradleProperty("github.token").orNull ?: "")
            }
        }
    }
}

rootProject.name = "SpeedtestMonitor"
include(":app")
