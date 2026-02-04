package io.stateproof.sync

/**
 * Parses test files to extract StateProof-generated sections and user code.
 *
 * A test file has this structure:
 * ```
 * @StateProofGenerated(pathHash = "XXXX", ...)
 * @Test
 * fun testName() = runBlocking {
 *     // ▼▼▼ STATEPROOF:EXPECTED - Do not edit below this line ▼▼▼
 *     val expectedTransitions = listOf(...)
 *     // ▲▲▲ STATEPROOF:END ▲▲▲
 *
 *     // User implementation below (preserved)
 *     ...user code...
 * }
 * ```
 */
object TestFileParser {

    /**
     * Represents a parsed test function from a test file.
     */
    data class ParsedTest(
        /** The full function text including annotations */
        val fullText: String,
        /** The path hash from @StateProofGenerated annotation */
        val pathHash: String?,
        /** The function name */
        val functionName: String,
        /** Content between STATEPROOF:EXPECTED and STATEPROOF:END markers */
        val generatedSection: String?,
        /** Content after STATEPROOF:END marker (user implementation) */
        val userSection: String?,
        /** Whether this test has @StateProofObsolete annotation */
        val isObsolete: Boolean,
        /** The expected transitions from the annotation or parsed from code */
        val expectedTransitions: List<String>,
        /** Line number where this test starts */
        val startLine: Int,
    )

    /**
     * Represents the result of parsing a test file.
     */
    data class ParsedTestFile(
        /** Path to the test file */
        val filePath: String,
        /** All imports and package declaration */
        val header: String,
        /** Parsed test functions */
        val tests: List<ParsedTest>,
        /** Any content after the last test (e.g., closing brace of class) */
        val footer: String,
    )

    // Regex patterns
    private val ANNOTATION_PATTERN = Regex(
        """@StateProofGenerated\s*\(\s*pathHash\s*=\s*"([^"]+)""""
    )
    private val OBSOLETE_PATTERN = Regex("""@StateProofObsolete""")
    private val FUNCTION_PATTERN = Regex("""fun\s+`?([^`(]+)`?\s*\(""")
    private val EXPECTED_TRANSITIONS_PATTERN = Regex(
        """val\s+expectedTransitions\s*=\s*listOf\s*\(([\s\S]*?)\)""",
        RegexOption.MULTILINE
    )
    private val TRANSITION_STRING_PATTERN = Regex(""""([^"]+)"""")

    /**
     * Parses a test file and extracts all StateProof-generated tests.
     *
     * @param filePath Path to the file (for reporting)
     * @param content The file content
     * @return Parsed test file structure
     */
    fun parseTestFile(filePath: String, content: String): ParsedTestFile {
        val lines = content.lines()
        val tests = mutableListOf<ParsedTest>()

        var headerEndLine = 0
        var currentTestStart = -1
        var inTest = false
        var braceCount = 0

        // Find where imports/package end and tests begin
        for ((index, line) in lines.withIndex()) {
            if (line.trim().startsWith("@Test") ||
                line.trim().startsWith("@StateProofGenerated") ||
                line.trim().startsWith("class ") ||
                FUNCTION_PATTERN.containsMatchIn(line)
            ) {
                headerEndLine = index
                break
            }
        }

        // Extract header
        val header = lines.take(headerEndLine).joinToString("\n")

        // Find and parse each test function
        var i = headerEndLine
        while (i < lines.size) {
            val line = lines[i]

            // Look for start of a test (annotation or @Test)
            if (line.trim().startsWith("@StateProofGenerated") ||
                line.trim().startsWith("@StateProofObsolete") ||
                line.trim().startsWith("@Test")
            ) {
                currentTestStart = i

                // Find the function and its body
                val testLines = mutableListOf<String>()
                braceCount = 0
                var foundFunctionStart = false

                while (i < lines.size) {
                    val currentLine = lines[i]
                    testLines.add(currentLine)

                    // Count braces to find function end
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

                    // Function ended when braces balance after seeing at least one
                    if (foundFunctionStart && braceCount == 0) {
                        break
                    }
                }

                val testText = testLines.joinToString("\n")
                val parsedTest = parseTestFunction(testText, currentTestStart)
                if (parsedTest != null) {
                    tests.add(parsedTest)
                }
            } else {
                i++
            }
        }

        // Extract footer (content after last test)
        val lastTestEndLine = if (tests.isNotEmpty()) {
            val lastTest = tests.last()
            lastTest.startLine + lastTest.fullText.lines().size
        } else {
            headerEndLine
        }
        val footer = if (lastTestEndLine < lines.size) {
            lines.drop(lastTestEndLine).joinToString("\n")
        } else {
            ""
        }

        return ParsedTestFile(
            filePath = filePath,
            header = header,
            tests = tests,
            footer = footer,
        )
    }

    /**
     * Parses a single test function text.
     */
    private fun parseTestFunction(testText: String, startLine: Int): ParsedTest? {
        // Extract path hash from annotation
        val pathHashMatch = ANNOTATION_PATTERN.find(testText)
        val pathHash = pathHashMatch?.groupValues?.get(1)

        // Extract function name
        val functionMatch = FUNCTION_PATTERN.find(testText)
        val functionName = functionMatch?.groupValues?.get(1)?.trim() ?: return null

        // Check if obsolete
        val isObsolete = OBSOLETE_PATTERN.containsMatchIn(testText)

        // Extract generated section (between markers)
        var generatedSection: String? = null
        var userSection: String? = null

        val beginMatch = StateProofMarkers.BEGIN_PATTERN.find(testText)
        val endMatch = StateProofMarkers.END_PATTERN.find(testText)

        if (beginMatch != null && endMatch != null && beginMatch.range.first < endMatch.range.first) {
            generatedSection = testText.substring(
                beginMatch.range.last + 1,
                endMatch.range.first
            ).trim()

            // Everything after END marker until the closing brace
            val afterEnd = testText.substring(endMatch.range.last + 1)
            // Remove the final closing brace of the function
            val lastBrace = afterEnd.lastIndexOf('}')
            userSection = if (lastBrace > 0) {
                afterEnd.substring(0, lastBrace).trim()
            } else {
                afterEnd.trim()
            }
        }

        // Extract expected transitions
        val expectedTransitions = extractExpectedTransitions(testText)

        return ParsedTest(
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

    /**
     * Extracts expected transitions from test code.
     */
    private fun extractExpectedTransitions(testText: String): List<String> {
        val match = EXPECTED_TRANSITIONS_PATTERN.find(testText) ?: return emptyList()
        val transitionsBlock = match.groupValues[1]

        return TRANSITION_STRING_PATTERN.findAll(transitionsBlock)
            .map { it.groupValues[1] }
            .toList()
    }

    /**
     * Scans a directory for test files containing StateProof tests.
     *
     * @param testDir The directory to scan
     * @param fileReader Function to read file content (platform-specific)
     * @param fileWalker Function to walk directory (platform-specific)
     * @return Map of path hash to parsed test
     */
    fun scanTestDirectory(
        testDir: String,
        fileReader: (String) -> String,
        fileWalker: (String) -> List<String>,
    ): Map<String, ParsedTest> {
        val result = mutableMapOf<String, ParsedTest>()

        for (filePath in fileWalker(testDir)) {
            if (!filePath.endsWith(".kt")) continue

            val content = fileReader(filePath)
            if (!content.contains("@StateProofGenerated")) continue

            val parsedFile = parseTestFile(filePath, content)
            for (test in parsedFile.tests) {
                val hash = test.pathHash
                if (hash != null) {
                    result[hash] = test
                }
            }
        }

        return result
    }
}
