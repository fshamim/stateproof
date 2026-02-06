package io.stateproof.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * StateProof Gradle Plugin
 *
 * Provides tasks for state machine test synchronization.
 * Supports both single state machine and multiple state machine configurations.
 * Supports dual test targets: JVM unit tests and Android instrumented tests.
 *
 * ## Important: Sync-Only Design
 * StateProof uses sync-only to protect your test implementations:
 * - **Never overwrites** existing test code
 * - **Never deletes** tests automatically
 * - **Always preserves** user implementations
 * - To regenerate a test, delete it manually and run sync
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
 * ## Multiple State Machines Mode with Dual Targets
 * ```kotlin
 * stateproof {
 *     stateMachines {
 *         create("main") {
 *             factory.set("...")
 *             testTargets.set(listOf("jvm", "android"))
 *         }
 *         create("laser") { ... }
 *     }
 * }
 * ```
 *
 * Tasks:
 * - stateproofSyncMain, stateproofSyncMainAndroid
 * - stateproofSyncDryRunMain, stateproofSyncDryRunMainAndroid
 * - stateproofSyncAll: Sync all state machines
 */
class StateProofPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "stateproof",
            StateProofExtension::class.java,
            project
        )

        project.afterEvaluate {
            if (extension.isMultiMode()) {
                registerMultiModeTasks(project, extension)
            } else {
                registerSingleModeTasks(project, extension)
            }

            registerSharedTasks(project, extension)
        }
    }

    /**
     * Registers tasks for single state machine mode (backward compatible).
     */
    private fun registerSingleModeTasks(project: Project, extension: StateProofExtension) {
        val targets = extension.testTargets.get()

        for (target in targets) {
            val targetSuffix = if (target == "android") "Android" else ""
            val targetLabel = if (target == "android") "Android " else ""

            project.tasks.register("stateproofSync$targetSuffix", StateProofSyncTask::class.java) { task ->
                task.group = TASK_GROUP
                task.description = "Sync ${targetLabel}tests with current state machine definition"
                task.configureFrom(extension)
                task.dryRunMode.set(false)
                if (target == "android") {
                    configureTaskForAndroidSingleMode(task, extension)
                }
            }

            project.tasks.register("stateproofSyncDryRun$targetSuffix", StateProofSyncTask::class.java) { task ->
                task.group = TASK_GROUP
                task.description = "Preview ${targetLabel}sync changes without writing files"
                task.configureFrom(extension)
                task.dryRunMode.set(true)
                if (target == "android") {
                    configureTaskForAndroidSingleMode(task, extension)
                }
            }
        }
    }

    private fun configureTaskForAndroidSingleMode(task: StateProofSyncTask, extension: StateProofExtension) {
        task.testDir.set(extension.androidTestDir)
        val androidImports = extension.androidAdditionalImports.get()
        val userImports = extension.additionalImports.get()
        task.classAnnotations.set(listOf("@RunWith(AndroidJUnit4::class)"))
        task.useRunTest.set(true)
        task.syncClassName.set(extension.androidTestClassName)
        task.syncImports.set(androidImports + userImports)
    }

    /**
     * Registers tasks for multi state machine mode.
     * Creates per-SM, per-target tasks.
     */
    private fun registerMultiModeTasks(project: Project, extension: StateProofExtension) {
        val allSyncTasks = mutableListOf<String>()

        extension.stateMachines.forEach { smConfig ->
            val name = smConfig.name
            val capitalizedName = name.replaceFirstChar { it.uppercase() }
            val targets = smConfig.testTargets.get()

            for (target in targets) {
                val targetSuffix = if (target == "android") "Android" else ""
                val targetLabel = if (target == "android") "Android " else ""

                // Sync task
                val syncTaskName = "stateproofSync$capitalizedName$targetSuffix"
                allSyncTasks.add(syncTaskName)

                project.tasks.register(syncTaskName, StateProofSyncTask::class.java) { task ->
                    task.group = TASK_GROUP
                    task.description = "Sync ${targetLabel}tests for $name state machine"
                    task.configureFromStateMachineConfig(smConfig, extension, target)
                    task.dryRunMode.set(false)
                }

                // Dry-run sync task
                project.tasks.register("stateproofSyncDryRun$capitalizedName$targetSuffix", StateProofSyncTask::class.java) { task ->
                    task.group = TASK_GROUP
                    task.description = "Preview ${targetLabel}sync changes for $name state machine"
                    task.configureFromStateMachineConfig(smConfig, extension, target)
                    task.dryRunMode.set(true)
                }
            }
        }

        // Aggregate task
        project.tasks.register("stateproofSyncAll") { task ->
            task.group = TASK_GROUP
            task.description = "Sync tests for all state machines (all targets)"
            task.dependsOn(allSyncTasks)
        }
    }

    /**
     * Registers tasks that are available in both modes.
     */
    private fun registerSharedTasks(project: Project, extension: StateProofExtension) {
        if (extension.isMultiMode()) {
            val allCleanTasks = mutableListOf<String>()

            extension.stateMachines.forEach { smConfig ->
                val name = smConfig.name
                val capitalizedName = name.replaceFirstChar { it.uppercase() }
                val targets = smConfig.testTargets.get()

                for (target in targets) {
                    val targetSuffix = if (target == "android") "Android" else ""
                    val targetLabel = if (target == "android") "Android " else ""
                    val effectivePackage = smConfig.getEffectivePackage()
                    val packagePath = effectivePackage.replace('.', '/')

                    // Clean obsolete per target
                    val cleanTaskName = "stateproofCleanObsolete$capitalizedName$targetSuffix"
                    allCleanTasks.add(cleanTaskName)

                    project.tasks.register(cleanTaskName, StateProofCleanObsoleteTask::class.java) { task ->
                        task.group = TASK_GROUP
                        task.description = "Remove obsolete ${targetLabel}test files for $name state machine"
                        if (target == "android") {
                            if (!smConfig.androidTestDir.isPresent) {
                                task.testDir.set(project.layout.projectDirectory.dir("src/androidTest/kotlin/$packagePath"))
                            } else {
                                task.testDir.set(smConfig.androidTestDir)
                            }
                        } else {
                            task.testDir.set(project.layout.projectDirectory.dir("src/test/kotlin/$packagePath"))
                        }
                        task.autoDelete.set(false)
                    }

                    // Status per target
                    project.tasks.register("stateproofStatus$capitalizedName$targetSuffix", StateProofStatusTask::class.java) { task ->
                        task.group = TASK_GROUP
                        task.description = "Show ${targetLabel}sync status for $name state machine"
                        task.configureFromStateMachineConfig(smConfig, extension, target)
                    }
                }
            }

            // Aggregate clean task
            project.tasks.register("stateproofCleanObsoleteAll") { task ->
                task.group = TASK_GROUP
                task.description = "Remove obsolete test files for all state machines"
                task.dependsOn(allCleanTasks)
            }
        } else {
            // Single-SM mode
            project.tasks.register("stateproofCleanObsolete", StateProofCleanObsoleteTask::class.java) { task ->
                task.group = TASK_GROUP
                task.description = "Remove obsolete test files (marked with @StateProofObsolete)"
                task.testDir.set(extension.testDir)
                task.autoDelete.set(false)
            }

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
