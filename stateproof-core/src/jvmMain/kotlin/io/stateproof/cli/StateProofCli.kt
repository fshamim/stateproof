package io.stateproof.cli

import io.stateproof.graph.StateInfo
import io.stateproof.sync.TestCodeGenConfig
import io.stateproof.sync.TestCodeGenerator
import io.stateproof.sync.TestFileParser
import io.stateproof.sync.TestSyncEngine
import io.stateproof.testgen.SimplePathEnumerator
import io.stateproof.testgen.TestGenConfig
import java.io.File
import java.time.Instant

/**
 * Command-line interface for StateProof test generation and sync.
 *
 * Usage:
 * ```
 * # Generate tests from a state machine info provider
 * java -cp <classpath> io.stateproof.cli.StateProofCli generate \
 *   --provider com.example.MainStateMachineKt#getMainStateMachineInfo \
 *   --initial-state Initial \
 *   --output-dir src/test/kotlin/generated \
 *   --package com.example.test \
 *   --class-name GeneratedMainStateMachineTest
 *
 * # Sync existing tests with current state machine
 * java -cp <classpath> io.stateproof.cli.StateProofCli sync \
 *   --provider com.example.MainStateMachineKt#getMainStateMachineInfo \
 *   --initial-state Initial \
 *   --test-dir src/test/kotlin/generated
 *
 * # Dry run (preview changes without writing)
 * java -cp <classpath> io.stateproof.cli.StateProofCli sync \
 *   --provider ... --test-dir ... --dry-run
 *
 * # Show status
 * java -cp <classpath> io.stateproof.cli.StateProofCli status \
 *   --provider ... --test-dir ...
 *
 * # Clean obsolete tests
 * java -cp <classpath> io.stateproof.cli.StateProofCli clean-obsolete \
 *   --test-dir src/test/kotlin/generated --auto-delete
 * ```
 */
object StateProofCli {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }

        try {
            when (args[0]) {
                "generate" -> runGenerate(args.drop(1))
                "sync" -> runSync(args.drop(1))
                "status" -> runStatus(args.drop(1))
                "clean-obsolete" -> runCleanObsolete(args.drop(1))
                "help", "--help", "-h" -> printUsage()
                else -> {
                    System.err.println("Unknown command: ${args[0]}")
                    printUsage()
                    System.exit(1)
                }
            }
        } catch (e: Exception) {
            System.err.println("ERROR: ${e.message}")
            if (System.getenv("STATEPROOF_DEBUG") != null) {
                e.printStackTrace()
            }
            System.exit(1)
        }
    }

    private fun printUsage() {
        println("""
            |StateProof CLI - State Machine Test Generation & Sync
            |
            |Usage: stateproof <command> [options]
            |
            |Commands:
            |  generate         Generate test file from state machine
            |  sync             Sync existing tests with current state machine
            |  status           Show sync status without modifying files
            |  clean-obsolete   Remove obsolete tests
            |  help             Show this help message
            |
            |Common Options:
            |  --provider <fqn>         State machine info provider (required for generate/sync/status)
            |                           Format: com.package.ClassName#methodName
            |                           For top-level Kotlin functions: com.package.FileKt#functionName
            |  --is-factory              Provider is a factory (returns StateMachine, auto-extracts StateInfo)
            |  --initial-state <name>   Initial state name (default: "Initial")
            |  --max-visits <n>         Max visits per state during enumeration (default: 2)
            |  --max-depth <n>          Max path depth, -1 for unlimited (default: -1)
            |
            |Generate Options:
            |  --output-dir <dir>       Output directory for generated test file (required)
            |  --output-file <file>     Exact output file path (alternative to --output-dir)
            |  --package <name>         Package name for generated tests (required)
            |  --class-name <name>      Test class name (default: GeneratedStateMachineTest)
            |  --factory <expr>         State machine factory expression (default: createStateMachine())
            |  --event-prefix <name>    Event class prefix (default: Events)
            |  --imports <list>         Comma-separated additional imports
            |  --class-annotations <a>  Pipe-separated class annotations (e.g., "@RunWith(AndroidJUnit4::class)")
            |  --use-run-test           Use runTest instead of runBlocking for coroutine wrapper
            |
            |Sync Options:
            |  --test-dir <dir>         Directory containing test files (required)
            |  --test-file <file>       Single test file to sync (alternative to --test-dir)
            |  --dry-run                Preview changes without writing files
            |  --report <file>          Write sync report to file
            |
            |Clean Obsolete Options:
            |  --test-dir <dir>         Directory containing test files (required)
            |  --auto-delete            Delete without confirmation
            |
            |Examples:
            |  # Generate tests for iCages MainStateMachine
            |  stateproof generate \
            |    --provider com.mubea.icages.main.MainStateMachineKt#getMainStateMachineInfo \
            |    --initial-state Initial \
            |    --output-dir src/test/kotlin/generated \
            |    --package com.mubea.icages \
            |    --class-name GeneratedMainStateMachineTest
            |
            |  # Sync existing tests
            |  stateproof sync \
            |    --provider com.mubea.icages.main.MainStateMachineKt#getMainStateMachineInfo \
            |    --initial-state Initial \
            |    --test-dir src/test/kotlin/generated
        """.trimMargin())
    }

    // ========================================================================
    // GENERATE command
    // ========================================================================

    private fun runGenerate(args: List<String>) {
        val provider = args.requireArg("--provider")
        val isFactory = args.contains("--is-factory")
        val initialState = args.getArgValue("--initial-state") ?: "Initial"
        val outputDir = args.getArgValue("--output-dir")
        val outputFile = args.getArgValue("--output-file")
        val packageName = args.requireArg("--package")
        val className = args.getArgValue("--class-name") ?: "GeneratedStateMachineTest"
        val factory = args.getArgValue("--factory") ?: "createStateMachine()"
        val eventPrefix = args.getArgValue("--event-prefix") ?: "Events"
        val importsStr = args.getArgValue("--imports")
        val classAnnotationsStr = args.getArgValue("--class-annotations")
        val useRunTest = args.contains("--use-run-test")
        val maxVisits = args.getArgValue("--max-visits")?.toIntOrNull() ?: 2
        val maxDepth = args.getArgValue("--max-depth")?.toIntOrNull() ?: -1
        val reportPath = args.getArgValue("--report")

        if (outputDir == null && outputFile == null) {
            throw IllegalArgumentException("--output-dir or --output-file required")
        }

        val additionalImports = importsStr?.split(",")?.map { it.trim() } ?: emptyList()
        val classAnnotations = classAnnotationsStr?.split("|")?.map { it.trim() } ?: emptyList()

        println("=".repeat(60))
        println("StateProof Generate")
        println("=".repeat(60))
        println("Provider: $provider")
        println("Provider mode: ${if (isFactory) "factory" else "info provider"}")
        println("Initial state: $initialState")
        println("Max visits per state: $maxVisits")
        println("Max depth: ${if (maxDepth == -1) "unlimited" else maxDepth}")
        if (classAnnotations.isNotEmpty()) {
            println("Class annotations: $classAnnotations")
        }
        if (useRunTest) {
            println("Using runTest (coroutines test)")
        }
        println()

        // Load state info
        println("Loading state machine info...")
        val stateInfoMap = if (isFactory) {
            StateInfoLoader.loadFromFactory(provider)
        } else {
            StateInfoLoader.load(provider)
        }
        println("Loaded ${stateInfoMap.size} states")
        println()

        // Generate
        val config = TestGenConfig(
            maxVisitsPerState = maxVisits,
            maxPathDepth = if (maxDepth == -1) null else maxDepth,
        )

        val codeGenConfig = TestCodeGenConfig(
            packageName = packageName,
            testClassName = className,
            stateMachineFactory = factory,
            eventClassPrefix = eventPrefix,
            additionalImports = additionalImports,
            classAnnotations = classAnnotations,
            useRunTest = useRunTest,
            useRunBlocking = !useRunTest,
        )

        val result = StateProofSync.generate(
            stateInfoMap = stateInfoMap,
            initialState = initialState,
            outputFile = if (outputFile != null) File(outputFile) else null,
            codeGenConfig = codeGenConfig,
            testGenConfig = config,
        )

        // Write to output dir if specified
        val targetFile = if (outputFile != null) {
            File(outputFile)
        } else {
            val dir = File(outputDir!!)
            dir.mkdirs()
            File(dir, "$className.kt")
        }

        targetFile.parentFile?.mkdirs()
        targetFile.writeText(result)

        println("Generated ${result.lines().count { it.contains("@Test") }} tests")
        println("Written to: ${targetFile.absolutePath}")

        // Write report
        if (reportPath != null) {
            val reportFile = File(reportPath)
            reportFile.parentFile?.mkdirs()
            reportFile.writeText(buildString {
                appendLine("StateProof Generate Report")
                appendLine("=".repeat(60))
                appendLine("Timestamp: ${Instant.now()}")
                appendLine("Provider: $provider")
                appendLine("Initial state: $initialState")
                appendLine("States: ${stateInfoMap.size}")
                appendLine("Tests generated: ${result.lines().count { it.contains("@Test") }}")
                appendLine("Output: ${targetFile.absolutePath}")
            })
            println("Report: $reportPath")
        }
    }

    // ========================================================================
    // SYNC command
    // ========================================================================

    private fun runSync(args: List<String>) {
        val provider = args.requireArg("--provider")
        val isFactory = args.contains("--is-factory")
        val initialState = args.getArgValue("--initial-state") ?: "Initial"
        val testDir = args.getArgValue("--test-dir")
        val testFile = args.getArgValue("--test-file")
        val dryRun = args.contains("--dry-run")
        val maxVisits = args.getArgValue("--max-visits")?.toIntOrNull() ?: 2
        val maxDepth = args.getArgValue("--max-depth")?.toIntOrNull() ?: -1
        val reportPath = args.getArgValue("--report")
        val importsStr = args.getArgValue("--imports")
        val classAnnotationsStr = args.getArgValue("--class-annotations")
        val useRunTest = args.contains("--use-run-test")
        val packageName = args.getArgValue("--package")
        val className = args.getArgValue("--class-name")
        val factory = args.getArgValue("--factory") ?: "createStateMachine()"
        val eventPrefix = args.getArgValue("--event-prefix") ?: "Events"

        if (testDir == null && testFile == null) {
            throw IllegalArgumentException("--test-dir or --test-file required")
        }

        val additionalImports = importsStr?.split(",")?.map { it.trim() } ?: emptyList()
        val classAnnotations = classAnnotationsStr?.split("|")?.map { it.trim() } ?: emptyList()

        println("=".repeat(60))
        println("StateProof Sync ${if (dryRun) "(DRY RUN)" else ""}")
        println("=".repeat(60))
        println("Provider: $provider")
        println("Provider mode: ${if (isFactory) "factory" else "info provider"}")
        println("Initial state: $initialState")
        println()

        // Load state info
        println("Loading state machine info...")
        val stateInfoMap = if (isFactory) {
            StateInfoLoader.loadFromFactory(provider)
        } else {
            StateInfoLoader.load(provider)
        }
        println("Loaded ${stateInfoMap.size} states")
        println()

        // Run sync
        val config = TestGenConfig(
            maxVisitsPerState = maxVisits,
            maxPathDepth = if (maxDepth == -1) null else maxDepth,
        )

        // Build code gen config for creating new test files during sync
        val codeGenConfig = if (packageName != null || classAnnotations.isNotEmpty() || useRunTest) {
            TestCodeGenConfig(
                packageName = packageName ?: "",
                testClassName = className ?: "",
                stateMachineFactory = factory,
                eventClassPrefix = eventPrefix,
                additionalImports = additionalImports,
                classAnnotations = classAnnotations,
                useRunTest = useRunTest,
                useRunBlocking = !useRunTest,
            )
        } else null

        val result = StateProofSync.sync(
            stateInfoMap = stateInfoMap,
            initialState = initialState,
            testDir = if (testDir != null) File(testDir) else null,
            testFile = if (testFile != null) File(testFile) else null,
            dryRun = dryRun,
            config = config,
            codeGenConfig = codeGenConfig,
        )

        // Print report
        println(result.report.summary())

        // Write report to file
        if (reportPath != null) {
            val reportFile = File(reportPath)
            reportFile.parentFile?.mkdirs()
            reportFile.writeText(buildString {
                appendLine(result.report.summary())
                appendLine()
                appendLine("Files modified: ${result.filesModified.size}")
                result.filesModified.forEach { appendLine("  - ${it.absolutePath}") }
                appendLine("Files created: ${result.filesCreated.size}")
                result.filesCreated.forEach { appendLine("  - ${it.absolutePath}") }
            })
            println("Report: $reportPath")
        }
    }

    // ========================================================================
    // STATUS command
    // ========================================================================

    private fun runStatus(args: List<String>) {
        val provider = args.requireArg("--provider")
        val initialState = args.getArgValue("--initial-state") ?: "Initial"
        val testDir = args.getArgValue("--test-dir")
        val testFile = args.getArgValue("--test-file")
        val maxVisits = args.getArgValue("--max-visits")?.toIntOrNull() ?: 2
        val maxDepth = args.getArgValue("--max-depth")?.toIntOrNull() ?: -1

        if (testDir == null && testFile == null) {
            throw IllegalArgumentException("--test-dir or --test-file required")
        }

        println("=".repeat(60))
        println("StateProof Status")
        println("=".repeat(60))
        println()

        // Load state info
        val stateInfoMap = StateInfoLoader.load(provider)
        println("State machine: $provider")
        println("States: ${stateInfoMap.size}")

        // Count total transitions
        val totalTransitions = stateInfoMap.values.sumOf { it.transitions.size }
        println("Transitions: $totalTransitions")
        println()

        // Enumerate paths
        val config = TestGenConfig(
            maxVisitsPerState = maxVisits,
            maxPathDepth = if (maxDepth == -1) null else maxDepth,
        )
        val enumerator = SimplePathEnumerator(stateInfoMap, initialState, config)
        val testCases = enumerator.generateTestCases()
        println("Expected test paths: ${testCases.size}")
        println()

        // Scan existing tests
        val testFiles = if (testFile != null) {
            listOf(File(testFile))
        } else {
            val dir = File(testDir!!)
            if (dir.exists()) {
                dir.walkTopDown()
                    .filter { it.extension == "kt" && it.readText().contains("@Test") }
                    .toList()
            } else {
                emptyList()
            }
        }

        val existingTests = mutableMapOf<String, TestFileParser.ParsedTest>()
        var obsoleteCount = 0
        for (file in testFiles) {
            val content = file.readText()
            val parsed = TestFileParser.parseTestFile(file.absolutePath, content)
            for (test in parsed.tests) {
                if (test.isObsolete) {
                    obsoleteCount++
                }
                val hash = test.pathHash
                if (hash != null) {
                    existingTests[hash] = test
                }
            }
        }

        println("Test files found: ${testFiles.size}")
        println("Existing tests with path info: ${existingTests.size}")
        println("Obsolete tests: $obsoleteCount")
        println()

        // Run sync to get classification
        if (existingTests.isNotEmpty()) {
            val engine = TestSyncEngine(config)
            val report = engine.sync(
                stateInfoMap = stateInfoMap,
                initialState = initialState,
                existingTests = existingTests,
                currentTimestamp = Instant.now().toString(),
            )
            println(report.summary())
        } else {
            println("No existing StateProof tests found.")
            println("Run 'stateproof generate' or './gradlew stateproofGenerate' to create tests.")
        }
    }

    // ========================================================================
    // CLEAN-OBSOLETE command
    // ========================================================================

    private fun runCleanObsolete(args: List<String>) {
        val testDir = args.requireArg("--test-dir")
        val autoDelete = args.contains("--auto-delete")

        println("=".repeat(60))
        println("StateProof Clean Obsolete Tests")
        println("=".repeat(60))
        println()

        val testDirFile = File(testDir)
        if (!testDirFile.exists()) {
            println("Test directory not found: $testDir")
            return
        }

        // Find files with obsolete tests
        data class ObsoleteInfo(val file: File, val test: TestFileParser.ParsedTest)

        val obsoleteTests = mutableListOf<ObsoleteInfo>()

        testDirFile.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val content = file.readText()
                if (content.contains("@StateProofObsolete")) {
                    val parsed = TestFileParser.parseTestFile(file.absolutePath, content)
                    for (test in parsed.tests) {
                        if (test.isObsolete) {
                            obsoleteTests.add(ObsoleteInfo(file, test))
                        }
                    }
                }
            }

        if (obsoleteTests.isEmpty()) {
            println("No obsolete tests found.")
            return
        }

        println("Found ${obsoleteTests.size} obsolete test(s):")
        println()

        obsoleteTests.forEachIndexed { index, info ->
            println("${index + 1}. ${info.test.functionName}")
            println("   File: ${info.file.name}")
            if (info.test.expectedTransitions.isNotEmpty()) {
                println("   Path: ${info.test.expectedTransitions.take(3).joinToString(" -> ")}...")
            }
            println()
        }

        if (autoDelete) {
            println("Auto-deleting obsolete tests...")
            deleteObsoleteTests(obsoleteTests.map { it.file to it.test })
            println("Done. ${obsoleteTests.size} obsolete test(s) removed.")
        } else {
            println("Run with --auto-delete to remove these tests.")
            println("Or review and delete manually in your IDE.")
        }
    }

    /**
     * Removes obsolete test functions from their files.
     */
    private fun deleteObsoleteTests(tests: List<Pair<File, TestFileParser.ParsedTest>>) {
        // Group by file
        val byFile = tests.groupBy({ it.first }, { it.second })

        for ((file, obsoleteTests) in byFile) {
            var content = file.readText()
            for (test in obsoleteTests) {
                // Remove the test function text from the file
                content = content.replace(test.fullText, "")
            }
            // Clean up multiple blank lines
            content = content.replace(Regex("\n{3,}"), "\n\n")
            file.writeText(content)
            println("  Updated: ${file.name} (removed ${obsoleteTests.size} test(s))")
        }
    }

    // ========================================================================
    // Argument parsing helpers
    // ========================================================================

    private fun List<String>.getArgValue(name: String): String? {
        val index = indexOf(name)
        return if (index >= 0 && index < size - 1) get(index + 1) else null
    }

    private fun List<String>.requireArg(name: String): String {
        return getArgValue(name)
            ?: throw IllegalArgumentException("Required argument '$name' not provided")
    }
}

/**
 * Programmatic API for StateProof sync and generate operations.
 *
 * Use this directly from your test code when you don't want to use the Gradle plugin:
 * ```kotlin
 * val stateInfo = getMainStateMachineInfo()
 * val code = StateProofSync.generate(
 *     stateInfoMap = stateInfo,
 *     initialState = "Initial",
 *     outputFile = File("src/test/kotlin/generated/GeneratedTest.kt"),
 *     codeGenConfig = TestCodeGenConfig(
 *         packageName = "com.example",
 *         testClassName = "GeneratedTest",
 *     ),
 * )
 * ```
 */
object StateProofSync {

    data class SyncResult(
        val report: io.stateproof.sync.SyncReport,
        val filesModified: List<File>,
        val filesCreated: List<File>,
    )

    /**
     * Generates a complete test file from a state machine definition.
     *
     * @param stateInfoMap Map of state names to their info
     * @param initialState Name of the initial state
     * @param outputFile Optional file to write to (if null, only returns the code)
     * @param codeGenConfig Configuration for code generation
     * @param testGenConfig Configuration for path enumeration
     * @return Generated test code as a string
     */
    fun generate(
        stateInfoMap: Map<String, StateInfo>,
        initialState: String,
        outputFile: File? = null,
        codeGenConfig: TestCodeGenConfig,
        testGenConfig: TestGenConfig = TestGenConfig.DEFAULT,
    ): String {
        val enumerator = SimplePathEnumerator(
            stateInfoMap = stateInfoMap,
            initialState = initialState,
            config = testGenConfig,
        )

        val testCases = enumerator.generateTestCases()
        val timestamp = Instant.now().toString()
        val code = TestCodeGenerator.generateTestFile(codeGenConfig, testCases, timestamp)

        if (outputFile != null) {
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(code)
        }

        return code
    }

    /**
     * Syncs existing tests with the current state machine definition.
     *
     * This method:
     * 1. Enumerates all paths from the current state machine
     * 2. Scans existing test files for @StateProofGenerated annotations
     * 3. Classifies each test as NEW, UNCHANGED, MODIFIED, or OBSOLETE
     * 4. When not in dry-run mode:
     *    - NEW tests: generates and appends to the test file
     *    - MODIFIED tests: updates expected transitions, preserves user code
     *    - OBSOLETE tests: marks with @StateProofObsolete and @Disabled
     *
     * @param stateInfoMap The current state machine definition
     * @param initialState The initial state name
     * @param testDir Directory containing test files
     * @param testFile Single test file (alternative to testDir)
     * @param dryRun If true, report changes without writing files
     * @param config Test generation configuration
     * @return SyncResult with report and list of modified/created files
     */
    fun sync(
        stateInfoMap: Map<String, StateInfo>,
        initialState: String,
        testDir: File? = null,
        testFile: File? = null,
        dryRun: Boolean = false,
        config: TestGenConfig = TestGenConfig.DEFAULT,
        codeGenConfig: TestCodeGenConfig? = null,
    ): SyncResult {
        require(testDir != null || testFile != null) {
            "Either testDir or testFile must be provided"
        }

        // Find test files
        val testFiles = if (testFile != null) {
            listOf(testFile)
        } else {
            if (testDir!!.exists()) {
                testDir.walkTopDown()
                    .filter { it.extension == "kt" && it.readText().contains("@Test") }
                    .toList()
            } else {
                emptyList()
            }
        }

        // Parse existing tests
        val existingTests = mutableMapOf<String, TestFileParser.ParsedTest>()
        val testsByFile = mutableMapOf<File, TestFileParser.ParsedTestFile>()

        for (file in testFiles) {
            val content = file.readText()
            val parsed = TestFileParser.parseTestFile(file.absolutePath, content)
            testsByFile[file] = parsed
            for (test in parsed.tests) {
                val hash = test.pathHash
                if (hash != null) {
                    existingTests[hash] = test
                } else if (test.expectedTransitions.isNotEmpty()) {
                    val legacyHash = test.expectedTransitions.hashCode().toString(16).uppercase()
                    existingTests[legacyHash] = test
                }
            }
        }

        // Run sync engine
        val engine = TestSyncEngine(config)
        val report = engine.sync(
            stateInfoMap = stateInfoMap,
            initialState = initialState,
            existingTests = existingTests,
            currentTimestamp = Instant.now().toString(),
        )

        val filesModified = mutableListOf<File>()
        val filesCreated = mutableListOf<File>()

        if (!dryRun) {
            val timestamp = Instant.now().toString()

            // Handle MODIFIED tests: update expected transitions in existing files
            for (result in report.modifiedTests) {
                val existing = result.existingTest ?: continue
                val newTransitions = result.newTransitions ?: continue

                // Find the file containing this test
                val fileEntry = testsByFile.entries.find { (_, parsed) ->
                    parsed.tests.any { it.pathHash == existing.pathHash }
                } ?: continue

                val (file, _) = fileEntry
                var content = file.readText()

                val updatedTest = TestCodeGenerator.updateExistingTest(
                    existingTest = existing,
                    newTransitions = newTransitions,
                    timestamp = timestamp,
                )
                content = content.replace(existing.fullText, updatedTest)
                file.writeText(content)

                if (file !in filesModified) {
                    filesModified.add(file)
                }
            }

            // Handle OBSOLETE tests: mark with @StateProofObsolete
            for (result in report.obsoleteTests) {
                val existing = result.existingTest ?: continue

                val fileEntry = testsByFile.entries.find { (_, parsed) ->
                    parsed.tests.any { it.pathHash == existing.pathHash || it.functionName == existing.functionName }
                } ?: continue

                val (file, _) = fileEntry
                var content = file.readText()

                val obsoleteTest = TestCodeGenerator.markTestObsolete(
                    existingTest = existing,
                    reason = "Path no longer exists in state machine",
                    timestamp = timestamp,
                )
                content = content.replace(existing.fullText, obsoleteTest)
                file.writeText(content)

                if (file !in filesModified) {
                    filesModified.add(file)
                }
            }

            // Handle NEW tests: append to existing file or create new file
            if (report.newTests.isNotEmpty()) {
                // Find existing test file to append to, or create a new one
                val targetFile = testFiles.firstOrNull() ?: run {
                    val dir = testDir ?: testFile?.parentFile ?: File(".")
                    File(dir, "GeneratedStateMachineTest.kt")
                }

                if (targetFile.exists()) {
                    // Append new tests before the closing brace of the class
                    var content = targetFile.readText()
                    val lastBrace = content.lastIndexOf('}')

                    if (lastBrace > 0) {
                        val singleTestConfig = codeGenConfig ?: TestCodeGenConfig(
                            packageName = "",
                            testClassName = "",
                        )
                        val newTestsCode = buildString {
                            for (result in report.newTests) {
                                val testCase = result.testCase ?: continue
                                appendLine()
                                append(TestCodeGenerator.generateSingleTest(
                                    config = singleTestConfig,
                                    testCase = testCase,
                                    timestamp = timestamp,
                                ))
                            }
                        }

                        content = content.substring(0, lastBrace) +
                            newTestsCode +
                            content.substring(lastBrace)
                        targetFile.writeText(content)
                        filesModified.add(targetFile)
                    }
                } else {
                    // Create a new file - use provided config or infer
                    val newFileConfig = codeGenConfig?.let {
                        it.copy(
                            packageName = it.packageName.ifBlank { inferPackageName(targetFile) },
                            testClassName = it.testClassName.ifBlank { targetFile.nameWithoutExtension },
                        )
                    } ?: TestCodeGenConfig(
                        packageName = inferPackageName(targetFile),
                        testClassName = targetFile.nameWithoutExtension,
                    )

                    val testCases = report.newTests.mapNotNull { it.testCase }
                    val code = TestCodeGenerator.generateTestFile(newFileConfig, testCases, timestamp)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(code)
                    filesCreated.add(targetFile)
                }
            }
        }

        return SyncResult(
            report = report,
            filesModified = filesModified,
            filesCreated = filesCreated,
        )
    }

    /**
     * Infers package name from a file's path based on common source set conventions.
     */
    private fun inferPackageName(file: File): String {
        val path = file.absolutePath
        val sourceRoots = listOf(
            "src/test/kotlin/",
            "src/test/java/",
            "src/androidTest/kotlin/",
            "src/androidTest/java/",
            "src/main/kotlin/",
            "src/main/java/",
        )

        for (root in sourceRoots) {
            val index = path.indexOf(root)
            if (index >= 0) {
                val relative = path.substring(index + root.length)
                val dir = relative.substringBeforeLast('/')
                return dir.replace('/', '.')
            }
        }

        return "generated"
    }
}
