package io.stateproof.graph

/**
 * JVM-only introspection model describing a state machine graph with optional grouping.
 */
data class StateGraph(
    val initialStateId: String,
    val states: List<StateNode>,
    val groups: List<StateGroup>,
    val transitions: List<StateTransitionEdge>,
)

/**
 * A concrete state node in the introspected graph.
 */
data class StateNode(
    val id: String,
    val displayName: String,
    val qualifiedName: String?,
    val groupId: String,
    val isInitial: Boolean,
)

/**
 * A logical group inferred from sealed class hierarchy.
 */
data class StateGroup(
    val id: String,
    val displayName: String,
    val parentGroupId: String?,
    val stateIds: List<String>,
    val childGroupIds: List<String>,
)

/**
 * A transition edge between states.
 */
data class StateTransitionEdge(
    val fromStateId: String,
    val eventName: String,
    val toStateId: String?,
    val toStateDisplayName: String,
    val guardLabel: String?,
    val emittedEvents: List<EmittedEventInfo>,
    val targetKnown: Boolean,
)
