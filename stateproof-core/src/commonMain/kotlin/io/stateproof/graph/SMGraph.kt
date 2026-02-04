package io.stateproof.graph

import io.stateproof.matcher.Matcher

/**
 * Represents a state machine graph definition.
 *
 * @param STATE The type of states in the state machine
 * @param EVENT The type of events that trigger transitions
 * @param initialState The starting state of the machine
 * @param stateDefinition Map of state matchers to their state definitions
 */
data class SMGraph<STATE : Any, EVENT : Any>(
    val initialState: STATE,
    val stateDefinition: Map<Matcher<STATE, STATE>, State<STATE, EVENT>>,
) {
    /**
     * Represents a state's definition including all its transitions.
     */
    class State<STATE : Any, EVENT : Any> internal constructor() {
        /**
         * Map of event matchers to transition functions.
         * LinkedHashMap preserves insertion order for deterministic matching.
         */
        val transitions: LinkedHashMap<Matcher<EVENT, EVENT>, (STATE, EVENT) -> TransitionTo<STATE, EVENT>> =
            linkedMapOf()
    }
}

/**
 * Represents a transition target with an optional side effect.
 *
 * @param STATE The type of states
 * @param EVENT The type of events
 * @param toState The target state after transition
 * @param sideEffect Optional suspend function to execute during transition.
 *                   Can return a follow-up event to process.
 */
@ConsistentCopyVisibility
data class TransitionTo<STATE : Any, EVENT : Any> internal constructor(
    val toState: STATE,
    val sideEffect: (suspend STATE.(EVENT) -> EVENT?)? = null,
)

/**
 * Represents a transition being processed.
 *
 * @param event The event that triggered this transition
 * @param targetState The state to transition to
 * @param sideEffect Optional side effect to execute
 */
class Transition<STATE, EVENT>(
    val event: EVENT,
    val targetState: STATE,
    val sideEffect: (suspend STATE.(EVENT) -> EVENT?)? = null,
)

/**
 * Basic state information for introspection and diagram generation.
 */
data class StateInfo(
    val stateName: String,
    val sideEffect: String = "",
    val transitions: MutableMap<String, String> = mutableMapOf(),
)
