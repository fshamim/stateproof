package io.stateproof.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestFileParserTest {

    @Test
    fun parseTestFile_extractsPathHash() {
        val content = """
            package com.example.test

            import kotlin.test.Test

            class MyTest {
                @StateProofGenerated(
                    pathHash = "ABCD1234",
                    generatedAt = "2024-01-15T10:00:00Z",
                    schemaVersion = 1,
                )
                @Test
                fun `_3_ABCD1234_A_ToB_B`() = runBlocking {
                    val expectedTransitions = listOf(
                        "A_ToB_B",
                    )
                }
            }
        """.trimIndent()

        val result = TestFileParser.parseTestFile("test.kt", content)

        assertEquals(1, result.tests.size)
        assertEquals("ABCD1234", result.tests[0].pathHash)
    }

    @Test
    fun parseTestFile_extractsFunctionName() {
        val content = """
            package com.example.test

            class MyTest {
                @StateProofGenerated(pathHash = "ABCD1234", generatedAt = "2024-01-15T10:00:00Z")
                @Test
                fun `my_test_function`() {
                    // test body
                }
            }
        """.trimIndent()

        val result = TestFileParser.parseTestFile("test.kt", content)

        assertEquals("my_test_function", result.tests[0].functionName)
    }

    @Test
    fun parseTestFile_extractsExpectedTransitions() {
        val content = """
            package com.example.test

            class MyTest {
                @StateProofGenerated(pathHash = "ABCD1234", generatedAt = "2024-01-15T10:00:00Z")
                @Test
                fun test() = runBlocking {
                    val expectedTransitions = listOf(
                        "A_ToB_B",
                        "B_ToC_C",
                    )
                    // user code
                }
            }
        """.trimIndent()

        val result = TestFileParser.parseTestFile("test.kt", content)

        assertEquals(listOf("A_ToB_B", "B_ToC_C"), result.tests[0].expectedTransitions)
    }

    @Test
    fun parseTestFile_extractsGeneratedSection() {
        val content = """
            package com.example.test

            class MyTest {
                @Test
                fun test() = runBlocking {
                    // ▼▼▼ STATEPROOF:EXPECTED - Do not edit below this line ▼▼▼
                    val expectedTransitions = listOf(
                        "A_ToB_B",
                    )
                    // ▲▲▲ STATEPROOF:END ▲▲▲

                    // user code here
                    val sm = createStateMachine()
                }
            }
        """.trimIndent()

        val result = TestFileParser.parseTestFile("test.kt", content)

        assertNotNull(result.tests[0].generatedSection)
        assertTrue(result.tests[0].generatedSection!!.contains("expectedTransitions"))
    }

    @Test
    fun parseTestFile_extractsUserSection() {
        val content = """
            package com.example.test

            class MyTest {
                @Test
                fun test() = runBlocking {
                    // ▼▼▼ STATEPROOF:EXPECTED - Do not edit below this line ▼▼▼
                    val expectedTransitions = listOf("A_ToB_B")
                    // ▲▲▲ STATEPROOF:END ▲▲▲

                    val sm = createStateMachine()
                    sm.onEvent(Events.ToB)
                }
            }
        """.trimIndent()

        val result = TestFileParser.parseTestFile("test.kt", content)

        assertNotNull(result.tests[0].userSection)
        assertTrue(result.tests[0].userSection!!.contains("createStateMachine"))
    }

    @Test
    fun parseTestFile_handlesMultipleTests() {
        val content = """
            package com.example.test

            class MyTest {
                @StateProofGenerated(pathHash = "HASH1", generatedAt = "2024-01-15T10:00:00Z")
                @Test
                fun test1() {
                }

                @StateProofGenerated(pathHash = "HASH2", generatedAt = "2024-01-15T10:00:00Z")
                @Test
                fun test2() {
                }

                @StateProofGenerated(pathHash = "HASH3", generatedAt = "2024-01-15T10:00:00Z")
                @Test
                fun test3() {
                }
            }
        """.trimIndent()

        val result = TestFileParser.parseTestFile("test.kt", content)

        assertEquals(3, result.tests.size)
        assertEquals("HASH1", result.tests[0].pathHash)
        assertEquals("HASH2", result.tests[1].pathHash)
        assertEquals("HASH3", result.tests[2].pathHash)
    }

    @Test
    fun parseTestFile_detectsObsoleteAnnotation() {
        val content = """
            package com.example.test

            class MyTest {
                @StateProofObsolete(reason = "Path removed", markedAt = "2024-01-15", originalPath = "A_B")
                @StateProofGenerated(pathHash = "ABCD1234", generatedAt = "2024-01-15T10:00:00Z")
                @Test
                fun obsoleteTest() {
                    // test body
                    val x = 1
                }
            }
        """.trimIndent()

        val result = TestFileParser.parseTestFile("test.kt", content)

        assertEquals(1, result.tests.size, "Should parse one test")
        assertTrue(result.tests[0].isObsolete, "Should detect obsolete annotation")
    }
}
