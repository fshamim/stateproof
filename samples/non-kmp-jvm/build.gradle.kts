plugins {
    kotlin("jvm") version "1.9.21"
    id("io.github.fshamim.stateproof") version "0.8.0-alpha01"
}

group = "io.stateproof.samples"
version = "0.8.0-alpha01"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("io.github.fshamim:stateproof-core-jvm:0.8.0-alpha01")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha01")
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
