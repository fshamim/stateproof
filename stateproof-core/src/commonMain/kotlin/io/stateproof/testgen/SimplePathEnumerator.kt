package io.stateproof.testgen

import io.stateproof.graph.StateInfo

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

    /**
     * Builds a graph from the state info map.
     * Returns a map of state name to list of (event, targetState) pairs.
     */
    private fun buildGraph(): Map<String, List<Pair<String, String>>> {
        val graph = mutableMapOf<String, MutableList<Pair<String, String>>>()

        for ((stateName, stateInfo) in stateInfoMap) {
            val transitions = mutableListOf<Pair<String, String>>()
            for ((event, toState) in stateInfo.transitions) {
                transitions.add(event to toState)
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
        val graph = buildGraph()
        val testCases = mutableListOf<List<String>>()

        fun dfs(path: List<String>, visited: Map<String, Int>) {
            val current = path.last()

            // Check depth limit
            if (config.maxPathDepth != null) {
                val transitionCount = (path.size - 1) / 2
                if (transitionCount >= config.maxPathDepth) {
                    if (path.size > 1) {
                        testCases.add(path)
                    }
                    return
                }
            }

            val outgoingTransitions = graph[current]

            // Terminal state (no outgoing transitions)
            if (outgoingTransitions.isNullOrEmpty()) {
                if (config.includeTerminalPaths && path.size > 1) {
                    testCases.add(path)
                }
                return
            }

            // Explore each outgoing transition
            for ((event, next) in outgoingTransitions) {
                val newPath = path + listOf(event, next)
                val nextCount = visited[next] ?: 0

                if (nextCount < config.maxVisitsPerState) {
                    if (nextCount + 1 == config.maxVisitsPerState) {
                        // Record path when we hit the visit limit
                        testCases.add(newPath)
                    } else {
                        // Continue DFS
                        dfs(newPath, visited + (next to nextCount + 1))
                    }
                }
            }
        }

        dfs(listOf(initialState), mapOf(initialState to 1))
        return testCases
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
     * Format: _<depth>_<hash>_<path truncated>
     */
    fun generateTestName(path: List<String>, maxLength: Int = 80): String {
        val pathString = path.joinToString("_")
        val hash = HashUtils.hashPath(pathString, config.hashAlgorithm)
        val depth = (path.size + 1) / 3 + 1

        // For CRC16, use 4 chars; for CRC32, use first 4 chars for compatibility
        val shortHash = if (config.hashAlgorithm == TestGenConfig.HashAlgorithm.CRC16) {
            hash
        } else {
            hash.take(4)
        }

        val prefix = "_${depth}_${shortHash}_"
        val remaining = maxLength - prefix.length
        val truncatedPath = pathString.take(remaining)

        return "$prefix$truncatedPath"
    }

    /**
     * Generates all test cases with names, expected transitions, and event sequences.
     */
    fun generateTestCases(): List<SimpleTestCase> {
        return findAllPaths()
            .sortedBy { it.size }
            .map { path ->
                SimpleTestCase(
                    name = generateTestName(path),
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
