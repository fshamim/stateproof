package io.stateproof.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Task to generate static PlantUML/Mermaid diagrams from a state machine.
 */
abstract class StateProofDiagramsTask : StateProofBaseTask() {

    init {
        outputs.upToDateWhen { false }
    }

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val machineName: Property<String>

    @get:Input
    @get:Optional
    abstract val diagramFormat: Property<String>

    override fun configureFrom(extension: StateProofExtension) {
        super.configureFrom(extension)
        outputDir.set(extension.diagramOutputDir)
        machineName.convention("")
        diagramFormat.convention("both")
    }

    override fun configureFromStateMachineConfig(config: StateMachineConfig, extension: StateProofExtension) {
        super.configureFromStateMachineConfig(config, extension)
        outputDir.set(extension.diagramOutputDir)
        machineName.set(config.name)
        diagramFormat.convention("both")
    }

    @TaskAction
    fun generate() {
        val provider = stateMachineInfoProvider.get()
        if (provider.isBlank()) {
            throw org.gradle.api.GradleException(
                "State machine provider must be set. Either configure single mode " +
                    "or set stateproof.stateMachines { create(\"name\") { factory.set(...) } }."
            )
        }

        val args = mutableListOf(
            "--output-dir", outputDir.get().asFile.absolutePath,
        )

        val name = machineName.orNull?.trim()
        if (!name.isNullOrBlank()) {
            args.addAll(listOf("--name", name))
        }

        val format = diagramFormat.orNull?.trim()
        if (!format.isNullOrBlank()) {
            args.addAll(listOf("--format", format))
        }

        if (providerIsFactory.getOrElse(false)) {
            args.add("--is-factory")
        }

        executeCli("diagrams", *args.toTypedArray())
    }
}
