package io.stateproof.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Task to show the current sync status.
 *
 * Shows:
 * - State machine info (states, transitions)
 * - Number of expected test paths
 * - Existing test count and classification
 * - Sync report (NEW, UNCHANGED, MODIFIED, OBSOLETE)
 *
 * Usage: ./gradlew stateproofStatus
 */
abstract class StateProofStatusTask : StateProofBaseTask() {

    @get:OutputDirectory
    abstract val testDir: DirectoryProperty

    override fun configureFrom(extension: StateProofExtension) {
        super.configureFrom(extension)
        testDir.set(extension.testDir)
    }

    fun configureFromStateMachineConfig(config: StateMachineConfig, extension: StateProofExtension, target: String = "jvm") {
        super.configureFromStateMachineConfig(config, extension)

        val effectivePackage = config.getEffectivePackage()
        val packagePath = effectivePackage.replace('.', '/')

        if (target == "android") {
            if (!config.androidTestDir.isPresent) {
                testDir.set(project.layout.projectDirectory.dir("src/androidTest/kotlin/$packagePath"))
            } else {
                testDir.set(config.androidTestDir)
            }
        } else {
            if (!config.testDir.isPresent) {
                testDir.set(project.layout.projectDirectory.dir("src/test/kotlin/$packagePath"))
            } else {
                testDir.set(config.testDir)
            }
        }
    }

    @TaskAction
    fun status() {
        val provider = stateMachineInfoProvider.get()
        if (provider.isBlank()) {
            throw org.gradle.api.GradleException(
                "stateMachineInfoProvider must be set in the stateproof extension."
            )
        }

        executeCli(
            "status",
            "--test-dir", testDir.get().asFile.absolutePath,
        )
    }
}
