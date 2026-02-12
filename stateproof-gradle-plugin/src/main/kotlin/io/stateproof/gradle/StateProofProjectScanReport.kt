package io.stateproof.gradle

/**
 * Deterministic project profile emitted by `stateproofScan`.
 */
data class StateProofProjectScanReport(
    val projectType: String,
    val integrationMode: String,
    val hasAndroidPlugin: Boolean,
    val hasKmpPlugin: Boolean,
    val hasCompose: Boolean,
    val hasNavigationCompose: Boolean,
    val hasStateProofDependencies: Boolean,
    val detectedStateMachineFiles: List<String>,
    val detectedNavHostFiles: List<String>,
    val recommendedDependencies: List<String>,
    val recommendedTasks: List<String>,
    val warnings: List<String>,
    val generatedAtIso: String,
) {
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"projectType\": ${projectType.jsonString()},")
        appendLine("  \"integrationMode\": ${integrationMode.jsonString()},")
        appendLine("  \"hasAndroidPlugin\": $hasAndroidPlugin,")
        appendLine("  \"hasKmpPlugin\": $hasKmpPlugin,")
        appendLine("  \"hasCompose\": $hasCompose,")
        appendLine("  \"hasNavigationCompose\": $hasNavigationCompose,")
        appendLine("  \"hasStateProofDependencies\": $hasStateProofDependencies,")
        appendLine("  \"detectedStateMachineFiles\": ${detectedStateMachineFiles.jsonArray()},")
        appendLine("  \"detectedNavHostFiles\": ${detectedNavHostFiles.jsonArray()},")
        appendLine("  \"recommendedDependencies\": ${recommendedDependencies.jsonArray()},")
        appendLine("  \"recommendedTasks\": ${recommendedTasks.jsonArray()},")
        appendLine("  \"warnings\": ${warnings.jsonArray()},")
        appendLine("  \"generatedAtIso\": ${generatedAtIso.jsonString()}")
        append("}")
    }

    private fun String.jsonString(): String = "\"" + buildString {
        for (ch in this@jsonString) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch < ' ') {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
    } + "\""

    private fun List<String>.jsonArray(): String {
        if (isEmpty()) return "[]"
        return joinToString(prefix = "[", postfix = "]") { it.jsonString() }
    }
}
