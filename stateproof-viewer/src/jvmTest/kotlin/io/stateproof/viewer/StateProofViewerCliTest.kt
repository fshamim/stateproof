package io.stateproof.viewer

import io.stateproof.StateMachine
import io.stateproof.registry.StateMachineDescriptor
import io.stateproof.registry.StateMachineRegistry
import io.stateproof.viewer.cli.StateProofViewerCli
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StateProofViewerCliTest {

    @Test
    fun viewerCommand_withFactory_generatesFiles() {
        val dir = createTempDirectory(prefix = "stateproof-viewer-cli-").toFile()

        try {
            StateProofViewerCli.main(
                arrayOf(
                    "viewer",
                    "--provider", "io.stateproof.viewer.StateProofViewerCliTestKt#createViewerCliTestStateMachine",
                    "--is-factory",
                    "--name", "CliMain",
                    "--output-dir", dir.absolutePath,
                )
            )

            assertTrue(File(dir, "climain/index.html").exists())
            assertTrue(File(dir, "climain/graph.json").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun viewerAllCommand_withRegistry_generatesFilesForDiscoveredMachine() {
        val dir = createTempDirectory(prefix = "stateproof-viewer-all-").toFile()

        try {
            StateProofViewerCli.main(
                arrayOf(
                    "viewer-all",
                    "--output-dir", dir.absolutePath,
                )
            )

            assertTrue(File(dir, "testviewer/index.html").exists())
            assertTrue(File(dir, "testviewer/graph.json").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun invalidArgs_printActionableError() {
        val dir = createTempDirectory(prefix = "stateproof-viewer-invalid-").toFile()

        try {
            val result = runCliInSeparateProcess(
                "viewer",
                "--output-dir",
                dir.absolutePath,
            )

            assertNotEquals(0, result.exitCode)
            assertTrue(result.stderr.contains("--provider is required"))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun runCliInSeparateProcess(vararg args: String): ProcessResult {
        val javaBinary = File(System.getProperty("java.home"), "bin/java").absolutePath
        val classpath = System.getProperty("java.class.path")

        val command = mutableListOf(
            javaBinary,
            "-cp",
            classpath,
            "io.stateproof.viewer.cli.StateProofViewerCli",
        )
        command.addAll(args)

        val process = ProcessBuilder(command)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return ProcessResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
    }

    private data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}

class TestViewerRegistry : StateMachineRegistry {
    override fun getStateMachines(): List<StateMachineDescriptor> {
        return listOf(
            StateMachineDescriptor(
                name = "TestViewer",
                baseName = "TestViewerStateMachine",
                packageName = "io.stateproof.viewer",
                factoryFqn = "io.stateproof.viewer.StateProofViewerCliTestKt#createViewerCliTestStateMachine",
                eventClassName = "CliEvent",
                eventClassFqn = "io.stateproof.viewer.CliEvent",
            )
        )
    }
}

sealed interface CliState {
    data object Initial : CliState
    data object Done : CliState
}

sealed interface CliEvent {
    data object Start : CliEvent
}

fun createViewerCliTestStateMachine(): StateMachine<CliState, CliEvent> {
    return StateMachine {
        initialState(CliState.Initial)

        state<CliState.Initial> {
            on<CliEvent.Start> {
                transitionTo(CliState.Done)
            }
        }

        state<CliState.Done> {}
    }
}
