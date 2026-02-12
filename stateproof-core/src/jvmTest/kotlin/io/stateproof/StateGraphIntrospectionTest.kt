package io.stateproof

import io.stateproof.graph.StateGraph
import io.stateproof.graph.toStateGraph
import io.stateproof.graph.toStateInfo
import io.stateproof.logging.PrintLogger
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StateGraphIntrospectionTest {

    sealed class FlatState {
        data object Initial : FlatState()
        data object Running : FlatState()
        data object Done : FlatState()
    }

    sealed class FlatEvent {
        data object Start : FlatEvent()
        data object Finish : FlatEvent()
    }

    sealed interface NestedState {
        sealed interface Auth : NestedState {
            data object Login : Auth
            data object Register : Auth
        }

        sealed interface App : NestedState {
            data object Feed : App
            data object Detail : App
        }
    }

    sealed interface NestedEvent {
        data object ToRegister : NestedEvent
        data object ToLogin : NestedEvent
        data object ToDetail : NestedEvent
        data object ToFeed : NestedEvent
    }

    sealed interface MixedState {
        data object Idle : MixedState

        sealed interface Flow : MixedState {
            data object StepOne : Flow
            data object StepTwo : Flow
        }
    }

    sealed interface MixedEvent {
        data object Start : MixedEvent
        data object Next : MixedEvent
        data object Reset : MixedEvent
    }

    sealed interface DuplicateState {
        sealed interface BranchA : DuplicateState {
            data object Same : BranchA
        }

        sealed interface BranchB : DuplicateState {
            data object Same : BranchB
        }
    }

    sealed interface DuplicateEvent {
        data object ToA : DuplicateEvent
        data object ToB : DuplicateEvent
    }

    sealed interface GuardedState {
        data object Initial : GuardedState
        data object Processing : GuardedState
    }

    sealed interface GuardedEvent {
        data object Decide : GuardedEvent
        data object Done : GuardedEvent
        data object Retry : GuardedEvent
    }

    sealed class UnknownState {
        data object Initial : UnknownState()
        data class NeedsData(val token: String) : UnknownState()
    }

    sealed class UnknownEvent {
        data object Go : UnknownEvent()
    }

    @Test
    fun toStateGraph_flatStates_useSingleGeneralGroup() {
        val sm = flatStateMachine()
        val graph = sm.toStateGraph()

        val general = graph.groups.singleOrNull { it.id == "group:General" }
        assertNotNull(general)
        assertEquals(3, general.stateIds.size)
        assertTrue(graph.states.all { it.groupId == "group:General" })

        sm.close()
    }

    @Test
    fun toStateGraph_nestedSealedStates_buildHierarchyGroups() {
        val sm = nestedStateMachine()
        val graph = sm.toStateGraph()

        val rootGroupId = groupIdFor(NestedState::class)
        val authGroupId = groupIdFor(NestedState.Auth::class)
        val appGroupId = groupIdFor(NestedState.App::class)

        val root = graph.groups.singleOrNull { it.id == rootGroupId }
        assertNotNull(root)
        assertEquals(setOf(authGroupId, appGroupId), root.childGroupIds.toSet())

        val authGroup = graph.groups.singleOrNull { it.id == authGroupId }
        assertNotNull(authGroup)
        assertEquals(rootGroupId, authGroup.parentGroupId)

        val appGroup = graph.groups.singleOrNull { it.id == appGroupId }
        assertNotNull(appGroup)
        assertEquals(rootGroupId, appGroup.parentGroupId)

        val login = graph.states.single { it.displayName == "Login" }
        assertEquals(authGroupId, login.groupId)
        val feed = graph.states.single { it.displayName == "Feed" }
        assertEquals(appGroupId, feed.groupId)
        assertFalse(graph.groups.any { it.id == "group:General" })

        sm.close()
    }

    @Test
    fun toStateGraph_mixedStates_haveNestedGroupsAndGeneralFallback() {
        val sm = mixedStateMachine()
        val graph = sm.toStateGraph()

        val general = graph.groups.singleOrNull { it.id == "group:General" }
        assertNotNull(general)
        val idleId = graph.states.single { it.displayName == "Idle" }.id
        assertTrue(general.stateIds.contains(idleId))

        val rootGroupId = groupIdFor(MixedState::class)
        val flowGroupId = groupIdFor(MixedState.Flow::class)
        assertTrue(graph.groups.any { it.id == rootGroupId })
        assertTrue(graph.groups.any { it.id == flowGroupId && it.parentGroupId == rootGroupId })

        sm.close()
    }

    @Test
    fun toStateGraph_duplicateSimpleNames_haveUniqueIds() {
        val sm = duplicateNameMachine()
        val graph = sm.toStateGraph()

        val sameStates = graph.states.filter { it.displayName == "Same" }
        assertEquals(2, sameStates.size)
        assertEquals(2, sameStates.map { it.id }.toSet().size)
        assertTrue(sameStates.all { it.qualifiedName != null })

        sm.close()
    }

    @Test
    fun toStateGraph_preservesGuardAndEmittedMetadata() {
        val sm = guardedMachine()
        val graph = sm.toStateGraph()

        val decideEdges = graph.transitions.filter { it.eventName == "Decide" }
        assertEquals(2, decideEdges.size)
        assertTrue(
            decideEdges.any { edge ->
                edge.guardLabel == "canProcess" &&
                    edge.targetKnown &&
                    edge.emittedEvents.any { emitted -> emitted.eventName == "Done" }
            }
        )
        assertTrue(
            decideEdges.any { edge ->
                edge.guardLabel == "otherwise" &&
                    edge.emittedEvents.any { emitted -> emitted.eventName == "Retry" }
            }
        )

        sm.close()
    }

    @Test
    fun toStateGraph_marksUnknownTargetsWhenResolutionFails() {
        val sm = unknownTargetMachine()
        val graph = sm.toStateGraph()

        val unknownEdge = graph.transitions.singleOrNull {
            it.eventName == "Go" &&
                graph.states.any { state -> state.id == it.fromStateId && state.displayName == "NeedsData" }
        }
        assertNotNull(unknownEdge)
        assertFalse(unknownEdge.targetKnown)
        assertNull(unknownEdge.toStateId)
        assertEquals("?", unknownEdge.toStateDisplayName)

        sm.close()
    }

    @Test
    fun toStateGraph_isDeterministicAcrossRepeatedExtraction() {
        val sm = nestedStateMachine()

        val first = sm.toStateGraph()
        val second = sm.toStateGraph()

        assertEquals(first, second)
        sm.close()
    }

    @Test
    fun toStateInfo_remainsStableAfterStateGraphAddition() {
        val sm = flatStateMachine()

        val before = sm.toStateInfo()
        sm.toStateGraph()
        val after = sm.toStateInfo()

        assertEquals(before, after)
        assertEquals("Running", after["Initial"]?.transitions?.get("Start"))

        sm.close()
    }

    private fun flatStateMachine() = StateMachine<FlatState, FlatEvent>(
        dispatcher = Dispatchers.Default,
        ioDispatcher = Dispatchers.Default,
        logger = PrintLogger("Flat"),
    ) {
        initialState(FlatState.Initial)

        state<FlatState.Initial> {
            on<FlatEvent.Start> { transitionTo(FlatState.Running) }
        }

        state<FlatState.Running> {
            on<FlatEvent.Finish> { transitionTo(FlatState.Done) }
        }

        state<FlatState.Done> {}
    }

    private fun nestedStateMachine() = StateMachine<NestedState, NestedEvent>(
        dispatcher = Dispatchers.Default,
        ioDispatcher = Dispatchers.Default,
        logger = PrintLogger("Nested"),
    ) {
        initialState(NestedState.Auth.Login)

        state<NestedState.Auth.Login> {
            on<NestedEvent.ToRegister> { transitionTo(NestedState.Auth.Register) }
        }

        state<NestedState.Auth.Register> {
            on<NestedEvent.ToLogin> { transitionTo(NestedState.Auth.Login) }
        }

        state<NestedState.App.Feed> {
            on<NestedEvent.ToDetail> { transitionTo(NestedState.App.Detail) }
        }

        state<NestedState.App.Detail> {
            on<NestedEvent.ToFeed> { transitionTo(NestedState.App.Feed) }
        }
    }

    private fun mixedStateMachine() = StateMachine<MixedState, MixedEvent>(
        dispatcher = Dispatchers.Default,
        ioDispatcher = Dispatchers.Default,
        logger = PrintLogger("Mixed"),
    ) {
        initialState(MixedState.Idle)

        state<MixedState.Idle> {
            on<MixedEvent.Start> { transitionTo(MixedState.Flow.StepOne) }
        }

        state<MixedState.Flow.StepOne> {
            on<MixedEvent.Next> { transitionTo(MixedState.Flow.StepTwo) }
        }

        state<MixedState.Flow.StepTwo> {
            on<MixedEvent.Reset> { transitionTo(MixedState.Idle) }
        }
    }

    private fun duplicateNameMachine() = StateMachine<DuplicateState, DuplicateEvent>(
        dispatcher = Dispatchers.Default,
        ioDispatcher = Dispatchers.Default,
        logger = PrintLogger("Duplicate"),
    ) {
        initialState(DuplicateState.BranchA.Same)

        state<DuplicateState.BranchA.Same> {
            on<DuplicateEvent.ToB> { transitionTo(DuplicateState.BranchB.Same) }
        }

        state<DuplicateState.BranchB.Same> {
            on<DuplicateEvent.ToA> { transitionTo(DuplicateState.BranchA.Same) }
        }
    }

    private fun guardedMachine() = StateMachine<GuardedState, GuardedEvent>(
        dispatcher = Dispatchers.Default,
        ioDispatcher = Dispatchers.Default,
        logger = PrintLogger("Guarded"),
    ) {
        initialState(GuardedState.Initial)

        state<GuardedState.Initial> {
            on<GuardedEvent.Decide> {
                condition("canProcess") { _, _ -> true } then {
                    transitionTo(GuardedState.Processing)
                    sideEffect { null }.emits("done" to GuardedEvent.Done::class)
                }
                otherwise {
                    doNotTransition()
                    sideEffect { null }.emits("retry" to GuardedEvent.Retry::class)
                }
            }
        }

        state<GuardedState.Processing> {
            on<GuardedEvent.Done> { doNotTransition() }
            on<GuardedEvent.Retry> { transitionTo(GuardedState.Initial) }
        }
    }

    private fun unknownTargetMachine() = StateMachine<UnknownState, UnknownEvent>(
        dispatcher = Dispatchers.Default,
        ioDispatcher = Dispatchers.Default,
        logger = PrintLogger("Unknown"),
    ) {
        initialState(UnknownState.Initial)

        state<UnknownState.Initial> {
            on<UnknownEvent.Go> { transitionTo(UnknownState.NeedsData("token")) }
        }

        state<UnknownState.NeedsData> {
            on<UnknownEvent.Go> {
                transitionTo(
                    resolveTo = { _: UnknownEvent.Go ->
                        UnknownState.NeedsData(this.token)
                    }
                )
            }
        }
    }

    private fun groupIdFor(kClass: KClass<*>): String =
        "group:${kClass.qualifiedName ?: kClass.simpleName ?: "Unknown"}"
}
