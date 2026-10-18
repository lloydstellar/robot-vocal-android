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
        /* LA2L, Lloyd's App2Live: the link to Ableton Live (artifacts signed, see docs/research/08) */
        maven("https://lloydstellar.nl/la2l/maven/")
    }
}
rootProject.name = "RobotVocal"
include(":app")
