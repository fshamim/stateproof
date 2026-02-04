package io.stateproof.sync

import io.stateproof.testgen.SimplePathEnumerator
import io.stateproof.testgen.SimpleTestCase
import io.stateproof.testgen.TestGenConfig
import io.stateproof.graph.StateInfo

/**
 * Classification of a test during sync.
 */
enum class TestSyncStatus {
    /** Path exists in current state machine, no existing test */
    NEW,
    /** Path exists, test exists, transitions unchanged */
    UNCHANGED,
    /** Path exists, test exists, transitions differ */
    MODIFIED,
    /** Path doesn't exist, test exists (path was removed) */
    OBSOLETE,
}

/**
 * Result of syncing a single test.
 */
data class TestSyncResult(
    /** The path hash identifying this test */
    val pathHash: String,
    /** Sync classification */
    val status: TestSyncStatus,
    /** The test case from current enumeration (null if OBSOLETE) */
    val testCase: SimpleTestCase?,
    /** The existing parsed test (null if NEW) */
    val existingTest: TestFileParser.ParsedTest?,
    /** Old expected transitions (for MODIFIED) */
    val oldTransitions: List<String>?,
    /** New expected transitions */
    val newTransitions: List<String>?,
)

/**
 * Summary of a sync operation.
 */
data class SyncReport(
    /** Total tests in sync */
    val total: Int,
    /** New tests to generate */
    val newTests: List<TestSyncResult>,
    /** Unchanged tests */
    val unchangedTests: List<TestSyncResult>,
    /** Modified tests (transitions changed) */
    val modifiedTests: List<TestSyncResult>,
    /** Obsolete tests (path no longer exists) */
    val obsoleteTests: List<TestSyncResult>,
    /** Timestamp of sync */
    val syncedAt: String,
) {
    fun summary(): String = buildString {
        appendLine("┌─────────────────────────────────────────────────────────────┐")
        appendLine("│ StateProof Sync Report                                      │")
        appendLine("├─────────────────────────────────────────────────────────────┤")

        if (unchangedTests.isNotEmpty()) {
            appendLine("│ ✓ ${unchangedTests.size} tests unchanged".padEnd(62) + "│")
        }

        if (newTests.isNotEmpty()) {
            appendLine("│ + ${newTests.size} new tests generated".padEnd(62) + "│")
            newTests.take(3).forEach { result ->
                val name = result.testCase?.name?.take(50) ?: "unknown"
                appendLine("│   • $name".padEnd(62) + "│")
            }
            if (newTests.size > 3) {
                appendLine("│   ... and ${newTests.size - 3} more".padEnd(62) + "│")
            }
        }

        if (modifiedTests.isNotEmpty()) {
            appendLine("│".padEnd(62) + "│")
            appendLine("│ ~ ${modifiedTests.size} tests updated (expected transitions changed)".padEnd(62) + "│")
            modifiedTests.take(3).forEach { result ->
                val name = result.testCase?.name?.take(50) ?: "unknown"
                appendLine("│   • $name".padEnd(62) + "│")
            }
            if (modifiedTests.size > 3) {
                appendLine("│   ... and ${modifiedTests.size - 3} more".padEnd(62) + "│")
            }
        }

        if (obsoleteTests.isNotEmpty()) {
            appendLine("│".padEnd(62) + "│")
            appendLine("│ ⚠ ${obsoleteTests.size} tests marked OBSOLETE (require manual review)".padEnd(62) + "│")
            obsoleteTests.take(3).forEach { result ->
                val name = result.existingTest?.functionName?.take(45) ?: "unknown"
                appendLine("│   • $name".padEnd(62) + "│")
            }
            if (obsoleteTests.size > 3) {
                appendLine("│   ... and ${obsoleteTests.size - 3} more".padEnd(62) + "│")
            }
            appendLine("│".padEnd(62) + "│")
            appendLine("│ Run: ./gradlew stateproofCleanObsolete to review & delete".padEnd(62) + "│")
        }

        appendLine("└─────────────────────────────────────────────────────────────┘")
    }
}

/**
 * Engine that syncs generated tests with the current state machine definition.
 *
 * The sync algorithm:
 * 1. Enumerate all paths from current state machine → Set<PathHash>
 * 2. Scan existing test files for @StateProofGenerated → Map<PathHash, Test>
 * 3. Classify each test as NEW, UNCHANGED, MODIFIED, or OBSOLETE
 */
class TestSyncEngine(
    private val config: TestGenConfig = TestGenConfig.DEFAULT,
) {

    /**
     * Performs a sync between current state machine and existing tests.
     *
     * @param stateInfoMap The current state machine definition
     * @param initialState The initial state name
     * @param existingTests Map of path hash to existing parsed tests
     * @param currentTimestamp ISO-8601 timestamp
     * @return Sync report with classification of all tests
     */
    fun sync(
        stateInfoMap: Map<String, StateInfo>,
        initialState: String,
        existingTests: Map<String, TestFileParser.ParsedTest>,
        currentTimestamp: String,
    ): SyncReport {
        // Step 1: Enumerate all current paths
        val enumerator = SimplePathEnumerator(
            stateInfoMap = stateInfoMap,
            initialState = initialState,
            config = config,
        )
        val currentTestCases = enumerator.generateTestCases()
        val currentByHash = currentTestCases.associateBy { extractHash(it.name) }

        // Step 2 & 3: Classify each test
        val allHashes = (currentByHash.keys + existingTests.keys).distinct()

        val results = allHashes.map { hash ->
            val currentCase = currentByHash[hash]
            val existingTest = existingTests[hash]

            when {
                // NEW: exists in current, not in existing
                currentCase != null && existingTest == null -> {
                    TestSyncResult(
                        pathHash = hash,
                        status = TestSyncStatus.NEW,
                        testCase = currentCase,
                        existingTest = null,
                        oldTransitions = null,
                        newTransitions = currentCase.expectedTransitions,
                    )
                }

                // OBSOLETE: exists in existing, not in current
                currentCase == null && existingTest != null -> {
                    TestSyncResult(
                        pathHash = hash,
                        status = TestSyncStatus.OBSOLETE,
                        testCase = null,
                        existingTest = existingTest,
                        oldTransitions = existingTest.expectedTransitions,
                        newTransitions = null,
                    )
                }

                // Both exist - check if transitions match
                currentCase != null && existingTest != null -> {
                    val currentTransitions = currentCase.expectedTransitions
                    val existingTransitions = existingTest.expectedTransitions

                    if (currentTransitions == existingTransitions) {
                        TestSyncResult(
                            pathHash = hash,
                            status = TestSyncStatus.UNCHANGED,
                            testCase = currentCase,
                            existingTest = existingTest,
                            oldTransitions = existingTransitions,
                            newTransitions = currentTransitions,
                        )
                    } else {
                        TestSyncResult(
                            pathHash = hash,
                            status = TestSyncStatus.MODIFIED,
                            testCase = currentCase,
                            existingTest = existingTest,
                            oldTransitions = existingTransitions,
                            newTransitions = currentTransitions,
                        )
                    }
                }

                else -> error("Unreachable: hash=$hash")
            }
        }

        return SyncReport(
            total = results.size,
            newTests = results.filter { it.status == TestSyncStatus.NEW },
            unchangedTests = results.filter { it.status == TestSyncStatus.UNCHANGED },
            modifiedTests = results.filter { it.status == TestSyncStatus.MODIFIED },
            obsoleteTests = results.filter { it.status == TestSyncStatus.OBSOLETE },
            syncedAt = currentTimestamp,
        )
    }

    /**
     * Extracts the hash from a generated test name.
     * Test names follow pattern: _<depth>_<hash>_<path>
     */
    private fun extractHash(testName: String): String {
        // Pattern: _3_XXXX_path...
        val parts = testName.split("_")
        return if (parts.size >= 3) parts[2] else testName
    }
}
