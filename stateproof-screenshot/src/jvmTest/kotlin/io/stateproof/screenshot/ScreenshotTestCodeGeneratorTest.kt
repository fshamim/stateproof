package io.stateproof.screenshot

import io.stateproof.testgen.SimpleTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

class ScreenshotTestCodeGeneratorTest {

    @Test
    fun generateSingleTest_includesMovieCaptureAndTransitionAssertion() {
        val config = ScreenshotCodeGenConfig(
            packageName = "sample",
            testClassName = "GeneratedSampleScreenshotTest",
            machineSlug = "sample-machine",
            harnessFactoryExpression = "sample.SampleHarnessKt.createHarness()",
        )
        val testCase = SimpleTestCase(
            name = "_3_ABCD_from_Initial_to_Done",
            path = listOf("Initial", "Start", "Working", "Finish", "Done"),
            expectedTransitions = listOf(
                "Initial_Start_Working",
                "Working_Finish_Done",
            ),
            eventSequence = listOf("Start", "Finish"),
        )

        val code = ScreenshotTestCodeGenerator.generateSingleTest(
            config = config,
            testCase = testCase,
            timestamp = "2026-02-23T00:00:00Z",
        )

        assertTrue(code.contains("@StateProofScreenshotGenerated"))
        assertTrue(code.contains(StateProofScreenshotMarkers.BEGIN_EXPECTED))
        assertTrue(code.contains("paparazzi.snapshot("))
        assertTrue(code.contains("harness.eventForTransition"))
        assertTrue(code.contains("assertEquals(expectedTransitions, sm.getTransitionLog())"))
    }

    @Test
    fun generateFileFromRenderedTests_emitsRuleAndHelpers() {
        val config = ScreenshotCodeGenConfig(
            packageName = "sample",
            testClassName = "GeneratedSampleScreenshotTest",
            machineSlug = "sample-machine",
            harnessFactoryExpression = "sample.SampleHarnessKt.createHarness()",
        )
        val file = ScreenshotTestCodeGenerator.generateTestFileFromRenderedTests(
            config = config,
            renderedTests = listOf("    @Test\n    fun `dummy`() = runBlocking {\n    }"),
            timestamp = "2026-02-23T00:00:00Z",
            totalTests = 1,
        )

        assertTrue(file.contains("val paparazzi = Paparazzi()"))
        assertTrue(file.contains("private fun buildSnapshotName("))
    }
}
