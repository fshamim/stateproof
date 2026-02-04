package io.stateproof.sync

import io.stateproof.graph.StateInfo
import io.stateproof.testgen.TestGenConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestSyncEngineTest {

    private fun createSimpleStateMachine(): Map<String, StateInfo> {
        return mapOf(
            "A" to StateInfo("A").apply {
                transitions["ToB"] = "B"
            },
            "B" to StateInfo("B").apply {
                transitions["ToC"] = "C"
            },
            "C" to StateInfo("C"),
        )
    }

    @Test
    fun sync_withNoExistingTests_allTestsAreNew() {
        val engine = TestSyncEngine(TestGenConfig.DEFAULT)
        val stateInfo = createSimpleStateMachine()

        val report = engine.sync(
            stateInfoMap = stateInfo,
            initialState = "A",
            existingTests = emptyMap(),
            currentTimestamp = "2024-01-15T10:00:00Z",
        )

        assertTrue(report.newTests.isNotEmpty(), "Should have new tests")
        assertTrue(report.unchangedTests.isEmpty(), "Should have no unchanged tests")
        assertTrue(report.modifiedTests.isEmpty(), "Should have no modified tests")
        assertTrue(report.obsoleteTests.isEmpty(), "Should have no obsolete tests")
    }

    @Test
    fun sync_withMatchingExistingTests_testsAreUnchanged() {
        val engine = TestSyncEngine(TestGenConfig.DEFAULT)
        val stateInfo = createSimpleStateMachine()

        // First sync to get the expected test cases
        val initialReport = engine.sync(
            stateInfoMap = stateInfo,
            initialState = "A",
            existingTests = emptyMap(),
            currentTimestamp = "2024-01-15T10:00:00Z",
        )

        // Create "existing" tests that match
        val existingTests = initialReport.newTests.associate { result ->
            result.pathHash to TestFileParser.ParsedTest(
                fullText = "fun test() {}",
                pathHash = result.pathHash,
                functionName = result.testCase?.name ?: "unknown",
                generatedSection = null,
                userSection = null,
                isObsolete = false,
                expectedTransitions = result.newTransitions ?: emptyList(),
                startLine = 0,
            )
        }

        // Sync again with existing tests
        val report = engine.sync(
            stateInfoMap = stateInfo,
            initialState = "A",
            existingTests = existingTests,
            currentTimestamp = "2024-01-15T11:00:00Z",
        )

        assertTrue(report.newTests.isEmpty(), "Should have no new tests")
        assertEquals(existingTests.size, report.unchangedTests.size, "All tests should be unchanged")
        assertTrue(report.modifiedTests.isEmpty(), "Should have no modified tests")
        assertTrue(report.obsoleteTests.isEmpty(), "Should have no obsolete tests")
    }

    @Test
    fun sync_withRemovedPath_testIsObsolete() {
        val engine = TestSyncEngine(TestGenConfig.DEFAULT)
        val stateInfo = createSimpleStateMachine()

        // Create an existing test that doesn't match any current path
        val existingTests = mapOf(
            "DEADBEEF" to TestFileParser.ParsedTest(
                fullText = "fun test() {}",
                pathHash = "DEADBEEF",
                functionName = "old_test",
                generatedSection = null,
                userSection = null,
                isObsolete = false,
                expectedTransitions = listOf("OldState_OldEvent_OldTarget"),
                startLine = 0,
            )
        )

        val report = engine.sync(
            stateInfoMap = stateInfo,
            initialState = "A",
            existingTests = existingTests,
            currentTimestamp = "2024-01-15T10:00:00Z",
        )

        assertTrue(report.obsoleteTests.isNotEmpty(), "Should have obsolete tests")
        assertEquals("DEADBEEF", report.obsoleteTests.first().pathHash)
        assertEquals(TestSyncStatus.OBSOLETE, report.obsoleteTests.first().status)
    }

    @Test
    fun sync_withModifiedTransitions_testIsModified() {
        val engine = TestSyncEngine(TestGenConfig.DEFAULT)
        val stateInfo = createSimpleStateMachine()

        // First sync to get the expected test cases
        val initialReport = engine.sync(
            stateInfoMap = stateInfo,
            initialState = "A",
            existingTests = emptyMap(),
            currentTimestamp = "2024-01-15T10:00:00Z",
        )

        // Create "existing" tests with DIFFERENT transitions
        val existingTests = initialReport.newTests.associate { result ->
            result.pathHash to TestFileParser.ParsedTest(
                fullText = "fun test() {}",
                pathHash = result.pathHash,
                functionName = result.testCase?.name ?: "unknown",
                generatedSection = null,
                userSection = null,
                isObsolete = false,
                // Different transitions!
                expectedTransitions = listOf("Different_Transition_Here"),
                startLine = 0,
            )
        }

        val report = engine.sync(
            stateInfoMap = stateInfo,
            initialState = "A",
            existingTests = existingTests,
            currentTimestamp = "2024-01-15T11:00:00Z",
        )

        assertTrue(report.newTests.isEmpty(), "Should have no new tests")
        assertTrue(report.unchangedTests.isEmpty(), "Should have no unchanged tests")
        assertEquals(existingTests.size, report.modifiedTests.size, "All tests should be modified")
        assertTrue(report.obsoleteTests.isEmpty(), "Should have no obsolete tests")
    }

    @Test
    fun syncReport_summary_formatsCorrectly() {
        val report = SyncReport(
            total = 10,
            newTests = listOf(
                TestSyncResult("HASH1", TestSyncStatus.NEW, null, null, null, listOf("A_B_C")),
            ),
            unchangedTests = listOf(
                TestSyncResult("HASH2", TestSyncStatus.UNCHANGED, null, null, listOf("A_B_C"), listOf("A_B_C")),
            ),
            modifiedTests = emptyList(),
            obsoleteTests = listOf(
                TestSyncResult("HASH3", TestSyncStatus.OBSOLETE, null,
                    TestFileParser.ParsedTest("", "HASH3", "old_test", null, null, false, emptyList(), 0),
                    listOf("Old_Path"), null),
            ),
            syncedAt = "2024-01-15T10:00:00Z",
        )

        val summary = report.summary()

        assertTrue(summary.contains("StateProof Sync Report"), "Should have title")
        assertTrue(summary.contains("1 tests unchanged"), "Should show unchanged count")
        assertTrue(summary.contains("1 new tests generated"), "Should show new count")
        assertTrue(summary.contains("1 tests marked OBSOLETE"), "Should show obsolete count")
    }
}
