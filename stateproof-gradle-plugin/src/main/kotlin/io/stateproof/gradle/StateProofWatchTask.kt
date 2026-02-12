package io.stateproof.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.lang.ProcessBuilder.Redirect
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Polling watch task that debounces source changes and triggers StateProof CLI actions.
 *
 * Note: this task executes long-running in-process CLI actions and is intended for local dev loops.
 */
abstract class StateProofWatchTask : DefaultTask() {

    init {
        outputs.upToDateWhen { false }
    }

    @get:Input
    abstract val watchMode: Property<String>

    @get:Input
    abstract val watchDebounceMs: Property<Long>

    @get:Input
    abstract val watchPaths: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val classpathConfiguration: Property<String>

    /**
     * Encoded command specs: "<label><US><mainClass><US><arg1><US><arg2>..."
     */
    @get:Input
    abstract val syncCommands: ListProperty<String>

    /**
     * Encoded command specs: "<label><US><mainClass><US><arg1><US><arg2>..."
     */
    @get:Input
    abstract val diagramCommands: ListProperty<String>

    /**
     * Encoded command specs: "<label><US><mainClass><US><arg1><US><arg2>..."
     */
    @get:Input
    abstract val viewerCommands: ListProperty<String>

    @get:Input
    abstract val prepTaskNames: ListProperty<String>

    @get:Input
    abstract val targetProjectPath: Property<String>

    @TaskAction
    fun watch() {
        val mode = normalizeMode(watchMode.getOrElse("all"))
        val debounceMs = watchDebounceMs.getOrElse(1200L).coerceAtLeast(100L)
        val resolvedWatchRoots = resolveWatchRoots(watchPaths.getOrElse(emptyList()))
        if (resolvedWatchRoots.isEmpty()) {
            throw GradleException(
                "stateproofWatch has no valid watch paths. Configure stateproof.watchPaths with at least one path."
            )
        }

        val commands = commandsForMode(mode)
        if (commands.isEmpty()) {
            throw GradleException(
                "stateproofWatch has no configured commands for mode '$mode'. " +
                    "Check StateProof plugin task wiring."
            )
        }

        logger.lifecycle("StateProof watch started")
        logger.lifecycle("Mode: $mode")
        logger.lifecycle("Debounce: ${debounceMs}ms")
        logger.lifecycle("Watching:")
        resolvedWatchRoots.forEach { logger.lifecycle("  - ${it.absolutePath}") }
        logger.lifecycle("Actions:")
        commands.forEach { logger.lifecycle("  - ${it.label}") }
        logger.lifecycle("Press Ctrl+C to stop.")

        val running = AtomicBoolean(true)
        val shutdownHook = Thread { running.set(false) }
        Runtime.getRuntime().addShutdownHook(shutdownHook)

        try {
            var snapshot = captureSnapshot(resolvedWatchRoots)
            var pendingSince: Long? = null
            val pendingPaths = linkedSetOf<String>()

            while (running.get()) {
                Thread.sleep(POLL_INTERVAL_MS)

                val current = captureSnapshot(resolvedWatchRoots)
                val changedPaths = diffSnapshot(snapshot, current)
                if (changedPaths.isNotEmpty()) {
                    pendingSince = System.currentTimeMillis()
                    pendingPaths.addAll(changedPaths.take(MAX_LOGGED_CHANGED_PATHS))
                    snapshot = current
                }

                val changeStart = pendingSince
                if (changeStart != null && System.currentTimeMillis() - changeStart >= debounceMs) {
                    val cause = pendingPaths
                        .take(MAX_LOGGED_CHANGED_PATHS)
                        .joinToString(", ")
                        .ifBlank { "(changes detected)" }
                    logger.lifecycle(
                        "Detected changes (${pendingPaths.size} path(s)); running watch actions due to: $cause"
                    )

                    runPrepTasks()
                    executeCommands(commands)

                    pendingPaths.clear()
                    pendingSince = null
                    snapshot = captureSnapshot(resolvedWatchRoots)
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            } catch (_: IllegalStateException) {
                // JVM is shutting down.
            }
            logger.lifecycle("StateProof watch stopped")
        }
    }

    private fun commandsForMode(mode: String): List<CliCommand> {
        val tests = decodeCommands(syncCommands.getOrElse(emptyList()))
        val diagrams = decodeCommands(diagramCommands.getOrElse(emptyList()))
        val viewers = decodeCommands(viewerCommands.getOrElse(emptyList()))
        return when (mode) {
            "tests" -> tests
            "diagram" -> diagrams
            "viewer" -> viewers
            "all" -> tests + diagrams + viewers
            else -> emptyList()
        }
    }

    private fun decodeCommands(encoded: List<String>): List<CliCommand> {
        return encoded
            .map { item ->
                val tokens = item.split(StateProofPlugin.WATCH_COMMAND_SEPARATOR)
                if (tokens.size < 2) {
                    throw GradleException("Invalid command spec: '$item'")
                }
                val label = tokens[0]
                val mainClass = tokens[1]
                val args = if (tokens.size > 2) tokens.drop(2) else emptyList()
                CliCommand(label = label, mainClass = mainClass, args = args)
            }
            .sortedBy { it.label }
    }

    private fun executeCommands(commands: List<CliCommand>) {
        val classpath = resolveClasspath()
        if (commands.any { it.mainClass == StateProofPlugin.VIEWER_CLI_MAIN_CLASS }) {
            ensureViewerCliAvailable(classpath)
        }

        for (command in commands) {
            logger.lifecycle("Executing: ${command.label}")
            project.javaexec { spec ->
                spec.classpath = classpath
                spec.mainClass.set(command.mainClass)
                spec.args = command.args
                spec.standardOutput = System.out
                spec.errorOutput = System.err
            }
        }
    }

    private fun runPrepTasks() {
        val prepTasks = prepTaskNames.getOrElse(emptyList())
        if (prepTasks.isEmpty()) {
            return
        }

        val scopedTasks = prepTasks.map { taskName ->
            val projectPath = targetProjectPath.getOrElse(project.path)
            if (projectPath == ":" || projectPath.isBlank()) {
                taskName
            } else {
                "$projectPath:$taskName"
            }
        }

        val gradleExecutable = resolveGradleExecutable()
        val command = mutableListOf(gradleExecutable)
        command.addAll(scopedTasks)

        logger.lifecycle("Running prep tasks: ${scopedTasks.joinToString(", ")}")
        val process = ProcessBuilder(command)
            .directory(project.rootProject.projectDir)
            .redirectOutput(Redirect.INHERIT)
            .redirectError(Redirect.INHERIT)
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException(
                "stateproofWatch prep tasks failed with exit code $exitCode. " +
                    "Fix compilation errors and rerun stateproofWatch."
            )
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

    private fun resolveWatchRoots(configuredPaths: List<String>): List<File> {
        return configuredPaths
            .map { path ->
                val file = File(path)
                if (file.isAbsolute) {
                    file
                } else {
                    if (path == "settings.gradle.kts" || path == "settings.gradle") {
                        project.rootProject.projectDir.resolve(path)
                    } else {
                        project.projectDir.resolve(path)
                    }
                }
            }
            .distinctBy { it.absolutePath }
            .sortedBy { it.absolutePath }
    }

    private fun captureSnapshot(watchRoots: List<File>): Map<String, FileFingerprint> {
        val snapshot = linkedMapOf<String, FileFingerprint>()
        for (root in watchRoots) {
            if (!root.exists()) {
                snapshot["MISSING:${relativeLabel(root)}"] = FileFingerprint(false, false, 0L, 0L)
                continue
            }
            if (root.isFile) {
                snapshot[relativeLabel(root)] = fingerprintFor(root)
                continue
            }
            root.walkTopDown()
                .sortedBy { it.absolutePath }
                .forEach { file ->
                    snapshot[relativeLabel(file)] = fingerprintFor(file)
                }
        }
        return snapshot
    }

    private fun diffSnapshot(
        previous: Map<String, FileFingerprint>,
        current: Map<String, FileFingerprint>,
    ): List<String> {
        val keys = (previous.keys + current.keys).toSortedSet()
        return keys.filter { key -> previous[key] != current[key] }
    }

    private fun fingerprintFor(file: File): FileFingerprint {
        val exists = file.exists()
        val isDirectory = exists && file.isDirectory
        val length = if (exists && file.isFile) file.length() else 0L
        val modified = if (exists) safeLastModified(file) else 0L
        return FileFingerprint(exists, isDirectory, length, modified)
    }

    private fun relativeLabel(file: File): String {
        return if (file.absolutePath.startsWith(project.projectDir.absolutePath)) {
            file.relativeTo(project.projectDir).invariantSeparatorsPath
        } else {
            file.absolutePath
        }
    }

    private fun safeLastModified(file: File): Long {
        return try {
            file.lastModified()
        } catch (_: Exception) {
            0L
        }
    }

    private fun normalizeMode(value: String): String {
        val normalized = value.trim().lowercase()
        if (normalized !in allowedModes) {
            throw GradleException(
                "Invalid stateproof.watchMode='$value'. Expected one of: tests | diagram | viewer | all"
            )
        }
        return normalized
    }

    data class CliCommand(
        val label: String,
        val mainClass: String,
        val args: List<String>,
    )

    data class FileFingerprint(
        val exists: Boolean,
        val isDirectory: Boolean,
        val length: Long,
        val lastModified: Long,
    )

    companion object {
        private const val POLL_INTERVAL_MS = 300L
        private const val MAX_LOGGED_CHANGED_PATHS = 5
        private val allowedModes = setOf("tests", "diagram", "viewer", "all")
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }

    private fun resolveGradleExecutable(): String {
        val wrapperName = if (isWindows()) "gradlew.bat" else "gradlew"
        var dir: File? = project.rootProject.projectDir
        while (dir != null) {
            val candidate = File(dir, wrapperName)
            if (candidate.exists() && candidate.isFile) {
                return if (isWindows()) {
                    candidate.absolutePath
                } else {
                    candidate.absolutePath
                }
            }
            dir = dir.parentFile
        }
        return if (isWindows()) "gradle.bat" else "gradle"
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
                    "Set stateproof.classpathConfiguration to your test runtime configuration. " +
                    "Common values: 'testRuntimeClasspath' (JVM), 'debugUnitTestRuntimeClasspath' (Android)."
            )
        }

        val resolvedFiles: FileCollection = try {
            resolvedConfig.incoming.artifactView { view ->
                view.attributes { attrs ->
                    attrs.attribute(
                        org.gradle.api.attributes.Attribute.of("artifactType", String::class.java),
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
        try {
            val jarFiles = resolvedConfig.incoming.artifactView { view ->
                view.attributes { attrs ->
                    attrs.attribute(
                        org.gradle.api.attributes.Attribute.of("artifactType", String::class.java),
                        "jar"
                    )
                }
                view.lenient(true)
            }.files
            if (jarFiles.files.isNotEmpty()) {
                classpath = classpath + jarFiles
            }
        } catch (_: Exception) {
            // Ignore optional jar view failures.
        }

        val buildDir = project.layout.buildDirectory.get().asFile
        val compiledClassDirs = listOf(
            "tmp/kotlin-classes/debug",
            "tmp/kotlin-classes/release",
            "intermediates/javac/debug/classes",
            "intermediates/javac/release/classes",
            "classes/kotlin/main",
            "classes/java/main",
        ).map { File(buildDir, it) }
            .filter { it.exists() && it.isDirectory }

        if (compiledClassDirs.isNotEmpty()) {
            classpath = classpath + project.files(*compiledClassDirs.toTypedArray())
        }

        val kspResourceDirs = listOf(
            "generated/ksp/debug/resources",
            "generated/ksp/release/resources",
            "generated/ksp/main/resources",
            "generated/ksp/test/resources",
            "generated/ksp/androidTest/resources",
        ).map { File(buildDir, it) }
            .filter { it.exists() && it.isDirectory }

        if (kspResourceDirs.isNotEmpty()) {
            classpath = classpath + project.files(*kspResourceDirs.toTypedArray())
        }

        val androidExt = project.extensions.findByName("android")
        if (androidExt != null) {
            try {
                val method = androidExt.javaClass.methods.firstOrNull { it.name == "getBootClasspath" }
                val boot = (method?.invoke(androidExt) as? Iterable<*>)
                    ?.filterIsInstance<File>()
                    ?.filter { it.exists() }
                    ?: emptyList()
                if (boot.isNotEmpty()) {
                    classpath = classpath + project.files(*boot.toTypedArray())
                }
            } catch (_: Exception) {
                // Ignore boot classpath lookup failures.
            }
        }

        return classpath
    }
}
