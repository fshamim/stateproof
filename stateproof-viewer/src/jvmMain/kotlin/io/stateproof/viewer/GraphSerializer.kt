package io.stateproof.viewer

import io.stateproof.graph.EmittedEventInfo
import io.stateproof.graph.StateGraph
import io.stateproof.graph.StateTransitionEdge

/**
 * Serialized payload consumed by the interactive viewer runtime.
 */
data class ViewerGraphPayload(
    val machineName: String,
    val initialStateId: String,
    val groups: List<ViewerGroup>,
    val states: List<ViewerState>,
    val transitions: List<ViewerTransition>,
    val overviewEdges: List<ViewerOverviewEdge>,
)

/**
 * Group metadata for viewer rendering.
 */
data class ViewerGroup(
    val id: String,
    val displayName: String,
    val parentGroupId: String?,
    val stateIds: List<String>,
    val childGroupIds: List<String>,
)

/**
 * State node metadata for viewer rendering.
 */
data class ViewerState(
    val id: String,
    val displayName: String,
    val qualifiedName: String?,
    val groupId: String,
    val isInitial: Boolean,
)

/**
 * Transition metadata for viewer rendering.
 */
data class ViewerTransition(
    val id: String,
    val fromStateId: String,
    val eventName: String,
    val toStateId: String?,
    val toStateDisplayName: String,
    val guardLabel: String?,
    val emittedEvents: List<ViewerEmittedEvent>,
    val targetKnown: Boolean,
    val label: String,
)

/**
 * Emitted-event metadata attached to a transition.
 */
data class ViewerEmittedEvent(
    val label: String,
    val eventName: String,
)

/**
 * Aggregated edge between groups used in overview mode.
 */
data class ViewerOverviewEdge(
    val id: String,
    val fromGroupId: String,
    val fromGroupDisplayName: String,
    val toGroupId: String,
    val toGroupDisplayName: String,
    val count: Int,
    val sampleLabels: List<String>,
    val extraLabelCount: Int,
    val label: String,
)

private data class OverviewAggregationKey(
    val fromGroupId: String,
    val toGroupId: String,
)

private const val UNKNOWN_GROUP_ID = "group:Unknown"
private const val UNKNOWN_GROUP_DISPLAY_NAME = "Unknown"
private const val UNKNOWN_TARGET_DISPLAY = "?"
private const val OVERVIEW_SAMPLE_SIZE = 3

/**
 * Converts a [StateGraph] to deterministic viewer payload.
 */
fun StateGraph.toViewerGraphPayload(
    machineName: String,
    options: ViewerRenderOptions = ViewerRenderOptions(),
): ViewerGraphPayload {
    val sortedGroups = groups
        .map { group ->
            ViewerGroup(
                id = group.id,
                displayName = group.displayName,
                parentGroupId = group.parentGroupId,
                stateIds = group.stateIds.sorted(),
                childGroupIds = group.childGroupIds.sorted(),
            )
        }
        .sortedBy { it.id }

    val sortedStates = states
        .map { state ->
            ViewerState(
                id = state.id,
                displayName = state.displayName,
                qualifiedName = state.qualifiedName,
                groupId = state.groupId,
                isInitial = state.isInitial,
            )
        }
        .sortedBy { it.id }

    val stateById = sortedStates.associateBy { it.id }
    val groupById = sortedGroups.associateBy { it.id }

    val sortedEdges = transitions.sortedWith(
        compareBy<StateTransitionEdge>(
            { it.fromStateId },
            { it.eventName },
            { it.guardLabel ?: "" },
            { it.toStateId ?: "" },
            { it.toStateDisplayName },
            {
                it.emittedEvents.joinToString("|") { emitted ->
                    "${emitted.label}:${emitted.eventName}"
                }
            },
        )
    )

    val viewerTransitions = sortedEdges.mapIndexed { index, edge ->
        val emitted = edge.emittedEvents
            .map { ViewerEmittedEvent(it.label, it.eventName) }
            .sortedWith(compareBy({ it.label }, { it.eventName }))

        ViewerTransition(
            id = "t_%04d".format(index),
            fromStateId = edge.fromStateId,
            eventName = edge.eventName,
            toStateId = edge.toStateId,
            toStateDisplayName = edge.toStateDisplayName,
            guardLabel = edge.guardLabel,
            emittedEvents = emitted,
            targetKnown = edge.targetKnown,
            label = buildTransitionLabel(edge.eventName, edge.guardLabel, edge.emittedEvents),
        )
    }

    val overviewAggregations = mutableMapOf<OverviewAggregationKey, MutableList<String>>()

    for (transition in viewerTransitions) {
        val fromGroupId = stateById[transition.fromStateId]?.groupId ?: UNKNOWN_GROUP_ID
        val toGroupId = when {
            transition.toStateId != null -> stateById[transition.toStateId]?.groupId ?: UNKNOWN_GROUP_ID
            else -> UNKNOWN_GROUP_ID
        }

        if (fromGroupId == toGroupId) continue

        val key = OverviewAggregationKey(fromGroupId = fromGroupId, toGroupId = toGroupId)
        overviewAggregations.getOrPut(key) { mutableListOf() }.add(transition.label)
    }

    val hasUnknownGroupEdge = overviewAggregations.keys.any {
        it.fromGroupId == UNKNOWN_GROUP_ID || it.toGroupId == UNKNOWN_GROUP_ID
    }

    val viewerGroups = if (hasUnknownGroupEdge && UNKNOWN_GROUP_ID !in groupById) {
        (sortedGroups + ViewerGroup(
            id = UNKNOWN_GROUP_ID,
            displayName = UNKNOWN_GROUP_DISPLAY_NAME,
            parentGroupId = null,
            stateIds = emptyList(),
            childGroupIds = emptyList(),
        )).sortedBy { it.id }
    } else {
        sortedGroups
    }

    val viewerGroupById = viewerGroups.associateBy { it.id }

    val overviewEdges = overviewAggregations
        .entries
        .sortedWith(compareBy({ it.key.fromGroupId }, { it.key.toGroupId }))
        .mapIndexed { index, (key, labels) ->
            val sortedLabels = labels.sorted()
            val sample = sortedLabels.take(OVERVIEW_SAMPLE_SIZE)
            val extra = (sortedLabels.size - sample.size).coerceAtLeast(0)

            ViewerOverviewEdge(
                id = "o_%04d".format(index),
                fromGroupId = key.fromGroupId,
                fromGroupDisplayName = viewerGroupById[key.fromGroupId]?.displayName ?: UNKNOWN_GROUP_DISPLAY_NAME,
                toGroupId = key.toGroupId,
                toGroupDisplayName = viewerGroupById[key.toGroupId]?.displayName ?: UNKNOWN_GROUP_DISPLAY_NAME,
                count = sortedLabels.size,
                sampleLabels = sample,
                extraLabelCount = extra,
                label = buildOverviewLabel(sortedLabels.size, sample, extra),
            )
        }

    val normalizedInitialState = if (initialStateId in stateById) {
        initialStateId
    } else {
        sortedStates.firstOrNull { it.isInitial }?.id
            ?: sortedStates.firstOrNull()?.id
            ?: initialStateId
    }

    return ViewerGraphPayload(
        machineName = machineName,
        initialStateId = normalizedInitialState,
        groups = viewerGroups,
        states = sortedStates,
        transitions = viewerTransitions,
        overviewEdges = overviewEdges,
    )
}

/**
 * Serializes the payload to deterministic JSON.
 */
fun ViewerGraphPayload.toJson(): String = buildString {
    appendLine("{")
    appendLine("  \"machineName\": ${jsonString(machineName)},")
    appendLine("  \"initialStateId\": ${jsonString(initialStateId)},")
    appendLine("  \"groups\": [")
    groups.forEachIndexed { index, group ->
        append(group.toJson("    "))
        append(if (index == groups.lastIndex) "\n" else ",\n")
    }
    appendLine("  ],")
    appendLine("  \"states\": [")
    states.forEachIndexed { index, state ->
        append(state.toJson("    "))
        append(if (index == states.lastIndex) "\n" else ",\n")
    }
    appendLine("  ],")
    appendLine("  \"transitions\": [")
    transitions.forEachIndexed { index, transition ->
        append(transition.toJson("    "))
        append(if (index == transitions.lastIndex) "\n" else ",\n")
    }
    appendLine("  ],")
    appendLine("  \"overviewEdges\": [")
    overviewEdges.forEachIndexed { index, edge ->
        append(edge.toJson("    "))
        append(if (index == overviewEdges.lastIndex) "\n" else ",\n")
    }
    appendLine("  ]")
    append('}')
}

private fun ViewerGroup.toJson(indent: String): String = buildString {
    appendLine("${indent}{")
    appendLine("${indent}  \"id\": ${jsonString(id)},")
    appendLine("${indent}  \"displayName\": ${jsonString(displayName)},")
    appendLine("${indent}  \"parentGroupId\": ${jsonNullableString(parentGroupId)},")
    appendLine("${indent}  \"stateIds\": ${jsonStringArray(stateIds)},")
    appendLine("${indent}  \"childGroupIds\": ${jsonStringArray(childGroupIds)}")
    append("${indent}}")
}

private fun ViewerState.toJson(indent: String): String = buildString {
    appendLine("${indent}{")
    appendLine("${indent}  \"id\": ${jsonString(id)},")
    appendLine("${indent}  \"displayName\": ${jsonString(displayName)},")
    appendLine("${indent}  \"qualifiedName\": ${jsonNullableString(qualifiedName)},")
    appendLine("${indent}  \"groupId\": ${jsonString(groupId)},")
    appendLine("${indent}  \"isInitial\": $isInitial")
    append("${indent}}")
}

private fun ViewerTransition.toJson(indent: String): String = buildString {
    appendLine("${indent}{")
    appendLine("${indent}  \"id\": ${jsonString(id)},")
    appendLine("${indent}  \"fromStateId\": ${jsonString(fromStateId)},")
    appendLine("${indent}  \"eventName\": ${jsonString(eventName)},")
    appendLine("${indent}  \"toStateId\": ${jsonNullableString(toStateId)},")
    appendLine("${indent}  \"toStateDisplayName\": ${jsonString(toStateDisplayName)},")
    appendLine("${indent}  \"guardLabel\": ${jsonNullableString(guardLabel)},")
    appendLine("${indent}  \"emittedEvents\": ${jsonEmittedEvents(emittedEvents)},")
    appendLine("${indent}  \"targetKnown\": $targetKnown,")
    appendLine("${indent}  \"label\": ${jsonString(label)}")
    append("${indent}}")
}

private fun ViewerOverviewEdge.toJson(indent: String): String = buildString {
    appendLine("${indent}{")
    appendLine("${indent}  \"id\": ${jsonString(id)},")
    appendLine("${indent}  \"fromGroupId\": ${jsonString(fromGroupId)},")
    appendLine("${indent}  \"fromGroupDisplayName\": ${jsonString(fromGroupDisplayName)},")
    appendLine("${indent}  \"toGroupId\": ${jsonString(toGroupId)},")
    appendLine("${indent}  \"toGroupDisplayName\": ${jsonString(toGroupDisplayName)},")
    appendLine("${indent}  \"count\": $count,")
    appendLine("${indent}  \"sampleLabels\": ${jsonStringArray(sampleLabels)},")
    appendLine("${indent}  \"extraLabelCount\": $extraLabelCount,")
    appendLine("${indent}  \"label\": ${jsonString(label)}")
    append("${indent}}")
}

private fun buildTransitionLabel(
    eventName: String,
    guardLabel: String?,
    emittedEvents: List<EmittedEventInfo>,
): String {
    val pieces = mutableListOf<String>()
    pieces += eventName
    if (!guardLabel.isNullOrBlank()) {
        pieces += "[$guardLabel]"
    }
    if (emittedEvents.isNotEmpty()) {
        val emitted = emittedEvents
            .sortedWith(compareBy({ it.label }, { it.eventName }))
            .joinToString(",") { "${it.label}:${it.eventName}" }
        pieces += "{$emitted}"
    }
    return pieces.joinToString(" ")
}

private fun buildOverviewLabel(count: Int, sampleLabels: List<String>, extra: Int): String = buildString {
    append("${count}x")
    for (sample in sampleLabels) {
        append(" | ")
        append(sample)
    }
    if (extra > 0) {
        append(" | +")
        append(extra)
        append(" more")
    }
}

private fun jsonNullableString(value: String?): String = if (value == null) "null" else jsonString(value)

private fun jsonStringArray(values: List<String>): String {
    if (values.isEmpty()) return "[]"
    return values.joinToString(prefix = "[", postfix = "]") { jsonString(it) }
}

private fun jsonEmittedEvents(values: List<ViewerEmittedEvent>): String {
    if (values.isEmpty()) return "[]"
    return values.joinToString(prefix = "[", postfix = "]") {
        "{" +
            "\"label\":${jsonString(it.label)}," +
            "\"eventName\":${jsonString(it.eventName)}" +
            "}"
    }
}

private fun jsonString(value: String): String = buildString {
    append('"')
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
    append('"')
}
