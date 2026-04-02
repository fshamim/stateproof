package io.stateproof.screenshot

import io.stateproof.graph.StateInfo
import io.stateproof.testgen.SimplePathEnumerator
import io.stateproof.testgen.TestGenConfig
import java.io.File
import java.time.Instant

data class ScreenshotSyncOutcome(
    val report: ScreenshotSyncReport,
    val filesModified: List<File>,
    val filesCreated: List<File>,
)

object StateProofScreenshotSync {

    fun generate(
        stateInfoMap: Map<String, StateInfo>,
        initialState: String,
        codeGenConfig: ScreenshotCodeGenConfig,
        testGenConfig: TestGenConfig = TestGenConfig.DEFAULT,
    ): String {
        val enumerator = SimplePathEnumerator(stateInfoMap, initialState, testGenConfig)
        val testCases = enumerator.generateTestCases()
        return ScreenshotTestCodeGenerator.generateTestFile(
            config = codeGenConfig,
            tests = testCases,
            timestamp = Instant.now().toString(),
        )
    }

    fun sync(
        stateInfoMap: Map<String, StateInfo>,
        initialState: String,
        testFile: File,
        codeGenConfig: ScreenshotCodeGenConfig,
        testGenConfig: TestGenConfig = TestGenConfig.DEFAULT,
        dryRun: Boolean = false,
    ): ScreenshotSyncOutcome {
        val timestamp = Instant.now().toString()
        val enumerator = SimplePathEnumerator(stateInfoMap, initialState, testGenConfig)
        val currentTestCases = enumerator.generateTestCases()
        val currentByHash = currentTestCases.associateBy { extractHash(it.name) }

        val existingByHash = if (testFile.exists()) {
            val parsed = ScreenshotFileParser.parseTestFile(testFile.absolutePath, testFile.readText())
            parsed.tests.mapNotNull { test -> test.pathHash?.let { it to test } }.toMap()
        } else {
            emptyMap()
        }

        val engine = ScreenshotSyncEngine(testGenConfig)
        val report = engine.sync(
            stateInfoMap = stateInfoMap,
            initialState = initialState,
            existingTests = existingByHash,
            currentTimestamp = timestamp,
        )

        val renderedTests = mutableListOf<String>()
        currentTestCases.sortedBy { it.name }.forEach { testCase ->
            val hash = extractHash(testCase.name)
            val existing = existingByHash[hash]
            if (existing == null) {
                renderedTests += ScreenshotTestCodeGenerator.generateSingleTest(codeGenConfig, testCase, timestamp)
            } else if (existing.expectedTransitions != testCase.expectedTransitions) {
                renderedTests += ScreenshotTestCodeGenerator.updateExistingTest(
                    existingTest = existing,
                    newTransitions = testCase.expectedTransitions,
                    timestamp = timestamp,
                    indent = codeGenConfig.indent,
                )
            } else {
                renderedTests += existing.fullText
            }
        }

        report.obsoleteTests
            .mapNotNull { it.existingTest }
            .sortedBy { it.functionName }
            .forEach { obsolete ->
                renderedTests += ScreenshotTestCodeGenerator.markTestObsolete(
                    existingTest = obsolete,
                    reason = "Path removed from current state graph",
                    timestamp = timestamp,
                    indent = codeGenConfig.indent,
                )
            }

        val fileContent = ScreenshotTestCodeGenerator.generateTestFileFromRenderedTests(
            config = codeGenConfig,
            renderedTests = renderedTests,
            timestamp = timestamp,
            totalTests = renderedTests.size,
        )

        val filesModified = mutableListOf<File>()
        val filesCreated = mutableListOf<File>()
        if (!dryRun) {
            testFile.parentFile?.mkdirs()
            val existed = testFile.exists()
            testFile.writeText(fileContent)
            if (existed) filesModified += testFile else filesCreated += testFile
        } else {
            if (testFile.exists()) filesModified += testFile else filesCreated += testFile
        }

        return ScreenshotSyncOutcome(
            report = report,
            filesModified = filesModified,
            filesCreated = filesCreated,
        )
    }

    private fun extractHash(testName: String): String {
        val parts = testName.split("_")
        return if (parts.size >= 3) parts[2] else testName
    }
}
