package io.stateproof.graph

import io.stateproof.StateMachine
import io.stateproof.registry.StateProofAutoMocks
import kotlin.reflect.KClass

private const val UNKNOWN_NAME = "Unknown"
private const val UNKNOWN_TARGET_DISPLAY_NAME = "?"
private const val GENERAL_GROUP_ID = "group:General"
private const val GENERAL_GROUP_DISPLAY_NAME = "General"

private data class IntrospectedTransition(
    val fromStateClass: KClass<*>,
    val eventName: String,
    val toStateClass: KClass<*>?,
    val toStateDisplayName: String,
    val guardLabel: String?,
    val emittedEvents: List<EmittedEventInfo>,
    val targetKnown: Boolean,
)

private data class IntrospectedGraph(
    val stateClasses: Set<KClass<*>>,
    val transitions: List<IntrospectedTransition>,
)

private data class StateIdentity(
    val stateClass: KClass<*>,
    val id: String,
    val displayName: String,
    val qualifiedName: String?,
)

private data class MutableGroup(
    val id: String,
    val displayName: String,
    var parentGroupId: String?,
    val stateIds: MutableSet<String> = linkedSetOf(),
    val childGroupIds: MutableSet<String> = linkedSetOf(),
)

private data class GroupBuildResult(
    val groups: List<StateGroup>,
    val stateIdToGroupId: Map<String, String>,
)

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
        val stateName = stateClass.simpleName ?: UNKNOWN_NAME
        val stateInfo = StateInfo(stateName)

        // Get the state instance for invoking transitions.
        // For object declarations, use objectInstance.
        // Otherwise, fall back to initialState.
        @Suppress("UNCHECKED_CAST")
        val stateInstance = (stateClass.objectInstance as? STATE) ?: initialState

        for ((eventMatcher, eventTransition) in stateDefinition.transitions) {
            val eventClass = eventMatcher.matchedClass
            val eventName = eventClass.simpleName ?: UNKNOWN_NAME

            try {
                val placeholderEvent = eventClass.objectInstance ?: createPlaceholderEvent(eventClass)

                if (placeholderEvent != null) {
                    for (branch in eventTransition.branches) {
                        @Suppress("UNCHECKED_CAST")
                        val transitionTo = branch.createTransition(stateInstance, placeholderEvent as EVENT)
                        val targetStateName = transitionTo.toState::class.simpleName ?: UNKNOWN_NAME
                        stateInfo.transitions.putIfAbsent(eventName, targetStateName)

                        val metadata = transitionTo.metadata
                        val emittedEvents = if (metadata.emittedEvents.isNotEmpty()) {
                            metadata.emittedEvents
                        } else {
                            branch.emittedEvents
                        }

                        stateInfo.transitionDetails.add(
                            StateTransitionInfo(
                                eventName = eventName,
                                toStateName = targetStateName,
                                guardLabel = metadata.guardLabel ?: branch.guardLabel,
                                emittedEvents = emittedEvents,
                            )
                        )
                    }
                } else {
                    // For events that cannot be instantiated, target is unknown.
                    stateInfo.transitions.putIfAbsent(eventName, UNKNOWN_TARGET_DISPLAY_NAME)
                    for (branch in eventTransition.branches) {
                        stateInfo.transitionDetails.add(
                            StateTransitionInfo(
                                eventName = eventName,
                                toStateName = UNKNOWN_TARGET_DISPLAY_NAME,
                                guardLabel = branch.guardLabel,
                                emittedEvents = branch.emittedEvents,
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                stateInfo.transitions.putIfAbsent(eventName, UNKNOWN_TARGET_DISPLAY_NAME)
                for (branch in eventTransition.branches) {
                    stateInfo.transitionDetails.add(
                        StateTransitionInfo(
                            eventName = eventName,
                            toStateName = UNKNOWN_TARGET_DISPLAY_NAME,
                            guardLabel = branch.guardLabel,
                            emittedEvents = branch.emittedEvents,
                        )
                    )
                }
            }
        }

        result[stateName] = stateInfo
    }

    return result
}

/**
 * Extracts a rich JVM StateGraph from this graph definition.
 *
 * Group detection is derived from nested sealed hierarchy:
 * - Nested sealed branches become groups.
 * - Flat state machines are grouped into synthetic "General".
 */
fun <STATE : Any, EVENT : Any> SMGraph<STATE, EVENT>.toStateGraph(): StateGraph {
    val introspected = introspectForStateGraph()
    val stateIdentities = buildStateIdentities(introspected.stateClasses)
    val groups = buildGroups(stateIdentities)
    val stateIdToGroupId = groups.stateIdToGroupId

    val sortedStates = stateIdentities.values.sortedBy { it.id }
    val initialStateId = stateIdentities.getValue(initialState::class).id

    val states = sortedStates.map { identity ->
        StateNode(
            id = identity.id,
            displayName = identity.displayName,
            qualifiedName = identity.qualifiedName,
            groupId = stateIdToGroupId.getValue(identity.id),
            isInitial = identity.id == initialStateId,
        )
    }

    val transitions = introspected.transitions
        .map { transition ->
            val fromStateId = stateIdentities.getValue(transition.fromStateClass).id
            val target = transition.toStateClass?.let { stateIdentities[it] }
            val targetKnown = transition.targetKnown && target != null

            StateTransitionEdge(
                fromStateId = fromStateId,
                eventName = transition.eventName,
                toStateId = if (targetKnown) target?.id else null,
                toStateDisplayName = if (targetKnown) {
                    target?.displayName ?: transition.toStateDisplayName
                } else {
                    UNKNOWN_TARGET_DISPLAY_NAME
                },
                guardLabel = transition.guardLabel,
                emittedEvents = transition.emittedEvents,
                targetKnown = targetKnown,
            )
        }
        .sortedWith(
            compareBy<StateTransitionEdge>(
                { it.fromStateId },
                { it.eventName },
                { it.guardLabel ?: "" },
                { it.toStateId ?: "" },
                { it.toStateDisplayName },
                { if (it.targetKnown) 0 else 1 },
                { emittedEventsSortKey(it.emittedEvents) },
            )
        )

    return StateGraph(
        initialStateId = initialStateId,
        states = states,
        groups = groups.groups,
        transitions = transitions,
    )
}

private fun <STATE : Any, EVENT : Any> SMGraph<STATE, EVENT>.introspectForStateGraph(): IntrospectedGraph {
    val stateClasses = linkedSetOf<KClass<*>>()
    val transitions = mutableListOf<IntrospectedTransition>()

    for ((stateMatcher, stateDefinition) in stateDefinition) {
        val stateClass = stateMatcher.matchedClass
        stateClasses += stateClass

        @Suppress("UNCHECKED_CAST")
        val stateInstance = (stateClass.objectInstance as? STATE) ?: initialState

        for ((eventMatcher, eventTransition) in stateDefinition.transitions) {
            val eventClass = eventMatcher.matchedClass
            val eventName = eventClass.simpleName ?: UNKNOWN_NAME

            val placeholderEvent = try {
                eventClass.objectInstance ?: createPlaceholderEvent(eventClass)
            } catch (_: Exception) {
                null
            }

            if (placeholderEvent == null) {
                for (branch in eventTransition.branches) {
                    transitions += IntrospectedTransition(
                        fromStateClass = stateClass,
                        eventName = eventName,
                        toStateClass = null,
                        toStateDisplayName = UNKNOWN_TARGET_DISPLAY_NAME,
                        guardLabel = branch.guardLabel,
                        emittedEvents = branch.emittedEvents,
                        targetKnown = false,
                    )
                }
                continue
            }

            for (branch in eventTransition.branches) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val transitionTo = branch.createTransition(stateInstance, placeholderEvent as EVENT)
                    val targetClass = transitionTo.toState::class
                    stateClasses += targetClass

                    val metadata = transitionTo.metadata
                    val emittedEvents = if (metadata.emittedEvents.isNotEmpty()) {
                        metadata.emittedEvents
                    } else {
                        branch.emittedEvents
                    }

                    transitions += IntrospectedTransition(
                        fromStateClass = stateClass,
                        eventName = eventName,
                        toStateClass = targetClass,
                        toStateDisplayName = targetClass.simpleName ?: UNKNOWN_NAME,
                        guardLabel = metadata.guardLabel ?: branch.guardLabel,
                        emittedEvents = emittedEvents,
                        targetKnown = true,
                    )
                } catch (_: Exception) {
                    transitions += IntrospectedTransition(
                        fromStateClass = stateClass,
                        eventName = eventName,
                        toStateClass = null,
                        toStateDisplayName = UNKNOWN_TARGET_DISPLAY_NAME,
                        guardLabel = branch.guardLabel,
                        emittedEvents = branch.emittedEvents,
                        targetKnown = false,
                    )
                }
            }
        }
    }

    stateClasses += initialState::class

    return IntrospectedGraph(
        stateClasses = stateClasses,
        transitions = transitions,
    )
}

private fun buildStateIdentities(stateClasses: Set<KClass<*>>): Map<KClass<*>, StateIdentity> {
    val sortedClasses = stateClasses.sortedBy { classNameSortKey(it) }
    val usedIds = mutableMapOf<String, Int>()
    val result = linkedMapOf<KClass<*>, StateIdentity>()

    for (stateClass in sortedClasses) {
        val baseId = stateClass.qualifiedName ?: (stateClass.simpleName ?: UNKNOWN_NAME)
        val duplicateIndex = usedIds.getOrDefault(baseId, 0)
        usedIds[baseId] = duplicateIndex + 1
        val resolvedId = if (duplicateIndex == 0) {
            baseId
        } else {
            "$baseId#$duplicateIndex"
        }

        result[stateClass] = StateIdentity(
            stateClass = stateClass,
            id = resolvedId,
            displayName = stateClass.simpleName ?: (stateClass.qualifiedName ?: UNKNOWN_NAME),
            qualifiedName = stateClass.qualifiedName,
        )
    }

    return result
}

private fun buildGroups(stateIdentities: Map<KClass<*>, StateIdentity>): GroupBuildResult {
    val groups = linkedMapOf<String, MutableGroup>()
    val stateIdToGroupId = linkedMapOf<String, String>()

    val sortedStates = stateIdentities.values.sortedBy { it.id }
    for (state in sortedStates) {
        val groupPath = sealedGroupPath(state.stateClass)
        if (groupPath.isEmpty()) {
            val generalGroup = groups.getOrPut(GENERAL_GROUP_ID) {
                MutableGroup(
                    id = GENERAL_GROUP_ID,
                    displayName = GENERAL_GROUP_DISPLAY_NAME,
                    parentGroupId = null,
                )
            }
            generalGroup.stateIds += state.id
            stateIdToGroupId[state.id] = GENERAL_GROUP_ID
            continue
        }

        var parentGroupId: String? = null
        var leafGroupId: String? = null

        for (groupClass in groupPath) {
            val groupId = groupIdFor(groupClass)
            val group = groups.getOrPut(groupId) {
                MutableGroup(
                    id = groupId,
                    displayName = groupClass.simpleName ?: (groupClass.qualifiedName ?: UNKNOWN_NAME),
                    parentGroupId = parentGroupId,
                )
            }

            if (group.parentGroupId == null && parentGroupId != null) {
                group.parentGroupId = parentGroupId
            }

            parentGroupId?.let { parentId ->
                val parent = groups.getOrPut(parentId) {
                    MutableGroup(
                        id = parentId,
                        displayName = parentId.removePrefix("group:"),
                        parentGroupId = null,
                    )
                }
                parent.childGroupIds += group.id
            }

            parentGroupId = group.id
            leafGroupId = group.id
        }

        val resolvedLeafGroupId = leafGroupId ?: GENERAL_GROUP_ID
        val leafGroup = groups.getOrPut(resolvedLeafGroupId) {
            MutableGroup(
                id = resolvedLeafGroupId,
                displayName = resolvedLeafGroupId.removePrefix("group:"),
                parentGroupId = null,
            )
        }
        leafGroup.stateIds += state.id
        stateIdToGroupId[state.id] = resolvedLeafGroupId
    }

    val materialized = groups.values
        .sortedBy { it.id }
        .map { group ->
            StateGroup(
                id = group.id,
                displayName = group.displayName,
                parentGroupId = group.parentGroupId,
                stateIds = group.stateIds.sorted(),
                childGroupIds = group.childGroupIds.sorted(),
            )
        }

    return GroupBuildResult(groups = materialized, stateIdToGroupId = stateIdToGroupId)
}

private fun sealedGroupPath(stateClass: KClass<*>): List<KClass<*>> {
    val sealedAncestors = mutableListOf<KClass<*>>()
    var current: KClass<*>? = stateClass

    while (current != null) {
        val parent = directSuperClass(current) ?: break
        if (parent.isSealed) {
            sealedAncestors += parent
        }
        current = parent
    }

    val rootToLeaf = sealedAncestors.asReversed()
    return if (rootToLeaf.size > 1) rootToLeaf else emptyList()
}

private fun directSuperClass(kClass: KClass<*>): KClass<*>? {
    val supertypes = kClass.supertypes.mapNotNull { it.classifier as? KClass<*> }
    return supertypes.firstOrNull { it != Any::class && !it.java.isInterface }
        ?: supertypes.firstOrNull { it != Any::class }
}

private fun groupIdFor(groupClass: KClass<*>): String {
    val rawId = groupClass.qualifiedName ?: (groupClass.simpleName ?: UNKNOWN_NAME)
    return "group:$rawId"
}

private fun classNameSortKey(kClass: KClass<*>): String =
    kClass.qualifiedName ?: (kClass.simpleName ?: UNKNOWN_NAME)

private fun emittedEventsSortKey(events: List<EmittedEventInfo>): String =
    events.joinToString(separator = "|") { "${it.label}:${it.eventName}" }

private fun createPlaceholderEvent(eventClass: KClass<*>): Any? {
    return try {
        StateProofAutoMocks.provide(eventClass)
    } catch (_: Exception) {
        null
    }
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

/**
 * Extracts a rich JVM StateGraph from a running state machine.
 */
fun <STATE : Any, EVENT : Any> StateMachine<STATE, EVENT>.toStateGraph(): StateGraph =
    getGraph().toStateGraph()
