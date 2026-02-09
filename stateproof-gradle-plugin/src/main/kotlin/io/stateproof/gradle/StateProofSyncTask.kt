package io.stateproof.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
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

    init {
        // Sync must always execute to detect manual test edits/deletions.
        outputs.upToDateWhen { false }
    }

    @get:OutputDirectory
    abstract val testDir: DirectoryProperty

    @get:Input
    abstract val dryRunMode: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val classAnnotations: ListProperty<String>

    @get:Input
    abstract val useRunTest: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val syncPackage: Property<String>

    @get:Input
    @get:Optional
    abstract val syncClassName: Property<String>

    @get:Input
    @get:Optional
    abstract val syncFactory: Property<String>

    @get:Input
    @get:Optional
    abstract val syncEventPrefix: Property<String>

    @get:Input
    @get:Optional
    abstract val syncImports: ListProperty<String>

    override fun configureFrom(extension: StateProofExtension) {
        super.configureFrom(extension)
        testDir.set(extension.testDir)
        classAnnotations.set(emptyList())
        useRunTest.set(false)
        syncPackage.set(extension.testPackage)
        syncClassName.set(extension.testClassName)
        syncFactory.set(extension.stateMachineFactory)
        syncEventPrefix.set(extension.eventClassPrefix)
        syncImports.set(extension.additionalImports)
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
            val androidImports = config.androidAdditionalImports.getOrElse(emptyList())
            val userImports = config.additionalImports.getOrElse(emptyList())
            classAnnotations.set(listOf("@RunWith(AndroidJUnit4::class)"))
            useRunTest.set(true)
            syncClassName.set(config.getEffectiveAndroidClassName())
            syncImports.set(androidImports + userImports)
        } else {
            if (!config.testDir.isPresent) {
                testDir.set(project.layout.projectDirectory.dir("src/test/kotlin/$packagePath"))
            } else {
                testDir.set(config.testDir)
            }
            classAnnotations.set(emptyList())
            useRunTest.set(false)
            syncClassName.set(config.getEffectiveClassName())
            syncImports.set(config.additionalImports)
        }

        syncPackage.set(effectivePackage)
        syncFactory.set(config.stateMachineFactory)
        syncEventPrefix.set(config.eventClassPrefix)
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

        if (providerIsFactory.get()) {
            args.add("--is-factory")
        }

        if (dryRunMode.get()) {
            args.add("--dry-run")
        }

        // Pass code gen config for creating new test files during sync
        val annotations = classAnnotations.getOrElse(emptyList())
        if (annotations.isNotEmpty()) {
            args.addAll(listOf("--class-annotations", annotations.joinToString("|")))
        }

        if (useRunTest.getOrElse(false)) {
            args.add("--use-run-test")
        }

        val pkg = syncPackage.orNull
        if (!pkg.isNullOrBlank()) {
            args.addAll(listOf("--package", pkg))
        }

        val cls = syncClassName.orNull
        if (!cls.isNullOrBlank()) {
            args.addAll(listOf("--class-name", cls))
        }

        val factory = syncFactory.orNull
        if (!factory.isNullOrBlank()) {
            args.addAll(listOf("--factory", factory))
        }

        val eventPrefix = syncEventPrefix.orNull
        if (!eventPrefix.isNullOrBlank()) {
            args.addAll(listOf("--event-prefix", eventPrefix))
        }

        val imports = syncImports.getOrElse(emptyList())
        if (imports.isNotEmpty()) {
            args.addAll(listOf("--imports", imports.joinToString(",")))
        }

        executeCli("sync", *args.toTypedArray())
    }
}
