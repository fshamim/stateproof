package io.stateproof.testgen

import io.stateproof.graph.StateInfo
import io.stateproof.graph.StateTransitionInfo

/**
 * Simple path enumerator that works with StateInfo maps.
 *
 * This is the same algorithm used by iCages StateTestGenerator,
 * adapted for StateProof. It doesn't require reflection and works
 * with a pre-built map of state transitions.
 *
 * @param stateInfoMap Map of state names to their StateInfo
 * @param initialState The starting state name
 * @param config Configuration for path enumeration
 */
class SimplePathEnumerator(
    private val stateInfoMap: Map<String, StateInfo>,
    private val initialState: String,
    private val config: TestGenConfig = TestGenConfig.DEFAULT,
) {

    /**
     * Represents a transition in the state machine.
     */
    data class Transition(
        val fromState: String,
        val event: String,
        val toState: String,
    )

    private data class GraphEdge(
        val event: String,
        val toState: String,
        val detail: StateTransitionInfo?,
    )

    private data class EnumeratedPath(
        val path: List<String>,
        val identityTokens: List<String>,
    )

    /**
     * Builds a graph from the state info map.
     * Returns a map of state name to list of (event, targetState) pairs.
     */
    private fun buildGraph(): Map<String, List<GraphEdge>> {
        val graph = mutableMapOf<String, MutableList<GraphEdge>>()

        for ((stateName, stateInfo) in stateInfoMap) {
            val transitions = mutableListOf<GraphEdge>()
            if (stateInfo.transitionDetails.isNotEmpty()) {
                for (detail in stateInfo.transitionDetails) {
                    transitions.add(
                        GraphEdge(
                            event = detail.eventName,
                            toState = detail.toStateName,
                            detail = detail,
                        )
                    )
                }
            } else {
                for ((event, toState) in stateInfo.transitions) {
                    transitions.add(
                        GraphEdge(
                            event = event,
                            toState = toState,
                            detail = null,
                        )
                    )
                }
            }
            graph[stateName] = transitions
        }

        return graph
    }

    /**
     * Finds all test paths using DFS with visit counting.
     *
     * This is the core algorithm: DFS with maxVisitsPerState to control depth.
     * A path is recorded when a state reaches maxVisitsPerState visits.
     *
     * @return List of paths, where each path is a list of alternating states and events
     */
    fun findAllPaths(): List<List<String>> {
        return findAllEnumeratedPaths().map { it.path }
    }

    private fun findAllEnumeratedPaths(): List<EnumeratedPath> {
        val graph = buildGraph()
        val testCases = mutableListOf<EnumeratedPath>()

        fun dfs(path: List<String>, visited: Map<String, Int>, identityTokens: List<String>) {
            val current = path.last()

            // Check depth limit
            if (config.maxPathDepth != null) {
                val transitionCount = (path.size - 1) / 2
                if (transitionCount >= config.maxPathDepth) {
                    if (path.size > 1) {
                        testCases.add(EnumeratedPath(path = path, identityTokens = identityTokens))
                    }
                    return
                }
            }

            val outgoingTransitions = graph[current]

            // Terminal state (no outgoing transitions)
            if (outgoingTransitions.isNullOrEmpty()) {
                if (config.includeTerminalPaths && path.size > 1) {
                    testCases.add(EnumeratedPath(path = path, identityTokens = identityTokens))
                }
                return
            }

            // Explore each outgoing transition
            for (edge in outgoingTransitions) {
                val event = edge.event
                val next = edge.toState
                val newPath = path + listOf(event, next)
                val nextCount = visited[next] ?: 0
                val newIdentity = if (isIdentityRelevant(edge.detail)) {
                    identityTokens + buildIdentityToken(
                        fromState = current,
                        event = event,
                        toState = next,
                        detail = edge.detail,
                    )
                } else {
                    identityTokens
                }

                if (nextCount < config.maxVisitsPerState) {
                    if (nextCount + 1 == config.maxVisitsPerState) {
                        // Record path when we hit the visit limit
                        testCases.add(EnumeratedPath(path = newPath, identityTokens = newIdentity))
                    } else {
                        // Continue DFS
                        dfs(newPath, visited + (next to nextCount + 1), newIdentity)
                    }
                }
            }
        }

        dfs(listOf(initialState), mapOf(initialState to 1), emptyList())
        return testCases
    }

    private fun buildIdentityToken(
        fromState: String,
        event: String,
        toState: String,
        detail: StateTransitionInfo?,
    ): String {
        val guard = detail?.guardLabel ?: ""
        val emitted = detail?.emittedEvents
            ?.joinToString(";") { "${it.label}:${it.eventName}" }
            .orEmpty()
        return "$fromState|$event|$toState|$guard|$emitted"
    }

    private fun isIdentityRelevant(detail: StateTransitionInfo?): Boolean {
        if (detail == null) return false
        return detail.guardLabel != null || detail.emittedEvents.isNotEmpty()
    }

    /**
     * Converts a path to transition strings in "State_Event_State" format.
     */
    fun pathToTransitions(path: List<String>): List<String> {
        val transitions = mutableListOf<String>()
        var i = 0
        while (i < path.size - 2) {
            transitions.add("${path[i]}_${path[i + 1]}_${path[i + 2]}")
            i += 2
        }
        return transitions
    }

    /**
     * Generates a test name from a path.
     *
     * Format: _<depth>_<hash>_from_<startState>_to_<endState>
     * Example: _4_1698_from_Initial_to_Settings
     */
    fun generateTestName(path: List<String>, identityTokens: List<String>? = null): String {
        val pathString = if (identityTokens.isNullOrEmpty()) {
            path.joinToString("_")
        } else {
            "${path.joinToString("_")}||${identityTokens.joinToString("||")}"
        }
        val hash = HashUtils.hashPath(pathString, config.hashAlgorithm)
        val depth = (path.size + 1) / 3 + 1

        // For CRC16, use 4 chars; for CRC32, use first 4 chars for compatibility
        val shortHash = if (config.hashAlgorithm == TestGenConfig.HashAlgorithm.CRC16) {
            hash
        } else {
            hash.take(4)
        }

        val startState = path.firstOrNull() ?: "Unknown"
        val endState = path.lastOrNull() ?: "Unknown"

        return "_${depth}_${shortHash}_from_${startState}_to_${endState}"
    }

    /**
     * Generates all test cases with names, expected transitions, and event sequences.
     */
    fun generateTestCases(): List<SimpleTestCase> {
        return findAllEnumeratedPaths()
            .sortedBy { it.path.size }
            .map { enumerated ->
                val path = enumerated.path
                SimpleTestCase(
                    name = generateTestName(path, enumerated.identityTokens),
                    path = path,
                    expectedTransitions = pathToTransitions(path),
                    eventSequence = path.filterIndexed { index, _ -> index % 2 == 1 },
                )
            }
    }
}

/**
 * Simple test case data class.
 */
data class SimpleTestCase(
    val name: String,
    val path: List<String>,
    val expectedTransitions: List<String>,
    val eventSequence: List<String>,
) {
    /**
     * Generates Kotlin test code for this test case.
     */
    fun generateKotlinCode(): String {
        val builder = StringBuilder()
        builder.appendLine("@Test")
        builder.appendLine("fun `$name`() = runBlocking {")
        builder.appendLine("    //${path.joinToString("_")}")
        builder.appendLine("    val expectedTransitions = listOf(")
        expectedTransitions.forEach { transition ->
            builder.appendLine("        \"$transition\",")
        }
        builder.appendLine("    )")
        builder.appendLine()
        builder.appendLine("}")
        return builder.toString()
    }
}
