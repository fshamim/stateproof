package io.stateproof.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Task to sync existing tests with the current state machine definition.
 *
 * This task:
 * 1. Loads the state machine info via reflection (using the configured provider)
 * 2. Enumerates all paths from the current state machine
 * 3. Scans existing test files for @StateProofGenerated annotations
 * 4. Classifies each test as NEW, UNCHANGED, MODIFIED, or OBSOLETE
 * 5. Updates tests accordingly (preserving user code)
 *
 * Usage:
 *   ./gradlew stateproofSync          # Full sync
 *   ./gradlew stateproofSyncDryRun    # Preview changes
 */
abstract class StateProofSyncTask : StateProofBaseTask() {

    @get:OutputDirectory
    abstract val testDir: DirectoryProperty

    @get:Input
    abstract val preserveUserCode: Property<Boolean>

    @get:Input
    abstract val autoDeleteObsolete: Property<Boolean>

    @get:Input
    abstract val dryRunMode: Property<Boolean>

    override fun configureFrom(extension: StateProofExtension) {
        super.configureFrom(extension)
        testDir.set(extension.testDir)
        preserveUserCode.set(extension.preserveUserCode)
        autoDeleteObsolete.set(extension.autoDeleteObsolete)
    }

    override fun configureFromStateMachineConfig(config: StateMachineConfig, extension: StateProofExtension) {
        super.configureFromStateMachineConfig(config, extension)
        
        // Compute test directory from package if not explicitly set
        // Default: src/test/kotlin/<package-as-path>
        if (!config.testDir.isPresent) {
            val effectivePackage = config.getEffectivePackage()
            val packagePath = effectivePackage.replace('.', '/')
            testDir.set(project.layout.projectDirectory.dir("src/test/kotlin/$packagePath"))
        } else {
            testDir.set(config.testDir)
        }
        
        preserveUserCode.set(extension.preserveUserCode)
        autoDeleteObsolete.set(extension.autoDeleteObsolete)
    }

    @TaskAction
    fun sync() {
        val provider = stateMachineInfoProvider.get()
        if (provider.isBlank()) {
            throw org.gradle.api.GradleException(
                "State machine provider must be set. Either:\n" +
                    "1. Set stateproof.stateMachineFactoryFqn or stateproof.stateMachineInfoProvider\n" +
                    "2. Use stateproof.stateMachines { create(\"name\") { factory.set(...) } }"
            )
        }

        val args = mutableListOf(
            "--test-dir", testDir.get().asFile.absolutePath,
            "--report", reportFile.get().asFile.absolutePath,
        )

        // Pass --is-factory flag if using factory mode (auto-extracts StateInfo)
        if (providerIsFactory.get()) {
            args.add("--is-factory")
        }

        if (dryRunMode.get()) {
            args.add("--dry-run")
        }

        if (!preserveUserCode.get()) {
            args.add("--no-preserve-user-code")
        }

        if (autoDeleteObsolete.get()) {
            args.add("--auto-delete-obsolete")
        }

        executeCli("sync", *args.toTypedArray())
    }
}

