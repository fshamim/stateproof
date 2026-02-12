package io.stateproof.gradle

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.URLClassLoader

/**
 * Task to generate interactive viewer files from a state machine.
 */
abstract class StateProofViewerTask : StateProofBaseTask() {

    init {
        outputs.upToDateWhen { false }
    }

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val machineName: Property<String>

    @get:Input
    abstract val viewerLayout: Property<String>

    @get:Input
    abstract val includeJsonSidecar: Property<Boolean>

    override fun configureFrom(extension: StateProofExtension) {
        super.configureFrom(extension)
        outputDir.set(extension.viewerOutputDir)
        machineName.convention("")
        viewerLayout.set(extension.viewerLayout)
        includeJsonSidecar.set(extension.viewerIncludeJsonSidecar)
    }

    override fun configureFromStateMachineConfig(config: StateMachineConfig, extension: StateProofExtension) {
        super.configureFromStateMachineConfig(config, extension)
        outputDir.set(extension.viewerOutputDir)
        machineName.set(config.name)
        viewerLayout.set(extension.viewerLayout)
        includeJsonSidecar.set(extension.viewerIncludeJsonSidecar)
    }

    @TaskAction
    fun generate() {
        val provider = stateMachineInfoProvider.get().trim()
        if (provider.isBlank()) {
            throw GradleException(
                "State machine provider must be set. Either configure single mode " +
                    "or set stateproof.stateMachines { create(\"name\") { factory.set(...) } }."
            )
        }

        val classpath = resolveClasspath()
        ensureViewerCliAvailable(classpath)

        val args = mutableListOf(
            "viewer",
            "--provider", provider,
            "--output-dir", outputDir.get().asFile.absolutePath,
            "--layout", viewerLayout.get(),
            "--include-json-sidecar", includeJsonSidecar.get().toString(),
        )

        val initial = initialState.orNull?.trim()
        if (!initial.isNullOrBlank()) {
            args.addAll(listOf("--initial-state", initial))
        }

        val name = machineName.orNull?.trim()
        if (!name.isNullOrBlank()) {
            args.addAll(listOf("--name", name))
        }

        if (providerIsFactory.getOrElse(false)) {
            args.add("--is-factory")
        }

        logger.lifecycle("Executing: StateProofViewerCli viewer")
        logger.lifecycle("Provider: $provider")

        project.javaexec { spec ->
            spec.classpath = classpath
            spec.mainClass.set(StateProofPlugin.VIEWER_CLI_MAIN_CLASS)
            spec.args = args
            spec.standardOutput = System.out
            spec.errorOutput = System.err
        }
    }

    private fun ensureViewerCliAvailable(classpath: FileCollection) {
        val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
        val loader = URLClassLoader(urls, javaClass.classLoader)
        try {
            Class.forName(StateProofPlugin.VIEWER_CLI_MAIN_CLASS, false, loader)
        } catch (_: ClassNotFoundException) {
            throw GradleException(
                "StateProof viewer classes were not found on the task classpath. " +
                    "Add testImplementation(\"io.stateproof:stateproof-viewer-jvm:0.1.0-SNAPSHOT\") " +
                    "to your module dependencies and rerun."
            )
        } finally {
            loader.close()
        }
    }
}
