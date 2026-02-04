package io.stateproof

import io.stateproof.logging.PrintLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Edge case tests for event queuing and processing behavior.
 *
 * These tests verify:
 * 1. Circular state machine paths
 * 2. Side effect events take priority over queued events
 * 3. Queued events that become invalid after state transitions
 * 4. Chained side effects (side effect returns event whose side effect returns another event)
 * 5. Self-transitions (stay in same state)
 * 6. Multiple rapid events queued
 * 7. Side effects returning null (no follow-up)
 */
class EventQueueEdgeCasesTest {

    // region Test States and Events

    sealed class CircularState {
        data object A : CircularState()
        data object B : CircularState()
        data object C : CircularState()
    }

    sealed class CircularEvent {
        data object ToB : CircularEvent()
        data object ToC : CircularEvent()
        data object ToA : CircularEvent()
    }

    sealed class SideEffectState {
        data object Initial : SideEffectState()
        data object Processing : SideEffectState()
        data object Intermediate : SideEffectState()
        data object Final : SideEffectState()
    }

    sealed class SideEffectEvent {
        data object Start : SideEffectEvent()
        data object Continue : SideEffectEvent()
        data object Finish : SideEffectEvent()
        data object ExternalEvent : SideEffectEvent()
    }

    sealed class InvalidTransitionState {
        data object StateA : InvalidTransitionState()
        data object StateB : InvalidTransitionState()
        data object StateC : InvalidTransitionState()
    }

    sealed class InvalidTransitionEvent {
        data object GoToB : InvalidTransitionEvent()
        data object OnlyValidInA : InvalidTransitionEvent()
        data object GoToC : InvalidTransitionEvent()
    }

    sealed class ChainState {
        data object S1 : ChainState()
        data object S2 : ChainState()
        data object S3 : ChainState()
        data object S4 : ChainState()
        data object S5 : ChainState()
    }

    sealed class ChainEvent {
        data object E1 : ChainEvent()
        data object E2 : ChainEvent()
        data object E3 : ChainEvent()
        data object E4 : ChainEvent()
        data object External : ChainEvent()
    }

    sealed class SelfTransitionState {
        data object Active : SelfTransitionState()
        data object Done : SelfTransitionState()
    }

    sealed class SelfTransitionEvent {
        data class Increment(val value: Int) : SelfTransitionEvent()
        data object Finish : SelfTransitionEvent()
    }

    // States and events for complex scenario test
    sealed class ComplexState {
        data object S1 : ComplexState()
        data object S2 : ComplexState()
        data object S3 : ComplexState()
        data object S4 : ComplexState()
        data object S5 : ComplexState()
    }

    sealed class ComplexEvent {
        data object A : ComplexEvent()
        data object B : ComplexEvent()
        data object C : ComplexEvent()
        data object D : ComplexEvent()
        data object X : ComplexEvent()
    }

    // States and events for interrupt test
    sealed class InterruptState {
        data object S1 : InterruptState()
        data object S2 : InterruptState()
        data object S3 : InterruptState()
    }

    sealed class InterruptEvent {
        data object A : InterruptEvent()
        data object B : InterruptEvent()
        data object InvalidInS2 : InterruptEvent()
    }

    // endregion

    // region 1. Circular Paths Tests

    @Test
    fun circularPath_shouldAllowLoopingThroughStates() = runTest {
        val sm = StateMachine<CircularState, CircularEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("Circular"),
        ) {
            initialState(CircularState.A)

            state<CircularState.A> {
                on<CircularEvent.ToB> { transitionTo(CircularState.B) }
            }

            state<CircularState.B> {
                on<CircularEvent.ToC> { transitionTo(CircularState.C) }
            }

            state<CircularState.C> {
                on<CircularEvent.ToA> { transitionTo(CircularState.A) }
            }
        }

        // First loop: A -> B -> C -> A
        sm.onEvent(CircularEvent.ToB)
        sm.awaitIdle()
        assertEquals(CircularState.B, sm.currentState)

        sm.onEvent(CircularEvent.ToC)
        sm.awaitIdle()
        assertEquals(CircularState.C, sm.currentState)

        sm.onEvent(CircularEvent.ToA)
        sm.awaitIdle()
        assertEquals(CircularState.A, sm.currentState)

        // Second loop: A -> B -> C -> A (proving we can loop multiple times)
        sm.onEvent(CircularEvent.ToB)
        sm.awaitIdle()
        sm.onEvent(CircularEvent.ToC)
        sm.awaitIdle()
        sm.onEvent(CircularEvent.ToA)
        sm.awaitIdle()
        assertEquals(CircularState.A, sm.currentState)

        val expectedTransitions = listOf(
            "A_ToB_B",
            "B_ToC_C",
            "C_ToA_A",
            "A_ToB_B",
            "B_ToC_C",
            "C_ToA_A",
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    // endregion

    // region 2. Side Effect Event Priority Tests

    @Test
    fun sideEffectEvent_shouldBeProcessedBeforeQueuedEvents() = runTest {
        val sm = StateMachine<SideEffectState, SideEffectEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("SideEffect"),
        ) {
            initialState(SideEffectState.Initial)

            state<SideEffectState.Initial> {
                // Start triggers side effect that returns Continue
                on<SideEffectEvent.Start> {
                    transitionTo(SideEffectState.Processing) {
                        SideEffectEvent.Continue // Return event to be processed next
                    }
                }
            }

            state<SideEffectState.Processing> {
                on<SideEffectEvent.Continue> { transitionTo(SideEffectState.Intermediate) }
                on<SideEffectEvent.ExternalEvent> { transitionTo(SideEffectState.Final) }
            }

            state<SideEffectState.Intermediate> {
                on<SideEffectEvent.ExternalEvent> { transitionTo(SideEffectState.Final) }
            }

            state<SideEffectState.Final> {
                // Terminal
            }
        }

        // Queue Start, then ExternalEvent
        // Start should transition to Processing, then side effect returns Continue
        // Continue should be processed BEFORE ExternalEvent (priority)
        // So: Initial -> Processing -> Intermediate -> Final
        sm.onEvent(SideEffectEvent.Start)
        sm.onEvent(SideEffectEvent.ExternalEvent)
        sm.awaitIdle()

        assertEquals(SideEffectState.Final, sm.currentState)

        val expectedTransitions = listOf(
            "Initial_Start_Processing",
            "Processing_Continue_Intermediate",  // Side effect event processed first
            "Intermediate_ExternalEvent_Final",  // Then queued event
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    @Test
    fun sideEffectEvent_withMultipleQueuedEvents_shouldMaintainPriority() = runTest {
        val sm = StateMachine<SideEffectState, SideEffectEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("SideEffect"),
        ) {
            initialState(SideEffectState.Initial)

            state<SideEffectState.Initial> {
                on<SideEffectEvent.Start> {
                    transitionTo(SideEffectState.Processing) {
                        SideEffectEvent.Continue
                    }
                }
            }

            state<SideEffectState.Processing> {
                on<SideEffectEvent.Continue> { transitionTo(SideEffectState.Intermediate) }
            }

            state<SideEffectState.Intermediate> {
                on<SideEffectEvent.Finish> { transitionTo(SideEffectState.Final) }
            }

            state<SideEffectState.Final> {
                // Terminal
            }
        }

        // Queue: Start, Finish, ExternalEvent
        // Start's side effect returns Continue
        // Order should be: Start -> Continue (side effect) -> Finish -> ExternalEvent (ignored, no transition)
        sm.onEvent(SideEffectEvent.Start)
        sm.onEvent(SideEffectEvent.Finish)
        sm.onEvent(SideEffectEvent.ExternalEvent)
        sm.awaitIdle()

        assertEquals(SideEffectState.Final, sm.currentState)

        val expectedTransitions = listOf(
            "Initial_Start_Processing",
            "Processing_Continue_Intermediate",
            "Intermediate_Finish_Final",
            // ExternalEvent has no transition in Final state - logged as error
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    // endregion

    // region 3. Queued Events Becoming Invalid Tests

    @Test
    fun queuedEvent_invalidAfterStateChange_shouldLogError() = runTest {
        val sm = StateMachine<InvalidTransitionState, InvalidTransitionEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("Invalid"),
        ) {
            initialState(InvalidTransitionState.StateA)

            state<InvalidTransitionState.StateA> {
                on<InvalidTransitionEvent.GoToB> { transitionTo(InvalidTransitionState.StateB) }
                on<InvalidTransitionEvent.OnlyValidInA> { transitionTo(InvalidTransitionState.StateC) }
            }

            state<InvalidTransitionState.StateB> {
                on<InvalidTransitionEvent.GoToC> { transitionTo(InvalidTransitionState.StateC) }
                // OnlyValidInA is NOT valid here
            }

            state<InvalidTransitionState.StateC> {
                // Terminal
            }
        }

        // Queue: GoToB, OnlyValidInA
        // GoToB transitions to StateB
        // OnlyValidInA is NOT valid in StateB - should be skipped/error
        sm.onEvent(InvalidTransitionEvent.GoToB)
        sm.onEvent(InvalidTransitionEvent.OnlyValidInA)
        sm.awaitIdle()

        // Should be in StateB, OnlyValidInA should have failed silently
        assertEquals(InvalidTransitionState.StateB, sm.currentState)

        val expectedTransitions = listOf(
            "StateA_GoToB_StateB",
            // OnlyValidInA fails - no transition logged
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    @Test
    fun queuedEvent_becomesValidInNewState_shouldTransition() = runTest {
        val sm = StateMachine<InvalidTransitionState, InvalidTransitionEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("Valid"),
        ) {
            initialState(InvalidTransitionState.StateA)

            state<InvalidTransitionState.StateA> {
                on<InvalidTransitionEvent.GoToB> { transitionTo(InvalidTransitionState.StateB) }
            }

            state<InvalidTransitionState.StateB> {
                on<InvalidTransitionEvent.GoToC> { transitionTo(InvalidTransitionState.StateC) }
            }

            state<InvalidTransitionState.StateC> {
                // Terminal
            }
        }

        // Queue: GoToB, GoToC
        // GoToB transitions A -> B
        // GoToC is valid in B, should transition B -> C
        sm.onEvent(InvalidTransitionEvent.GoToB)
        sm.onEvent(InvalidTransitionEvent.GoToC)
        sm.awaitIdle()

        assertEquals(InvalidTransitionState.StateC, sm.currentState)

        val expectedTransitions = listOf(
            "StateA_GoToB_StateB",
            "StateB_GoToC_StateC",
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    // endregion

    // region 4. Chained Side Effects Tests

    @Test
    fun chainedSideEffects_shouldProcessInOrder() = runTest {
        val sm = StateMachine<ChainState, ChainEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("Chain"),
        ) {
            initialState(ChainState.S1)

            state<ChainState.S1> {
                on<ChainEvent.E1> {
                    transitionTo(ChainState.S2) { ChainEvent.E2 }
                }
            }

            state<ChainState.S2> {
                on<ChainEvent.E2> {
                    transitionTo(ChainState.S3) { ChainEvent.E3 }
                }
            }

            state<ChainState.S3> {
                on<ChainEvent.E3> {
                    transitionTo(ChainState.S4) { ChainEvent.E4 }
                }
            }

            state<ChainState.S4> {
                on<ChainEvent.E4> { transitionTo(ChainState.S5) }
            }

            state<ChainState.S5> {
                on<ChainEvent.External> { doNotTransition() }
            }
        }

        // Single E1 triggers chain: E1 -> E2 -> E3 -> E4
        // Queue External after E1
        sm.onEvent(ChainEvent.E1)
        sm.onEvent(ChainEvent.External)
        sm.awaitIdle()

        assertEquals(ChainState.S5, sm.currentState)

        // All chained side effect events should process before External
        val expectedTransitions = listOf(
            "S1_E1_S2",
            "S2_E2_S3",
            "S3_E3_S4",
            "S4_E4_S5",
            "S5_External_S5", // Self-transition at end
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    // endregion

    // region 5. Self-Transition Tests

    @Test
    fun selfTransition_shouldStayInSameState() = runTest {
        var counter = 0

        val sm = StateMachine<SelfTransitionState, SelfTransitionEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("Self"),
        ) {
            initialState(SelfTransitionState.Active)

            state<SelfTransitionState.Active> {
                on<SelfTransitionEvent.Increment> {
                    doNotTransition {
                        counter += (it as SelfTransitionEvent.Increment).value
                        null
                    }
                }
                on<SelfTransitionEvent.Finish> { transitionTo(SelfTransitionState.Done) }
            }

            state<SelfTransitionState.Done> {
                // Terminal
            }
        }

        sm.onEvent(SelfTransitionEvent.Increment(5))
        sm.awaitIdle()
        assertEquals(SelfTransitionState.Active, sm.currentState)
        assertEquals(5, counter)

        sm.onEvent(SelfTransitionEvent.Increment(10))
        sm.awaitIdle()
        assertEquals(SelfTransitionState.Active, sm.currentState)
        assertEquals(15, counter)

        sm.onEvent(SelfTransitionEvent.Finish)
        sm.awaitIdle()
        assertEquals(SelfTransitionState.Done, sm.currentState)

        val expectedTransitions = listOf(
            "Active_Increment_Active",
            "Active_Increment_Active",
            "Active_Finish_Done",
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    // endregion

    // region 6. Multiple Rapid Events Tests

    @Test
    fun multipleRapidEvents_shouldProcessInOrder() = runTest {
        val sm = StateMachine<CircularState, CircularEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("Rapid"),
        ) {
            initialState(CircularState.A)

            state<CircularState.A> {
                on<CircularEvent.ToB> { transitionTo(CircularState.B) }
            }

            state<CircularState.B> {
                on<CircularEvent.ToC> { transitionTo(CircularState.C) }
            }

            state<CircularState.C> {
                on<CircularEvent.ToA> { transitionTo(CircularState.A) }
            }
        }

        // Fire all events rapidly without waiting
        sm.onEvent(CircularEvent.ToB)
        sm.onEvent(CircularEvent.ToC)
        sm.onEvent(CircularEvent.ToA)
        sm.onEvent(CircularEvent.ToB)
        sm.onEvent(CircularEvent.ToC)

        sm.awaitIdle()

        assertEquals(CircularState.C, sm.currentState)

        val expectedTransitions = listOf(
            "A_ToB_B",
            "B_ToC_C",
            "C_ToA_A",
            "A_ToB_B",
            "B_ToC_C",
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    // endregion

    // region 7. Side Effect Returning Null Tests

    @Test
    fun sideEffect_returningNull_shouldNotQueueFollowUpEvent() = runTest {
        var sideEffectExecuted = false

        val sm = StateMachine<SideEffectState, SideEffectEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("NullSideEffect"),
        ) {
            initialState(SideEffectState.Initial)

            state<SideEffectState.Initial> {
                on<SideEffectEvent.Start> {
                    transitionTo(SideEffectState.Processing) {
                        sideEffectExecuted = true
                        null // Explicitly return null
                    }
                }
            }

            state<SideEffectState.Processing> {
                on<SideEffectEvent.Finish> { transitionTo(SideEffectState.Final) }
            }

            state<SideEffectState.Final> {
                // Terminal
            }
        }

        sm.onEvent(SideEffectEvent.Start)
        sm.awaitIdle()

        assertEquals(SideEffectState.Processing, sm.currentState)
        assertEquals(true, sideEffectExecuted)

        val expectedTransitions = listOf(
            "Initial_Start_Processing",
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    // endregion

    // region 8. Complex Mixed Scenario Tests

    @Test
    fun complexScenario_sideEffectsAndQueuedEvents_mixedPriority() = runTest {
        val sm = StateMachine<ComplexState, ComplexEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("Complex"),
        ) {
            initialState(ComplexState.S1)

            state<ComplexState.S1> {
                on<ComplexEvent.A> { transitionTo(ComplexState.S2) { ComplexEvent.B } }
            }

            state<ComplexState.S2> {
                on<ComplexEvent.B> { transitionTo(ComplexState.S3) }
            }

            state<ComplexState.S3> {
                on<ComplexEvent.C> { transitionTo(ComplexState.S4) { ComplexEvent.D } }
            }

            state<ComplexState.S4> {
                on<ComplexEvent.D> { transitionTo(ComplexState.S5) }
            }

            state<ComplexState.S5> {
                on<ComplexEvent.X> { doNotTransition() }
            }
        }

        // Queue: A, C, X
        // A transitions S1->S2, returns B
        // B (side effect) transitions S2->S3
        // C transitions S3->S4, returns D
        // D (side effect) transitions S4->S5
        // X self-transitions in S5
        sm.onEvent(ComplexEvent.A)
        sm.onEvent(ComplexEvent.C)
        sm.onEvent(ComplexEvent.X)
        sm.awaitIdle()

        assertEquals(ComplexState.S5, sm.currentState)

        val expectedTransitions = listOf(
            "S1_A_S2",
            "S2_B_S3",   // B from side effect, processed before C
            "S3_C_S4",
            "S4_D_S5",   // D from side effect, processed before X
            "S5_X_S5",
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    @Test
    fun sideEffectChain_interruptedByInvalidEvent_shouldContinue() = runTest {
        val sm = StateMachine<InterruptState, InterruptEvent>(
            dispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.Default,
            logger = PrintLogger("Interrupt"),
        ) {
            initialState(InterruptState.S1)

            state<InterruptState.S1> {
                on<InterruptEvent.A> { transitionTo(InterruptState.S2) { InterruptEvent.B } }
                on<InterruptEvent.InvalidInS2> { transitionTo(InterruptState.S3) }
            }

            state<InterruptState.S2> {
                on<InterruptEvent.B> { transitionTo(InterruptState.S3) }
                // InvalidInS2 is NOT defined here
            }

            state<InterruptState.S3> {
                // Terminal
            }
        }

        // Queue: A, InvalidInS2
        // A transitions S1->S2, returns B
        // B (side effect) transitions S2->S3
        // InvalidInS2 is not valid in S3 - error, ignored
        sm.onEvent(InterruptEvent.A)
        sm.onEvent(InterruptEvent.InvalidInS2)
        sm.awaitIdle()

        assertEquals(InterruptState.S3, sm.currentState)

        val expectedTransitions = listOf(
            "S1_A_S2",
            "S2_B_S3",
            // InvalidInS2 fails silently
        )
        assertContentEquals(expectedTransitions, sm.getTransitionLog())

        sm.close()
    }

    // endregion
}
