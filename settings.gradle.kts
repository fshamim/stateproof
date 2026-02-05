rootProject.name = "stateproof"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":stateproof-core")
include(":stateproof-compose")
include(":stateproof-navigation")
include(":stateproof-gradle-plugin")
