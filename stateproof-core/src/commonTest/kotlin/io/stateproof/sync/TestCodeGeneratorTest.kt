package io.stateproof.sync

import io.stateproof.testgen.SimpleTestCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestCodeGeneratorTest {

    @Test
    fun generateSingleTest_includesAnnotation() {
        val config = TestCodeGenConfig(
            packageName = "com.example.test",
            testClassName = "GeneratedTest",
        )

        val testCase = SimpleTestCase(
            name = "_3_ABCD_A_ToB_B",
            path = listOf("A", "ToB", "B"),
            expectedTransitions = listOf("A_ToB_B"),
            eventSequence = listOf("ToB"),
        )

        val code = TestCodeGenerator.generateSingleTest(config, testCase, "2024-01-15T10:00:00Z")

        assertTrue(code.contains("@StateProofGenerated"))
        assertTrue(code.contains("pathHash = \"ABCD\""))
        assertTrue(code.contains("generatedAt = \"2024-01-15T10:00:00Z\""))
    }

    @Test
    fun generateSingleTest_includesMarkers() {
        val config = TestCodeGenConfig(
            packageName = "com.example.test",
            testClassName = "GeneratedTest",
        )

        val testCase = SimpleTestCase(
            name = "_3_ABCD_A_ToB_B",
            path = listOf("A", "ToB", "B"),
            expectedTransitions = listOf("A_ToB_B"),
            eventSequence = listOf("ToB"),
        )

        val code = TestCodeGenerator.generateSingleTest(config, testCase, "2024-01-15T10:00:00Z")

        assertTrue(code.contains("STATEPROOF:EXPECTED"))
        assertTrue(code.contains("STATEPROOF:END"))
    }

    @Test
    fun generateSingleTest_includesExpectedTransitions() {
        val config = TestCodeGenConfig(
            packageName = "com.example.test",
            testClassName = "GeneratedTest",
        )

        val testCase = SimpleTestCase(
            name = "_3_ABCD_A_ToB_B_ToC_C",
            path = listOf("A", "ToB", "B", "ToC", "C"),
            expectedTransitions = listOf("A_ToB_B", "B_ToC_C"),
            eventSequence = listOf("ToB", "ToC"),
        )

        val code = TestCodeGenerator.generateSingleTest(config, testCase, "2024-01-15T10:00:00Z")

        assertTrue(code.contains("\"A_ToB_B\""))
        assertTrue(code.contains("\"B_ToC_C\""))
    }

    @Test
    fun generateSingleTest_commentOnly_keepsCommentedBody() {
        val config = TestCodeGenConfig(
            packageName = "com.example.test",
            testClassName = "GeneratedTest",
            eventClassPrefix = "Events",
            generatedBodyPolicy = GeneratedBodyPolicy.COMMENT_ONLY,
            eventInvocationByName = mapOf("ToB" to "Events.ToB"),
        )

        val testCase = SimpleTestCase(
            name = "_3_ABCD_A_ToB_B",
            path = listOf("A", "ToB", "B"),
            expectedTransitions = listOf("A_ToB_B"),
            eventSequence = listOf("ToB"),
        )

        val code = TestCodeGenerator.generateSingleTest(config, testCase, "2024-01-15T10:00:00Z")

        assertTrue(code.contains("// sm.onEvent(Events.ToB)"))
        assertFalse(code.contains("STATEPROOF:MANUAL_REQUIRED"))
    }

    @Test
    fun generateSingleTest_safeAutogen_emitsExecutableBodyWhenResolvable() {
        val config = TestCodeGenConfig(
            packageName = "com.example.test",
            testClassName = "GeneratedTest",
            eventClassPrefix = "Events",
            generatedBodyPolicy = GeneratedBodyPolicy.SAFE_AUTOGEN,
            eventInvocationByName = mapOf("ToB" to "Events.ToB"),
        )

        val testCase = SimpleTestCase(
            name = "_3_ABCD_A_ToB_B",
            path = listOf("A", "ToB", "B"),
            expectedTransitions = listOf("A_ToB_B"),
            eventSequence = listOf("ToB"),
        )

        val code = TestCodeGenerator.generateSingleTest(config, testCase, "2024-01-15T10:00:00Z")

        assertTrue(code.contains("val sm = createStateMachine()"))
        assertTrue(code.contains("sm.onEvent(Events.ToB)"))
        assertTrue(code.contains("assertEquals(expectedTransitions, sm.getTransitionLog())"))
        assertFalse(code.contains("// sm.onEvent"))
        assertFalse(code.contains("STATEPROOF:MANUAL_REQUIRED"))
    }

    @Test
    fun generateSingleTest_safeAutogen_marksManualRequiredWhenInvocationMissing() {
        val config = TestCodeGenConfig(
            packageName = "com.example.test",
            testClassName = "GeneratedTest",
            eventClassPrefix = "Events",
            generatedBodyPolicy = GeneratedBodyPolicy.SAFE_AUTOGEN,
            eventInvocationByName = emptyMap(),
        )

        val testCase = SimpleTestCase(
            name = "_3_ABCD_A_ToB_B",
            path = listOf("A", "ToB", "B"),
            expectedTransitions = listOf("A_ToB_B"),
            eventSequence = listOf("ToB"),
        )

        val code = TestCodeGenerator.generateSingleTest(config, testCase, "2024-01-15T10:00:00Z")

        assertTrue(code.contains(StateProofMarkers.MANUAL_REQUIRED))
        assertTrue(code.contains("ToB: unresolved invocation expression"))
        assertTrue(code.contains("// sm.onEvent(Events.ToB)"))
    }

    @Test
    fun generateSingleTest_safeAutogen_marksManualRequiredWhenExplicitReasonPresent() {
        val config = TestCodeGenConfig(
            packageName = "com.example.test",
            testClassName = "GeneratedTest",
            eventClassPrefix = "Events",
            generatedBodyPolicy = GeneratedBodyPolicy.SAFE_AUTOGEN,
            eventInvocationByName = mapOf("ToB" to "Events.ToB"),
            manualReviewReasonsByEvent = mapOf("ToB" to listOf("guarded transition needs setup")),
        )

        val testCase = SimpleTestCase(
            name = "_3_ABCD_A_ToB_B",
            path = listOf("A", "ToB", "B"),
            expectedTransitions = listOf("A_ToB_B"),
            eventSequence = listOf("ToB"),
        )

        val code = TestCodeGenerator.generateSingleTest(config, testCase, "2024-01-15T10:00:00Z")

        assertTrue(code.contains(StateProofMarkers.MANUAL_REQUIRED))
        assertTrue(code.contains("ToB: guarded transition needs setup"))
        assertTrue(code.contains("// sm.onEvent(Events.ToB)"))
    }

    @Test
    fun generateTestFile_includesImports() {
        val config = TestCodeGenConfig(
            packageName = "com.example.test",
            testClassName = "GeneratedTest",
            additionalImports = listOf("com.example.Events"),
        )

        val testCase = SimpleTestCase(
            name = "_3_ABCD_A_ToB_B",
            path = listOf("A", "ToB", "B"),
            expectedTransitions = listOf("A_ToB_B"),
            eventSequence = listOf("ToB"),
        )

        val code = TestCodeGenerator.generateTestFile(config, listOf(testCase), "2024-01-15T10:00:00Z")

        assertTrue(code.contains("package com.example.test"))
        assertTrue(code.contains("import io.stateproof.sync.StateProofGenerated"))
        assertTrue(code.contains("import com.example.Events"))
        assertTrue(code.contains("class GeneratedTest"))
    }

    @Test
    fun generateSingleTest_usesRunBlocking() {
        val config = TestCodeGenConfig(
            packageName = "com.example.test",
            testClassName = "GeneratedTest",
            useRunBlocking = true,
        )

        val testCase = SimpleTestCase(
            name = "_3_ABCD_A_ToB_B",
            path = listOf("A", "ToB", "B"),
            expectedTransitions = listOf("A_ToB_B"),
            eventSequence = listOf("ToB"),
        )

        val code = TestCodeGenerator.generateSingleTest(config, testCase, "2024-01-15T10:00:00Z")

        assertTrue(code.contains("= runBlocking {"))
    }

    @Test
    fun generateSingleTest_usesRunTest() {
        val config = TestCodeGenConfig(
            packageName = "com.example.test",
            testClassName = "GeneratedTest",
            useRunBlocking = false,
            useRunTest = true,
        )

        val testCase = SimpleTestCase(
            name = "_3_ABCD_A_ToB_B",
            path = listOf("A", "ToB", "B"),
            expectedTransitions = listOf("A_ToB_B"),
            eventSequence = listOf("ToB"),
        )

        val code = TestCodeGenerator.generateSingleTest(config, testCase, "2024-01-15T10:00:00Z")

        assertTrue(code.contains("= runTest {"))
    }

    @Test
    fun updateExistingTest_preservesUserCode() {
        val existingTest = TestFileParser.ParsedTest(
            fullText = """
                @Test
                fun test() = runBlocking {
                    // ▼▼▼ STATEPROOF:EXPECTED - Do not edit below this line ▼▼▼
                    val expectedTransitions = listOf("OLD_TRANSITION")
                    // ▲▲▲ STATEPROOF:END ▲▲▲

                    // MY CUSTOM USER CODE
                    val sm = myCustomFactory()
                    sm.doSomething()
                }
            """.trimIndent(),
            pathHash = "ABCD",
            functionName = "test",
            generatedSection = "val expectedTransitions = listOf(\"OLD_TRANSITION\")",
            userSection = "// MY CUSTOM USER CODE\nval sm = myCustomFactory()\nsm.doSomething()",
            isObsolete = false,
            expectedTransitions = listOf("OLD_TRANSITION"),
            startLine = 0,
        )

        val updated = TestCodeGenerator.updateExistingTest(
            existingTest,
            newTransitions = listOf("NEW_TRANSITION_A", "NEW_TRANSITION_B"),
            timestamp = "2024-01-16T10:00:00Z",
        )

        assertTrue(updated.contains("NEW_TRANSITION_A"))
        assertTrue(updated.contains("NEW_TRANSITION_B"))
        assertTrue(updated.contains("MY CUSTOM USER CODE"))
        assertTrue(updated.contains("myCustomFactory"))
    }

    @Test
    fun markTestObsolete_addsAnnotation() {
        val existingTest = TestFileParser.ParsedTest(
            fullText = """
                @StateProofGenerated(pathHash = "ABCD", generatedAt = "2024-01-15T10:00:00Z")
                @Test
                fun test() {
                }
            """.trimIndent(),
            pathHash = "ABCD",
            functionName = "test",
            generatedSection = null,
            userSection = null,
            isObsolete = false,
            expectedTransitions = listOf("A_B_C"),
            startLine = 0,
        )

        val marked = TestCodeGenerator.markTestObsolete(
            existingTest,
            reason = "State 'A' was removed",
            timestamp = "2024-01-16T10:00:00Z",
        )

        assertTrue(marked.contains("@StateProofObsolete"))
        assertTrue(marked.contains("State 'A' was removed"))
        assertTrue(marked.contains("@Ignore"))
    }
}
