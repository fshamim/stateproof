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
         * Map of event matchers to transition definitions.
         * LinkedHashMap preserves insertion order for deterministic matching.
         */
        val transitions: LinkedHashMap<Matcher<EVENT, EVENT>, EventTransition<STATE, EVENT>> =
            linkedMapOf()
    }
}

/**
 * Transition definition for a matched event.
 *
 * A matched event can have one or more guarded branches. Runtime evaluation picks the
 * first branch whose guard returns true.
 */
class EventTransition<STATE : Any, EVENT : Any>(
    val branches: List<TransitionBranchSpec<STATE, EVENT>>,
) {
    init {
        require(branches.isNotEmpty()) { "EventTransition requires at least one branch" }
    }

    fun resolve(state: STATE, event: EVENT): TransitionTo<STATE, EVENT>? {
        for (branch in branches) {
            if (branch.guard(state, event)) {
                val selected = branch.createTransition(state, event)
                return if (selected.metadata.guardLabel != null || selected.metadata.emittedEvents.isNotEmpty()) {
                    selected
                } else {
                    selected.copy(
                        metadata = TransitionMetadata(
                            guardLabel = branch.guardLabel,
                            emittedEvents = branch.emittedEvents,
                        )
                    )
                }
            }
        }
        return null
    }
}

/**
 * One guarded branch for an event transition.
 */
data class TransitionBranchSpec<STATE : Any, EVENT : Any>(
    val guardLabel: String? = null,
    val guard: (STATE, EVENT) -> Boolean = { _, _ -> true },
    val createTransition: (STATE, EVENT) -> TransitionTo<STATE, EVENT>,
    val emittedEvents: List<EmittedEventInfo> = emptyList(),
)

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
    val metadata: TransitionMetadata = TransitionMetadata(),
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
    val metadata: TransitionMetadata = TransitionMetadata(),
)

/**
 * Basic state information for introspection and diagram generation.
 */
data class StateInfo(
    val stateName: String,
    val sideEffect: String = "",
    val transitions: MutableMap<String, String> = mutableMapOf(),
    val transitionDetails: MutableList<StateTransitionInfo> = mutableListOf(),
)

/**
 * Rich transition model used for guarded branches and emitted-event metadata.
 */
data class StateTransitionInfo(
    val eventName: String,
    val toStateName: String,
    val guardLabel: String? = null,
    val emittedEvents: List<EmittedEventInfo> = emptyList(),
)

/**
 * Metadata for events that can be emitted from a side effect.
 */
data class EmittedEventInfo(
    val label: String,
    val eventName: String,
)

/**
 * Runtime metadata for a selected transition.
 */
data class TransitionMetadata(
    val guardLabel: String? = null,
    val emittedEvents: List<EmittedEventInfo> = emptyList(),
)
