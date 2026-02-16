plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

val pluginSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

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
            id = "io.github.fshamim.stateproof"
            implementationClass = "io.stateproof.gradle.StateProofPlugin"
            displayName = "StateProof Plugin"
            description = "Gradle plugin for StateProof state machine test generation and sync"
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifact(pluginSourcesJar)
            artifact(emptyJavadocJar)
        }
        pom {
            name.set("StateProof Gradle Plugin")
            description.set("Gradle plugin for StateProof state machine test generation and synchronization")
            url.set("https://github.com/stateproof/stateproof")
        }
    }
}
