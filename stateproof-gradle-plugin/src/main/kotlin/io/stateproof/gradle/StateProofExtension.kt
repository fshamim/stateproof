package io.stateproof.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Extension for configuring the StateProof Gradle plugin.
 *
 * Supports two configuration modes:
 *
 * ## Mode 1: Single State Machine (Simple)
 * ```kotlin
 * stateproof {
 *     // Use factory (recommended - auto-extracts StateInfo)
 *     stateMachineFactory.set("com.example.MainStateMachineKt#getMainStateMachine")
 *     // Or legacy info provider
 *     // stateMachineInfoProvider.set("com.example.MainStateMachineKt#getMainStateMachineInfo")
 *
 *     initialState.set("Initial")
 *     testDir.set(file("src/test/kotlin/generated/stateproof"))
 *     testPackage.set("com.example.test")
 *     testClassName.set("GeneratedMainStateMachineTest")
 * }
 * ```
 *
 * ## Mode 2: Multiple State Machines
 * ```kotlin
 * stateproof {
 *     stateMachines {
 *         create("main") {
 *             factory.set("com.example.MainStateMachineKt#getMainStateMachine")
 *             initialState.set("Initial")
 *             testDir.set(file("src/test/kotlin/generated/main"))
 *             testClassName.set("GeneratedMainStateMachineTest")
 *             stateMachineFactory.set("createTestStateMachine()")
 *         }
 *         create("laser") {
 *             factory.set("com.example.LaserSensorKt#getLaserSensorStateMachine")
 *             initialState.set("Idle")
 *             testDir.set(file("src/test/kotlin/generated/laser"))
 *             testClassName.set("GeneratedLaserSensorTest")
 *         }
 *     }
 * }
 * ```
 */
abstract class StateProofExtension(private val project: Project) {

    /**
     * Container for multiple state machine configurations.
     *
     * Use this when you have multiple state machines in your project.
     * Each state machine gets its own test generation and sync configuration.
     */
    val stateMachines: NamedDomainObjectContainer<StateMachineConfig> =
        project.container(StateMachineConfig::class.java) { name ->
            project.objects.newInstance(StateMachineConfig::class.java, name).apply {
                // Set defaults for each state machine config
                initialState.convention("Initial")
                // NOTE: testDir is NOT set here - it will be derived from the provider package
                // in the task configuration (StateProofGenerateTask.configureFromStateMachineConfig)
                maxVisitsPerState.convention(2)
                maxPathDepth.convention(-1)
                testPackage.convention("")
                testClassName.convention("")  // Will be derived from provider method name
                stateMachineFactory.convention("create${name.replaceFirstChar { it.uppercase() }}StateMachine()")
                eventClassPrefix.convention("Events")
                additionalImports.convention(emptyList<String>())
                testTargets.convention(listOf("jvm"))
                androidTestClassName.convention("")  // Will be derived
                androidAdditionalImports.convention(listOf(
                    "androidx.test.ext.junit.runners.AndroidJUnit4",
                    "org.junit.runner.RunWith",
                ))
            }
        }

    // =====================================================================
    // Single State Machine Properties (backward compatibility)
    // =====================================================================

    /**
     * Factory function that returns a StateMachine instance (RECOMMENDED).
     *
     * Format: "com.package.ClassName#methodName"
     *
     * The function must take no parameters and return StateMachine<*, *>.
     * StateInfo will be extracted automatically using StateMachine.toStateInfo().
     */
    abstract val stateMachineFactoryFqn: Property<String>

    /**
     * Fully qualified state machine info provider function (LEGACY).
     *
     * Format: "com.package.ClassName#methodName"
     *
     * The function must take no parameters and return Map<String, StateInfo>.
     *
     * Use `stateMachineFactoryFqn` instead for new projects.
     */
    abstract val stateMachineInfoProvider: Property<String>

    /**
     * Name of the initial state in the state machine.
     * Default: "Initial"
     */
    abstract val initialState: Property<String>

    /**
     * Directory where generated test files will be written.
     * Default: src/test/kotlin/generated/stateproof
     */
    abstract val testDir: DirectoryProperty

    /**
     * Maximum number of times each state can be visited during path enumeration.
     * Higher values = more test coverage but exponentially more tests.
     * Default: 2 (captures return-to-state scenarios)
     */
    abstract val maxVisitsPerState: Property<Int>

    /**
     * Maximum depth of paths to enumerate.
     * -1 means no limit.
     * Default: -1 (no limit)
     */
    abstract val maxPathDepth: Property<Int>

    /**
     * Package name for generated test files.
     * Default: derived from stateMachineInfoProvider package
     */
    abstract val testPackage: Property<String>

    /**
     * Test class name for generated test file.
     * Default: "GeneratedStateMachineTest"
     */
    abstract val testClassName: Property<String>

    /**
     * State machine factory expression used in generated test code.
     * Default: "createStateMachine()"
     */
    abstract val stateMachineFactory: Property<String>

    /**
     * Event class prefix used in generated test code.
     * Default: "Events"
     */
    abstract val eventClassPrefix: Property<String>

    /**
     * Additional import statements for generated test files.
     * Example: listOf("com.example.States", "com.example.Events")
     */
    abstract val additionalImports: ListProperty<String>

    /**
     * Test targets to generate. Valid values: "jvm", "android".
     * Default: listOf("jvm") — only generate JVM unit tests.
     * Set to listOf("jvm", "android") to generate both.
     */
    abstract val testTargets: ListProperty<String>

    /**
     * Directory where generated Android test files will be written.
     * Default: src/androidTest/kotlin/generated/stateproof
     * Only used when testTargets includes "android".
     */
    abstract val androidTestDir: DirectoryProperty

    /**
     * Test class name for generated Android test file.
     * Default: "GeneratedStateMachineAndroidTest"
     */
    abstract val androidTestClassName: Property<String>

    /**
     * Additional imports for Android tests.
     * Default: AndroidJUnit4 and RunWith imports.
     */
    abstract val androidAdditionalImports: ListProperty<String>

    /**
     * File where the sync report will be written.
     * Default: build/stateproof/sync-report.txt
     */
    abstract val reportFile: RegularFileProperty

    /**
     * Whether to run in dry-run mode (preview changes without writing).
     * Default: false
     */
    abstract val dryRun: Property<Boolean>

    /**
     * Name of the Gradle configuration to use for the classpath when running the CLI.
     *
     * The classpath must include:
     * - The project's compiled main classes (where the state machine is defined)
     * - stateproof-core (for StateInfo class)
     * - Any transitive dependencies needed by the state machine info provider
     *
     * Common values:
     * - "testRuntimeClasspath" (default for JVM projects)
     * - "debugUnitTestRuntimeClasspath" (for Android projects)
     *
     * Default: auto-detect (tries testRuntimeClasspath, then debugUnitTestRuntimeClasspath)
     */
    abstract val classpathConfiguration: Property<String>

    /**
     * Enables auto-discovery via KSP generated registries when no explicit config is provided.
     */
    abstract val autoDiscovery: Property<Boolean>


    init {
        // Set defaults for single-SM mode (backward compatibility)
        stateMachineFactoryFqn.convention("")
        stateMachineInfoProvider.convention("")
        initialState.convention("Initial")
        testDir.convention(project.layout.projectDirectory.dir("src/test/kotlin/generated/stateproof"))
        maxVisitsPerState.convention(2)
        maxPathDepth.convention(-1)
        testPackage.convention("")
        testClassName.convention("GeneratedStateMachineTest")
        stateMachineFactory.convention("createStateMachine()")
        eventClassPrefix.convention("Events")
        additionalImports.convention(emptyList<String>())
        testTargets.convention(listOf("jvm"))
        androidTestDir.convention(project.layout.projectDirectory.dir("src/androidTest/kotlin/generated/stateproof"))
        androidTestClassName.convention("GeneratedStateMachineAndroidTest")
        androidAdditionalImports.convention(listOf(
            "androidx.test.ext.junit.runners.AndroidJUnit4",
            "org.junit.runner.RunWith",
        ))
        reportFile.convention(project.layout.buildDirectory.file("stateproof/sync-report.txt"))
        dryRun.convention(false)
        classpathConfiguration.convention("")
        autoDiscovery.convention(true)
    }

    /**
     * Returns true if multi-SM mode is configured (stateMachines container has entries).
     */
    fun isMultiMode(): Boolean = stateMachines.isNotEmpty()

    /**
     * Returns true if single-SM mode is configured (legacy properties are set).
     */
    fun isSingleMode(): Boolean {
        val hasFactory = stateMachineFactoryFqn.orNull?.isNotBlank() == true
        val hasProvider = stateMachineInfoProvider.orNull?.isNotBlank() == true
        return hasFactory || hasProvider
    }

    /**
     * Gets the effective provider for single-SM mode.
     * Returns Pair<providerFqn, isFactory> where isFactory=true means it's a factory function.
     */
    fun getSingleModeProvider(): Pair<String, Boolean> {
        val factoryFqn = stateMachineFactoryFqn.orNull
        val infoProviderFqn = stateMachineInfoProvider.orNull

        if (!factoryFqn.isNullOrBlank()) {
            return factoryFqn to true
        }
        if (!infoProviderFqn.isNullOrBlank()) {
            return infoProviderFqn to false
        }
        throw org.gradle.api.GradleException(
            "No state machine configuration found. Either:\n" +
                "1. Set stateproof.stateMachineFactoryFqn or stateproof.stateMachineInfoProvider for single-SM mode\n" +
                "2. Use stateproof.stateMachines { create(\"name\") { ... } } for multi-SM mode"
        )
    }

    /**
     * Configures the stateMachines container using a DSL-style action.
     */
    fun stateMachines(action: org.gradle.api.Action<NamedDomainObjectContainer<StateMachineConfig>>) {
        action.execute(stateMachines)
    }
}

