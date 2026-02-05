plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    `maven-publish`
}

group = "io.stateproof"
version = "0.1.0-SNAPSHOT"

kotlin {
    // JVM target (Android, Desktop)
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // iOS targets for Compose Multiplatform
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // macOS targets
    macosX64()
    macosArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":stateproof-core"))
            implementation(compose.runtime)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

// Publishing configuration
publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("StateProof Compose")
            description.set("Compose Multiplatform integration for StateProof state machine library")
            url.set("https://github.com/stateproof/stateproof")
        }
    }
}
