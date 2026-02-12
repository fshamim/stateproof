package io.stateproof.viewer.cli

import io.stateproof.cli.StateInfoLoader
import io.stateproof.diagram.toFlatStateGraph
import io.stateproof.registry.StateMachineRegistryLoader
import io.stateproof.viewer.ViewerLayout
import io.stateproof.viewer.ViewerRenderOptions
import io.stateproof.viewer.renderViewer
import io.stateproof.viewer.writeTo
import java.io.File

/**
 * CLI for StateProof interactive viewer generation.
 */
object StateProofViewerCli {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }

        try {
            when (args[0]) {
                "viewer" -> runViewer(args.drop(1))
                "viewer-all" -> runViewerAll(args.drop(1))
                "help", "--help", "-h" -> printUsage()
                else -> {
                    System.err.println("Unknown command: ${args[0]}")
                    printUsage()
                    System.exit(1)
                }
            }
        } catch (t: Throwable) {
            System.err.println("ERROR: ${t.message}")
            if (System.getenv("STATEPROOF_DEBUG") != null) {
                t.printStackTrace()
            }
            System.exit(1)
        }
    }

    private fun runViewer(args: List<String>) {
        val provider = args.requireArg("--provider")
        val outputDir = File(args.requireArg("--output-dir"))
        val isFactory = args.contains("--is-factory")
        val initialState = args.getArgValue("--initial-state") ?: "Initial"
        val machineName = args.getArgValue("--name") ?: deriveMachineName(provider)
        val layout = parseLayout(args.getArgValue("--layout"))
        val includeJsonSidecar = parseBoolean(args.getArgValue("--include-json-sidecar"), defaultValue = true)

        println("=".repeat(60))
        println("StateProof Viewer")
        println("=".repeat(60))
        println("Provider: $provider")
        println("Provider mode: ${if (isFactory) "factory" else "info provider"}")
        println("Machine name: $machineName")
        println("Output dir: ${outputDir.absolutePath}")
        println("Layout: ${layout.name}")
        println("JSON sidecar: $includeJsonSidecar")
        println()

        val graph = if (isFactory) {
            StateInfoLoader.loadFromFactoryWithGraph(provider).stateGraph
        } else {
            StateInfoLoader.load(provider).toFlatStateGraph(initialState)
        }

        val bundle = graph.renderViewer(
            machineName = machineName,
            options = ViewerRenderOptions(
                layout = layout,
                includeJsonSidecar = includeJsonSidecar,
            )
        )
        val written = bundle.writeTo(outputDir)

        println("Generated ${written.size} viewer files")
        written.forEach { println("  - ${it.absolutePath}") }
    }

    private fun runViewerAll(args: List<String>) {
        val outputDir = File(args.requireArg("--output-dir"))
        val layout = parseLayout(args.getArgValue("--layout"))
        val includeJsonSidecar = parseBoolean(args.getArgValue("--include-json-sidecar"), defaultValue = true)
        val descriptors = StateMachineRegistryLoader.loadAll()
        if (descriptors.isEmpty()) {
            throw IllegalArgumentException(
                "No StateProof registries found. Ensure KSP generated registries are on the classpath."
            )
        }

        println("=".repeat(60))
        println("StateProof Viewer-All")
        println("=".repeat(60))
        println("Discovered ${descriptors.size} state machines")
        println("Output dir: ${outputDir.absolutePath}")
        println("Layout: ${layout.name}")
        println("JSON sidecar: $includeJsonSidecar")
        println()

        var totalFiles = 0
        for (descriptor in descriptors.sortedBy { it.name }) {
            val machineName = descriptor.name.ifBlank {
                descriptor.baseName.ifBlank { deriveMachineName(descriptor.factoryFqn) }
            }
            println("Rendering viewer for $machineName ...")
            val loaded = StateInfoLoader.loadFromFactoryWithGraph(descriptor.factoryFqn)
            val bundle = loaded.stateGraph.renderViewer(
                machineName = machineName,
                options = ViewerRenderOptions(
                    layout = layout,
                    includeJsonSidecar = includeJsonSidecar,
                )
            )
            val written = bundle.writeTo(outputDir)
            totalFiles += written.size
            println("  -> ${written.size} files")
        }

        println()
        println("Generated $totalFiles viewer files in total")
    }

    private fun printUsage() {
        println(
            """
            |StateProof Viewer CLI
            |
            |Usage: stateproof-viewer <command> [options]
            |
            |Commands:
            |  viewer       Generate viewer for one state machine
            |  viewer-all   Auto-discover and generate viewers for all state machines
            |
            |viewer options:
            |  --provider <fqn>        State machine provider (required)
            |  --output-dir <dir>      Output root directory (required)
            |  --is-factory            Provider returns StateMachine<*, *>
            |  --name <machine>        Optional machine folder name
            |  --initial-state <name>  Initial state name for info-provider mode (default: Initial)
            |  --layout <value>        breadthfirst (default: breadthfirst)
            |  --include-json-sidecar <true|false>  Include graph.json output (default: true)
            |
            |viewer-all options:
            |  --output-dir <dir>      Output root directory (required)
            |  --layout <value>        breadthfirst (default: breadthfirst)
            |  --include-json-sidecar <true|false>  Include graph.json output (default: true)
            |
            |Examples:
            |  stateproof-viewer viewer \
            |    --provider com.example.MainStateMachineKt#createMainStateMachineForIntrospection \
            |    --is-factory \
            |    --output-dir build/stateproof/viewer
            |
            |  stateproof-viewer viewer-all \
            |    --output-dir build/stateproof/viewer
            """.trimMargin()
        )
    }

    private fun deriveMachineName(provider: String): String {
        val classPart = provider.substringBefore("#").substringAfterLast(".")
        val methodPart = provider.substringAfter("#", "")
        return when {
            methodPart.startsWith("create") && methodPart.length > "create".length -> {
                methodPart.removePrefix("create")
            }
            methodPart.startsWith("get") && methodPart.length > "get".length -> {
                methodPart.removePrefix("get")
            }
            methodPart.isNotBlank() -> methodPart.replaceFirstChar { it.uppercase() }
            classPart.endsWith("Kt") -> classPart.removeSuffix("Kt")
            else -> classPart
        }
    }

    private fun List<String>.requireArg(name: String): String {
        return getArgValue(name) ?: throw IllegalArgumentException("$name is required")
    }

    private fun List<String>.getArgValue(name: String): String? {
        val index = indexOf(name)
        if (index == -1 || index + 1 >= size) return null
        return get(index + 1)
    }

    private fun parseLayout(value: String?): ViewerLayout {
        return when (value?.lowercase()) {
            null, "", "breadthfirst" -> ViewerLayout.BREADTHFIRST
            else -> throw IllegalArgumentException(
                "Invalid --layout '$value'. Expected: breadthfirst"
            )
        }
    }

    private fun parseBoolean(value: String?, defaultValue: Boolean): Boolean {
        return when (value?.lowercase()) {
            null, "" -> defaultValue
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> throw IllegalArgumentException(
                "Invalid boolean value '$value'. Expected true/false."
            )
        }
    }
}
