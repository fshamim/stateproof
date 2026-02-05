package io.stateproof.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Task to generate test cases from state machine paths.
 *
 * This task:
 * 1. Loads the state machine info via reflection (using the configured provider)
 * 2. Enumerates all paths through the state machine (DFS with visit limits)
 * 3. Generates a Kotlin test file with test cases for each path
 * 4. Writes the test file to the configured output directory
 *
 * Usage: ./gradlew stateproofGenerate
 */
abstract class StateProofGenerateTask : StateProofBaseTask() {

    @get:OutputDirectory
    abstract val testDir: DirectoryProperty

    @get:Input
    abstract val testPackage: Property<String>

    @get:Input
    abstract val testClassName: Property<String>

    @get:Input
    abstract val stateMachineFactory: Property<String>

    @get:Input
    abstract val eventClassPrefix: Property<String>

    @get:Input
    @get:Optional
    abstract val additionalImports: ListProperty<String>

    override fun configureFrom(extension: StateProofExtension) {
        super.configureFrom(extension)
        testDir.set(extension.testDir)
        testPackage.set(extension.testPackage)
        testClassName.set(extension.testClassName)
        stateMachineFactory.set(extension.stateMachineFactory)
        eventClassPrefix.set(extension.eventClassPrefix)
        additionalImports.set(extension.additionalImports)
    }

    override fun configureFromStateMachineConfig(config: StateMachineConfig, extension: StateProofExtension) {
        super.configureFromStateMachineConfig(config, extension)
        
        // Use effective methods that derive from provider FQN if not explicitly set
        val effectivePackage = config.getEffectivePackage()
        testPackage.set(effectivePackage)
        testClassName.set(config.getEffectiveClassName())
        
        // Compute test directory from package if not explicitly set
        // Default: src/test/kotlin/<package-as-path>
        if (!config.testDir.isPresent) {
            val packagePath = effectivePackage.replace('.', '/')
            testDir.set(project.layout.projectDirectory.dir("src/test/kotlin/$packagePath"))
        } else {
            testDir.set(config.testDir)
        }
        
        stateMachineFactory.set(config.stateMachineFactory)
        eventClassPrefix.set(config.eventClassPrefix)
        additionalImports.set(config.additionalImports)
    }

    @TaskAction
    fun generate() {
        val provider = stateMachineInfoProvider.get()
        if (provider.isBlank()) {
            throw org.gradle.api.GradleException(
                "State machine provider must be set. Either:\n" +
                    "1. Set stateproof.stateMachineFactoryFqn or stateproof.stateMachineInfoProvider\n" +
                    "2. Use stateproof.stateMachines { create(\"name\") { factory.set(...) } }"
            )
        }

        val pkg = testPackage.get().ifBlank {
            // Derive from provider
            val className = provider.substringBefore("#")
            className.substringBeforeLast(".")
        }

        val args = mutableListOf(
            "--output-dir", testDir.get().asFile.absolutePath,
            "--package", pkg,
            "--class-name", testClassName.get(),
            "--factory", stateMachineFactory.get(),
            "--event-prefix", eventClassPrefix.get(),
            "--report", reportFile.get().asFile.absolutePath,
        )

        // Pass --is-factory flag if using factory mode (auto-extracts StateInfo)
        if (providerIsFactory.get()) {
            args.add("--is-factory")
        }

        val imports = additionalImports.get()
        if (imports.isNotEmpty()) {
            args.addAll(listOf("--imports", imports.joinToString(",")))
        }

        executeCli("generate", *args.toTypedArray())
    }
}

