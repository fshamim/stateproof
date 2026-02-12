plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "io.stateproof"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":stateproof-core"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.junit.jupiter:junit-jupiter:5.10.2")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
