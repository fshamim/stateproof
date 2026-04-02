package io.stateproof.screenshot

/**
 * Parses generated screenshot test files and extracts generated test functions.
 */
object ScreenshotFileParser {

    data class ParsedScreenshotTest(
        val fullText: String,
        val pathHash: String?,
        val functionName: String,
        val generatedSection: String?,
        val userSection: String?,
        val isObsolete: Boolean,
        val expectedTransitions: List<String>,
        val startLine: Int,
    )

    data class ParsedScreenshotFile(
        val filePath: String,
        val tests: List<ParsedScreenshotTest>,
    )

    private val GENERATED_ANNOTATION_PATTERN = Regex(
        """@StateProofScreenshotGenerated\s*\(\s*pathHash\s*=\s*"([^"]+)""""
    )
    private val OBSOLETE_PATTERN = Regex("""@StateProofScreenshotObsolete""")
    private val FUNCTION_PATTERN = Regex("""fun\s+`?([^`(]+)`?\s*\(""")
    private val EXPECTED_TRANSITIONS_PATTERN = Regex(
        """val\s+expectedTransitions\s*=\s*listOf\s*\(([\s\S]*?)\)""",
        RegexOption.MULTILINE,
    )
    private val TRANSITION_STRING_PATTERN = Regex(""""([^"]+)"""")

    fun parseTestFile(filePath: String, content: String): ParsedScreenshotFile {
        val lines = content.lines()
        val tests = mutableListOf<ParsedScreenshotTest>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trim().startsWith("@StateProofScreenshotGenerated") ||
                line.trim().startsWith("@StateProofScreenshotObsolete") ||
                line.trim().startsWith("@Test")
            ) {
                val start = i
                val testLines = mutableListOf<String>()
                var braceCount = 0
                var foundFunctionStart = false

                while (i < lines.size) {
                    val currentLine = lines[i]
                    testLines.add(currentLine)

                    for (char in currentLine) {
                        when (char) {
                            '{' -> {
                                braceCount++
                                foundFunctionStart = true
                            }
                            '}' -> braceCount--
                        }
                    }

                    i++
                    if (foundFunctionStart && braceCount == 0) {
                        break
                    }
                }

                val parsed = parseTestFunction(testLines.joinToString("\n"), start)
                if (parsed != null) {
                    tests.add(parsed)
                }
            } else {
                i++
            }
        }

        return ParsedScreenshotFile(
            filePath = filePath,
            tests = tests,
        )
    }

    private fun parseTestFunction(testText: String, startLine: Int): ParsedScreenshotTest? {
        val pathHash = GENERATED_ANNOTATION_PATTERN.find(testText)?.groupValues?.get(1)
        val functionName = FUNCTION_PATTERN.find(testText)?.groupValues?.get(1)?.trim() ?: return null
        val isObsolete = OBSOLETE_PATTERN.containsMatchIn(testText)

        var generatedSection: String? = null
        var userSection: String? = null
        val begin = StateProofScreenshotMarkers.BEGIN_PATTERN.find(testText)
        val end = StateProofScreenshotMarkers.END_PATTERN.find(testText)
        if (begin != null && end != null && begin.range.first < end.range.first) {
            generatedSection = testText.substring(begin.range.last + 1, end.range.first).trim()
            val after = testText.substring(end.range.last + 1)
            val lastBrace = after.lastIndexOf('}')
            userSection = if (lastBrace > 0) after.substring(0, lastBrace).trim() else after.trim()
        }

        val expectedTransitions = extractExpectedTransitions(testText)

        return ParsedScreenshotTest(
            fullText = testText,
            pathHash = pathHash,
            functionName = functionName,
            generatedSection = generatedSection,
            userSection = userSection,
            isObsolete = isObsolete,
            expectedTransitions = expectedTransitions,
            startLine = startLine,
        )
    }

    private fun extractExpectedTransitions(testText: String): List<String> {
        val match = EXPECTED_TRANSITIONS_PATTERN.find(testText) ?: return emptyList()
        val block = match.groupValues[1]
        return TRANSITION_STRING_PATTERN.findAll(block).map { it.groupValues[1] }.toList()
    }

    fun scanTestDirectory(
        testDir: String,
        fileReader: (String) -> String,
        fileWalker: (String) -> List<String>,
    ): Map<String, ParsedScreenshotTest> {
        val result = mutableMapOf<String, ParsedScreenshotTest>()

        for (filePath in fileWalker(testDir)) {
            if (!filePath.endsWith(".kt")) continue
            val content = fileReader(filePath)
            if (!content.contains("@StateProofScreenshotGenerated")) continue

            val parsed = parseTestFile(filePath, content)
            parsed.tests.forEach { test ->
                val hash = test.pathHash
                if (hash != null) {
                    result[hash] = test
                }
            }
        }

        return result
    }
}
