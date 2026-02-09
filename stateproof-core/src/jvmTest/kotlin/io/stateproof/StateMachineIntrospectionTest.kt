package io.stateproof

import io.stateproof.graph.toStateInfo
import io.stateproof.logging.PrintLogger
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only tests for introspection capabilities.
 */
class StateMachineIntrospectionTest {

    // Test state and event definitions (using data object for introspection)
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

    sealed class GuardedState {
        data object Initial : GuardedState()
        data object Processing : GuardedState()
    }

    sealed class GuardedEvent {
        data object Decide : GuardedEvent()
        data object Done : GuardedEvent()
        data object Retry : GuardedEvent()
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
    fun toStateInfo_shouldExtractGraphStructure() {
        val sm = createTestStateMachine()

        val stateInfo = sm.toStateInfo()

        // Should have 4 states
        assertEquals(4, stateInfo.size)

        // Verify Initial state transitions
        val initial = stateInfo["Initial"]
        assertEquals("Initial", initial?.stateName)
        assertEquals(1, initial?.transitions?.size)
        assertEquals("Running", initial?.transitions?.get("Start"))

        // Verify Running state transitions
        val running = stateInfo["Running"]
        assertEquals("Running", running?.stateName)
        assertEquals(2, running?.transitions?.size)
        assertEquals("Paused", running?.transitions?.get("Pause"))
        assertEquals("Stopped", running?.transitions?.get("Stop"))

        // Verify Paused state transitions
        val paused = stateInfo["Paused"]
        assertEquals("Paused", paused?.stateName)
        assertEquals(2, paused?.transitions?.size)
        assertEquals("Running", paused?.transitions?.get("Resume"))
        assertEquals("Stopped", paused?.transitions?.get("Stop"))

        // Verify Stopped state (terminal, no transitions)
        val stopped = stateInfo["Stopped"]
        assertEquals("Stopped", stopped?.stateName)
        assertEquals(0, stopped?.transitions?.size)

        sm.close()
    }

    @Test
    fun toStateInfo_shouldReturnEmptyMapForNoStates() {
        // State machine with only initial state, no other state definitions
        val sm = StateMachine<TestState, TestEvent>(
            dispatcher = Dispatchers.Default,
        ) {
            initialState(TestState.Initial)
            // No state definitions - will have empty map
        }

        val stateInfo = sm.toStateInfo()
        assertEquals(0, stateInfo.size)
        sm.close()
    }

    @Test
    fun toStateInfo_shouldIncludeGuardAndEmittedMetadata() {
        val sm = StateMachine<GuardedState, GuardedEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("GuardedIntrospection"),
        ) {
            initialState(GuardedState.Initial)

            state<GuardedState.Initial> {
                on<GuardedEvent.Decide> {
                    condition("canProcess") { _, _ -> true } then {
                        transitionTo(GuardedState.Processing)
                        sideEffect { null }.emits("done" to GuardedEvent.Done::class)
                    }
                    otherwise {
                        doNotTransition()
                        sideEffect { null }.emits("retry" to GuardedEvent.Retry::class)
                    }
                }
            }

            state<GuardedState.Processing> {
                on<GuardedEvent.Done> { doNotTransition() }
                on<GuardedEvent.Retry> { transitionTo(GuardedState.Initial) }
            }
        }

        val info = sm.toStateInfo()
        val initial = info["Initial"]
        assertEquals("Processing", initial?.transitions?.get("Decide"))

        val details = initial?.transitionDetails.orEmpty().filter { it.eventName == "Decide" }
        assertEquals(2, details.size)
        assertTrue(details.any { it.guardLabel == "canProcess" && it.emittedEvents.any { e -> e.eventName == "Done" } })
        assertTrue(details.any { it.guardLabel == "otherwise" && it.emittedEvents.any { e -> e.eventName == "Retry" } })

        sm.close()
    }
}
