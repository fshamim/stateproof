package io.stateproof.diagram

import io.stateproof.graph.StateGraph
import io.stateproof.graph.StateGroup
import io.stateproof.graph.StateInfo
import io.stateproof.graph.StateNode
import io.stateproof.graph.StateTransitionEdge
import io.stateproof.graph.StateTransitionInfo
import java.io.File
import java.util.Locale
import java.util.zip.CRC32

/**
 * Diagram output format selection.
 */
enum class DiagramFormat {
    PLANT_UML,
    MERMAID,
    BOTH,
}

/**
 * Rendering options for static diagram generation.
 */
data class DiagramRenderOptions(
    val format: DiagramFormat = DiagramFormat.BOTH,
    val includeOverview: Boolean = true,
    val includePerGroup: Boolean = true,
    val overviewLabelSampleSize: Int = 3,
    val mermaidUseElk: Boolean = true,
    val showGuardLabels: Boolean = true,
    val showEmittedEvents: Boolean = true,
)

/**
 * One generated diagram file.
 */
data class GeneratedDiagramFile(
    val relativePath: String,
    val content: String,
)

/**
 * Collection of generated files for one state machine.
 */
data class GeneratedDiagramBundle(
    val machineName: String,
    val files: List<GeneratedDiagramFile>,
)

private data class OverviewEdgeAggregation(
    val count: Int,
    val labels: List<String>,
)

private data class GroupDiagramEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val label: String,
    val isExternal: Boolean,
)

private data class GroupDiagramModel(
    val group: StateGroup,
    val stateNodes: List<StateNode>,
    val externalNodes: List<Pair<String, String>>,
    val edges: List<GroupDiagramEdge>,
)

private const val GENERAL_GROUP_ID = "group:General"
private const val UNKNOWN_TARGET_DISPLAY = "?"
private const val EXTERNAL_UNKNOWN = "External::?"
private const val EXTERNAL_UNKNOWN_ID = "__external_unknown__"

private val GROUP_COLORS = listOf(
    "#D8E8FF",
    "#D9F2E6",
    "#FDE7D4",
    "#FCE5F6",
    "#EAE4FF",
    "#F9E4E4",
    "#DFF4F8",
    "#FFF2CC",
)

private const val GENERAL_COLOR = "#ECECEC"
private const val EXTERNAL_COLOR = "#F5F5F5"

/**
 * Renders overview and per-group static diagrams from a [StateGraph].
 */
fun StateGraph.renderDiagrams(
    machineName: String,
    options: DiagramRenderOptions = DiagramRenderOptions(),
): GeneratedDiagramBundle {
    val safeMachine = sanitizePathSegment(machineName.ifBlank { "state-machine" })
    val stateById = states.associateBy { it.id }
    val groupById = groups.associateBy { it.id }
    val sortedGroups = groups.sortedBy { it.id }

    val files = mutableListOf<GeneratedDiagramFile>()

    if (options.includeOverview) {
        if (options.format == DiagramFormat.PLANT_UML || options.format == DiagramFormat.BOTH) {
            files += GeneratedDiagramFile(
                relativePath = "$safeMachine/overview.puml",
                content = renderPlantUmlOverview(
                    graph = this,
                    groups = sortedGroups,
                    groupById = groupById,
                    stateById = stateById,
                    options = options,
                )
            )
        }
        if (options.format == DiagramFormat.MERMAID || options.format == DiagramFormat.BOTH) {
            files += GeneratedDiagramFile(
                relativePath = "$safeMachine/overview.mmd",
                content = renderMermaidOverview(
                    graph = this,
                    groups = sortedGroups,
                    groupById = groupById,
                    stateById = stateById,
                    options = options,
                )
            )
        }
    }

    if (options.includePerGroup) {
        for (group in sortedGroups) {
            val slug = buildGroupSlug(group)
            val model = buildGroupDiagramModel(group, groupById, stateById, transitions, options)

            if (options.format == DiagramFormat.PLANT_UML || options.format == DiagramFormat.BOTH) {
                files += GeneratedDiagramFile(
                    relativePath = "$safeMachine/groups/$slug.puml",
                    content = renderPlantUmlGroup(model)
                )
            }
            if (options.format == DiagramFormat.MERMAID || options.format == DiagramFormat.BOTH) {
                files += GeneratedDiagramFile(
                    relativePath = "$safeMachine/groups/$slug.mmd",
                    content = renderMermaidGroup(model, options)
                )
            }
        }
    }

    return GeneratedDiagramBundle(machineName = safeMachine, files = files.sortedBy { it.relativePath })
}

/**
 * Writes the generated bundle under [outputDir].
 */
fun GeneratedDiagramBundle.writeTo(outputDir: File): List<File> {
    outputDir.mkdirs()
    val written = mutableListOf<File>()
    for (file in files) {
        val target = File(outputDir, file.relativePath)
        target.parentFile?.mkdirs()
        target.writeText(file.content)
        written += target
    }
    return written.sortedBy { it.absolutePath }
}

/**
 * Adapts legacy StateInfo maps into a flat StateGraph (General group).
 */
fun Map<String, StateInfo>.toFlatStateGraph(initialStateName: String = "Initial"): StateGraph {
    val allStateNames = linkedSetOf<String>()
    allStateNames += keys

    for ((_, info) in this) {
        for ((_, target) in info.transitions) {
            if (target.isNotBlank() && target != UNKNOWN_TARGET_DISPLAY) {
                allStateNames += target
            }
        }
        for (detail in info.transitionDetails) {
            if (detail.toStateName.isNotBlank() && detail.toStateName != UNKNOWN_TARGET_DISPLAY) {
                allStateNames += detail.toStateName
            }
        }
    }

    val sortedNames = allStateNames.sorted()
    val states = sortedNames.map { stateName ->
        StateNode(
            id = stateName,
            displayName = stateName,
            qualifiedName = null,
            groupId = GENERAL_GROUP_ID,
            isInitial = stateName == initialStateName ||
                (stateName == sortedNames.firstOrNull() && initialStateName !in allStateNames),
        )
    }

    val stateIds = states.map { it.id }.toSet()
    val transitions = buildList {
        val orderedEntries = entries.sortedBy { it.key }
        for ((fromState, info) in orderedEntries) {
            val detailed = info.transitionDetails
            if (detailed.isNotEmpty()) {
                val sortedDetailed = detailed.sortedWith(
                    compareBy<StateTransitionInfo>(
                        { it.eventName },
                        { it.guardLabel ?: "" },
                        { it.toStateName },
                        { it.emittedEvents.joinToString("|") { event -> "${event.label}:${event.eventName}" } },
                    )
                )
                for (detail in sortedDetailed) {
                    val known = detail.toStateName != UNKNOWN_TARGET_DISPLAY && stateIds.contains(detail.toStateName)
                    add(
                        StateTransitionEdge(
                            fromStateId = fromState,
                            eventName = detail.eventName,
                            toStateId = if (known) detail.toStateName else null,
                            toStateDisplayName = if (known) detail.toStateName else UNKNOWN_TARGET_DISPLAY,
                            guardLabel = detail.guardLabel,
                            emittedEvents = detail.emittedEvents,
                            targetKnown = known,
                        )
                    )
                }
            } else {
                for ((eventName, toState) in info.transitions.toSortedMap()) {
                    val known = toState != UNKNOWN_TARGET_DISPLAY && stateIds.contains(toState)
                    add(
                        StateTransitionEdge(
                            fromStateId = fromState,
                            eventName = eventName,
                            toStateId = if (known) toState else null,
                            toStateDisplayName = if (known) toState else UNKNOWN_TARGET_DISPLAY,
                            guardLabel = null,
                            emittedEvents = emptyList(),
                            targetKnown = known,
                        )
                    )
                }
            }
        }
    }

    val initialStateId = states.firstOrNull { it.isInitial }?.id ?: initialStateName
    return StateGraph(
        initialStateId = initialStateId,
        states = states.sortedBy { it.id },
        groups = listOf(
            StateGroup(
                id = GENERAL_GROUP_ID,
                displayName = "General",
                parentGroupId = null,
                stateIds = states.map { it.id }.sorted(),
                childGroupIds = emptyList(),
            )
        ),
        transitions = transitions.sortedWith(
            compareBy<StateTransitionEdge>(
                { it.fromStateId },
                { it.eventName },
                { it.guardLabel ?: "" },
                { it.toStateId ?: "" },
                { it.toStateDisplayName },
            )
        ),
    )
}

private fun renderPlantUmlOverview(
    graph: StateGraph,
    groups: List<StateGroup>,
    groupById: Map<String, StateGroup>,
    stateById: Map<String, StateNode>,
    options: DiagramRenderOptions,
): String {
    val sb = StringBuilder()
    sb.appendLine("@startuml")
    sb.appendLine("hide empty description")
    sb.appendLine("skinparam defaultTextAlignment center")
    sb.appendLine()

    val nodeIdByGroupId = groups.associate { it.id to "g_${shortHash(it.id)}" }
    for (group in groups) {
        val nodeId = nodeIdByGroupId.getValue(group.id)
        val color = colorForGroup(group.id)
        sb.appendLine("state \"${escapePlant(group.displayName)}\" as $nodeId $color")
    }

    val aggregations = aggregateOverviewEdges(graph, stateById, groupById, options)
    val includeExternalUnknown = aggregations.keys.any { it.second == EXTERNAL_UNKNOWN_ID }
    if (includeExternalUnknown) {
        sb.appendLine("state \"${escapePlant(EXTERNAL_UNKNOWN)}\" as $EXTERNAL_UNKNOWN_ID $EXTERNAL_COLOR")
    }
    sb.appendLine()

    for ((key, aggregate) in aggregations.toSortedMap(compareBy<Pair<String, String>>({ it.first }, { it.second }))) {
        val fromId = nodeIdByGroupId[key.first] ?: continue
        val toId = if (key.second == EXTERNAL_UNKNOWN_ID) EXTERNAL_UNKNOWN_ID else nodeIdByGroupId[key.second] ?: continue
        val label = overviewLabel(aggregate.count, aggregate.labels, options.overviewLabelSampleSize)
        sb.appendLine("$fromId --> $toId : ${escapePlant(label)}")
    }

    sb.appendLine("@enduml")
    return sb.toString()
}

private fun renderMermaidOverview(
    graph: StateGraph,
    groups: List<StateGroup>,
    groupById: Map<String, StateGroup>,
    stateById: Map<String, StateNode>,
    options: DiagramRenderOptions,
): String {
    val sb = StringBuilder()
    if (options.mermaidUseElk) {
        sb.appendLine("%%{init: {\"flowchart\":{\"defaultRenderer\":\"elk\"}} }%%")
    }
    sb.appendLine("flowchart LR")
    sb.appendLine("classDef external fill:$EXTERNAL_COLOR,stroke:#A0A0A0,stroke-width:1px,stroke-dasharray:5 5")

    val nodeIdByGroupId = groups.associate { it.id to "g_${shortHash(it.id)}" }
    for ((index, group) in groups.withIndex()) {
        val nodeId = nodeIdByGroupId.getValue(group.id)
        val className = "group_$index"
        sb.appendLine("classDef $className fill:${colorForGroup(group.id)},stroke:#666,stroke-width:1px")
        sb.appendLine("$nodeId[\"${escapeMermaid(group.displayName)}\"]:::$className")
    }

    val aggregations = aggregateOverviewEdges(graph, stateById, groupById, options)
    val includeExternalUnknown = aggregations.keys.any { it.second == EXTERNAL_UNKNOWN_ID }
    if (includeExternalUnknown) {
        sb.appendLine("$EXTERNAL_UNKNOWN_ID[\"${escapeMermaid(EXTERNAL_UNKNOWN)}\"]:::external")
    }

    for ((key, aggregate) in aggregations.toSortedMap(compareBy<Pair<String, String>>({ it.first }, { it.second }))) {
        val fromId = nodeIdByGroupId[key.first] ?: continue
        val toId = if (key.second == EXTERNAL_UNKNOWN_ID) EXTERNAL_UNKNOWN_ID else nodeIdByGroupId[key.second] ?: continue
        val label = overviewLabel(aggregate.count, aggregate.labels, options.overviewLabelSampleSize)
        sb.appendLine("$fromId -->|${escapeMermaidEdgeLabel(label)}| $toId")
    }

    return sb.toString()
}

private fun aggregateOverviewEdges(
    graph: StateGraph,
    stateById: Map<String, StateNode>,
    groupById: Map<String, StateGroup>,
    options: DiagramRenderOptions,
): Map<Pair<String, String>, OverviewEdgeAggregation> {
    val labelsByKey = mutableMapOf<Pair<String, String>, MutableList<String>>()
    for (edge in graph.transitions.sortedWith(
        compareBy<StateTransitionEdge>(
            { it.fromStateId },
            { it.eventName },
            { it.guardLabel ?: "" },
            { it.toStateId ?: "" },
            { it.toStateDisplayName },
        )
    )) {
        val fromState = stateById[edge.fromStateId] ?: continue
        val fromGroupId = fromState.groupId
        if (!groupById.containsKey(fromGroupId)) continue

        val toGroupId = if (edge.targetKnown && edge.toStateId != null) {
            stateById[edge.toStateId]?.groupId
        } else {
            null
        }

        if (toGroupId != null && toGroupId == fromGroupId) {
            continue
        }

        val targetGroupId = if (toGroupId != null && groupById.containsKey(toGroupId)) {
            toGroupId
        } else {
            EXTERNAL_UNKNOWN_ID
        }

        val key = fromGroupId to targetGroupId
        val labels = labelsByKey.getOrPut(key) { mutableListOf() }
        labels += transitionLabelToken(edge, options)
    }

    return labelsByKey.mapValues { (_, labels) ->
        OverviewEdgeAggregation(
            count = labels.size,
            labels = labels.distinct().sorted(),
        )
    }
}

private fun renderPlantUmlGroup(model: GroupDiagramModel): String {
    val sb = StringBuilder()
    sb.appendLine("@startuml")
    sb.appendLine("hide empty description")
    sb.appendLine("skinparam defaultTextAlignment center")
    sb.appendLine()

    val groupColor = colorForGroup(model.group.id)
    val groupNodeId = "grp_${shortHash(model.group.id)}"
    sb.appendLine("state \"Group: ${escapePlant(model.group.displayName)}\" as $groupNodeId {")

    for (state in model.stateNodes.sortedBy { it.id }) {
        val stateNodeId = "s_${shortHash(state.id)}"
        sb.appendLine("  state \"${escapePlant(state.displayName)}\" as $stateNodeId $groupColor")
    }
    sb.appendLine("}")
    sb.appendLine()

    for ((externalId, externalLabel) in model.externalNodes.sortedBy { it.first }) {
        sb.appendLine("state \"${escapePlant(externalLabel)}\" as $externalId $EXTERNAL_COLOR")
    }
    if (model.externalNodes.isNotEmpty()) {
        sb.appendLine()
    }

    for (edge in model.edges.sortedWith(compareBy<GroupDiagramEdge>({ it.fromNodeId }, { it.toNodeId }, { it.label }))) {
        val arrow = if (edge.isExternal) "-[#888888,dashed]->" else "-->"
        sb.appendLine("${edge.fromNodeId} $arrow ${edge.toNodeId} : ${escapePlant(edge.label)}")
    }

    sb.appendLine("@enduml")
    return sb.toString()
}

private fun renderMermaidGroup(
    model: GroupDiagramModel,
    options: DiagramRenderOptions,
): String {
    val sb = StringBuilder()
    if (options.mermaidUseElk) {
        sb.appendLine("%%{init: {\"flowchart\":{\"defaultRenderer\":\"elk\"}} }%%")
    }
    sb.appendLine("flowchart LR")
    sb.appendLine("classDef external fill:$EXTERNAL_COLOR,stroke:#A0A0A0,stroke-width:1px,stroke-dasharray:5 5")

    val className = "group_${shortHash(model.group.id)}"
    sb.appendLine("classDef $className fill:${colorForGroup(model.group.id)},stroke:#666,stroke-width:1px")
    val subgraphId = "grp_${shortHash(model.group.id)}"
    sb.appendLine("subgraph $subgraphId[\"${escapeMermaid("Group: ${model.group.displayName}")}\"]")
    for (state in model.stateNodes.sortedBy { it.id }) {
        val stateNodeId = "s_${shortHash(state.id)}"
        sb.appendLine("$stateNodeId[\"${escapeMermaid(state.displayName)}\"]:::$className")
    }
    sb.appendLine("end")

    for ((externalId, externalLabel) in model.externalNodes.sortedBy { it.first }) {
        sb.appendLine("$externalId[\"${escapeMermaid(externalLabel)}\"]:::external")
    }

    for (edge in model.edges.sortedWith(compareBy<GroupDiagramEdge>({ it.fromNodeId }, { it.toNodeId }, { it.label }))) {
        val arrow = if (edge.isExternal) "-.->" else "-->"
        sb.appendLine("${edge.fromNodeId} $arrow|${escapeMermaidEdgeLabel(edge.label)}| ${edge.toNodeId}")
    }

    return sb.toString()
}

private fun buildGroupDiagramModel(
    group: StateGroup,
    groupById: Map<String, StateGroup>,
    stateById: Map<String, StateNode>,
    transitions: List<StateTransitionEdge>,
    options: DiagramRenderOptions,
): GroupDiagramModel {
    val currentGroupId = group.id
    val groupStateIds = group.stateIds.toSet()
    val stateNodes = group.stateIds.mapNotNull { stateById[it] }.sortedBy { it.id }
    val externalNodes = linkedMapOf<String, String>()
    val edges = mutableListOf<GroupDiagramEdge>()

    for (edge in transitions.sortedWith(
        compareBy<StateTransitionEdge>(
            { it.fromStateId },
            { it.eventName },
            { it.guardLabel ?: "" },
            { it.toStateId ?: "" },
            { it.toStateDisplayName },
        )
    )) {
        val fromState = stateById[edge.fromStateId] ?: continue
        val toState = edge.toStateId?.let { stateById[it] }
        val fromInGroup = groupStateIds.contains(fromState.id)
        val toInGroup = toState?.let { groupStateIds.contains(it.id) } == true
        val label = transitionLabelToken(edge, options)

        if (fromInGroup && toInGroup && edge.targetKnown) {
            edges += GroupDiagramEdge(
                fromNodeId = "s_${shortHash(fromState.id)}",
                toNodeId = "s_${shortHash(toState!!.id)}",
                label = label,
                isExternal = false,
            )
            continue
        }

        if (fromInGroup) {
            val placeholderLabel = when {
                !edge.targetKnown || toState == null -> EXTERNAL_UNKNOWN
                toState.groupId != currentGroupId -> "External::${groupById[toState.groupId]?.displayName ?: toState.displayName}"
                else -> "External::${toState.displayName}"
            }
            val placeholderId = "e_${shortHash("$currentGroupId|out|$placeholderLabel")}"
            externalNodes[placeholderId] = placeholderLabel
            edges += GroupDiagramEdge(
                fromNodeId = "s_${shortHash(fromState.id)}",
                toNodeId = placeholderId,
                label = label,
                isExternal = true,
            )
            continue
        }

        if (toInGroup) {
            val placeholderLabel = if (fromState.groupId != currentGroupId) {
                "External::${groupById[fromState.groupId]?.displayName ?: fromState.displayName}"
            } else {
                "External::${fromState.displayName}"
            }
            val placeholderId = "e_${shortHash("$currentGroupId|in|$placeholderLabel")}"
            externalNodes[placeholderId] = placeholderLabel
            edges += GroupDiagramEdge(
                fromNodeId = placeholderId,
                toNodeId = "s_${shortHash(toState!!.id)}",
                label = label,
                isExternal = true,
            )
        }
    }

    return GroupDiagramModel(
        group = group,
        stateNodes = stateNodes,
        externalNodes = externalNodes.entries.map { it.toPair() },
        edges = edges,
    )
}

private fun transitionLabelToken(edge: StateTransitionEdge, options: DiagramRenderOptions): String {
    val sb = StringBuilder()
    sb.append(edge.eventName.ifBlank { "event" })

    if (options.showGuardLabels && !edge.guardLabel.isNullOrBlank()) {
        sb.append(" [").append(edge.guardLabel).append(']')
    }

    if (options.showEmittedEvents && edge.emittedEvents.isNotEmpty()) {
        val emitted = edge.emittedEvents
            .sortedWith(compareBy({ it.label }, { it.eventName }))
            .joinToString(",") { "${it.label}:${it.eventName}" }
        sb.append(" {").append(emitted).append('}')
    }

    return sb.toString()
}

private fun overviewLabel(count: Int, labels: List<String>, sampleSize: Int): String {
    val safeSample = sampleSize.coerceAtLeast(1)
    val samples = labels.take(safeSample)
    val extra = labels.size - samples.size
    return buildString {
        append("${count}x")
        for (sample in samples) {
            append(" | ").append(sample)
        }
        if (extra > 0) {
            append(" | +").append(extra).append(" more")
        }
    }
}

private fun colorForGroup(groupId: String): String {
    if (groupId == GENERAL_GROUP_ID) return GENERAL_COLOR
    val index = (stableHash(groupId) and Int.MAX_VALUE) % GROUP_COLORS.size
    return GROUP_COLORS[index]
}

private fun buildGroupSlug(group: StateGroup): String {
    val base = sanitizePathSegment(group.displayName.ifBlank { "group" })
    return "${base}-${shortHash(group.id)}"
}

private fun sanitizePathSegment(value: String): String {
    val normalized = value
        .lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
    return if (normalized.isBlank()) "item" else normalized
}

private fun shortHash(value: String): String {
    val crc = CRC32()
    crc.update(value.toByteArray())
    return java.lang.Long.toHexString(crc.value).uppercase(Locale.getDefault()).take(8)
}

private fun stableHash(value: String): Int {
    var h = 0
    for (ch in value) {
        h = 31 * h + ch.code
    }
    return h
}

private fun escapePlant(value: String): String =
    value.replace("\n", "\\n").replace("\"", "'")

private fun escapeMermaid(value: String): String =
    value.replace("\"", "\\\"").replace("\n", "<br/>")

private fun escapeMermaidEdgeLabel(value: String): String {
    // Mermaid parsers (especially browser-integrated/older builds) are strict
    // around punctuation in edge labels. Keep only broadly-safe characters.
    val normalized = escapeMermaid(value)
        .replace("|", " / ")
        .replace(Regex("[^A-Za-z0-9 _.-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (normalized.isBlank()) "transition" else normalized
}
