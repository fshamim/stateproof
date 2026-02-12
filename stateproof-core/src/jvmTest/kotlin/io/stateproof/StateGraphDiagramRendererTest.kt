package io.stateproof

import io.stateproof.diagram.DiagramRenderOptions
import io.stateproof.diagram.GeneratedDiagramBundle
import io.stateproof.diagram.renderDiagrams
import io.stateproof.diagram.toFlatStateGraph
import io.stateproof.diagram.writeTo
import io.stateproof.graph.EmittedEventInfo
import io.stateproof.graph.StateGraph
import io.stateproof.graph.StateGroup
import io.stateproof.graph.StateInfo
import io.stateproof.graph.StateNode
import io.stateproof.graph.StateTransitionEdge
import io.stateproof.graph.StateTransitionInfo
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StateGraphDiagramRendererTest {

    @Test
    fun flatGraph_rendersOverviewAndPerGroupFiles() {
        val graph = flatGraph()
        val bundle = graph.renderDiagrams(machineName = "FlatMachine")

        assertHasFile(bundle, "flatmachine/overview.puml")
        assertHasFile(bundle, "flatmachine/overview.mmd")
        assertTrue(bundle.files.any { it.relativePath.startsWith("flatmachine/groups/") && it.relativePath.endsWith(".puml") })
        assertTrue(bundle.files.any { it.relativePath.startsWith("flatmachine/groups/") && it.relativePath.endsWith(".mmd") })

        val overview = bundle.files.first { it.relativePath == "flatmachine/overview.puml" }.content
        assertTrue(overview.contains("General"))
    }

    @Test
    fun nestedGraph_overviewContainsGroupsAndInterGroupEdges() {
        val graph = nestedGraph()
        val bundle = graph.renderDiagrams(machineName = "NestedMachine")

        val overview = bundle.files.first { it.relativePath == "nestedmachine/overview.puml" }.content
        assertTrue(overview.contains("Auth"))
        assertTrue(overview.contains("App"))
        assertTrue(overview.contains("1x"))
    }

    @Test
    fun mixedGraph_containsNestedAndGeneralGroups() {
        val graph = mixedGraph()
        val bundle = graph.renderDiagrams(machineName = "MixedMachine")

        val overview = bundle.files.first { it.relativePath == "mixedmachine/overview.mmd" }.content
        assertTrue(overview.contains("General"))
        assertTrue(overview.contains("Flow"))
    }

    @Test
    fun edgeLabels_includeEventGuardAndEmitsByDefault() {
        val graph = graphWithRichLabels()
        val bundle = graph.renderDiagrams(machineName = "LabelMachine")

        val groupPuml = bundle.files
            .first { it.relativePath.startsWith("labelmachine/groups/") && it.relativePath.endsWith(".puml") }
            .content

        assertTrue(groupPuml.contains("OnDecide [projectId.exists]"))
        assertTrue(groupPuml.contains("valid:OnJsonValidated"))
        assertTrue(groupPuml.contains("retry:OnRetry"))
    }

    @Test
    fun overviewAggregation_usesCountAndLabelSampling() {
        val graph = graphWithManyInterGroupLabels()
        val bundle = graph.renderDiagrams(machineName = "AggMachine")

        val overview = bundle.files.first { it.relativePath == "aggmachine/overview.puml" }.content
        assertTrue(overview.contains("4x"))
        assertTrue(overview.contains("+1 more"))
    }

    @Test
    fun perGroupRender_usesExternalPlaceholdersForCrossGroupEdges() {
        val graph = nestedGraph()
        val bundle = graph.renderDiagrams(machineName = "ExternalMachine")

        val authPuml = bundle.files
            .first { it.relativePath.startsWith("externalmachine/groups/") && it.relativePath.endsWith(".puml") && it.content.contains("Auth") }
            .content

        assertTrue(authPuml.contains("External::"))
        assertTrue(authPuml.contains("-[#888888,dashed]->"))
    }

    @Test
    fun unknownTarget_rendersExternalUnknownPlaceholder() {
        val graph = graphWithUnknownTarget()
        val bundle = graph.renderDiagrams(machineName = "UnknownMachine")

        val groupMmd = bundle.files
            .first { it.relativePath.startsWith("unknownmachine/groups/") && it.relativePath.endsWith(".mmd") }
            .content
        assertTrue(groupMmd.contains("External::?"))
    }

    @Test
    fun rendering_isDeterministicAcrossRuns() {
        val graph = nestedGraph()

        val first = graph.renderDiagrams(machineName = "StableMachine")
        val second = graph.renderDiagrams(machineName = "StableMachine")

        assertEquals(first, second)
    }

    @Test
    fun spaghettiGraph_rendersWithoutStructuralLoss() {
        val graph = spaghettiGraph()
        val bundle = graph.renderDiagrams(machineName = "SpaghettiMachine")

        assertHasFile(bundle, "spaghettimachine/overview.puml")
        assertHasFile(bundle, "spaghettimachine/overview.mmd")
        assertTrue(bundle.files.any { it.relativePath.startsWith("spaghettimachine/groups/") })
        assertTrue(bundle.files.all { it.content.isNotBlank() })
    }

    @Test
    fun writeTo_writesAllFilesToDisk() {
        val graph = flatGraph()
        val bundle = graph.renderDiagrams(machineName = "WriteMachine")
        val tempDir = createTempDirectory(prefix = "stateproof-diagrams-").toFile()

        try {
            val written = bundle.writeTo(tempDir)
            assertEquals(bundle.files.size, written.size)
            assertTrue(written.all { it.exists() && it.readText().isNotBlank() })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun legacyStateInfoMap_canBeAdaptedToFlatGraph() {
        val stateInfo = mapOf(
            "Initial" to StateInfo(
                stateName = "Initial",
                transitions = mutableMapOf("Start" to "Loading"),
                transitionDetails = mutableListOf(
                    StateTransitionInfo("Start", "Loading")
                ),
            ),
            "Loading" to StateInfo(
                stateName = "Loading",
                transitionDetails = mutableListOf(
                    StateTransitionInfo(
                        eventName = "Done",
                        toStateName = "Ready",
                        guardLabel = "success",
                        emittedEvents = listOf(EmittedEventInfo("ok", "OnOk")),
                    )
                ),
            ),
        )

        val graph = stateInfo.toFlatStateGraph(initialStateName = "Initial")
        val bundle = graph.renderDiagrams(machineName = "LegacyMachine")

        assertEquals("Initial", graph.initialStateId)
        assertTrue(graph.groups.single().id == "group:General")
        assertHasFile(bundle, "legacymachine/overview.puml")
        assertTrue(bundle.files.any { it.content.contains("Done [success]") })
    }

    @Test
    fun options_canDisableGuardAndEmittedMetadataInLabels() {
        val graph = graphWithRichLabels()
        val richBundle = graph.renderDiagrams(machineName = "RichLabels")
        val plainBundle = graph.renderDiagrams(
            machineName = "PlainLabels",
            options = DiagramRenderOptions(showGuardLabels = false, showEmittedEvents = false),
        )

        val richContent = richBundle.files
            .first { it.relativePath.startsWith("richlabels/groups/") && it.relativePath.endsWith(".puml") }
            .content
        val plainContent = plainBundle.files
            .first { it.relativePath.startsWith("plainlabels/groups/") && it.relativePath.endsWith(".puml") }
            .content

        assertTrue(richContent.contains("[projectId.exists]"))
        assertTrue(richContent.contains("valid:OnJsonValidated"))
        assertTrue(richContent.contains("retry:OnRetry"))
        assertTrue(!plainContent.contains("[projectId.exists]"))
        assertTrue(!plainContent.contains("valid:OnJsonValidated"))
        assertTrue(!plainContent.contains("retry:OnRetry"))
        assertNotEquals(richContent, plainContent)
    }

    private fun assertHasFile(bundle: GeneratedDiagramBundle, relativePath: String) {
        assertTrue(
            bundle.files.any { it.relativePath == relativePath },
            "Expected file '$relativePath'. Found: ${bundle.files.map { it.relativePath }}"
        )
    }

    private fun flatGraph(): StateGraph {
        val states = listOf(
            StateNode("Initial", "Initial", null, "group:General", isInitial = true),
            StateNode("Running", "Running", null, "group:General", isInitial = false),
            StateNode("Done", "Done", null, "group:General", isInitial = false),
        )
        val groups = listOf(
            StateGroup("group:General", "General", null, states.map { it.id }, emptyList()),
        )
        val transitions = listOf(
            StateTransitionEdge("Initial", "Start", "Running", "Running", null, emptyList(), true),
            StateTransitionEdge("Running", "Finish", "Done", "Done", null, emptyList(), true),
        )
        return StateGraph(initialStateId = "Initial", states = states, groups = groups, transitions = transitions)
    }

    private fun nestedGraph(): StateGraph {
        val states = listOf(
            StateNode("Auth.Login", "Login", null, "group:Auth", isInitial = true),
            StateNode("Auth.Register", "Register", null, "group:Auth", isInitial = false),
            StateNode("App.Home", "Home", null, "group:App", isInitial = false),
            StateNode("App.Detail", "Detail", null, "group:App", isInitial = false),
        )
        val groups = listOf(
            StateGroup("group:Root", "Root", null, emptyList(), listOf("group:Auth", "group:App")),
            StateGroup("group:Auth", "Auth", "group:Root", listOf("Auth.Login", "Auth.Register"), emptyList()),
            StateGroup("group:App", "App", "group:Root", listOf("App.Home", "App.Detail"), emptyList()),
        )
        val transitions = listOf(
            StateTransitionEdge("Auth.Login", "OnRegistered", "Auth.Register", "Register", null, emptyList(), true),
            StateTransitionEdge("Auth.Register", "OnEnterApp", "App.Home", "Home", null, emptyList(), true),
            StateTransitionEdge("App.Home", "OnOpenDetail", "App.Detail", "Detail", null, emptyList(), true),
            StateTransitionEdge("App.Detail", "OnLogout", "Auth.Login", "Login", null, emptyList(), true),
        )
        return StateGraph(initialStateId = "Auth.Login", states = states, groups = groups, transitions = transitions)
    }

    private fun mixedGraph(): StateGraph {
        val states = listOf(
            StateNode("Idle", "Idle", null, "group:General", isInitial = true),
            StateNode("Flow.StepOne", "StepOne", null, "group:Flow", isInitial = false),
            StateNode("Flow.StepTwo", "StepTwo", null, "group:Flow", isInitial = false),
        )
        val groups = listOf(
            StateGroup("group:General", "General", null, listOf("Idle"), emptyList()),
            StateGroup("group:Root", "Root", null, emptyList(), listOf("group:Flow")),
            StateGroup("group:Flow", "Flow", "group:Root", listOf("Flow.StepOne", "Flow.StepTwo"), emptyList()),
        )
        val transitions = listOf(
            StateTransitionEdge("Idle", "OnStart", "Flow.StepOne", "StepOne", null, emptyList(), true),
            StateTransitionEdge("Flow.StepOne", "OnNext", "Flow.StepTwo", "StepTwo", null, emptyList(), true),
        )
        return StateGraph(initialStateId = "Idle", states = states, groups = groups, transitions = transitions)
    }

    private fun graphWithRichLabels(): StateGraph {
        val states = listOf(
            StateNode("Import", "Import", null, "group:General", isInitial = true),
            StateNode("Importing", "Importing", null, "group:General", isInitial = false),
        )
        val groups = listOf(
            StateGroup("group:General", "General", null, states.map { it.id }, emptyList()),
        )
        val transitions = listOf(
            StateTransitionEdge(
                fromStateId = "Import",
                eventName = "OnDecide",
                toStateId = "Importing",
                toStateDisplayName = "Importing",
                guardLabel = "projectId.exists",
                emittedEvents = listOf(
                    EmittedEventInfo("valid", "OnJsonValidated"),
                    EmittedEventInfo("retry", "OnRetry"),
                ),
                targetKnown = true,
            )
        )
        return StateGraph(initialStateId = "Import", states = states, groups = groups, transitions = transitions)
    }

    private fun graphWithManyInterGroupLabels(): StateGraph {
        val states = listOf(
            StateNode("A.One", "One", null, "group:A", isInitial = true),
            StateNode("B.One", "One", null, "group:B", isInitial = false),
        )
        val groups = listOf(
            StateGroup("group:A", "A", null, listOf("A.One"), emptyList()),
            StateGroup("group:B", "B", null, listOf("B.One"), emptyList()),
        )
        val transitions = listOf(
            StateTransitionEdge("A.One", "OnE1", "B.One", "One", "g1", emptyList(), true),
            StateTransitionEdge("A.One", "OnE2", "B.One", "One", "g2", emptyList(), true),
            StateTransitionEdge("A.One", "OnE3", "B.One", "One", "g3", emptyList(), true),
            StateTransitionEdge("A.One", "OnE4", "B.One", "One", "g4", emptyList(), true),
        )
        return StateGraph(initialStateId = "A.One", states = states, groups = groups, transitions = transitions)
    }

    private fun graphWithUnknownTarget(): StateGraph {
        val states = listOf(
            StateNode("Waiting", "Waiting", null, "group:General", isInitial = true),
        )
        val groups = listOf(
            StateGroup("group:General", "General", null, listOf("Waiting"), emptyList()),
        )
        val transitions = listOf(
            StateTransitionEdge("Waiting", "OnGo", null, "?", null, emptyList(), false),
        )
        return StateGraph(initialStateId = "Waiting", states = states, groups = groups, transitions = transitions)
    }

    private fun spaghettiGraph(): StateGraph {
        val states = (1..55).map { index ->
            val name = "S$index"
            StateNode(name, name, null, "group:General", isInitial = index == 1)
        }
        val groups = listOf(
            StateGroup("group:General", "General", null, states.map { it.id }, emptyList()),
        )
        val transitions = mutableListOf<StateTransitionEdge>()
        for (i in 1..55) {
            val from = "S$i"
            val to = "S${if (i == 55) 1 else i + 1}"
            transitions += StateTransitionEdge(from, "OnStep$i", to, to, null, emptyList(), true)
            if (i <= 50) {
                val alt = "S${i + 5}"
                transitions += StateTransitionEdge(from, "OnJump$i", alt, alt, "jump", emptyList(), true)
            }
        }
        return StateGraph(
            initialStateId = "S1",
            states = states,
            groups = groups,
            transitions = transitions,
        )
    }
}
