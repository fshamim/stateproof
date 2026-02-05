plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.stateproof"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())

    // StateProof core for test generation
    implementation(project(":stateproof-core"))

    // Kotlin reflection for introspection
    implementation(kotlin("reflect"))

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        create("stateproof") {
            id = "io.stateproof"
            implementationClass = "io.stateproof.gradle.StateProofPlugin"
            displayName = "StateProof Plugin"
            description = "Gradle plugin for StateProof state machine test generation and sync"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            pom {
                name.set("StateProof Gradle Plugin")
                description.set("Gradle plugin for StateProof state machine test generation and synchronization")
                url.set("https://github.com/stateproof/stateproof")
            }
        }
    }
}
