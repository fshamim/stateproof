package io.stateproof.testgen

import io.stateproof.graph.EmittedEventInfo
import io.stateproof.graph.StateInfo
import io.stateproof.graph.StateTransitionInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GuardedPathEnumeratorTest {

    @Test
    fun deterministicTransitions_shouldKeepLegacyHashBasis() {
        val map = mapOf(
            "A" to StateInfo("A").apply { transitions["Go"] = "B" },
            "B" to StateInfo("B"),
        )
        val config = TestGenConfig(hashAlgorithm = TestGenConfig.HashAlgorithm.CRC16)
        val enumerator = SimplePathEnumerator(map, initialState = "A", config = config)

        val testCase = enumerator.generateTestCases().single()
        val expectedHash = HashUtils.hashPath("A_Go_B", TestGenConfig.HashAlgorithm.CRC16)

        assertTrue(testCase.name.contains("_${expectedHash}_"))
        assertEquals(listOf("A_Go_B"), testCase.expectedTransitions)
    }

    @Test
    fun guardMetadata_shouldDifferentiateHashesForSameTransitionString() {
        val stateA = StateInfo("A").apply {
            transitions["Go"] = "B"
            transitionDetails += StateTransitionInfo(
                eventName = "Go",
                toStateName = "B",
                guardLabel = "case.one",
            )
            transitionDetails += StateTransitionInfo(
                eventName = "Go",
                toStateName = "B",
                guardLabel = "case.two",
            )
        }

        val map = mapOf(
            "A" to stateA,
            "B" to StateInfo("B"),
        )

        val enumerator = SimplePathEnumerator(
            stateInfoMap = map,
            initialState = "A",
            config = TestGenConfig(hashAlgorithm = TestGenConfig.HashAlgorithm.CRC16),
        )

        val testCases = enumerator.generateTestCases()
        assertEquals(2, testCases.size)
        assertEquals(testCases[0].expectedTransitions, testCases[1].expectedTransitions)

        val hash1 = extractHash(testCases[0].name)
        val hash2 = extractHash(testCases[1].name)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun emittedMetadata_shouldDifferentiateHashesWithoutGuardLabel() {
        val stateA = StateInfo("A").apply {
            transitions["Go"] = "B"
            transitionDetails += StateTransitionInfo(
                eventName = "Go",
                toStateName = "B",
                emittedEvents = listOf(EmittedEventInfo("ok", "Done")),
            )
            transitionDetails += StateTransitionInfo(
                eventName = "Go",
                toStateName = "B",
                emittedEvents = listOf(EmittedEventInfo("retry", "Retry")),
            )
        }

        val map = mapOf(
            "A" to stateA,
            "B" to StateInfo("B"),
        )
        val enumerator = SimplePathEnumerator(
            stateInfoMap = map,
            initialState = "A",
            config = TestGenConfig(hashAlgorithm = TestGenConfig.HashAlgorithm.CRC16),
        )

        val testCases = enumerator.generateTestCases()
        assertEquals(2, testCases.size)
        assertEquals(testCases[0].expectedTransitions, testCases[1].expectedTransitions)
        assertNotEquals(extractHash(testCases[0].name), extractHash(testCases[1].name))
    }

    private fun extractHash(testName: String): String {
        val parts = testName.split("_")
        return parts.getOrElse(2) { testName }
    }
}
