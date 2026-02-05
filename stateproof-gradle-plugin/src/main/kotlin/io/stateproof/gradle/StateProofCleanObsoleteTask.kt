package io.stateproof.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import io.stateproof.sync.TestFileParser

/**
 * Task to remove test functions marked with @StateProofObsolete.
 *
 * Obsolete tests are those whose paths no longer exist in the state machine.
 * This task finds and removes them from the test files.
 *
 * Usage:
 *   ./gradlew stateproofCleanObsolete
 */
abstract class StateProofCleanObsoleteTask : DefaultTask() {

    @get:InputDirectory
    @get:Optional
    abstract val testDir: DirectoryProperty

    @get:Input
    abstract val autoDelete: Property<Boolean>

    @TaskAction
    fun cleanObsolete() {
        val dir = testDir.orNull?.asFile
        if (dir == null || !dir.exists()) {
            logger.lifecycle("No test directory found. Nothing to clean.")
            return
        }

        logger.lifecycle("StateProof Clean Obsolete")
        logger.lifecycle("=========================")
        logger.lifecycle("Scanning: ${dir.absolutePath}")
        logger.lifecycle("")

        // Find files with @StateProofObsolete annotation
        data class ObsoleteInfo(val file: java.io.File, val test: TestFileParser.ParsedTest)

        val obsoleteTests = mutableListOf<ObsoleteInfo>()

        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val content = file.readText()
                if (content.contains("@StateProofObsolete")) {
                    val parsed = TestFileParser.parseTestFile(file.absolutePath, content)
                    for (test in parsed.tests) {
                        if (test.isObsolete) {
                            obsoleteTests.add(ObsoleteInfo(file, test))
                        }
                    }
                }
            }

        if (obsoleteTests.isEmpty()) {
            logger.lifecycle("No obsolete tests found.")
            return
        }

        logger.lifecycle("Found ${obsoleteTests.size} obsolete test(s):")
        logger.lifecycle("")

        obsoleteTests.forEachIndexed { index, info ->
            logger.lifecycle("${index + 1}. ${info.test.functionName}")
            logger.lifecycle("   File: ${info.file.name}")
            if (info.test.expectedTransitions.isNotEmpty()) {
                logger.lifecycle("   Path: ${info.test.expectedTransitions.take(3).joinToString(" -> ")}...")
            }
            logger.lifecycle("")
        }

        if (autoDelete.get()) {
            logger.lifecycle("Deleting obsolete tests...")

            // Group by file
            val byFile = obsoleteTests.groupBy({ it.file }, { it.test })
            for ((file, tests) in byFile) {
                var content = file.readText()
                for (test in tests) {
                    content = content.replace(test.fullText, "")
                }
                // Clean up multiple blank lines
                content = content.replace(Regex("\n{3,}"), "\n\n")
                file.writeText(content)
                logger.lifecycle("  Updated: ${file.name} (removed ${tests.size} test(s))")
            }

            logger.lifecycle("")
            logger.lifecycle("Done. ${obsoleteTests.size} obsolete test(s) removed.")
        } else {
            logger.lifecycle("Set autoDeleteObsolete = true in stateproof extension to auto-delete,")
            logger.lifecycle("or review and delete manually in your IDE.")
        }
    }
}
