plugins {
    kotlin("jvm") version "1.9.21"
    id("io.stateproof") version "0.1.0-SNAPSHOT"
}

group = "io.stateproof.samples"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation("io.stateproof:stateproof-core-jvm:0.1.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.stateproof:stateproof-viewer-jvm:0.1.0-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}

stateproof {
    autoDiscovery.set(false)

    // Non-KMP fallback uses explicit provider configuration.
    stateMachineFactoryFqn.set("sample.NonKmpStateMachineKt#createSampleStateMachineForIntrospection")
    initialState.set("Idle")

    testDir.set(layout.projectDirectory.dir("src/test/kotlin/generated/stateproof"))
    testPackage.set("sample.generated")
    testClassName.set("GeneratedSampleStateMachineTest")
    stateMachineFactory.set("sample.createSampleStateMachineForIntrospection()")
    eventClassPrefix.set("SampleEvent")
    additionalImports.set(listOf("sample.*"))

    diagramOutputDir.set(layout.buildDirectory.dir("stateproof/diagrams"))
    viewerOutputDir.set(layout.buildDirectory.dir("stateproof/viewer"))
}
