package io.stateproof.gradle

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
 * Task to sync generated screenshot tests for one state machine.
 */
abstract class StateProofScreenshotSyncTask : StateProofBaseTask() {

    init {
        outputs.upToDateWhen { false }
    }

    @get:OutputDirectory
    abstract val screenshotOutputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val screenshotHarnessFactoryFqn: Property<String>

    @get:Input
    @get:Optional
    abstract val screenshotPackageName: Property<String>

    @get:Input
    @get:Optional
    abstract val screenshotClassName: Property<String>

    @get:Input
    @get:Optional
    abstract val machineName: Property<String>

    @get:Input
    abstract val screenshotCaptureMode: Property<String>

    @get:Input
    abstract val screenshotStrict: Property<Boolean>

    override fun configureFrom(extension: StateProofExtension) {
        super.configureFrom(extension)
        screenshotOutputDir.set(extension.screenshotTestDir)
        screenshotHarnessFactoryFqn.set(extension.screenshotHarnessFactoryFqn)
        screenshotPackageName.set(extension.testPackage)
        screenshotClassName.set("")
        machineName.set("")
        screenshotCaptureMode.set(extension.screenshotCaptureMode)
        screenshotStrict.set(extension.screenshotStrict)
    }

    override fun configureFromStateMachineConfig(config: StateMachineConfig, extension: StateProofExtension) {
        super.configureFromStateMachineConfig(config, extension)
        screenshotOutputDir.set(extension.screenshotTestDir)
        screenshotHarnessFactoryFqn.set(config.screenshotHarnessFactoryFqn)
        screenshotPackageName.set(config.getEffectiveScreenshotPackage())
        screenshotClassName.set(config.getEffectiveScreenshotClassName())
        machineName.set(config.name)
        screenshotCaptureMode.set(extension.screenshotCaptureMode)
        screenshotStrict.set(extension.screenshotStrict)
    }

    @TaskAction
    fun syncScreenshots() {
        val strict = screenshotStrict.getOrElse(true)
        val harnessFqn = screenshotHarnessFactoryFqn.orNull?.trim().orEmpty()
        if (harnessFqn.isBlank()) {
            if (strict) {
                throw GradleException(
                    "Screenshot harness is not configured. Set stateproof.screenshotHarnessFactoryFqn " +
                        "or configure screenshotHarnessFactoryFqn in stateproof.stateMachines { ... }."
                )
            }
            logger.lifecycle("Skipping screenshot sync: screenshotHarnessFactoryFqn is blank and strict=false")
            return
        }

        val provider = stateMachineInfoProvider.get().trim()
        if (provider.isBlank()) {
            throw GradleException("State machine provider is required for screenshot sync.")
        }

        val packageName = screenshotPackageName.orNull?.takeIf { it.isNotBlank() }
            ?: derivePackageFromProvider(provider)
        val className = screenshotClassName.orNull?.takeIf { it.isNotBlank() }
            ?: deriveScreenshotClassName(provider)
        val machine = machineName.orNull?.takeIf { it.isNotBlank() } ?: className
        val outputFile = File(
            screenshotOutputDir.get().asFile,
            "${packageName.replace('.', '/')}/$className.kt",
        )

        val classpath = resolveClasspath()
        ensureScreenshotCliAvailable(classpath)

        val args = mutableListOf(
            "sync",
            "--provider", provider,
            "--harness-factory", harnessFqn,
            "--output-file", outputFile.absolutePath,
            "--package", packageName,
            "--class-name", className,
            "--machine-name", machine,
            "--initial-state", initialState.get(),
            "--max-visits", maxVisitsPerState.get().toString(),
            "--capture-mode", screenshotCaptureMode.get(),
        )

        if (providerIsFactory.getOrElse(false)) {
            args.add("--is-factory")
        }
        val depth = maxPathDepth.get()
        if (depth != -1) {
            args.addAll(listOf("--max-depth", depth.toString()))
        }

        logger.lifecycle("Executing: StateProofScreenshotCli sync")
        logger.lifecycle("Provider: $provider")
        logger.lifecycle("Harness: $harnessFqn")
        logger.lifecycle("Output: ${outputFile.absolutePath}")

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

    private fun derivePackageFromProvider(provider: String): String {
        val className = provider.substringBefore("#")
        return className.substringBeforeLast(".")
    }

    private fun deriveScreenshotClassName(provider: String): String {
        val method = provider.substringAfter("#", "")
        val baseName = when {
            method.startsWith("get") -> method.removePrefix("get")
            method.startsWith("create") -> method.removePrefix("create")
            method.isNotBlank() -> method.replaceFirstChar { it.uppercase() }
            else -> provider.substringBefore("#").substringAfterLast(".").removeSuffix("Kt")
        }
        return "Generated${baseName}ScreenshotTest"
    }
}
