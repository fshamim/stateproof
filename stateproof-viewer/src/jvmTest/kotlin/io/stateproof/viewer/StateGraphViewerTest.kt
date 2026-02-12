package io.stateproof.viewer

import io.stateproof.graph.EmittedEventInfo
import io.stateproof.graph.StateGraph
import io.stateproof.graph.StateGroup
import io.stateproof.graph.StateNode
import io.stateproof.graph.StateTransitionEdge
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateGraphViewerTest {

    @Test
    fun flatGraph_serializesGeneralGroupAndStates() {
        val payload = flatGraph().toViewerGraphPayload(machineName = "flat")

        assertEquals("flat", payload.machineName)
        assertEquals("group:General", payload.groups.single().id)
        assertEquals(3, payload.states.size)
        assertEquals(2, payload.transitions.size)
    }

    @Test
    fun nestedGraph_keepsGroupHierarchyAndOverviewEdges() {
        val payload = nestedGraph().toViewerGraphPayload(machineName = "nested")

        val auth = payload.groups.first { it.id == "group:Auth" }
        val app = payload.groups.first { it.id == "group:App" }

        assertEquals("group:Root", auth.parentGroupId)
        assertEquals("group:Root", app.parentGroupId)
        assertTrue(payload.overviewEdges.isNotEmpty())
        assertTrue(payload.overviewEdges.any { it.fromGroupId == "group:Auth" && it.toGroupId == "group:App" })
    }

    @Test
    fun mixedGraph_containsGeneralAndNestedGroups() {
        val payload = mixedGraph().toViewerGraphPayload(machineName = "mixed")

        assertTrue(payload.groups.any { it.id == "group:General" })
        assertTrue(payload.groups.any { it.id == "group:Flow" })
        assertTrue(payload.states.any { it.groupId == "group:General" })
        assertTrue(payload.states.any { it.groupId == "group:Flow" })
    }

    @Test
    fun serialization_isDeterministicAcrossRuns() {
        val graph = nestedGraph()

        val one = graph.toViewerGraphPayload(machineName = "stable").toJson()
        val two = graph.toViewerGraphPayload(machineName = "stable").toJson()

        assertEquals(one, two)
    }

    @Test
    fun transitionLabels_preserveGuardAndEmits() {
        val payload = richLabelGraph().toViewerGraphPayload(machineName = "labels")
        val transition = payload.transitions.first()

        assertTrue(transition.label.contains("OnDecide"))
        assertTrue(transition.label.contains("[projectId.exists]"))
        assertTrue(transition.label.contains("valid:OnJsonValidated"))
    }

    @Test
    fun unknownTargets_areRepresentedWithoutCrashingSerializer() {
        val payload = unknownTargetGraph().toViewerGraphPayload(machineName = "unknown")

        assertTrue(payload.transitions.any { !it.targetKnown && it.toStateId == null })
        assertTrue(payload.toJson().contains("\"toStateId\": null"))
    }

    @Test
    fun renderViewer_generatesIndexAndJsonFiles() {
        val bundle = nestedGraph().renderViewer(machineName = "Main")

        assertTrue(bundle.files.any { it.relativePath == "main/index.html" })
        assertTrue(bundle.files.any { it.relativePath == "main/graph.json" })
        assertTrue(bundle.files.all { it.content.isNotBlank() })

        val html = bundle.files.first { it.relativePath == "main/index.html" }.content
        assertTrue(html.contains("STATEPROOF_GRAPH_PAYLOAD"))
        assertTrue(html.contains("cytoscape"))
    }

    @Test
    fun renderViewer_canSkipJsonSidecarWhenDisabled() {
        val bundle = nestedGraph().renderViewer(
            machineName = "NoJson",
            options = ViewerRenderOptions(includeJsonSidecar = false),
        )

        assertTrue(bundle.files.any { it.relativePath == "nojson/index.html" })
        assertTrue(bundle.files.none { it.relativePath.endsWith("/graph.json") })
    }

    @Test
    fun largeGraph_rendersWithoutStructuralLoss() {
        val graph = largeGraph()
        val payload = graph.toViewerGraphPayload(machineName = "large")
        val bundle = graph.renderViewer(machineName = "Large")

        assertEquals(60, payload.states.size)
        assertEquals(60, payload.transitions.size)
        assertTrue(bundle.files.any { it.relativePath == "large/index.html" })
        assertTrue(bundle.files.any { it.relativePath == "large/graph.json" })
        assertTrue(bundle.files.all { it.content.isNotBlank() })
    }

    @Test
    fun writeTo_writesViewerFilesToDisk() {
        val bundle = nestedGraph().renderViewer(machineName = "Disk")
        val tempDir = createTempDirectory(prefix = "stateproof-viewer-").toFile()

        try {
            val written = bundle.writeTo(tempDir)
            assertEquals(bundle.files.size, written.size)
            assertTrue(written.all { it.exists() && it.readText().isNotBlank() })
        } finally {
            tempDir.deleteRecursively()
        }
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
            StateNode("Auth.Login", "Login", "Auth.Login", "group:Auth", isInitial = true),
            StateNode("Auth.Register", "Register", "Auth.Register", "group:Auth", isInitial = false),
            StateNode("App.Home", "Home", "App.Home", "group:App", isInitial = false),
            StateNode("App.Detail", "Detail", "App.Detail", "group:App", isInitial = false),
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

    private fun richLabelGraph(): StateGraph {
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

    private fun unknownTargetGraph(): StateGraph {
        val states = listOf(
            StateNode("Loading", "Loading", null, "group:General", isInitial = true),
        )
        val groups = listOf(
            StateGroup("group:General", "General", null, states.map { it.id }, emptyList()),
        )
        val transitions = listOf(
            StateTransitionEdge(
                fromStateId = "Loading",
                eventName = "OnUnexpected",
                toStateId = null,
                toStateDisplayName = "?",
                guardLabel = "default",
                emittedEvents = emptyList(),
                targetKnown = false,
            )
        )
        return StateGraph(initialStateId = "Loading", states = states, groups = groups, transitions = transitions)
    }

    private fun largeGraph(): StateGraph {
        val states = (0 until 60).map { index ->
            StateNode(
                id = "S$index",
                displayName = "State$index",
                qualifiedName = null,
                groupId = "group:General",
                isInitial = index == 0,
            )
        }

        val groups = listOf(
            StateGroup(
                id = "group:General",
                displayName = "General",
                parentGroupId = null,
                stateIds = states.map { it.id },
                childGroupIds = emptyList(),
            )
        )

        val transitions = (0 until 60).map { index ->
            val next = (index + 1) % 60
            StateTransitionEdge(
                fromStateId = "S$index",
                eventName = "On$index",
                toStateId = "S$next",
                toStateDisplayName = "State$next",
                guardLabel = if (index % 3 == 0) "default" else null,
                emittedEvents = if (index % 5 == 0) listOf(EmittedEventInfo("emit", "On$next")) else emptyList(),
                targetKnown = true,
            )
        }

        return StateGraph(
            initialStateId = "S0",
            states = states,
            groups = groups,
            transitions = transitions,
        )
    }
}
