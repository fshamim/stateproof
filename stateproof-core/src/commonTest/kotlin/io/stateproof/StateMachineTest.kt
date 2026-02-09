package io.stateproof

import io.stateproof.logging.PrintLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    sealed class GuardState {
        data object Idle : GuardState()
        data object Success : GuardState()
    }

    sealed class GuardEvent {
        data class Submit(val valid: Boolean) : GuardEvent()
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

    @Test
    fun guardedOn_shouldSelectBranchByPredicate() = runTest {
        val sm = StateMachine<GuardState, GuardEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("GuardTest"),
        ) {
            initialState(GuardState.Idle)

            state<GuardState.Idle> {
                on<GuardEvent.Submit> {
                    condition("isValid") { _, event -> event.valid } then {
                        transitionTo(GuardState.Success)
                    }
                    otherwise {
                        doNotTransition()
                    }
                }
            }

            state<GuardState.Success> {
                // terminal
            }
        }

        sm.onEvent(GuardEvent.Submit(valid = false))
        sm.awaitIdle()
        assertEquals(GuardState.Idle, sm.currentState)

        sm.onEvent(GuardEvent.Submit(valid = true))
        sm.awaitIdle()
        assertEquals(GuardState.Success, sm.currentState)

        val expectedTransitions = listOf(
            "Idle_Submit_Idle",
            "Idle_Submit_Success",
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())
        sm.close()
    }

    @Test
    fun guardedOn_sideEffect_shouldKeepConcreteEventTypeWithoutStateCastCrash() = runTest {
        val receivedValidFlags = mutableListOf<Boolean>()
        val receiverStates = mutableListOf<GuardState>()

        val sm = StateMachine<GuardState, GuardEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("GuardSideEffectTest"),
        ) {
            initialState(GuardState.Idle)

            state<GuardState.Idle> {
                on<GuardEvent.Submit> {
                    condition("always") { _, _ -> true } then {
                        transitionTo(GuardState.Success)
                        sideEffect { event ->
                            receiverStates += this
                            receivedValidFlags += event.valid
                            null
                        }
                    }
                }
            }

            state<GuardState.Success> { }
        }

        sm.onEvent(GuardEvent.Submit(valid = true))
        sm.awaitIdle()

        assertEquals(GuardState.Success, sm.currentState)
        assertContentEquals(listOf(true), receivedValidFlags)
        assertContentEquals(listOf(GuardState.Success), receiverStates)
        sm.close()
    }

    @Test
    fun guardedOn_multipleTransitionDirectives_shouldFailFast() {
        assertFailsWith<IllegalArgumentException> {
            StateMachine<GuardState, GuardEvent>(
                dispatcher = Dispatchers.Default,
                ioDispatcher = Dispatchers.Default,
                logger = PrintLogger("GuardValidation"),
            ) {
                initialState(GuardState.Idle)
                state<GuardState.Idle> {
                    on<GuardEvent.Submit> {
                        condition("always") { _, _ -> true } then {
                            transitionTo(GuardState.Success)
                            doNotTransition()
                        }
                    }
                }
            }.close()
        }
    }

    @Test
    fun guardedOn_multipleSideEffects_shouldFailFast() {
        assertFailsWith<IllegalArgumentException> {
            StateMachine<GuardState, GuardEvent>(
                dispatcher = Dispatchers.Default,
                ioDispatcher = Dispatchers.Default,
                logger = PrintLogger("GuardValidation"),
            ) {
                initialState(GuardState.Idle)
                state<GuardState.Idle> {
                    on<GuardEvent.Submit> {
                        condition("always") { _, _ -> true } then {
                            transitionTo(GuardState.Success)
                            sideEffect { null }
                            sideEffect { null }
                        }
                    }
                }
            }.close()
        }
    }

    @Test
    fun guardedOn_emitsWithoutSideEffect_shouldFailFast() {
        assertFailsWith<IllegalArgumentException> {
            StateMachine<GuardState, GuardEvent>(
                dispatcher = Dispatchers.Default,
                ioDispatcher = Dispatchers.Default,
                logger = PrintLogger("GuardValidation"),
            ) {
                initialState(GuardState.Idle)
                state<GuardState.Idle> {
                    on<GuardEvent.Submit> {
                        condition("always") { _, _ -> true } then {
                            transitionTo(GuardState.Success)
                            sideEffectEmits("submit" to GuardEvent.Submit::class)
                        }
                    }
                }
            }.close()
        }
    }

    @Test
    fun guardedOn_sideEffectChainEmits_shouldCaptureMetadata() {
        val sm = StateMachine<GuardState, GuardEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("GuardEmits"),
        ) {
            initialState(GuardState.Idle)
            state<GuardState.Idle> {
                on<GuardEvent.Submit> {
                    condition("always") { _, _ -> true } then {
                        sideEffect { null }.emits("submit" to GuardEvent.Submit::class)
                        transitionTo(GuardState.Success)
                    }
                }
            }
            state<GuardState.Success> { }
        }

        val transition = sm.getGraph().stateDefinition.values
            .first { it.transitions.isNotEmpty() }
            .transitions.values
            .first()
        val branch = transition.branches.first()
        assertEquals("always", branch.guardLabel)
        assertEquals("Submit", branch.emittedEvents.firstOrNull()?.eventName)
        sm.close()
    }
}
