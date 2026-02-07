package io.stateproof.annotations

/**
 * Marks a state machine factory function for StateProof auto-discovery.
 *
 * The annotated function must return StateMachine<*, *>.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class StateProofStateMachine(
    /** Optional display name used to derive generated test class/factory names. */
    val name: String = "",
    /** Optional override for generated test package. */
    val testPackage: String = "",
    /** Optional override for generated test class name. */
    val testClassName: String = "",
    /** Optional override for event class prefix used in generated tests. */
    val eventPrefix: String = "",
    /** Optional override for state machine factory expression used in generated tests. */
    val stateMachineFactory: String = "",
    /** Optional extra imports for generated test files. */
    val additionalImports: Array<String> = [],
    /** Test targets to generate. Valid values: "jvm", "android". */
    val targets: Array<String> = ["jvm", "android"],
)
