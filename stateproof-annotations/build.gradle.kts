plugins {
    kotlin("jvm")
    `maven-publish`
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

dependencies {
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks.named("kotlinSourcesJar"))
            artifact(emptyJavadocJar)
            pom {
                name.set("StateProof Annotations")
                description.set("Annotations for StateProof KSP auto-discovery")
                url.set("https://github.com/stateproof/stateproof")
            }
        }
    }
}
