package io.stateproof.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import java.io.File

/**
 * Base class for StateProof tasks that invoke the CLI via project.javaexec.
 *
 * Handles:
 * - Classpath resolution (auto-detects or uses configured configuration name)
 * - Adding compiled class output directories to the classpath
 * - Common properties (provider, initialState, maxVisits, maxDepth)
 * - JavaExec invocation
 */
abstract class StateProofBaseTask : DefaultTask() {

    @get:Input
    abstract val stateMachineInfoProvider: Property<String>

    @get:Input
    abstract val initialState: Property<String>

    @get:Input
    abstract val maxVisitsPerState: Property<Int>

    @get:Input
    abstract val maxPathDepth: Property<Int>

    @get:Input
    @get:Optional
    abstract val classpathConfiguration: Property<String>

    /**
     * Whether the stateMachineInfoProvider is a factory function (returns StateMachine)
     * or a legacy info provider (returns Map<String, StateInfo>).
     */
    @get:Input
    abstract val providerIsFactory: Property<Boolean>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    /**
     * Configures common properties from the extension (single-SM mode).
     */
    open fun configureFrom(extension: StateProofExtension) {
        val (providerFqn, isFactoryMode) = if (extension.isSingleMode()) {
            extension.getSingleModeProvider()
        } else {
            // If in multi-mode but configureFrom is called, use first SM or error
            throw org.gradle.api.GradleException(
                "Cannot use configureFrom(extension) in multi-SM mode. " +
                    "Use configureFromStateMachineConfig() instead."
            )
        }

        stateMachineInfoProvider.set(providerFqn)
        providerIsFactory.set(isFactoryMode)
        initialState.set(extension.initialState)
        maxVisitsPerState.set(extension.maxVisitsPerState)
        maxPathDepth.set(extension.maxPathDepth)
        classpathConfiguration.set(extension.classpathConfiguration)
        reportFile.set(extension.reportFile)
    }

    /**
     * Configures common properties from a StateMachineConfig (multi-SM mode).
     */
    open fun configureFromStateMachineConfig(config: StateMachineConfig, extension: StateProofExtension) {
        val (providerFqn, isFactoryMode) = config.getEffectiveProvider()

        stateMachineInfoProvider.set(providerFqn)
        providerIsFactory.set(isFactoryMode)
        initialState.set(config.initialState)
        maxVisitsPerState.set(config.maxVisitsPerState)
        maxPathDepth.set(config.maxPathDepth)
        classpathConfiguration.set(extension.classpathConfiguration)
        // Per-SM report file - use fileValue to get proper RegularFile type
        reportFile.fileValue(
            extension.reportFile.get().asFile.parentFile.resolve("${config.name}-sync-report.txt")
        )
    }

    /**
     * Resolves the classpath for the javaexec invocation.
     *
     * For Android projects, the configuration alone doesn't include the project's own
     * compiled classes. We also add the compiled Kotlin/Java class output directories.
     */
    protected fun resolveClasspath(): FileCollection {
        val configName = classpathConfiguration.orNull?.takeIf { it.isNotBlank() }
        var resolvedConfig: org.gradle.api.artifacts.Configuration? = null

        if (configName != null) {
            resolvedConfig = project.configurations.findByName(configName)
            if (resolvedConfig != null) {
                logger.lifecycle("Using classpath configuration: $configName")
            } else {
                logger.warn("Configuration '$configName' not found, falling back to auto-detect")
            }
        }

        if (resolvedConfig == null) {
            // Auto-detect
            val candidates = listOf(
                "testRuntimeClasspath",
                "debugUnitTestRuntimeClasspath",
                "releaseUnitTestRuntimeClasspath",
                "runtimeClasspath",
            )

            for (candidate in candidates) {
                val config = project.configurations.findByName(candidate)
                if (config != null) {
                    logger.lifecycle("Auto-detected classpath configuration: $candidate")
                    resolvedConfig = config
                    break
                }
            }
        }

        if (resolvedConfig == null) {
            throw org.gradle.api.GradleException(
                "Could not find a suitable classpath configuration. " +
                    "Set stateproof.classpathConfiguration to the name of your test runtime configuration. " +
                    "Common values: 'testRuntimeClasspath' (JVM), 'debugUnitTestRuntimeClasspath' (Android)."
            )
        }

        // Explicitly resolve the configuration to actual file paths.
        // For Android projects, we need to resolve through the lenient artifact view
        // to get the actual JAR/AAR files.
        val resolvedFiles: FileCollection = try {
            // Try lenient resolution first (works with Android configurations)
            resolvedConfig.incoming.artifactView { view ->
                view.attributes { attrs ->
                    // Request JAR artifacts specifically (needed for Android AARs)
                    attrs.attribute(
                        org.gradle.api.attributes.Attribute.of(
                            "artifactType",
                            String::class.java
                        ),
                        "android-classes-jar"
                    )
                }
                view.lenient(true)
            }.files
        } catch (e: Exception) {
            // Fallback: resolve normally (works for JVM projects)
            try {
                resolvedConfig.incoming.artifactView { view ->
                    view.lenient(true)
                }.files
            } catch (e2: Exception) {
                project.files(resolvedConfig.resolve())
            }
        }

        var classpath: FileCollection = resolvedFiles
        logger.lifecycle("Resolved ${resolvedFiles.files.size} dependency files")

        // If android-classes-jar resolved very few files, also try jar type
        if (resolvedFiles.files.size < 5) {
            try {
                val jarFiles = resolvedConfig.incoming.artifactView { view ->
                    view.attributes { attrs ->
                        attrs.attribute(
                            org.gradle.api.attributes.Attribute.of(
                                "artifactType",
                                String::class.java
                            ),
                            "jar"
                        )
                    }
                    view.lenient(true)
                }.files
                if (jarFiles.files.isNotEmpty()) {
                    classpath = classpath + jarFiles
                    logger.lifecycle("Added ${jarFiles.files.size} additional jar files")
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Add compiled class output directories to the classpath.
        // For Android projects, the configuration doesn't include the project's own compiled classes.
        val compiledClassDirs = findCompiledClassDirectories()
        if (compiledClassDirs.isNotEmpty()) {
            logger.lifecycle("Adding compiled class directories to classpath:")
            compiledClassDirs.forEach { logger.lifecycle("  ${it.absolutePath}") }
            classpath = classpath + project.files(*compiledClassDirs.toTypedArray())
        }

        return classpath
    }

    /**
     * Finds compiled class directories for the project.
     *
     * Checks common locations for both JVM and Android projects.
     */
    private fun findCompiledClassDirectories(): List<File> {
        val buildDir = project.layout.buildDirectory.get().asFile
        val dirs = mutableListOf<File>()

        // Android Kotlin compiled classes
        val androidKotlinDirs = listOf(
            "tmp/kotlin-classes/debug",
            "tmp/kotlin-classes/release",
        )

        // Android Java compiled classes
        val androidJavaDirs = listOf(
            "intermediates/javac/debug/classes",
            "intermediates/javac/release/classes",
        )

        // JVM compiled classes
        val jvmDirs = listOf(
            "classes/kotlin/main",
            "classes/java/main",
        )

        for (relativePath in androidKotlinDirs + androidJavaDirs + jvmDirs) {
            val dir = File(buildDir, relativePath)
            if (dir.exists() && dir.isDirectory) {
                dirs.add(dir)
            }
        }

        return dirs
    }

    /**
     * Executes the StateProof CLI with the given command and arguments.
     */
    protected fun executeCli(command: String, vararg extraArgs: String) {
        val classpath = resolveClasspath()
        val provider = stateMachineInfoProvider.get()

        val args = mutableListOf(
            command,
            "--provider", provider,
            "--initial-state", initialState.get(),
            "--max-visits", maxVisitsPerState.get().toString(),
        )

        val depth = maxPathDepth.get()
        if (depth != -1) {
            args.addAll(listOf("--max-depth", depth.toString()))
        }

        args.addAll(extraArgs)

        logger.lifecycle("Executing: StateProofCli $command")
        logger.lifecycle("Provider: $provider")

        project.javaexec { spec ->
            spec.classpath = classpath
            spec.mainClass.set(StateProofPlugin.CLI_MAIN_CLASS)
            spec.args = args
            spec.standardOutput = System.out
            spec.errorOutput = System.err
        }
    }
}
