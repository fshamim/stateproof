package io.stateproof.screenshot.cli

import io.stateproof.cli.StateInfoLoader
import io.stateproof.registry.StateMachineRegistryLoader
import io.stateproof.screenshot.ScreenshotCaptureMode
import io.stateproof.screenshot.ScreenshotCodeGenConfig
import io.stateproof.screenshot.StateProofScreenshotSync
import io.stateproof.testgen.TestGenConfig
import java.io.File

/**
 * CLI for StateProof screenshot test generation and sync.
 */
object StateProofScreenshotCli {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }

        try {
            when (args[0]) {
                "sync" -> runSync(args.drop(1))
                "sync-all" -> runSyncAll(args.drop(1))
                "help", "--help", "-h" -> printUsage()
                else -> error("Unknown command: ${args[0]}")
            }
        } catch (e: Exception) {
            System.err.println("ERROR: ${e.message}")
            if (System.getenv("STATEPROOF_DEBUG") != null) {
                e.printStackTrace()
            }
            kotlin.system.exitProcess(1)
        }
    }

    private fun runSync(args: List<String>) {
        val provider = args.requireArg("--provider")
        val isFactory = args.contains("--is-factory")
        val harnessFactoryFqn = args.requireArg("--harness-factory")
        val outputFile = File(args.requireArg("--output-file"))
        val packageName = args.requireArg("--package")
        val className = args.requireArg("--class-name")
        val machineName = args.getArgValue("--machine-name") ?: className
        val initialState = args.getArgValue("--initial-state") ?: "Initial"
        val maxVisits = args.getArgValue("--max-visits")?.toIntOrNull() ?: 2
        val maxDepth = args.getArgValue("--max-depth")?.toIntOrNull() ?: -1
        val dryRun = args.contains("--dry-run")
        val captureMode = args.getArgValue("--capture-mode")
            ?.let { parseCaptureMode(it) }
            ?: ScreenshotCaptureMode.MOVIE

        if (harnessFactoryFqn.isBlank()) {
            throw IllegalArgumentException("--harness-factory must not be blank")
        }

        val loadedFromFactory = if (isFactory) {
            StateInfoLoader.loadFromFactoryWithState(provider)
        } else {
            null
        }
        val stateInfoMap = loadedFromFactory?.stateInfoMap ?: StateInfoLoader.load(provider)
        val resolvedInitialState = loadedFromFactory?.initialStateName?.ifBlank { initialState } ?: initialState

        val codeGenConfig = ScreenshotCodeGenConfig(
            packageName = packageName,
            testClassName = className,
            machineSlug = sanitizeMachineSlug(machineName),
            harnessFactoryExpression = factoryFqnToCallExpression(harnessFactoryFqn),
            captureMode = captureMode,
        )
        val testGenConfig = TestGenConfig(
            maxVisitsPerState = maxVisits,
            maxPathDepth = if (maxDepth == -1) null else maxDepth,
        )

        val result = StateProofScreenshotSync.sync(
            stateInfoMap = stateInfoMap,
            initialState = resolvedInitialState,
            testFile = outputFile,
            codeGenConfig = codeGenConfig,
            testGenConfig = testGenConfig,
            dryRun = dryRun,
        )

        println(result.report.summary())
        if (!dryRun) {
            println("Screenshot test file synced: ${outputFile.absolutePath}")
        } else {
            println("Dry run complete for: ${outputFile.absolutePath}")
        }
    }

    private fun runSyncAll(args: List<String>) {
        val outputRootDir = File(args.requireArg("--output-root-dir"))
        val strict = args.getArgValue("--strict")?.toBooleanStrictOrNull() ?: true
        val maxVisits = args.getArgValue("--max-visits")?.toIntOrNull() ?: 2
        val maxDepth = args.getArgValue("--max-depth")?.toIntOrNull() ?: -1
        val dryRun = args.contains("--dry-run")
        val captureMode = args.getArgValue("--capture-mode")
            ?.let { parseCaptureMode(it) }
            ?: ScreenshotCaptureMode.MOVIE

        val descriptors = StateMachineRegistryLoader.loadAll()
        if (descriptors.isEmpty()) {
            throw IllegalArgumentException(
                "No StateProof registries found. Ensure KSP generated registries are on the classpath."
            )
        }

        println("Discovered ${descriptors.size} state machines")
        val selected = descriptors.filter { it.screenshotHarnessFactoryFqn.isNotBlank() }
        if (selected.isEmpty()) {
            if (strict) {
                throw IllegalArgumentException(
                    "No state machines are opted-in for screenshots. " +
                        "Set screenshotHarnessFactoryFqn in @StateProofStateMachine."
                )
            }
            println("No screenshot-opted machines found. Nothing to do.")
            return
        }

        if (!dryRun) {
            outputRootDir.mkdirs()
        }

        val testGenConfig = TestGenConfig(
            maxVisitsPerState = maxVisits,
            maxPathDepth = if (maxDepth == -1) null else maxDepth,
        )

        var processed = 0
        selected.forEach { descriptor ->
            val harnessFqn = descriptor.screenshotHarnessFactoryFqn
            if (harnessFqn.isBlank()) {
                if (strict) {
                    throw IllegalArgumentException(
                        "State machine '${descriptor.name}' has no screenshotHarnessFactoryFqn."
                    )
                }
                println("Skipping ${descriptor.name}: screenshotHarnessFactoryFqn is blank")
                return@forEach
            }

            val loaded = StateInfoLoader.loadFromFactoryWithState(descriptor.factoryFqn)
            val packageName = when {
                descriptor.screenshotTestPackage.isNotBlank() -> descriptor.screenshotTestPackage
                descriptor.testPackage.isNotBlank() -> descriptor.testPackage
                else -> descriptor.packageName
            }
            val className = when {
                descriptor.screenshotTestClassName.isNotBlank() -> descriptor.screenshotTestClassName
                descriptor.testClassName.isNotBlank() -> descriptor.testClassName.removeSuffix("Test") + "ScreenshotTest"
                else -> "Generated${descriptor.baseName.ifBlank { deriveBaseName(descriptor.name) }}ScreenshotTest"
            }
            val packagePath = packageName.replace('.', '/')
            val outputFile = File(outputRootDir, "$packagePath/$className.kt")

            val codeGenConfig = ScreenshotCodeGenConfig(
                packageName = packageName,
                testClassName = className,
                machineSlug = sanitizeMachineSlug(descriptor.baseName.ifBlank { descriptor.name }),
                harnessFactoryExpression = factoryFqnToCallExpression(harnessFqn),
                captureMode = captureMode,
            )

            val result = StateProofScreenshotSync.sync(
                stateInfoMap = loaded.stateInfoMap,
                initialState = loaded.initialStateName,
                testFile = outputFile,
                codeGenConfig = codeGenConfig,
                testGenConfig = testGenConfig,
                dryRun = dryRun,
            )
            println("Screenshot sync for ${descriptor.name}:")
            println(result.report.summary())
            processed++
        }

        println("Screenshot sync-all complete. Machines processed: $processed")
    }

    private fun parseCaptureMode(value: String): ScreenshotCaptureMode {
        return ScreenshotCaptureMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid --capture-mode '$value'. Supported: movie")
    }

    private fun deriveBaseName(name: String): String {
        if (name.isBlank()) return "StateMachine"
        val sanitized = name.replace(Regex("[^A-Za-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString("") { token -> token.replaceFirstChar { it.uppercase() } }
        return if (sanitized.endsWith("StateMachine")) sanitized else "${sanitized}StateMachine"
    }

    private fun sanitizeMachineSlug(value: String): String {
        return value
            .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
            .replace(Regex("[^A-Za-z0-9]+"), "-")
            .trim('-')
            .lowercase()
            .ifBlank { "state-machine" }
    }

    private fun factoryFqnToCallExpression(factoryFqn: String): String {
        val (className, methodName) = StateInfoLoader.parseProvider(factoryFqn)
        return if (className.endsWith("Kt")) {
            val packageName = className.substringBeforeLast(".", missingDelimiterValue = "")
            if (packageName.isBlank()) "$methodName()" else "$packageName.$methodName()"
        } else {
            "$className.$methodName()"
        }
    }

    private fun List<String>.getArgValue(name: String): String? {
        val index = indexOf(name)
        if (index == -1 || index + 1 >= size) return null
        return this[index + 1]
    }

    private fun List<String>.requireArg(name: String): String {
        return getArgValue(name)
            ?: throw IllegalArgumentException("$name is required")
    }

    private fun printUsage() {
        println(
            """
            |StateProof Screenshot CLI
            |
            |Usage: stateproof-screenshot <command> [options]
            |
            |Commands:
            |  sync       Sync screenshot tests for one state machine
            |  sync-all   Auto-discover and sync screenshot tests for opted-in state machines
            |
            |sync options:
            |  --provider <fqn>           Provider or factory FQN
            |  --is-factory               Provider is a factory
            |  --harness-factory <fqn>    Screenshot harness factory FQN
            |  --output-file <file>       Output Kotlin test file
            |  --package <name>           Test package
            |  --class-name <name>        Test class name
            |  --machine-name <name>      Optional machine slug source
            |  --initial-state <name>     Initial state (legacy provider mode)
            |  --max-visits <n>           Max visits per state (default 2)
            |  --max-depth <n>            Max path depth, -1 for unlimited
            |  --capture-mode <mode>      movie (default)
            |  --dry-run                  Preview without writing file
            |
            |sync-all options:
            |  --output-root-dir <dir>    Output root directory
            |  --strict <true|false>      Fail on missing screenshot config (default true)
            |  --max-visits <n>           Max visits per state (default 2)
            |  --max-depth <n>            Max path depth, -1 for unlimited
            |  --capture-mode <mode>      movie (default)
            |  --dry-run                  Preview without writing files
            """.trimMargin()
        )
    }
}
