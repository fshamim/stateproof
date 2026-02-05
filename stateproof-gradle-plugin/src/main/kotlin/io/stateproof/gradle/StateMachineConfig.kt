package io.stateproof.gradle

import org.gradle.api.Named
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import javax.inject.Inject

/**
 * Configuration for a single state machine.
 *
 * Used within the `stateproof.stateMachines` container to configure
 * multiple state machines in one project.
 *
 * Example usage in build.gradle.kts:
 * ```kotlin
 * stateproof {
 *     stateMachines {
 *         create("main") {
 *             // Use factory function (recommended - auto-extracts StateInfo)
 *             factory.set("com.example.MainStateMachineKt#getMainStateMachine")
 *             // Or use legacy info provider (returns Map<String, StateInfo>)
 *             // infoProvider.set("com.example.MainStateMachineKt#getMainStateMachineInfo")
 *
 *             initialState.set("Initial")
 *             testDir.set(file("src/test/kotlin/generated/main"))
 *             testPackage.set("com.example.test")
 *             testClassName.set("GeneratedMainStateMachineTest")
 *             stateMachineFactory.set("createTestStateMachine()")
 *             eventClassPrefix.set("Events")
 *             additionalImports.set(listOf("com.example.States", "com.example.Events"))
 *         }
 *         create("laser") {
 *             factory.set("com.example.LaserSensorKt#getLaserSensorStateMachine")
 *             initialState.set("Idle")
 *             testDir.set(file("src/test/kotlin/generated/laser"))
 *             testPackage.set("com.example.laser.test")
 *             testClassName.set("GeneratedLaserSensorTest")
 *             stateMachineFactory.set("createLaserSensorStateMachine()")
 *             eventClassPrefix.set("LaserEvents")
 *         }
 *     }
 * }
 * ```
 */
abstract class StateMachineConfig @Inject constructor(
    private val configName: String
) : Named {

    @Internal
    override fun getName(): String = configName

    /**
     * Factory function that returns a StateMachine instance.
     *
     * Format: "com.package.ClassName#methodName"
     *
     * The function must take no parameters and return StateMachine<*, *>.
     * StateInfo will be extracted automatically using StateMachine.toStateInfo().
     *
     * This is the **recommended** approach for new projects.
     */
    @get:Input
    @get:Optional
    abstract val factory: Property<String>

    /**
     * Legacy info provider function that returns Map<String, StateInfo>.
     *
     * Format: "com.package.ClassName#methodName"
     *
     * Use `factory` instead for new projects - it auto-extracts StateInfo.
     */
    @get:Input
    @get:Optional
    abstract val infoProvider: Property<String>

    /**
     * Name of the initial state in the state machine.
     * Default: "Initial"
     */
    @get:Input
    abstract val initialState: Property<String>

    /**
     * Directory where generated test files will be written.
     * Default: src/test/kotlin/generated/stateproof/{name}
     */
    @get:OutputDirectory
    abstract val testDir: DirectoryProperty

    /**
     * Maximum number of times each state can be visited during path enumeration.
     * Default: 2
     */
    @get:Input
    abstract val maxVisitsPerState: Property<Int>

    /**
     * Maximum depth of paths to enumerate. -1 means no limit.
     * Default: -1
     */
    @get:Input
    abstract val maxPathDepth: Property<Int>

    /**
     * Package name for generated test files.
     */
    @get:Input
    @get:Optional
    abstract val testPackage: Property<String>

    /**
     * Test class name for generated test file.
     */
    @get:Input
    abstract val testClassName: Property<String>

    /**
     * State machine factory expression used in generated test code.
     */
    @get:Input
    abstract val stateMachineFactory: Property<String>

    /**
     * Event class prefix used in generated test code.
     */
    @get:Input
    abstract val eventClassPrefix: Property<String>

    /**
     * Additional import statements for generated test files.
     */
    @get:Input
    @get:Optional
    abstract val additionalImports: ListProperty<String>

    /**
     * Gets the effective provider FQN (either factory or legacy infoProvider).
     * Returns Pair<String, Boolean> where Boolean is true if it's a factory.
     */
    fun getEffectiveProvider(): Pair<String, Boolean> {
        val factoryFqn = factory.orNull
        val infoProviderFqn = infoProvider.orNull

        if (!factoryFqn.isNullOrBlank()) {
            return factoryFqn to true
        }
        if (!infoProviderFqn.isNullOrBlank()) {
            return infoProviderFqn to false
        }
        throw org.gradle.api.GradleException(
            "State machine '$configName' must have either 'factory' or 'infoProvider' set. " +
                "Recommended: factory.set(\"com.package.FileKt#getStateMachine\")"
        )
    }

    /**
     * Derives the package name from the provider FQN.
     *
     * Example: "com.mubea.icages.main.MainStateMachineKt#getMainStateMachine"
     *        → "com.mubea.icages.main"
     */
    fun getEffectivePackage(): String {
        val explicit = testPackage.orNull
        if (!explicit.isNullOrBlank()) {
            return explicit
        }
        // Derive from provider: "com.package.ClassName#method" → "com.package"
        val (providerFqn, _) = getEffectiveProvider()
        val className = providerFqn.substringBefore("#")
        return className.substringBeforeLast(".")
    }

    /**
     * Derives the test class name from the provider FQN.
     *
     * Example: "com.mubea.icages.main.MainStateMachineKt#getMainStateMachine"
     *        → "GeneratedMainStateMachineTest"
     */
    fun getEffectiveClassName(): String {
        val explicit = testClassName.orNull
        if (!explicit.isNullOrBlank()) {
            return explicit
        }
        // Derive from provider: "com.package.MainStateMachineKt#getMainStateMachine" 
        // → "GeneratedMainStateMachineTest"
        val (providerFqn, _) = getEffectiveProvider()
        val methodName = providerFqn.substringAfter("#", "")
        
        // Try to extract a meaningful name from the method name
        val baseName = when {
            methodName.startsWith("get") -> methodName.removePrefix("get")
            methodName.startsWith("create") -> methodName.removePrefix("create")
            methodName.isNotBlank() -> methodName.replaceFirstChar { it.uppercase() }
            else -> {
                // Fall back to class name
                val className = providerFqn.substringBefore("#").substringAfterLast(".")
                className.removeSuffix("Kt")
            }
        }
        return "Generated${baseName}Test"
    }
}

