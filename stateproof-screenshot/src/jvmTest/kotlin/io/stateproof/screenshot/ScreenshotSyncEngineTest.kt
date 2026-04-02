package io.stateproof.screenshot

import io.stateproof.graph.StateInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenshotSyncEngineTest {

    @Test
    fun sync_classifiesNewAndObsoleteAndUnchanged() {
        val stateInfoMap = mapOf(
            "Initial" to StateInfo(
                stateName = "Initial",
                transitions = mutableMapOf("Go" to "Done"),
            ),
            "Done" to StateInfo(
                stateName = "Done",
                transitions = mutableMapOf(),
            ),
        )
        val existing = mapOf(
            "OLD1" to ScreenshotFileParser.ParsedScreenshotTest(
                fullText = "fun old() {}",
                pathHash = "OLD1",
                functionName = "old",
                generatedSection = null,
                userSection = null,
                isObsolete = false,
                expectedTransitions = listOf("A_E_B"),
                startLine = 1,
            )
        )

        val report = ScreenshotSyncEngine().sync(
            stateInfoMap = stateInfoMap,
            initialState = "Initial",
            existingTests = existing,
            currentTimestamp = "2026-02-23T00:00:00Z",
        )

        assertEquals(1, report.newTests.size)
        assertEquals(1, report.obsoleteTests.size)
        assertEquals(0, report.modifiedTests.size)
        assertEquals(0, report.unchangedTests.size)
    }
}
