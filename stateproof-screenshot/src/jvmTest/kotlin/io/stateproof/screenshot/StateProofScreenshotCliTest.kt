package io.stateproof.screenshot

import io.stateproof.StateMachine
import io.stateproof.registry.StateMachineDescriptor
import io.stateproof.registry.StateMachineRegistry
import io.stateproof.screenshot.cli.StateProofScreenshotCli
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class StateProofScreenshotCliTest {

    @Test
    fun sync_generatesSingleScreenshotTestFile() {
        val dir = createTempDirectory(prefix = "stateproof-screenshot-sync-").toFile()
        val outputFile = File(dir, "sample/GeneratedSampleScreenshotTest.kt")
        try {
            StateProofScreenshotCli.main(
                arrayOf(
                    "sync",
                    "--provider", "io.stateproof.screenshot.StateProofScreenshotCliTestKt#createScreenshotCliTestStateMachine",
                    "--is-factory",
                    "--harness-factory", "io.stateproof.screenshot.StateProofScreenshotCliTestKt#createScreenshotHarness",
                    "--output-file", outputFile.absolutePath,
                    "--package", "sample",
                    "--class-name", "GeneratedSampleScreenshotTest",
                    "--machine-name", "Sample",
                )
            )

            assertTrue(outputFile.exists())
            val content = outputFile.readText()
            assertTrue(content.contains("@StateProofScreenshotGenerated"))
            assertTrue(content.contains("val paparazzi = Paparazzi()"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun syncAll_generatesForOptedInRegistryDescriptors() {
        val dir = createTempDirectory(prefix = "stateproof-screenshot-sync-all-").toFile()
        try {
            StateProofScreenshotCli.main(
                arrayOf(
                    "sync-all",
                    "--output-root-dir", dir.absolutePath,
                    "--strict", "true",
                )
            )

            val expectedFile = File(
                dir,
                "io/stateproof/screenshot/GeneratedScreenshotCliStateMachineScreenshotTest.kt",
            )
            assertTrue(expectedFile.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}

class ScreenshotCliRegistry : StateMachineRegistry {
    override fun getStateMachines(): List<StateMachineDescriptor> {
        return listOf(
            StateMachineDescriptor(
                name = "ScreenshotCli",
                baseName = "ScreenshotCliStateMachine",
                packageName = "io.stateproof.screenshot",
                factoryFqn = "io.stateproof.screenshot.StateProofScreenshotCliTestKt#createScreenshotCliTestStateMachine",
                eventClassName = "CliEvent",
                eventClassFqn = "io.stateproof.screenshot.CliEvent",
                screenshotHarnessFactoryFqn = "io.stateproof.screenshot.StateProofScreenshotCliTestKt#createScreenshotHarness",
                screenshotTestPackage = "io.stateproof.screenshot",
                screenshotTestClassName = "GeneratedScreenshotCliStateMachineScreenshotTest",
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

fun createScreenshotCliTestStateMachine(): StateMachine<CliState, CliEvent> {
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

fun createScreenshotHarness(): StateProofScreenshotHarness<CliState, CliEvent> {
    return object : StateProofScreenshotHarness<CliState, CliEvent> {
        override fun createStateMachine(): StateMachine<CliState, CliEvent> = createScreenshotCliTestStateMachine()
        override fun eventForTransition(currentState: CliState, transition: String): CliEvent = CliEvent.Start

        @androidx.compose.runtime.Composable
        override fun render(state: CliState) {
            // No-op render for CLI generation tests.
        }
    }
}
