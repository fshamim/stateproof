package io.stateproof

import io.stateproof.logging.PrintLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class StateMachineTest {

    // Test state and event definitions
    sealed class TestState {
        data object Initial : TestState()
        data object Running : TestState()
        data object Paused : TestState()
        data object Stopped : TestState()
    }

    sealed class TestEvent {
        data object Start : TestEvent()
        data object Pause : TestEvent()
        data object Resume : TestEvent()
        data object Stop : TestEvent()
    }

    private fun createTestStateMachine() = StateMachine<TestState, TestEvent>(
        dispatcher = Dispatchers.Default,
        ioDispatcher = Dispatchers.Default,
        logger = PrintLogger("Test"),
    ) {
        initialState(TestState.Initial)

        state<TestState.Initial> {
            on<TestEvent.Start> { transitionTo(TestState.Running) }
        }

        state<TestState.Running> {
            on<TestEvent.Pause> { transitionTo(TestState.Paused) }
            on<TestEvent.Stop> { transitionTo(TestState.Stopped) }
        }

        state<TestState.Paused> {
            on<TestEvent.Resume> { transitionTo(TestState.Running) }
            on<TestEvent.Stop> { transitionTo(TestState.Stopped) }
        }

        state<TestState.Stopped> {
            // Terminal state - no transitions
        }
    }

    @Test
    fun initialState_shouldBeInitial() {
        val sm = createTestStateMachine()
        assertEquals(TestState.Initial, sm.currentState)
        sm.close()
    }

    @Test
    fun onEvent_shouldTransitionToCorrectState() = runTest {
        val sm = createTestStateMachine()

        sm.onEvent(TestEvent.Start)
        sm.awaitIdle()

        assertEquals(TestState.Running, sm.currentState)
        sm.close()
    }

    @Test
    fun multipleTransitions_shouldWorkCorrectly() = runTest {
        val sm = createTestStateMachine()

        sm.onEvent(TestEvent.Start)
        sm.awaitIdle()
        assertEquals(TestState.Running, sm.currentState)

        sm.onEvent(TestEvent.Pause)
        sm.awaitIdle()
        assertEquals(TestState.Paused, sm.currentState)

        sm.onEvent(TestEvent.Resume)
        sm.awaitIdle()
        assertEquals(TestState.Running, sm.currentState)

        sm.onEvent(TestEvent.Stop)
        sm.awaitIdle()
        assertEquals(TestState.Stopped, sm.currentState)

        val expectedTransitions = listOf(
            "Initial_Start_Running",
            "Running_Pause_Paused",
            "Paused_Resume_Running",
            "Running_Stop_Stopped",
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    @Test
    fun transitionLog_shouldRecordAllTransitions() = runTest {
        val sm = createTestStateMachine()

        sm.onEvent(TestEvent.Start)
        sm.awaitIdle()

        sm.onEvent(TestEvent.Pause)
        sm.awaitIdle()

        sm.onEvent(TestEvent.Stop)
        sm.awaitIdle()

        val expectedTransitions = listOf(
            "Initial_Start_Running",
            "Running_Pause_Paused",
            "Paused_Stop_Stopped",
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    @Test
    fun clearTransitionLog_shouldClearLog() = runTest {
        val sm = createTestStateMachine()

        sm.onEvent(TestEvent.Start)
        sm.awaitIdle()

        assertEquals(1, sm.getTransitionLog().size)

        sm.clearTransitionLog()

        assertEquals(0, sm.getTransitionLog().size)

        sm.close()
    }
}
