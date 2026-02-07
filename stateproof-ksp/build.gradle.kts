plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "io.stateproof"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":stateproof-annotations"))
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.27")
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("StateProof KSP")
            description.set("KSP processor for StateProof auto-discovery")
            url.set("https://github.com/stateproof/stateproof")
        }
    }
}
