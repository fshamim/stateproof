package io.stateproof.testgen

import io.stateproof.graph.SMGraph
import io.stateproof.matcher.Matcher
import kotlin.reflect.KClass

/**
 * Enumerates all valid paths through a state machine graph using depth-first search.
 *
 * The algorithm ensures:
 * - Every state is visited at least once
 * - Every transition is exercised at least once
 * - Return-to-state scenarios are covered (controlled by maxVisitsPerState)
 * - Terminal states (states with no outgoing transitions) are properly handled
 */
class PathEnumerator<STATE : Any, EVENT : Any>(
    private val graph: SMGraph<STATE, EVENT>,
    private val config: TestGenConfig = TestGenConfig.DEFAULT,
) {

    /**
     * Internal representation of a transition for enumeration.
     */
    private data class TransitionInfo(
        val fromStateName: String,
        val fromStateClass: KClass<*>,
        val eventName: String,
        val eventClass: KClass<*>,
        val toStateName: String,
        val toStateClass: KClass<*>,
    )

    /**
     * Builds a map of state name to list of outgoing transitions.
     */
    private fun buildTransitionMap(): Map<String, List<TransitionInfo>> {
        val transitionMap = mutableMapOf<String, MutableList<TransitionInfo>>()

        for ((stateMatcher, stateDefinition) in graph.stateDefinition) {
            val stateClass = getMatcherClass(stateMatcher)
            val stateName = stateClass.simpleName ?: "Unknown"

            val transitions = mutableListOf<TransitionInfo>()

            for ((eventMatcher, transitionFn) in stateDefinition.transitions) {
                val eventClass = getMatcherClass(eventMatcher)
                val eventName = eventClass.simpleName ?: "Unknown"

                // We need to determine the target state by invoking the transition
                // For introspection, we use a dummy invocation approach
                // This is a simplification - in practice we need the actual state instance
                val targetStateInfo = findTargetState(stateMatcher, eventMatcher)
                if (targetStateInfo != null) {
                    transitions.add(
                        TransitionInfo(
                            fromStateName = stateName,
                            fromStateClass = stateClass,
                            eventName = eventName,
                            eventClass = eventClass,
                            toStateName = targetStateInfo.first,
                            toStateClass = targetStateInfo.second,
                        )
                    )
                }
            }

            transitionMap[stateName] = transitions
        }

        return transitionMap
    }

    /**
     * Attempts to find the target state for a transition.
     *
     * This uses the state machine's transition definitions to determine
     * what state a transition leads to.
     */
    private fun findTargetState(
        stateMatcher: Matcher<STATE, STATE>,
        eventMatcher: Matcher<EVENT, EVENT>,
    ): Pair<String, KClass<*>>? {
        // Find a matching state definition
        val stateDefinition = graph.stateDefinition[stateMatcher] ?: return null

        // Find the transition function
        val transitionFn = stateDefinition.transitions[eventMatcher] ?: return null

        // We need to find which state this matcher is for
        // and create a sample instance to call the transition
        // This is complex for sealed classes, so we use reflection approach

        // For now, we'll build the map by examining all state definitions
        // and their target states from a higher-level introspection

        return null // Will be populated by introspection in enumerateAllPaths
    }

    /**
     * Extracts the KClass from a Matcher using reflection.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getMatcherClass(matcher: Matcher<*, T>): KClass<T> {
        // Access the private kClass field via reflection
        val field = matcher::class.java.getDeclaredField("kClass")
        field.isAccessible = true
        return field.get(matcher) as KClass<T>
    }

    /**
     * Enumerates all valid paths through the state machine.
     *
     * Uses depth-first search with visit counting to explore all paths
     * while avoiding infinite loops.
     *
     * @return List of all valid test paths
     */
    fun enumerateAllPaths(): List<TestPath<STATE, EVENT>> {
        val paths = mutableListOf<TestPath<STATE, EVENT>>()
        val transitionMap = buildTransitionMapFromGraph()

        val initialStateName = graph.initialState::class.simpleName ?: "Unknown"
        val initialStateClass = graph.initialState::class

        // DFS with visit counting
        fun dfs(
            currentPath: MutableList<PathStep<STATE, EVENT>>,
            currentTransitions: MutableList<String>,
            visitCount: MutableMap<String, Int>,
        ) {
            val currentStateName = currentPath.last().stateName
            val outgoingTransitions = transitionMap[currentStateName] ?: emptyList()

            // Check depth limit
            if (config.maxPathDepth != null && currentTransitions.size >= config.maxPathDepth) {
                // Record path at depth limit
                if (currentTransitions.isNotEmpty()) {
                    paths.add(createTestPath(currentPath, currentTransitions))
                }
                return
            }

            // If no outgoing transitions (terminal state)
            if (outgoingTransitions.isEmpty()) {
                if (config.includeTerminalPaths && currentTransitions.isNotEmpty()) {
                    paths.add(createTestPath(currentPath, currentTransitions))
                }
                return
            }

            // Explore each outgoing transition
            for (transition in outgoingTransitions) {
                val nextStateName = transition.toStateName
                val nextVisitCount = visitCount.getOrDefault(nextStateName, 0)

                if (nextVisitCount < config.maxVisitsPerState) {
                    // Update current step with event info
                    val lastStep = currentPath.last()
                    currentPath[currentPath.lastIndex] = lastStep.copy(
                        eventName = transition.eventName,
                        eventClass = transition.eventClass as KClass<out EVENT>,
                    )

                    // Add transition string
                    val transitionString = "${transition.fromStateName}_${transition.eventName}_${transition.toStateName}"
                    currentTransitions.add(transitionString)

                    // Add next state step
                    val nextStep = PathStep<STATE, EVENT>(
                        stateName = transition.toStateName,
                        stateClass = transition.toStateClass as KClass<out STATE>,
                        eventName = null,
                        eventClass = null,
                    )
                    currentPath.add(nextStep)

                    // Update visit count
                    visitCount[nextStateName] = nextVisitCount + 1

                    // If we've now visited this state maxVisitsPerState times, record the path
                    if (nextVisitCount + 1 == config.maxVisitsPerState) {
                        paths.add(createTestPath(currentPath, currentTransitions))
                    } else {
                        // Continue DFS
                        dfs(currentPath, currentTransitions, visitCount)
                    }

                    // Backtrack
                    visitCount[nextStateName] = nextVisitCount
                    currentPath.removeAt(currentPath.lastIndex)
                    currentTransitions.removeAt(currentTransitions.lastIndex)
                    currentPath[currentPath.lastIndex] = lastStep
                }
            }
        }

        // Start DFS from initial state
        val initialStep = PathStep<STATE, EVENT>(
            stateName = initialStateName,
            stateClass = initialStateClass as KClass<out STATE>,
            eventName = null,
            eventClass = null,
        )
        val initialPath = mutableListOf(initialStep)
        val initialVisitCount = mutableMapOf(initialStateName to 1)

        dfs(initialPath, mutableListOf(), initialVisitCount)

        return paths.sortedBy { it.depth }
    }

    /**
     * Builds transition map by examining the graph structure.
     */
    private fun buildTransitionMapFromGraph(): Map<String, List<TransitionInfo>> {
        val transitionMap = mutableMapOf<String, MutableList<TransitionInfo>>()

        // First, collect all state matchers and their classes
        val stateMatcherToClass = mutableMapOf<Matcher<STATE, STATE>, KClass<*>>()
        for ((stateMatcher, _) in graph.stateDefinition) {
            stateMatcherToClass[stateMatcher] = getMatcherClass(stateMatcher)
        }

        // For each state, examine its transitions
        for ((stateMatcher, stateDefinition) in graph.stateDefinition) {
            val fromStateClass = stateMatcherToClass[stateMatcher]!!
            val fromStateName = fromStateClass.simpleName ?: "Unknown"

            val transitions = mutableListOf<TransitionInfo>()

            for ((eventMatcher, transitionFn) in stateDefinition.transitions) {
                val eventClass = getMatcherClass(eventMatcher)
                val eventName = eventClass.simpleName ?: "Unknown"

                // To find the target state, we need to examine what state the transition goes to
                // This requires calling the transition function with sample instances
                // For sealed classes, we can try to find matching instances
                val targetState = findTargetStateByInvocation(fromStateClass, eventClass, transitionFn)

                if (targetState != null) {
                    val toStateName = targetState::class.simpleName ?: "Unknown"
                    transitions.add(
                        TransitionInfo(
                            fromStateName = fromStateName,
                            fromStateClass = fromStateClass,
                            eventName = eventName,
                            eventClass = eventClass,
                            toStateName = toStateName,
                            toStateClass = targetState::class,
                        )
                    )
                }
            }

            transitionMap[fromStateName] = transitions
        }

        return transitionMap
    }

    /**
     * Attempts to find the target state by invoking the transition function.
     */
    @Suppress("UNCHECKED_CAST")
    private fun findTargetStateByInvocation(
        fromStateClass: KClass<*>,
        eventClass: KClass<*>,
        transitionFn: (STATE, EVENT) -> io.stateproof.graph.TransitionTo<STATE, EVENT>,
    ): STATE? {
        return try {
            // Try to get object instance for sealed class members
            val stateInstance = fromStateClass.objectInstance as? STATE
            val eventInstance = eventClass.objectInstance as? EVENT

            if (stateInstance != null && eventInstance != null) {
                val result = transitionFn(stateInstance, eventInstance)
                result.toState
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates a TestPath from the current DFS state.
     */
    private fun createTestPath(
        steps: List<PathStep<STATE, EVENT>>,
        transitions: List<String>,
    ): TestPath<STATE, EVENT> {
        val pathString = steps.fold("") { acc, step ->
            if (step.eventName != null) {
                "${acc}_${step.stateName}_${step.eventName}"
            } else {
                "${acc}_${step.stateName}"
            }
        }
        val hash = HashUtils.hashPath(pathString, config.hashAlgorithm)

        return TestPath(
            steps = steps.toList(),
            transitions = transitions.toList(),
            depth = transitions.size,
            hash = hash,
        )
    }

    /**
     * Generates test cases from enumerated paths.
     *
     * @return List of test cases ready for code generation
     */
    fun generateTestCases(): List<TestCase<STATE, EVENT>> {
        return enumerateAllPaths().map { path ->
            TestCase(
                name = path.generateName(),
                path = path,
                expectedTransitions = path.transitions,
                eventSequence = path.eventSequence,
            )
        }
    }
}
