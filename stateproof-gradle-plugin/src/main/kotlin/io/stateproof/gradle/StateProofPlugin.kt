package io.stateproof.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * StateProof Gradle Plugin
 *
 * Provides tasks for state machine test generation and synchronization.
 * Each task uses `project.javaexec` to invoke the StateProof CLI with the
 * project's compiled classpath, avoiding classloader issues.
 *
 * Tasks:
 * - stateproofGenerate: Generate test cases from state machine paths
 * - stateproofSync: Sync existing tests with current state machine
 * - stateproofSyncDryRun: Preview sync changes without writing
 * - stateproofCleanObsolete: Remove obsolete test files
 * - stateproofStatus: Show current sync status
 *
 * Usage in build.gradle.kts:
 * ```kotlin
 * plugins {
 *     id("io.stateproof") version "0.1.0"
 * }
 *
 * stateproof {
 *     stateMachineInfoProvider.set("com.example.MainStateMachineKt#getMainStateMachineInfo")
 *     initialState.set("Initial")
 *     testDir.set(file("src/test/kotlin/generated"))
 *     testPackage.set("com.example.test")
 *     testClassName.set("GeneratedMainStateMachineTest")
 * }
 * ```
 */
class StateProofPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Create the extension
        val extension = project.extensions.create(
            "stateproof",
            StateProofExtension::class.java,
            project
        )

        // Register tasks
        project.tasks.register("stateproofGenerate", StateProofGenerateTask::class.java) { task ->
            task.group = TASK_GROUP
            task.description = "Generate test cases from state machine paths"
            task.configureFrom(extension)
        }

        project.tasks.register("stateproofSync", StateProofSyncTask::class.java) { task ->
            task.group = TASK_GROUP
            task.description = "Sync existing tests with current state machine definition"
            task.configureFrom(extension)
            task.dryRunMode.set(false)
        }

        project.tasks.register("stateproofSyncDryRun", StateProofSyncTask::class.java) { task ->
            task.group = TASK_GROUP
            task.description = "Preview sync changes without writing files"
            task.configureFrom(extension)
            task.dryRunMode.set(true)
        }

        project.tasks.register("stateproofCleanObsolete", StateProofCleanObsoleteTask::class.java) { task ->
            task.group = TASK_GROUP
            task.description = "Remove obsolete test files (marked with @StateProofObsolete)"
            task.testDir.set(extension.testDir)
            task.autoDelete.set(extension.autoDeleteObsolete)
        }

        project.tasks.register("stateproofStatus", StateProofStatusTask::class.java) { task ->
            task.group = TASK_GROUP
            task.description = "Show current sync status"
            task.configureFrom(extension)
        }
    }

    companion object {
        const val TASK_GROUP = "stateproof"
        const val CLI_MAIN_CLASS = "io.stateproof.cli.StateProofCli"
    }
}
