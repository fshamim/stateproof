package io.stateproof.dsl

import io.stateproof.graph.SMGraph
import io.stateproof.graph.TransitionTo
import io.stateproof.matcher.Matcher

/**
 * DSL builder for constructing state machine graphs.
 *
 * Example usage:
 * ```kotlin
 * val graph = SMGraphBuilder<MyState, MyEvent>().apply {
 *     initialState(MyState.Initial)
 *
 *     state<MyState.Initial> {
 *         on<MyEvent.Start> { transitionTo(MyState.Running) }
 *     }
 *
 *     state<MyState.Running> {
 *         on<MyEvent.Stop> { transitionTo(MyState.Stopped) }
 *     }
 * }.build()
 * ```
 *
 * @param STATE The base type of all states
 * @param EVENT The base type of all events
 * @param graph Optional existing graph to extend
 */
class SMGraphBuilder<STATE : Any, EVENT : Any>(
    graph: SMGraph<STATE, EVENT>? = null,
) {
    private var initialState = graph?.initialState
    private val stateDefinitions = LinkedHashMap(graph?.stateDefinition ?: emptyMap())

    /**
     * Sets the initial state of the state machine.
     */
    fun initialState(initialState: STATE) {
        this.initialState = initialState
    }

    /**
     * Defines transitions for a state matching the given matcher.
     *
     * @param stateMatcher Matcher for the state
     * @param init Builder block for defining transitions
     */
    fun <S : STATE> state(
        stateMatcher: Matcher<STATE, S>,
        init: StateTransitionBuilder<S>.() -> Unit,
    ) {
        val build = StateTransitionBuilder<S>().apply(init).build()
        stateDefinitions[stateMatcher] = build
    }

    /**
     * Defines transitions for a state using reified type inference.
     *
     * @param init Builder block for defining transitions
     */
    inline fun <reified S : STATE> state(
        noinline init: StateTransitionBuilder<S>.() -> Unit,
    ) {
        state(Matcher.any(), init)
    }

    /**
     * Builds the state machine graph.
     *
     * @throws IllegalStateException if initialState was not set
     */
    fun build(): SMGraph<STATE, EVENT> {
        return SMGraph(requireNotNull(initialState) { "initialState must be set" }, stateDefinitions.toMap())
    }

    /**
     * Builder for defining transitions within a state.
     */
    inner class StateTransitionBuilder<S : STATE> {

        private val holder = SMGraph.State<STATE, EVENT>()

        /**
         * Creates a matcher for any event of type E.
         */
        inline fun <reified E : EVENT> any(): Matcher<EVENT, E> = Matcher.any()

        /**
         * Defines a transition for events matching the given matcher.
         *
         * @param eventMatcher Matcher for the event
         * @param createTransitionTo Function that creates the transition target
         */
        fun <E : EVENT> on(
            eventMatcher: Matcher<EVENT, E>,
            createTransitionTo: S.(E) -> TransitionTo<STATE, EVENT>,
        ) {
            holder.transitions[eventMatcher] = { state, event ->
                @Suppress("UNCHECKED_CAST")
                createTransitionTo(state as S, event as E)
            }
        }

        /**
         * Defines a transition for events using reified type inference.
         *
         * @param createTransitionTo Function that creates the transition target
         */
        inline fun <reified E : EVENT> on(
            noinline createTransitionTo: S.(E) -> TransitionTo<STATE, EVENT>,
        ) {
            return on(any(), createTransitionTo)
        }

        /**
         * Creates a transition to the given state with an optional side effect.
         *
         * @param toState The target state
         * @param sideEffect Optional suspend function to execute during transition
         */
        fun S.transitionTo(
            toState: STATE,
            sideEffect: (suspend STATE.(EVENT) -> EVENT?)? = null,
        ): TransitionTo<STATE, EVENT> {
            return TransitionTo(toState, sideEffect)
        }

        /**
         * Creates a self-transition (stays in current state) with an optional side effect.
         *
         * @param sideEffect Optional suspend function to execute
         */
        fun S.doNotTransition(sideEffect: (suspend STATE.(EVENT) -> EVENT?)? = null) = transitionTo(this, sideEffect)

        internal fun build() = holder
    }
}

/**
 * Top-level DSL function for creating a state machine graph.
 *
 * Example:
 * ```kotlin
 * val graph = stateMachineGraph<MyState, MyEvent> {
 *     initialState(MyState.Initial)
 *     state<MyState.Initial> {
 *         on<MyEvent.Start> { transitionTo(MyState.Running) }
 *     }
 * }
 * ```
 */
inline fun <STATE : Any, EVENT : Any> stateMachineGraph(
    init: SMGraphBuilder<STATE, EVENT>.() -> Unit,
): SMGraph<STATE, EVENT> = SMGraphBuilder<STATE, EVENT>().apply(init).build()
