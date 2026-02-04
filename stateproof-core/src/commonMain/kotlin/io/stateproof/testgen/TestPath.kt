package io.stateproof.testgen

import kotlin.reflect.KClass

/**
 * Represents a single step in a test path.
 *
 * @param STATE The state type
 * @param EVENT The event type
 * @param stateName Name of the state at this step
 * @param stateClass KClass of the state
 * @param eventName Name of the event that triggers transition from this state (null for final step)
 * @param eventClass KClass of the event (null for final step)
 */
data class PathStep<STATE : Any, EVENT : Any>(
    val stateName: String,
    val stateClass: KClass<out STATE>,
    val eventName: String?,
    val eventClass: KClass<out EVENT>?,
)

/**
 * Represents a complete path through the state machine.
 *
 * A path consists of alternating states and events: State1 -> Event1 -> State2 -> Event2 -> State3
 *
 * @param STATE The state type
 * @param EVENT The event type
 * @param steps List of path steps (state + optional event)
 * @param transitions List of transition strings in format "FromState_Event_ToState"
 * @param depth Number of transitions in this path
 * @param hash Unique hash identifier for this path
 */
data class TestPath<STATE : Any, EVENT : Any>(
    val steps: List<PathStep<STATE, EVENT>>,
    val transitions: List<String>,
    val depth: Int,
    val hash: String,
) {
    /**
     * Returns the starting state name.
     */
    val startState: String
        get() = steps.firstOrNull()?.stateName ?: ""

    /**
     * Returns the ending state name.
     */
    val endState: String
        get() = steps.lastOrNull()?.stateName ?: ""

    /**
     * Returns the sequence of event names (excluding nulls).
     */
    val eventSequence: List<String>
        get() = steps.mapNotNull { it.eventName }

    /**
     * Returns the sequence of state names.
     */
    val stateSequence: List<String>
        get() = steps.map { it.stateName }

    /**
     * Generates a descriptive name for this path (truncated for readability).
     *
     * Format: "depth_hash_State1_Event1_State2_..."
     */
    fun generateName(maxLength: Int = 80): String {
        val fullName = steps.fold("") { acc, step ->
            if (step.eventName != null) {
                "${acc}_${step.stateName}_${step.eventName}"
            } else {
                "${acc}_${step.stateName}"
            }
        }
        val prefix = "_${depth}_${hash}"
        val remaining = maxLength - prefix.length
        val truncatedPath = if (fullName.length > remaining) {
            fullName.take(remaining)
        } else {
            fullName
        }
        return "$prefix$truncatedPath"
    }
}

/**
 * Represents a generated test case ready for code generation.
 *
 * @param name Generated test method name
 * @param path The underlying test path
 * @param expectedTransitions List of expected transition strings for assertions
 * @param eventSequence Ordered list of events to dispatch
 */
data class TestCase<STATE : Any, EVENT : Any>(
    val name: String,
    val path: TestPath<STATE, EVENT>,
    val expectedTransitions: List<String>,
    val eventSequence: List<String>,
) {
    /**
     * Generates Kotlin test code for this test case.
     */
    fun generateKotlinCode(
        stateMachineVarName: String = "sm",
        statesClassName: String = "States",
        eventsClassName: String = "Events",
    ): String {
        val builder = StringBuilder()

        builder.appendLine("@Test")
        builder.appendLine("fun `$name`() = runTest {")
        builder.appendLine("    val expectedTransitions = listOf(")
        expectedTransitions.forEach { transition ->
            builder.appendLine("        \"$transition\",")
        }
        builder.appendLine("    )")
        builder.appendLine()
        builder.appendLine("    // TODO: Initialize state machine")
        builder.appendLine("    // val $stateMachineVarName = createStateMachine()")
        builder.appendLine()
        eventSequence.forEach { event ->
            builder.appendLine("    $stateMachineVarName.onEvent($eventsClassName.$event)")
            builder.appendLine("    $stateMachineVarName.awaitIdle()")
        }
        builder.appendLine()
        builder.appendLine("    assertContentEquals(expectedTransitions, $stateMachineVarName.getTransitionLog())")
        builder.appendLine("    $stateMachineVarName.close()")
        builder.appendLine("}")

        return builder.toString()
    }
}
