package io.stateproof.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URLClassLoader

/**
 * Auto-discovery viewer task that uses KSP-generated registries.
 */
abstract class StateProofAutoViewerTask : DefaultTask() {

    init {
        outputs.upToDateWhen { false }
    }

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val viewerLayout: Property<String>

    @get:Input
    abstract val includeJsonSidecar: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val classpathConfiguration: Property<String>

    @TaskAction
    fun generateAll() {
        val classpath = resolveClasspath()
        ensureViewerCliAvailable(classpath)

        val args = mutableListOf(
            "viewer-all",
            "--output-dir", outputDir.get().asFile.absolutePath,
            "--layout", viewerLayout.get(),
            "--include-json-sidecar", includeJsonSidecar.get().toString(),
        )

        logger.lifecycle("Executing: StateProofViewerCli viewer-all")

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
            throw GradleException(
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
