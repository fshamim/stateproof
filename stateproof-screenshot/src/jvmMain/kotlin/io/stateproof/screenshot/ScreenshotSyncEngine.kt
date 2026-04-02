package io.stateproof.screenshot

import io.stateproof.graph.StateInfo
import io.stateproof.testgen.SimplePathEnumerator
import io.stateproof.testgen.SimpleTestCase
import io.stateproof.testgen.TestGenConfig

enum class ScreenshotSyncStatus {
    NEW,
    UNCHANGED,
    MODIFIED,
    OBSOLETE,
}

data class ScreenshotSyncResult(
    val pathHash: String,
    val status: ScreenshotSyncStatus,
    val testCase: SimpleTestCase?,
    val existingTest: ScreenshotFileParser.ParsedScreenshotTest?,
    val oldTransitions: List<String>?,
    val newTransitions: List<String>?,
)

data class ScreenshotSyncReport(
    val total: Int,
    val newTests: List<ScreenshotSyncResult>,
    val unchangedTests: List<ScreenshotSyncResult>,
    val modifiedTests: List<ScreenshotSyncResult>,
    val obsoleteTests: List<ScreenshotSyncResult>,
    val syncedAt: String,
) {
    fun summary(): String = buildString {
        appendLine("┌─────────────────────────────────────────────────────────────┐")
        appendLine("│ StateProof Screenshot Sync Report                           │")
        appendLine("├─────────────────────────────────────────────────────────────┤")
        if (unchangedTests.isNotEmpty()) {
            appendLine("│ ✓ ${unchangedTests.size} tests unchanged".padEnd(62) + "│")
        }
        if (newTests.isNotEmpty()) {
            appendLine("│ + ${newTests.size} new screenshot tests generated".padEnd(62) + "│")
        }
        if (modifiedTests.isNotEmpty()) {
            appendLine("│ ~ ${modifiedTests.size} screenshot tests updated".padEnd(62) + "│")
        }
        if (obsoleteTests.isNotEmpty()) {
            appendLine("│ ⚠ ${obsoleteTests.size} screenshot tests marked obsolete".padEnd(62) + "│")
        }
        appendLine("└─────────────────────────────────────────────────────────────┘")
    }
}

class ScreenshotSyncEngine(
    private val config: TestGenConfig = TestGenConfig.DEFAULT,
) {
    fun sync(
        stateInfoMap: Map<String, StateInfo>,
        initialState: String,
        existingTests: Map<String, ScreenshotFileParser.ParsedScreenshotTest>,
        currentTimestamp: String,
    ): ScreenshotSyncReport {
        val enumerator = SimplePathEnumerator(
            stateInfoMap = stateInfoMap,
            initialState = initialState,
            config = config,
        )
        val currentTestCases = enumerator.generateTestCases()
        val currentByHash = currentTestCases.associateBy { extractHash(it.name) }

        val allHashes = (currentByHash.keys + existingTests.keys).distinct()
        val results = allHashes.map { hash ->
            val currentCase = currentByHash[hash]
            val existingTest = existingTests[hash]
            when {
                currentCase != null && existingTest == null -> ScreenshotSyncResult(
                    pathHash = hash,
                    status = ScreenshotSyncStatus.NEW,
                    testCase = currentCase,
                    existingTest = null,
                    oldTransitions = null,
                    newTransitions = currentCase.expectedTransitions,
                )

                currentCase == null && existingTest != null -> ScreenshotSyncResult(
                    pathHash = hash,
                    status = ScreenshotSyncStatus.OBSOLETE,
                    testCase = null,
                    existingTest = existingTest,
                    oldTransitions = existingTest.expectedTransitions,
                    newTransitions = null,
                )

                currentCase != null && existingTest != null -> {
                    val currentTransitions = currentCase.expectedTransitions
                    val existingTransitions = existingTest.expectedTransitions
                    if (currentTransitions == existingTransitions) {
                        ScreenshotSyncResult(
                            pathHash = hash,
                            status = ScreenshotSyncStatus.UNCHANGED,
                            testCase = currentCase,
                            existingTest = existingTest,
                            oldTransitions = existingTransitions,
                            newTransitions = currentTransitions,
                        )
                    } else {
                        ScreenshotSyncResult(
                            pathHash = hash,
                            status = ScreenshotSyncStatus.MODIFIED,
                            testCase = currentCase,
                            existingTest = existingTest,
                            oldTransitions = existingTransitions,
                            newTransitions = currentTransitions,
                        )
                    }
                }

                else -> error("Unreachable hash=$hash")
            }
        }

        return ScreenshotSyncReport(
            total = results.size,
            newTests = results.filter { it.status == ScreenshotSyncStatus.NEW },
            unchangedTests = results.filter { it.status == ScreenshotSyncStatus.UNCHANGED },
            modifiedTests = results.filter { it.status == ScreenshotSyncStatus.MODIFIED },
            obsoleteTests = results.filter { it.status == ScreenshotSyncStatus.OBSOLETE },
            syncedAt = currentTimestamp,
        )
    }

    private fun extractHash(testName: String): String {
        val parts = testName.split("_")
        return if (parts.size >= 3) parts[2] else testName
    }
}
