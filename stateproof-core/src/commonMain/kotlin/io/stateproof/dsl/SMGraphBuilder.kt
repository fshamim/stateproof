package io.stateproof.dsl

import io.stateproof.graph.EmittedEventInfo
import io.stateproof.graph.EventTransition
import io.stateproof.graph.SMGraph
import io.stateproof.graph.TransitionBranchSpec
import io.stateproof.graph.TransitionMetadata
import io.stateproof.graph.TransitionTo
import io.stateproof.matcher.Matcher
import kotlin.reflect.KClass

/**
 * DSL builder for constructing state machine graphs.
 *
 * Example usage:
 * ```kotlin
 * val graph = SMGraphBuilder<MyState, MyEvent>().apply {
 *     initialState(MyState.Initial)
 *
 *     state<MyState.Initial> {
 *         on<MyEvent.Start> { transitionTo(MyState.Running) }
 *     }
 *
 *     state<MyState.Running> {
 *         on<MyEvent.Stop> { transitionTo(MyState.Stopped) }
 *     }
 * }.build()
 * ```
 *
 * @param STATE The base type of all states
 * @param EVENT The base type of all events
 * @param graph Optional existing graph to extend
 */
class SMGraphBuilder<STATE : Any, EVENT : Any>(
    graph: SMGraph<STATE, EVENT>? = null,
) {
    internal data class BuiltAction<STATE : Any, EVENT : Any>(
        val createTransition: (STATE, EVENT) -> TransitionTo<STATE, EVENT>,
        val emittedEvents: List<EmittedEventInfo>,
    )

    private var initialState = graph?.initialState
    private val stateDefinitions = LinkedHashMap(graph?.stateDefinition ?: emptyMap())

    /**
     * Sets the initial state of the state machine.
     */
    fun initialState(initialState: STATE) {
        this.initialState = initialState
    }

    /**
     * Defines transitions for a state matching the given matcher.
     *
     * @param stateMatcher Matcher for the state
     * @param init Builder block for defining transitions
     */
    fun <S : STATE> state(
        stateMatcher: Matcher<STATE, S>,
        init: StateTransitionBuilder<S>.() -> Unit,
    ) {
        val build = StateTransitionBuilder<S>().apply(init).build()
        stateDefinitions[stateMatcher] = build
    }

    /**
     * Defines transitions for a state using reified type inference.
     *
     * @param init Builder block for defining transitions
     */
    inline fun <reified S : STATE> state(
        noinline init: StateTransitionBuilder<S>.() -> Unit,
    ) {
        state(Matcher.any(), init)
    }

    /**
     * Builds the state machine graph.
     *
     * @throws IllegalStateException if initialState was not set
     */
    fun build(): SMGraph<STATE, EVENT> {
        return SMGraph(requireNotNull(initialState) { "initialState must be set" }, stateDefinitions.toMap())
    }

    /**
     * Builder for defining transitions within a state.
     */
    inner class StateTransitionBuilder<S : STATE> {

        private val holder = SMGraph.State<STATE, EVENT>()

        /**
         * Creates a matcher for any event of type E.
         */
        inline fun <reified E : EVENT> any(): Matcher<EVENT, E> = Matcher.any()

        /**
         * Defines transitions for a single event matcher.
         *
         * `on { ... }` supports:
         * - direct transition directives (`transitionTo` / `doNotTransition`)
         * - guarded branches (`condition` / `otherwise`)
         *
         * This enables explicit modeling of data-dependent branches. The first matching
         * case wins at runtime.
         */
        fun <E : EVENT> on(
            eventMatcher: Matcher<EVENT, E>,
            init: GuardedTransitionBuilder<S, E>.() -> Unit,
        ) {
            val builder = GuardedTransitionBuilder<S, E>().apply(init)
            val branches = builder.buildBranches()
            holder.transitions[eventMatcher] = EventTransition(branches)
        }

        /**
         * Defines transitions for events using reified type inference.
         */
        inline fun <reified E : EVENT> on(
            noinline init: GuardedTransitionBuilder<S, E>.() -> Unit,
        ) {
            return on(any(), init)
        }

        /**
         * Backward-compatible alias for guarded branch DSL.
         */
        @Deprecated(
            message = "Use on(...) with condition/otherwise.",
            replaceWith = ReplaceWith("on(eventMatcher, init)"),
        )
        fun <E : EVENT> onCases(
            eventMatcher: Matcher<EVENT, E>,
            init: GuardedTransitionBuilder<S, E>.() -> Unit,
        ) = on(eventMatcher, init)

        /**
         * Backward-compatible alias for guarded branch DSL.
         */
        @Deprecated(
            message = "Use on(...) with condition/otherwise.",
            replaceWith = ReplaceWith("on(init)"),
        )
        inline fun <reified E : EVENT> onCases(
            noinline init: GuardedTransitionBuilder<S, E>.() -> Unit,
        ) = on(init)

        internal fun build() = holder
    }

    /**
     * Builder for defining guarded conditions and their transition actions.
     */
    inner class GuardedTransitionBuilder<S : STATE, E : EVENT> {
        private val branches = mutableListOf<TransitionBranchSpec<STATE, EVENT>>()
        private var defaultAction: TransitionActionBuilder<S, E>? = null

        private fun defaultActionBuilder(): TransitionActionBuilder<S, E> {
            require(branches.isEmpty()) {
                "Cannot mix direct transition directives with condition/otherwise in the same on { ... } block."
            }
            return defaultAction ?: TransitionActionBuilder<S, E>().also { defaultAction = it }
        }

        fun transitionTo(
            toState: STATE,
            sideEffect: (suspend STATE.(E) -> EVENT?)? = null,
        ) {
            defaultActionBuilder().transitionTo(toState, sideEffect)
        }

        fun transitionTo(
            resolveTo: S.(E) -> STATE,
            sideEffect: (suspend STATE.(E) -> EVENT?)? = null,
        ) {
            defaultActionBuilder().transitionTo({ state, event -> resolveTo(state, event) }, sideEffect)
        }

        fun doNotTransition(sideEffect: (suspend STATE.(E) -> EVENT?)? = null) {
            defaultActionBuilder().doNotTransition(sideEffect)
        }

        fun sideEffect(effect: suspend STATE.(E) -> EVENT?): TransitionActionBuilder<S, E>.SideEffectSpec {
            return defaultActionBuilder().sideEffect(effect)
        }

        fun sideEffectEmits(vararg emitted: Pair<String, KClass<out EVENT>>) {
            defaultActionBuilder().sideEffectEmits(*emitted)
        }

        @Deprecated(
            message = "Use sideEffectEmits(...) or sideEffect { ... } emits (...).",
            replaceWith = ReplaceWith("sideEffectEmits(*emitted)"),
        )
        fun emits(vararg emitted: Pair<String, KClass<out EVENT>>) {
            sideEffectEmits(*emitted)
        }

        /**
         * Starts a labeled condition branch.
         */
        fun condition(
            label: String,
            predicate: (S, E) -> Boolean,
        ): GuardedCaseBuilder {
            require(defaultAction == null) {
                "Cannot mix condition/otherwise branches with direct transition directives in the same on { ... } block."
            }
            return GuardedCaseBuilder(label, predicate)
        }

        /**
         * Convenience overload when guard only depends on the current state.
         */
        fun condition(
            label: String,
            predicate: S.() -> Boolean,
        ): GuardedCaseBuilder = condition(label) { state, _ -> predicate(state) }

        /**
         * Backward-compatible alias for condition(...).
         */
        @Deprecated(
            message = "Use condition(...) instead.",
            replaceWith = ReplaceWith("condition(label, predicate)"),
        )
        fun whenCase(
            label: String,
            predicate: (S, E) -> Boolean,
        ): GuardedCaseBuilder = condition(label, predicate)

        /**
         * Backward-compatible alias for condition(...).
         */
        @Deprecated(
            message = "Use condition(...) instead.",
            replaceWith = ReplaceWith("condition(label, predicate)"),
        )
        fun whenCase(
            label: String,
            predicate: S.() -> Boolean,
        ): GuardedCaseBuilder = condition(label, predicate)

        /**
         * Adds a fallback case.
         */
        fun otherwise(
            init: TransitionActionBuilder<S, E>.() -> Unit,
        ) {
            require(defaultAction == null) {
                "Cannot mix condition/otherwise branches with direct transition directives in the same on { ... } block."
            }
            addBranch(label = "otherwise", predicate = { _, _ -> true }, init = init)
        }

        /**
         * Builder returned from condition(...), enabling infix then { ... } syntax.
         */
        inner class GuardedCaseBuilder internal constructor(
            private val label: String,
            private val predicate: (S, E) -> Boolean,
        ) {
            infix fun then(init: TransitionActionBuilder<S, E>.() -> Unit) {
                addBranch(label = label, predicate = predicate, init = init)
            }
        }

        private fun addBranch(
            label: String,
            predicate: (S, E) -> Boolean,
            init: TransitionActionBuilder<S, E>.() -> Unit,
        ) {
            require(defaultAction == null) {
                "Cannot mix condition/otherwise branches with direct transition directives in the same on { ... } block."
            }
            val action = TransitionActionBuilder<S, E>().apply(init).build(label)
            branches += TransitionBranchSpec(
                guardLabel = label,
                guard = { state, event ->
                    @Suppress("UNCHECKED_CAST")
                    predicate(state as S, event as E)
                },
                createTransition = { state, event ->
                    @Suppress("UNCHECKED_CAST")
                    action.createTransition(state as S, event as E)
                },
                emittedEvents = action.emittedEvents,
            )
        }

        internal fun buildBranches(): List<TransitionBranchSpec<STATE, EVENT>> {
            val defaultBranch: TransitionBranchSpec<STATE, EVENT>? = defaultAction?.let { actionBuilder ->
                val action = actionBuilder.build("default")
                TransitionBranchSpec<STATE, EVENT>(
                    guardLabel = "default",
                    guard = { _: STATE, _: EVENT -> true },
                    createTransition = { state, event ->
                        @Suppress("UNCHECKED_CAST")
                        action.createTransition(state as S, event as E)
                    },
                    emittedEvents = action.emittedEvents,
                )
            }

            require(!(defaultBranch != null && branches.isNotEmpty())) {
                "Cannot mix direct transition directives with condition/otherwise branches in the same on { ... } block."
            }

            return when {
                defaultBranch != null -> listOf(defaultBranch)
                branches.isNotEmpty() -> branches.toList()
                else -> error(
                    "on(...) requires either transitionTo/doNotTransition or condition/otherwise branches."
                )
            }
        }
    }

    /**
     * Transition action builder used inside guarded case branches.
     *
     * Option-B style:
     * - sideEffect { ... }
     * - transitionTo(...) or doNotTransition()
     * - emits(...)
     */
    inner class TransitionActionBuilder<S : STATE, E : EVENT> {
        private var targetResolver: ((S, E) -> STATE)? = null
        private var transitionDirectiveName: String? = null
        private var transitionSideEffect: (suspend STATE.(E) -> EVENT?)? = null
        private var hasSideEffect = false
        private var hasSideEffectEmits = false
        private val emittedEvents = mutableListOf<EmittedEventInfo>()

        fun transitionTo(
            toState: STATE,
            transitionSideEffect: (suspend STATE.(E) -> EVENT?)? = null,
        ) {
            transitionTo({ _, _ -> toState }, transitionSideEffect)
        }

        fun transitionTo(
            resolveTo: (S, E) -> STATE,
            transitionSideEffect: (suspend STATE.(E) -> EVENT?)? = null,
        ) {
            require(targetResolver == null) {
                "Only one transition directive is allowed per condition branch. " +
                    "Already set by '$transitionDirectiveName', cannot call 'transitionTo'."
            }
            targetResolver = resolveTo
            transitionDirectiveName = "transitionTo"
            transitionSideEffect?.let { sideEffect(it) }
        }

        fun doNotTransition(transitionSideEffect: (suspend STATE.(E) -> EVENT?)? = null) {
            require(targetResolver == null) {
                "Only one transition directive is allowed per condition branch. " +
                    "Already set by '$transitionDirectiveName', cannot call 'doNotTransition'."
            }
            targetResolver = { state, _ -> state }
            transitionDirectiveName = "doNotTransition"
            transitionSideEffect?.let { sideEffect(it) }
        }

        inner class SideEffectSpec internal constructor()

        fun SideEffectSpec.emits(vararg emitted: Pair<String, KClass<out EVENT>>) {
            sideEffectEmits(*emitted)
        }

        fun sideEffect(effect: suspend STATE.(E) -> EVENT?): SideEffectSpec {
            require(!hasSideEffect) {
                "Only one sideEffect(...) call is allowed per condition branch."
            }
            transitionSideEffect = effect
            hasSideEffect = true
            return SideEffectSpec()
        }

        fun sideEffectEmits(vararg emitted: Pair<String, KClass<out EVENT>>) {
            require(hasSideEffect) {
                "sideEffectEmits(...) requires sideEffect(...) in the same condition branch."
            }
            require(!hasSideEffectEmits) {
                "Only one sideEffectEmits(...) call is allowed per condition branch."
            }
            emittedEvents += emitted.map { (label, eventClass) ->
                EmittedEventInfo(
                    label = label,
                    eventName = eventClass.simpleName ?: "Unknown",
                )
            }
            hasSideEffectEmits = true
        }

        @Deprecated(
            message = "Use sideEffectEmits(...) or sideEffect { ... } emits (...).",
            replaceWith = ReplaceWith("sideEffectEmits(*emitted)"),
        )
        fun emits(vararg emitted: Pair<String, KClass<out EVENT>>) {
            sideEffectEmits(*emitted)
        }

        internal fun build(guardLabel: String): BuiltAction<STATE, EVENT> {
            val resolver = targetResolver
                ?: error("Case '$guardLabel' must call transitionTo(...) or doNotTransition()")
            val typedSideEffect = transitionSideEffect
            val emitted = emittedEvents.toList()

            return BuiltAction(
                createTransition = { state, event ->
                    @Suppress("UNCHECKED_CAST")
                    val targetState = resolver(state as S, event as E)
                    val sideEffect: (suspend STATE.(EVENT) -> EVENT?)? = typedSideEffect?.let { effect ->
                        { rawEvent ->
                            @Suppress("UNCHECKED_CAST")
                            effect(this, rawEvent as E)
                        }
                    }
                    TransitionTo(
                        toState = targetState,
                        sideEffect = sideEffect,
                        metadata = TransitionMetadata(
                            guardLabel = guardLabel,
                            emittedEvents = emitted,
                        ),
                    )
                },
                emittedEvents = emitted,
            )
        }
    }
}

/**
 * Top-level DSL function for creating a state machine graph.
 *
 * Example:
 * ```kotlin
 * val graph = stateMachineGraph<MyState, MyEvent> {
 *     initialState(MyState.Initial)
 *     state<MyState.Initial> {
 *         on<MyEvent.Start> { transitionTo(MyState.Running) }
 *     }
 * }
 * ```
 */
inline fun <STATE : Any, EVENT : Any> stateMachineGraph(
    init: SMGraphBuilder<STATE, EVENT>.() -> Unit,
): SMGraph<STATE, EVENT> = SMGraphBuilder<STATE, EVENT>().apply(init).build()
