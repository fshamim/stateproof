package io.stateproof.gradle

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Extension for configuring the StateProof Gradle plugin.
 *
 * Example usage in build.gradle.kts:
 * ```kotlin
 * stateproof {
 *     // State machine info provider (REQUIRED)
 *     // For top-level Kotlin functions:
 *     stateMachineInfoProvider.set("com.example.MainStateMachineKt#getMainStateMachineInfo")
 *     // For class companion/static methods:
 *     // stateMachineInfoProvider.set("com.example.MyStateMachine#getStateMachineInfo")
 *
 *     // Initial state name
 *     initialState.set("Initial")
 *
 *     // Test generation settings
 *     testDir.set(file("src/test/kotlin/generated/stateproof"))
 *     maxVisitsPerState.set(2)
 *     maxPathDepth.set(-1)  // -1 = unlimited
 *
 *     // Code generation settings
 *     testPackage.set("com.example.test")
 *     testClassName.set("GeneratedMainStateMachineTest")
 *     stateMachineFactory.set("createTestStateMachine()")
 *     eventClassPrefix.set("Events")
 *     additionalImports.set(listOf(
 *         "com.example.States",
 *         "com.example.Events",
 *     ))
 *
 *     // Sync settings
 *     preserveUserCode.set(true)
 *     autoDeleteObsolete.set(false)
 *
 *     // Report output
 *     reportFile.set(file("build/stateproof/sync-report.txt"))
 *
 *     // Classpath configuration name (default: auto-detect)
 *     // For Android projects, you may need to set this:
 *     // classpathConfiguration.set("debugUnitTestRuntimeClasspath")
 * }
 * ```
 */
abstract class StateProofExtension(project: Project) {

    /**
     * Fully qualified state machine info provider function.
     *
     * Format: "com.package.ClassName#methodName"
     *
     * For top-level Kotlin functions in file MainStateMachine.kt in package com.example.main:
     *   "com.example.main.MainStateMachineKt#getMainStateMachineInfo"
     *
     * For class companion/static methods:
     *   "com.example.MyStateMachine#getStateMachineInfo"
     *
     * The function must take no parameters and return Map<String, StateInfo>.
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
     * Whether to preserve user code outside of STATEPROOF markers during sync.
     * Default: true (strongly recommended)
     */
    abstract val preserveUserCode: Property<Boolean>

    /**
     * Whether to automatically delete obsolete tests.
     * Default: false (tests are marked @Disabled instead)
     */
    abstract val autoDeleteObsolete: Property<Boolean>

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

    init {
        // Set defaults
        initialState.convention("Initial")
        testDir.convention(project.layout.projectDirectory.dir("src/test/kotlin/generated/stateproof"))
        maxVisitsPerState.convention(2)
        maxPathDepth.convention(-1)
        testPackage.convention("")
        testClassName.convention("GeneratedStateMachineTest")
        stateMachineFactory.convention("createStateMachine()")
        eventClassPrefix.convention("Events")
        additionalImports.convention(emptyList<String>())
        preserveUserCode.convention(true)
        autoDeleteObsolete.convention(false)
        reportFile.convention(project.layout.buildDirectory.file("stateproof/sync-report.txt"))
        dryRun.convention(false)
        classpathConfiguration.convention("")
    }
}
