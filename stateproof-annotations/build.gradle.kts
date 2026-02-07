plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "io.stateproof"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("StateProof Annotations")
            description.set("Annotations for StateProof KSP auto-discovery")
            url.set("https://github.com/stateproof/stateproof")
        }
    }
}
