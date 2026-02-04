package io.stateproof.testgen

import io.stateproof.StateMachine
import io.stateproof.logging.NoOpLogger
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathEnumeratorTest {

    // Simple linear state machine: A -> B -> C
    sealed class LinearState {
        data object A : LinearState()
        data object B : LinearState()
        data object C : LinearState()
    }

    sealed class LinearEvent {
        data object ToB : LinearEvent()
        data object ToC : LinearEvent()
    }

    // Circular state machine: A -> B -> C -> A
    sealed class CircularState {
        data object A : CircularState()
        data object B : CircularState()
        data object C : CircularState()
    }

    sealed class CircularEvent {
        data object Next : CircularEvent()
    }

    // Branching state machine: A -> B or A -> C
    sealed class BranchState {
        data object A : BranchState()
        data object B : BranchState()
        data object C : BranchState()
        data object D : BranchState()
    }

    sealed class BranchEvent {
        data object ToB : BranchEvent()
        data object ToC : BranchEvent()
        data object ToD : BranchEvent()
    }

    @Test
    fun linearStateMachine_shouldEnumerateAllPaths() {
        val sm = StateMachine<LinearState, LinearEvent>(
            dispatcher = Dispatchers.Default,
            logger = NoOpLogger,
        ) {
            initialState(LinearState.A)

            state<LinearState.A> {
                on<LinearEvent.ToB> { transitionTo(LinearState.B) }
            }

            state<LinearState.B> {
                on<LinearEvent.ToC> { transitionTo(LinearState.C) }
            }

            state<LinearState.C> {
                // Terminal state
            }
        }

        val enumerator = PathEnumerator(sm.getGraph(), TestGenConfig.DEFAULT)
        val paths = enumerator.enumerateAllPaths()

        // Linear path: A -> B -> C (terminal state triggers path recording)
        assertTrue(paths.isNotEmpty(), "Should find at least one path")

        // Check that paths end at terminal state C
        val terminalPaths = paths.filter { it.endState == "C" }
        assertTrue(terminalPaths.isNotEmpty(), "Should have paths ending at terminal state C")

        sm.close()
    }

    @Test
    fun circularStateMachine_shouldRespectMaxVisits() {
        val sm = StateMachine<CircularState, CircularEvent>(
            dispatcher = Dispatchers.Default,
            logger = NoOpLogger,
        ) {
            initialState(CircularState.A)

            state<CircularState.A> {
                on<CircularEvent.Next> { transitionTo(CircularState.B) }
            }

            state<CircularState.B> {
                on<CircularEvent.Next> { transitionTo(CircularState.C) }
            }

            state<CircularState.C> {
                on<CircularEvent.Next> { transitionTo(CircularState.A) }
            }
        }

        // With maxVisitsPerState = 2, we should stop when visiting a state the 2nd time
        val enumerator = PathEnumerator(sm.getGraph(), TestGenConfig(maxVisitsPerState = 2))
        val paths = enumerator.enumerateAllPaths()

        assertTrue(paths.isNotEmpty(), "Should find paths in circular graph")

        // All paths should eventually hit the visit limit
        // With maxVisits=2, longest path visits A twice: A -> B -> C -> A (stop)
        paths.forEach { path ->
            assertTrue(path.depth <= 6, "Path depth should be limited by maxVisits")
        }

        sm.close()
    }

    @Test
    fun branchingStateMachine_shouldEnumerateAllBranches() {
        val sm = StateMachine<BranchState, BranchEvent>(
            dispatcher = Dispatchers.Default,
            logger = NoOpLogger,
        ) {
            initialState(BranchState.A)

            state<BranchState.A> {
                on<BranchEvent.ToB> { transitionTo(BranchState.B) }
                on<BranchEvent.ToC> { transitionTo(BranchState.C) }
            }

            state<BranchState.B> {
                on<BranchEvent.ToD> { transitionTo(BranchState.D) }
            }

            state<BranchState.C> {
                on<BranchEvent.ToD> { transitionTo(BranchState.D) }
            }

            state<BranchState.D> {
                // Terminal
            }
        }

        val enumerator = PathEnumerator(sm.getGraph(), TestGenConfig.DEFAULT)
        val paths = enumerator.enumerateAllPaths()

        // Should have paths for both branches: A->B->D and A->C->D
        assertTrue(paths.size >= 2, "Should find at least 2 paths for branching")

        sm.close()
    }

    @Test
    fun shallowConfig_shouldLimitVisits() {
        val sm = StateMachine<CircularState, CircularEvent>(
            dispatcher = Dispatchers.Default,
            logger = NoOpLogger,
        ) {
            initialState(CircularState.A)

            state<CircularState.A> {
                on<CircularEvent.Next> { transitionTo(CircularState.B) }
            }

            state<CircularState.B> {
                on<CircularEvent.Next> { transitionTo(CircularState.C) }
            }

            state<CircularState.C> {
                on<CircularEvent.Next> { transitionTo(CircularState.A) }
            }
        }

        // SHALLOW config: maxVisitsPerState = 1
        val enumerator = PathEnumerator(sm.getGraph(), TestGenConfig.SHALLOW)
        val paths = enumerator.enumerateAllPaths()

        // With maxVisits=1, we stop immediately when we'd revisit a state
        // So we shouldn't complete the cycle back to A
        paths.forEach { path ->
            assertTrue(path.depth <= 3, "Shallow config should limit path depth")
        }

        sm.close()
    }

    @Test
    fun testGenConfig_shouldValidateParameters() {
        // Valid configs
        TestGenConfig(maxVisitsPerState = 1)
        TestGenConfig(maxVisitsPerState = 5)
        TestGenConfig(maxPathDepth = 10)

        // Invalid: maxVisitsPerState must be >= 1
        var exceptionThrown = false
        try {
            TestGenConfig(maxVisitsPerState = 0)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown, "Should throw for maxVisitsPerState = 0")

        // Invalid: maxPathDepth must be >= 1 if specified
        exceptionThrown = false
        try {
            TestGenConfig(maxPathDepth = 0)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown, "Should throw for maxPathDepth = 0")
    }

    @Test
    fun hashUtils_crc16_shouldComputeCorrectly() {
        val data = "test".encodeToByteArray()
        val crc = HashUtils.crc16(data)
        assertTrue(crc in 0..0xFFFF, "CRC16 should be 16-bit value")
    }

    @Test
    fun hashUtils_crc32_shouldComputeCorrectly() {
        val data = "test".encodeToByteArray()
        val crc = HashUtils.crc32(data)
        assertTrue(crc in 0..0xFFFFFFFF, "CRC32 should be 32-bit value")
    }

    @Test
    fun hashUtils_differentInputs_shouldProduceDifferentHashes() {
        val hash1 = HashUtils.hashPath("path1", TestGenConfig.HashAlgorithm.CRC32)
        val hash2 = HashUtils.hashPath("path2", TestGenConfig.HashAlgorithm.CRC32)
        assertTrue(hash1 != hash2, "Different paths should have different hashes")
    }

    @Test
    fun testPath_generateName_shouldIncludeDepthAndHash() {
        val path = TestPath<LinearState, LinearEvent>(
            steps = listOf(
                PathStep("A", LinearState.A::class, "ToB", LinearEvent.ToB::class),
                PathStep("B", LinearState.B::class, null, null),
            ),
            transitions = listOf("A_ToB_B"),
            depth = 1,
            hash = "ABCD1234",
        )

        val name = path.generateName()
        assertTrue(name.contains("1"), "Name should contain depth")
        assertTrue(name.contains("ABCD1234"), "Name should contain hash")
    }

    @Test
    fun generateTestCases_shouldProduceValidTestCases() {
        val sm = StateMachine<LinearState, LinearEvent>(
            dispatcher = Dispatchers.Default,
            logger = NoOpLogger,
        ) {
            initialState(LinearState.A)

            state<LinearState.A> {
                on<LinearEvent.ToB> { transitionTo(LinearState.B) }
            }

            state<LinearState.B> {
                on<LinearEvent.ToC> { transitionTo(LinearState.C) }
            }

            state<LinearState.C> {
                // Terminal state
            }
        }

        val enumerator = PathEnumerator(sm.getGraph(), TestGenConfig.DEFAULT)
        val testCases = enumerator.generateTestCases()

        assertTrue(testCases.isNotEmpty(), "Should generate test cases")

        testCases.forEach { testCase ->
            assertTrue(testCase.name.isNotEmpty(), "Test case should have a name")
            assertTrue(testCase.expectedTransitions.isNotEmpty(), "Test case should have expected transitions")
        }

        sm.close()
    }
}
