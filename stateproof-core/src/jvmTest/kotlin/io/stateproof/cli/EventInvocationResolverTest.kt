package io.stateproof.cli

import io.stateproof.graph.EmittedEventInfo
import io.stateproof.graph.StateInfo
import io.stateproof.graph.StateTransitionInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventInvocationResolverTest {

    @Test
    fun resolve_detectsObjectAndNoArgEvents() {
        val stateInfoMap = mapOf(
            "A" to StateInfo(
                stateName = "A",
                transitions = mutableMapOf(
                    "OnObject" to "B",
                    "OnNoArg" to "C",
                ),
            ),
        )

        val result = EventInvocationResolver.resolve(
            providerFqn = "io.stateproof.cli.EventInvocationResolverTestKt#dummyProvider",
            eventClassPrefix = "ResolverEvents",
            additionalImports = listOf("io.stateproof.cli.ResolverEvents"),
            stateInfoMap = stateInfoMap,
        )

        assertEquals("ResolverEvents.OnObject", result.eventInvocationByName["OnObject"])
        assertEquals("ResolverEvents.OnNoArg()", result.eventInvocationByName["OnNoArg"])
        assertTrue(result.manualReviewReasonsByEvent.isEmpty())
    }

    @Test
    fun resolve_marksConstructorRequiredEventsAsManual() {
        val stateInfoMap = mapOf(
            "A" to StateInfo(
                stateName = "A",
                transitions = mutableMapOf("OnNeedsArgs" to "B"),
            ),
        )

        val result = EventInvocationResolver.resolve(
            providerFqn = "io.stateproof.cli.EventInvocationResolverTestKt#dummyProvider",
            eventClassPrefix = "ResolverEvents",
            additionalImports = listOf("io.stateproof.cli.ResolverEvents"),
            stateInfoMap = stateInfoMap,
        )

        assertFalse(result.eventInvocationByName.containsKey("OnNeedsArgs"))
        val reasons = result.manualReviewReasonsByEvent["OnNeedsArgs"].orEmpty()
        assertTrue(reasons.any { it.contains("constructor requires arguments") })
    }

    @Test
    fun resolve_marksGuardedOrEmittedEventsAsManual() {
        val stateInfoMap = mapOf(
            "A" to StateInfo(
                stateName = "A",
                transitions = mutableMapOf("OnObject" to "B"),
                transitionDetails = mutableListOf(
                    StateTransitionInfo(
                        eventName = "OnObject",
                        toStateName = "B",
                        guardLabel = "token.exists",
                        emittedEvents = listOf(EmittedEventInfo("ok", "OnObject")),
                    ),
                ),
            ),
        )

        val result = EventInvocationResolver.resolve(
            providerFqn = "io.stateproof.cli.EventInvocationResolverTestKt#dummyProvider",
            eventClassPrefix = "ResolverEvents",
            additionalImports = listOf("io.stateproof.cli.ResolverEvents"),
            stateInfoMap = stateInfoMap,
        )

        assertEquals("ResolverEvents.OnObject", result.eventInvocationByName["OnObject"])
        val reasons = result.manualReviewReasonsByEvent["OnObject"].orEmpty()
        assertTrue(reasons.any { it.contains("guarded transition") })
        assertTrue(reasons.any { it.contains("emit follow-up events") })
    }

    @Test
    fun resolve_marksManualWhenRootClassCannotBeLoaded() {
        val stateInfoMap = mapOf(
            "A" to StateInfo(
                stateName = "A",
                transitions = mutableMapOf("OnUnknown" to "B"),
            ),
        )

        val result = EventInvocationResolver.resolve(
            providerFqn = "io.stateproof.cli.EventInvocationResolverTestKt#dummyProvider",
            eventClassPrefix = "DoesNotExistEvents",
            additionalImports = emptyList(),
            stateInfoMap = stateInfoMap,
        )

        assertTrue(result.eventInvocationByName.isEmpty())
        val reasons = result.manualReviewReasonsByEvent["OnUnknown"].orEmpty()
        assertTrue(reasons.any { it.contains("unable to load event root class") })
    }
}

sealed interface ResolverEvents {
    data object OnObject : ResolverEvents
    class OnNoArg : ResolverEvents
    data class OnNeedsArgs(val value: String) : ResolverEvents
}
