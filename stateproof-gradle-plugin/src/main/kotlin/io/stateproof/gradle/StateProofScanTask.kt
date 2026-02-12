package io.stateproof.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Writes an AI-friendly integration scan report for the current module.
 */
abstract class StateProofScanTask : org.gradle.api.DefaultTask() {

    init {
        outputs.upToDateWhen { false }
    }

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun scan() {
        val report = StateProofProjectScanner.scan(project)
        val file = outputFile.get().asFile
        file.parentFile?.mkdirs()
        file.writeText(report.toJson() + "\n")

        logger.lifecycle("StateProof scan report written: ${file.absolutePath}")
        logger.lifecycle(
            "Project type=${report.projectType}, integrationMode=${report.integrationMode}, " +
                "stateMachineFiles=${report.detectedStateMachineFiles.size}"
        )
    }
}
