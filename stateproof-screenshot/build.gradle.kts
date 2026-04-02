plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    `maven-publish`
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

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
            implementation("androidx.compose.runtime:runtime:1.6.8")
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

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "jvm") {
            artifact(emptyJavadocJar)
        }
    }
}
