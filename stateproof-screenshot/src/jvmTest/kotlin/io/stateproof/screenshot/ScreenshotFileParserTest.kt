package io.stateproof.screenshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ScreenshotFileParserTest {

    @Test
    fun parseTestFile_extractsHashFunctionAndExpectedTransitions() {
        val content = """
            package sample

            class GeneratedScreenshots {
                @StateProofScreenshotGenerated(
                    pathHash = "ABCD",
                    generatedAt = "2026-02-23T00:00:00Z",
                    schemaVersion = 1,
                    captureMode = "MOVIE",
                )
                @Test
                fun `_3_ABCD_from_Initial_to_Done`() = runBlocking {
                    ${StateProofScreenshotMarkers.BEGIN_EXPECTED}
                    val expectedTransitions = listOf(
                        "Initial_Start_Working",
                        "Working_Finish_Done",
                    )
                    ${StateProofScreenshotMarkers.END}
                }
            }
        """.trimIndent()

        val parsed = ScreenshotFileParser.parseTestFile("GeneratedScreenshots.kt", content)
        assertEquals(1, parsed.tests.size)
        val test = parsed.tests.single()
        assertEquals("ABCD", test.pathHash)
        assertEquals("_3_ABCD_from_Initial_to_Done", test.functionName)
        assertFalse(test.isObsolete)
        assertEquals(
            listOf("Initial_Start_Working", "Working_Finish_Done"),
            test.expectedTransitions,
        )
        assertNotNull(test.generatedSection)
    }
}
