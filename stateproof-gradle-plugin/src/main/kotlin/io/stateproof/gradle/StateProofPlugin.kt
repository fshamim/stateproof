package io.stateproof.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * StateProof Gradle Plugin
 *
 * Provides tasks for state machine test generation and synchronization.
 * Supports both single state machine and multiple state machine configurations.
 *
 * ## Single State Machine Mode
 * ```kotlin
 * stateproof {
 *     stateMachineFactoryFqn.set("com.example.MainStateMachineKt#getMainStateMachine")
 *     initialState.set("Initial")
 *     testDir.set(file("src/test/kotlin/generated/stateproof"))
 * }
 * ```
 *
 * ## Multiple State Machines Mode
 * ```kotlin
 * stateproof {
 *     stateMachines {
 *         create("main") { ... }
 *         create("laser") { ... }
 *     }
 * }
 * ```
 *
 * Tasks (single mode):
 * - stateproofGenerate: Generate test cases
 * - stateproofSync: Sync existing tests
 *
 * Tasks (multi mode):
 * - stateproofGenerateMain, stateproofGenerateLaser: Generate test cases per SM
 * - stateproofSyncMain, stateproofSyncLaser: Sync tests per SM
 * - stateproofGenerateAll, stateproofSyncAll: Run all
 */
class StateProofPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Create the extension
        val extension = project.extensions.create(
            "stateproof",
            StateProofExtension::class.java,
            project
        )

        // Register tasks after project configuration is complete
        project.afterEvaluate {
            if (extension.isMultiMode()) {
                registerMultiModeTasks(project, extension)
            } else {
                registerSingleModeTasks(project, extension)
            }

            // Register shared tasks (always available)
            registerSharedTasks(project, extension)
        }
    }

    /**
     * Registers tasks for single state machine mode (backward compatible).
     */
    private fun registerSingleModeTasks(project: Project, extension: StateProofExtension) {
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
    }

    /**
     * Registers tasks for multi state machine mode.
     * Creates per-SM tasks like stateproofGenerateMain, stateproofSyncLaser, etc.
     */
    private fun registerMultiModeTasks(project: Project, extension: StateProofExtension) {
        val allGenerateTasks = mutableListOf<String>()
        val allSyncTasks = mutableListOf<String>()

        extension.stateMachines.forEach { smConfig ->
            val name = smConfig.name
            val capitalizedName = name.replaceFirstChar { it.uppercase() }

            // Generate task for this SM
            val generateTaskName = "stateproofGenerate$capitalizedName"
            allGenerateTasks.add(generateTaskName)

            project.tasks.register(generateTaskName, StateProofGenerateTask::class.java) { task ->
                task.group = TASK_GROUP
                task.description = "Generate test cases for $name state machine"
                task.configureFromStateMachineConfig(smConfig, extension)
            }

            // Sync task for this SM
            val syncTaskName = "stateproofSync$capitalizedName"
            allSyncTasks.add(syncTaskName)

            project.tasks.register(syncTaskName, StateProofSyncTask::class.java) { task ->
                task.group = TASK_GROUP
                task.description = "Sync tests for $name state machine"
                task.configureFromStateMachineConfig(smConfig, extension)
                task.dryRunMode.set(false)
            }

            // Dry-run sync task for this SM
            project.tasks.register("stateproofSyncDryRun$capitalizedName", StateProofSyncTask::class.java) { task ->
                task.group = TASK_GROUP
                task.description = "Preview sync changes for $name state machine"
                task.configureFromStateMachineConfig(smConfig, extension)
                task.dryRunMode.set(true)
            }
        }

        // Register aggregate tasks
        project.tasks.register("stateproofGenerateAll") { task ->
            task.group = TASK_GROUP
            task.description = "Generate test cases for all state machines"
            task.dependsOn(allGenerateTasks)
        }

        project.tasks.register("stateproofSyncAll") { task ->
            task.group = TASK_GROUP
            task.description = "Sync tests for all state machines"
            task.dependsOn(allSyncTasks)
        }
    }

    /**
     * Registers tasks that are available in both modes.
     */
    private fun registerSharedTasks(project: Project, extension: StateProofExtension) {
        // Clean obsolete - scans the test directory(s) for @StateProofObsolete marked tests
        // In multi-SM mode, we need to handle multiple test directories
        if (extension.isMultiMode()) {
            // In multi-SM mode, create per-SM clean tasks
            extension.stateMachines.forEach { smConfig ->
                val name = smConfig.name
                val capitalizedName = name.replaceFirstChar { it.uppercase() }
                
                project.tasks.register("stateproofCleanObsolete$capitalizedName", StateProofCleanObsoleteTask::class.java) { task ->
                    task.group = TASK_GROUP
                    task.description = "Remove obsolete test files for $name state machine"
                    // Use effective test directory based on package
                    val effectivePackage = smConfig.getEffectivePackage()
                    val packagePath = effectivePackage.replace('.', '/')
                    task.testDir.set(project.layout.projectDirectory.dir("src/test/kotlin/$packagePath"))
                    task.autoDelete.set(extension.autoDeleteObsolete)
                }
            }
            // Aggregate clean task
            val cleanTasks = extension.stateMachines.map { "stateproofCleanObsolete${it.name.replaceFirstChar { c -> c.uppercase() }}" }
            project.tasks.register("stateproofCleanObsoleteAll") { task ->
                task.group = TASK_GROUP
                task.description = "Remove obsolete test files for all state machines"
                task.dependsOn(cleanTasks)
            }
        } else {
            // Single-SM mode - use extension's testDir
            project.tasks.register("stateproofCleanObsolete", StateProofCleanObsoleteTask::class.java) { task ->
                task.group = TASK_GROUP
                task.description = "Remove obsolete test files (marked with @StateProofObsolete)"
                task.testDir.set(extension.testDir)
                task.autoDelete.set(extension.autoDeleteObsolete)
            }
        }

        // Status tasks
        if (extension.isMultiMode()) {
            // In multi-SM mode, create per-SM status tasks
            extension.stateMachines.forEach { smConfig ->
                val name = smConfig.name
                val capitalizedName = name.replaceFirstChar { it.uppercase() }
                
                project.tasks.register("stateproofStatus$capitalizedName", StateProofStatusTask::class.java) { task ->
                    task.group = TASK_GROUP
                    task.description = "Show sync status for $name state machine"
                    task.configureFromStateMachineConfig(smConfig, extension)
                }
            }
        } else {
            // Single-SM mode
            project.tasks.register("stateproofStatus", StateProofStatusTask::class.java) { task ->
                task.group = TASK_GROUP
                task.description = "Show current sync status"
                task.configureFrom(extension)
            }
        }
    }

    companion object {
        const val TASK_GROUP = "stateproof"
        const val CLI_MAIN_CLASS = "io.stateproof.cli.StateProofCli"
    }
}
