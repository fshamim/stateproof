package io.stateproof.viewer

import io.stateproof.graph.StateGraph
import java.io.File
import java.util.Locale

/**
 * Supported layout algorithms for the interactive viewer.
 */
enum class ViewerLayout {
    BREADTHFIRST,
}

/**
 * Rendering options for interactive viewer generation.
 */
data class ViewerRenderOptions(
    val layout: ViewerLayout = ViewerLayout.BREADTHFIRST,
    val showEventLabels: Boolean = true,
    val enableSearch: Boolean = true,
    val enableFocusMode: Boolean = true,
    val includeToolbar: Boolean = true,
    val includeJsonSidecar: Boolean = true,
)

/**
 * One generated viewer file.
 */
data class GeneratedViewerFile(
    val relativePath: String,
    val content: String,
)

/**
 * Collection of generated viewer files for one machine.
 */
data class GeneratedViewerBundle(
    val machineName: String,
    val files: List<GeneratedViewerFile>,
)

/**
 * Renders an interactive viewer bundle for this [StateGraph].
 */
fun StateGraph.renderViewer(
    machineName: String,
    options: ViewerRenderOptions = ViewerRenderOptions(),
): GeneratedViewerBundle {
    val safeMachineName = sanitizePathSegment(machineName.ifBlank { "state-machine" })
    val payload = toViewerGraphPayload(
        machineName = safeMachineName,
        options = options,
    )

    val files = mutableListOf<GeneratedViewerFile>()
    val json = payload.toJson()

    files += GeneratedViewerFile(
        relativePath = "$safeMachineName/index.html",
        content = ViewerExporter.renderHtml(payload, options),
    )

    if (options.includeJsonSidecar) {
        files += GeneratedViewerFile(
            relativePath = "$safeMachineName/graph.json",
            content = json,
        )
    }

    return GeneratedViewerBundle(
        machineName = safeMachineName,
        files = files.sortedBy { it.relativePath },
    )
}

/**
 * Writes generated viewer files under [outputDir].
 */
fun GeneratedViewerBundle.writeTo(outputDir: File): List<File> {
    outputDir.mkdirs()
    val written = mutableListOf<File>()
    for (file in files) {
        val target = File(outputDir, file.relativePath)
        target.parentFile?.mkdirs()
        target.writeText(file.content)
        written += target
    }
    return written.sortedBy { it.absolutePath }
}

internal fun sanitizePathSegment(value: String): String {
    val normalized = value
        .trim()
        .lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
    return if (normalized.isBlank()) "item" else normalized
}
