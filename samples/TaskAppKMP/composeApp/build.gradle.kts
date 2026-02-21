import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    id("com.google.devtools.ksp")
    id("io.github.fshamim.stateproof")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.github.fshamim:stateproof-core:0.8.0-alpha02")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.github.fshamim:stateproof-navigation:0.8.0-alpha02")
                implementation("androidx.navigation:navigation-compose:2.7.7")
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
                implementation("androidx.activity:activity-compose:1.8.2")
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("io.github.fshamim:stateproof-compose:0.8.0-alpha02")
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation("io.github.fshamim:stateproof-compose:0.8.0-alpha02")
                implementation(compose.desktop.currentOs)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.21")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
                implementation("io.github.fshamim:stateproof-viewer-jvm:0.8.0-alpha02")
            }
        }
    }
}

android {
    namespace = "io.stateproof.sample"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    add("kspCommonMainMetadata", "io.github.fshamim:stateproof-ksp:0.8.0-alpha02")
    add("kspDesktop", "io.github.fshamim:stateproof-ksp:0.8.0-alpha02")
}

compose.desktop {
    application {
        mainClass = "io.stateproof.sample.MainKt"
    }
}

stateproof {
    autoDiscovery.set(false)
    stateMachineFactoryFqn.set(
        "io.stateproof.sample.TaskProofIntrospectionKt#createTaskProofStateMachineForIntrospection"
    )
    initialState.set("Splash")
    testDir.set(layout.projectDirectory.dir("src/desktopTest/kotlin/generated/stateproof"))
    testPackage.set("generated.stateproof")
    testClassName.set("GeneratedTaskProofStateMachineTest")
    stateMachineFactory.set("io.stateproof.sample.createTaskProofStateMachineForIntrospection()")
    eventClassPrefix.set("AppEvent")
    additionalImports.set(listOf("io.stateproof.sample.*"))
    diagramOutputDir.set(layout.buildDirectory.dir("stateproof/diagrams"))
    viewerOutputDir.set(layout.buildDirectory.dir("stateproof/viewer"))
    classpathConfiguration.set(
        providers.gradleProperty("stateproofClasspathConfig").orElse("desktopTestRuntimeClasspath")
    )
}

afterEvaluate {
    val prepTask = listOf("compileKotlinDesktop", "compileKotlinJvm", "compileKotlin")
        .firstOrNull { tasks.findByName(it) != null }

    if (prepTask != null) {
        tasks.matching { it.name.startsWith("stateproof") }.configureEach {
            dependsOn(prepTask)
        }
    }
}
