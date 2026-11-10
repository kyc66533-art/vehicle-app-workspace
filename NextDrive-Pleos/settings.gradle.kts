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
        // Reserved for the separately verified Vehicle SDK integration phase.
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content {
                includeGroup("ai.pleos.playground")
                // STT/TTS 3.3.1 publish this transitive dependency under a separate group.
                includeGroup("ai.pleos.playground.security")
            }
        }
    }
}

rootProject.name = "NextDrive-Pleos"
include(":app")
