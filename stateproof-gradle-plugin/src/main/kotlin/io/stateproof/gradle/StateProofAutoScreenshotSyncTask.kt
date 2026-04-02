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
 * Auto-discovery screenshot sync task using KSP registries.
 */
abstract class StateProofAutoScreenshotSyncTask : DefaultTask() {

    init {
        outputs.upToDateWhen { false }
    }

    @get:OutputDirectory
    abstract val screenshotOutputDir: DirectoryProperty

    @get:Input
    abstract val screenshotCaptureMode: Property<String>

    @get:Input
    abstract val screenshotStrict: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val classpathConfiguration: Property<String>

    @TaskAction
    fun syncAll() {
        val classpath = resolveClasspath()
        ensureScreenshotCliAvailable(classpath)

        val args = mutableListOf(
            "sync-all",
            "--output-root-dir", screenshotOutputDir.get().asFile.absolutePath,
            "--capture-mode", screenshotCaptureMode.get(),
            "--strict", screenshotStrict.get().toString(),
        )

        logger.lifecycle("Executing: StateProofScreenshotCli sync-all")

        project.javaexec { spec ->
            spec.classpath = classpath
            spec.mainClass.set(StateProofPlugin.SCREENSHOT_CLI_MAIN_CLASS)
            spec.args = args
            spec.standardOutput = System.out
            spec.errorOutput = System.err
        }
    }

    private fun ensureScreenshotCliAvailable(classpath: FileCollection) {
        val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
        val loader = URLClassLoader(urls, javaClass.classLoader)
        try {
            Class.forName(StateProofPlugin.SCREENSHOT_CLI_MAIN_CLASS, false, loader)
        } catch (_: ClassNotFoundException) {
            throw GradleException(
                "StateProof screenshot classes were not found on the task classpath. " +
                    "Add testImplementation(\"io.github.fshamim:stateproof-screenshot-jvm:0.8.0-alpha02\") " +
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
                "Could not find a suitable classpath configuration for screenshot sync."
            )
        }

        val resolvedFiles: FileCollection = try {
            resolvedConfig.incoming.artifactView { view ->
                view.attributes { attrs ->
                    attrs.attribute(
                        org.gradle.api.attributes.Attribute.of("artifactType", String::class.java),
                        "android-classes-jar",
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

        var classpath = resolvedFiles
        try {
            val jarFiles = resolvedConfig.incoming.artifactView { view ->
                view.attributes { attrs ->
                    attrs.attribute(
                        org.gradle.api.attributes.Attribute.of("artifactType", String::class.java),
                        "jar",
                    )
                }
                view.lenient(true)
            }.files
            if (jarFiles.files.isNotEmpty()) {
                classpath = classpath + jarFiles
            }
        } catch (_: Exception) {
            // Ignore.
        }

        val buildDir = project.layout.buildDirectory.get().asFile
        val classDirs = listOf(
            "tmp/kotlin-classes/debug",
            "tmp/kotlin-classes/release",
            "classes/kotlin/main",
            "classes/java/main",
            "generated/ksp/debug/resources",
            "generated/ksp/release/resources",
            "generated/ksp/main/resources",
        ).map { File(buildDir, it) }
            .filter { it.exists() && it.isDirectory }
        if (classDirs.isNotEmpty()) {
            classpath = classpath + project.files(*classDirs.toTypedArray())
        }

        return classpath
    }
}
