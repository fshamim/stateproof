plugins {
    kotlin("jvm")
    `maven-publish`
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

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
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks.named("kotlinSourcesJar"))
            artifact(emptyJavadocJar)
            pom {
                name.set("StateProof KSP")
                description.set("KSP processor for StateProof auto-discovery")
                url.set("https://github.com/stateproof/stateproof")
            }
        }
    }
}
