package io.stateproof.viewer

/**
 * Renders the standalone viewer HTML.
 */
internal object ViewerExporter {

    private const val TEMPLATE_PATH = "viewer/viewer-template.html"
    private const val CYTOSCAPE_PATH = "viewer/cytoscape.min.js"
    private const val RUNTIME_PATH = "viewer/viewer-runtime.js"
    private const val CSS_PATH = "viewer/viewer.css"

    private val template by lazy { readResource(TEMPLATE_PATH) }
    private val cytoscapeJs by lazy { readResource(CYTOSCAPE_PATH) }
    private val runtimeJs by lazy { readResource(RUNTIME_PATH) }
    private val viewerCss by lazy { readResource(CSS_PATH) }

    fun renderHtml(payload: ViewerGraphPayload, options: ViewerRenderOptions): String {
        val machineTitle = if (payload.machineName.isBlank()) "StateProof Viewer" else "StateProof Viewer - ${payload.machineName}"
        val optionsJson = renderOptionsJson(options)

        return template
            .replace("__STATEPROOF_TITLE__", escapeHtml(machineTitle))
            .replace("__STATEPROOF_VIEWER_CSS__", viewerCss)
            .replace("__STATEPROOF_CYTOSCAPE_JS__", cytoscapeJs)
            .replace("__STATEPROOF_RUNTIME_JS__", runtimeJs)
            .replace("__STATEPROOF_GRAPH_JSON__", payload.toJson())
            .replace("__STATEPROOF_OPTIONS_JSON__", optionsJson)
    }

    private fun renderOptionsJson(options: ViewerRenderOptions): String {
        return buildString {
            append("{")
            append("\"layout\":\"")
            append(options.layout.name.lowercase())
            append("\",")
            append("\"showEventLabels\":")
            append(options.showEventLabels)
            append(",")
            append("\"enableSearch\":")
            append(options.enableSearch)
            append(",")
            append("\"enableFocusMode\":")
            append(options.enableFocusMode)
            append(",")
            append("\"includeToolbar\":")
            append(options.includeToolbar)
            append(",")
            append("\"includeJsonSidecar\":")
            append(options.includeJsonSidecar)
            append("}")
        }
    }

    private fun readResource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: throw IllegalStateException("Missing viewer resource: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
