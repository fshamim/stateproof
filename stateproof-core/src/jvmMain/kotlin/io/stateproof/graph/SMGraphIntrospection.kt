package io.stateproof.graph

import io.stateproof.StateMachine

/**
 * JVM-only extension for extracting StateInfo from an SMGraph.
 *
 * This uses reflection (objectInstance) which is only available on JVM.
 * The introspection is used for test generation and PlantUML export.
 */

/**
 * Extracts a Map<String, StateInfo> from this graph for test generation.
 *
 * This method introspects the graph structure and produces a string-based
 * representation suitable for the test generator and PlantUML export.
 *
 * For each state, it attempts to invoke transition functions with placeholder
 * events to discover target states. This works for object events (data object).
 * For data class events, the target is marked as "?" since we can't instantiate them.
 */
fun <STATE : Any, EVENT : Any> SMGraph<STATE, EVENT>.toStateInfoMap(): Map<String, StateInfo> {
    val result = mutableMapOf<String, StateInfo>()

    for ((stateMatcher, stateDefinition) in stateDefinition) {
        val stateClass = stateMatcher.matchedClass
        val stateName = stateClass.simpleName ?: "Unknown"
        val stateInfo = StateInfo(stateName)

        // Get the state instance for invoking transitions
        // For object declarations (data object), use objectInstance
        // Otherwise, fall back to initialState (may not work for all transitions)
        @Suppress("UNCHECKED_CAST")
        val stateInstance = (stateClass.objectInstance as? STATE) ?: initialState

        for ((eventMatcher, createTransition) in stateDefinition.transitions) {
            val eventClass = eventMatcher.matchedClass
            val eventName = eventClass.simpleName ?: "Unknown"

            // Try to get objectInstance for object declarations (most common case)
            try {
                val placeholderEvent = eventClass.objectInstance
                
                if (placeholderEvent != null) {
                    @Suppress("UNCHECKED_CAST")
                    val transitionTo = createTransition(stateInstance, placeholderEvent as EVENT)
                    val targetStateName = transitionTo.toState::class.simpleName ?: "Unknown"
                    stateInfo.transitions[eventName] = targetStateName
                } else {
                    // For data class events, we can't easily create instances
                    // Mark as "?" to indicate unknown target
                    stateInfo.transitions[eventName] = "?"
                }
            } catch (e: Exception) {
                // If introspection fails, mark target as unknown
                stateInfo.transitions[eventName] = "?"
            }
        }

        result[stateName] = stateInfo
    }

    return result
}

/**
 * Extracts state machine info for test generation and diagram export.
 *
 * This method introspects the graph and returns a map of state names
 * to their info (including transitions). This eliminates the need
 * for manually maintaining separate *Info() functions.
 *
 * Note: This is JVM-only due to reflection requirements.
 *
 * @return Map of state names to StateInfo objects
 */
fun <STATE : Any, EVENT : Any> StateMachine<STATE, EVENT>.toStateInfo(): Map<String, StateInfo> =
    getGraph().toStateInfoMap()
