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

    @TaskAction
    fun generate() {
        val provider = stateMachineInfoProvider.get()
        if (provider.isBlank()) {
            throw org.gradle.api.GradleException(
                "stateMachineInfoProvider must be set in the stateproof extension. " +
                    "Example: stateMachineInfoProvider.set(\"com.example.MainStateMachineKt#getMainStateMachineInfo\")"
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

        val imports = additionalImports.get()
        if (imports.isNotEmpty()) {
            args.addAll(listOf("--imports", imports.joinToString(",")))
        }

        executeCli("generate", *args.toTypedArray())
    }
}
