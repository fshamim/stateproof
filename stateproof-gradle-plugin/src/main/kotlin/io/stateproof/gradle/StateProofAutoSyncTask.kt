package io.stateproof.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Auto-discovery sync task that uses KSP-generated registries.
 */
abstract class StateProofAutoSyncTask : DefaultTask() {

    init {
        // Sync must always execute to detect manual test edits/deletions.
        outputs.upToDateWhen { false }
    }

    @get:Input
    abstract val dryRunMode: Property<Boolean>

    @get:OutputDirectory
    abstract val reportDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val classpathConfiguration: Property<String>

    @TaskAction
    fun syncAll() {
        val args = mutableListOf(
            "--report-dir", reportDir.get().asFile.absolutePath,
        )
        if (dryRunMode.get()) {
            args.add("--dry-run")
        }
        executeCli("sync-all", *args.toTypedArray())
    }

    private fun executeCli(command: String, vararg extraArgs: String) {
        val classpath = resolveClasspath()
        val args = mutableListOf(command)
        args.addAll(extraArgs)

        logger.lifecycle("Executing: StateProofCli $command")

        project.javaexec { spec ->
            spec.classpath = classpath
            spec.mainClass.set(StateProofPlugin.CLI_MAIN_CLASS)
            spec.args = args
            spec.standardOutput = System.out
            spec.errorOutput = System.err
        }
    }

    private fun resolveClasspath(): FileCollection {
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

        val resolvedFiles: FileCollection = try {
            resolvedConfig.incoming.artifactView { view ->
                view.attributes { attrs ->
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
        } catch (_: Exception) {
            try {
                resolvedConfig.incoming.artifactView { view ->
                    view.lenient(true)
                }.files
            } catch (_: Exception) {
                project.files(resolvedConfig.resolve())
            }
        }

        var classpath: FileCollection = resolvedFiles
        logger.lifecycle("Resolved ${resolvedFiles.files.size} dependency files")

        // Always add jar artifacts as well to cover non-AAR dependencies (e.g. MockK).
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
                logger.lifecycle("Added ${jarFiles.files.size} jar files")
            }
        } catch (_: Exception) {
            // Ignore
        }

        val compiledClassDirs = findCompiledClassDirectories()
        if (compiledClassDirs.isNotEmpty()) {
            logger.lifecycle("Adding compiled class directories to classpath:")
            compiledClassDirs.forEach { logger.lifecycle("  ${it.absolutePath}") }
            classpath = classpath + project.files(*compiledClassDirs.toTypedArray())
        }

        // Add KSP generated resources (ServiceLoader files live here).
        val kspResourceDirs = findKspResourceDirectories()
        if (kspResourceDirs.isNotEmpty()) {
            logger.lifecycle("Adding KSP resource directories to classpath:")
            kspResourceDirs.forEach { logger.lifecycle("  ${it.absolutePath}") }
            classpath = classpath + project.files(*kspResourceDirs.toTypedArray())
        }

        val androidBootClasspath = findAndroidBootClasspath()
        if (androidBootClasspath.isNotEmpty()) {
            logger.lifecycle("Adding Android boot classpath to classpath:")
            androidBootClasspath.forEach { logger.lifecycle("  ${it.absolutePath}") }
            classpath = classpath + project.files(*androidBootClasspath.toTypedArray())
        }

        return classpath
    }

    private fun findCompiledClassDirectories(): List<File> {
        val buildDir = project.layout.buildDirectory.get().asFile
        val dirs = mutableListOf<File>()

        val androidKotlinDirs = listOf(
            "tmp/kotlin-classes/debug",
            "tmp/kotlin-classes/release",
        )

        val androidJavaDirs = listOf(
            "intermediates/javac/debug/classes",
            "intermediates/javac/release/classes",
        )

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

    private fun findKspResourceDirectories(): List<File> {
        val buildDir = project.layout.buildDirectory.get().asFile
        val dirs = mutableListOf<File>()

        val kspResourcePaths = listOf(
            "generated/ksp/debug/resources",
            "generated/ksp/release/resources",
            "generated/ksp/main/resources",
            "generated/ksp/test/resources",
            "generated/ksp/androidTest/resources",
        )

        for (relativePath in kspResourcePaths) {
            val dir = File(buildDir, relativePath)
            if (dir.exists() && dir.isDirectory) {
                dirs.add(dir)
            }
        }

        return dirs
    }

    private fun findAndroidBootClasspath(): List<File> {
        val androidExt = project.extensions.findByName("android") ?: return emptyList()
        return try {
            val method = androidExt.javaClass.methods.firstOrNull { it.name == "getBootClasspath" }
            val result = method?.invoke(androidExt) as? Iterable<*>
            result?.filterIsInstance<File>()?.filter { it.exists() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
