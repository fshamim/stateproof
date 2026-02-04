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
 * Command-line interface for StateProof sync operations.
 *
 * Usage:
 * ```
 * java -jar stateproof.jar sync --state-info <file> --test-dir <dir> [--dry-run]
 * java -jar stateproof.jar generate --state-info <file> --output <file>
 * java -jar stateproof.jar clean-obsolete --test-dir <dir>
 * ```
 *
 * For Gradle integration, add to your build.gradle.kts:
 * ```kotlin
 * tasks.register<JavaExec>("stateproofSync") {
 *     classpath = sourceSets["main"].runtimeClasspath
 *     mainClass.set("io.stateproof.cli.StateProofCli")
 *     args("sync", "--state-info", "build/stateproof/state-info.json", "--test-dir", "src/test/kotlin")
 * }
 * ```
 */
object StateProofCli {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }

        when (args[0]) {
            "sync" -> runSync(args.drop(1))
            "sync-dry-run" -> runSync(args.drop(1), dryRun = true)
            "generate" -> runGenerate(args.drop(1))
            "clean-obsolete" -> runCleanObsolete(args.drop(1))
            "help", "--help", "-h" -> printUsage()
            else -> {
                println("Unknown command: ${args[0]}")
                printUsage()
            }
        }
    }

    private fun printUsage() {
        println("""
            |StateProof CLI - Test Sync & Generation
            |
            |Usage: stateproof <command> [options]
            |
            |Commands:
            |  sync              Sync tests with current state machine
            |  sync-dry-run     Preview sync changes without writing
            |  generate         Generate new test file
            |  clean-obsolete   Interactive cleanup of obsolete tests
            |  help             Show this help message
            |
            |Sync Options:
            |  --test-dir <dir>       Directory containing test files (required)
            |  --test-file <file>     Single test file to sync (alternative to --test-dir)
            |  --initial-state <name> Initial state name (default: "Initial")
            |  --output-report <file> Write sync report to file
            |
            |Generate Options:
            |  --output <file>        Output test file path (required)
            |  --package <name>       Package name for generated tests
            |  --class-name <name>    Test class name
            |  --initial-state <name> Initial state name (default: "Initial")
            |
            |Note: State machine info must be provided programmatically.
            |See documentation for integration examples.
        """.trimMargin())
    }

    /**
     * Run sync operation with the given arguments.
     *
     * Note: In a real implementation, the stateInfoMap would be loaded from the
     * compiled state machine. For now, this serves as a template.
     */
    private fun runSync(args: List<String>, dryRun: Boolean = false) {
        val testDir = args.getArgValue("--test-dir")
        val testFile = args.getArgValue("--test-file")
        val initialState = args.getArgValue("--initial-state") ?: "Initial"
        val outputReport = args.getArgValue("--output-report")

        if (testDir == null && testFile == null) {
            println("Error: --test-dir or --test-file required")
            return
        }

        println("=".repeat(60))
        println("StateProof Sync ${if (dryRun) "(DRY RUN)" else ""}")
        println("=".repeat(60))

        // Find test files
        val testFiles = if (testFile != null) {
            listOf(File(testFile))
        } else {
            File(testDir!!).walkTopDown()
                .filter { it.extension == "kt" && it.readText().contains("@Test") }
                .toList()
        }

        println("Found ${testFiles.size} test file(s)")

        // Parse existing tests
        val existingTests = mutableMapOf<String, TestFileParser.ParsedTest>()
        for (file in testFiles) {
            val content = file.readText()
            val parsed = TestFileParser.parseTestFile(file.absolutePath, content)
            for (test in parsed.tests) {
                val hash = test.pathHash
                if (hash != null) {
                    existingTests[hash] = test
                } else if (test.expectedTransitions.isNotEmpty()) {
                    // For legacy tests, use hash of transitions
                    val legacyHash = test.expectedTransitions.hashCode().toString(16).uppercase()
                    existingTests[legacyHash] = test
                }
            }
        }

        println("Found ${existingTests.size} existing test(s) with path info")
        println()

        // Note: In a real integration, stateInfoMap comes from the compiled state machine
        println("Note: To perform actual sync, provide stateInfoMap programmatically.")
        println("See StateProofTestGenerator in your project for an example.")
        println()

        if (outputReport != null) {
            File(outputReport).writeText(buildString {
                appendLine("StateProof Sync Report")
                appendLine("Generated: ${Instant.now()}")
                appendLine("Mode: ${if (dryRun) "DRY RUN" else "SYNC"}")
                appendLine()
                appendLine("Existing tests found: ${existingTests.size}")
                existingTests.forEach { (hash, test) ->
                    appendLine("  - $hash: ${test.functionName}")
                }
            })
            println("Report written to: $outputReport")
        }
    }

    /**
     * Generate test code for a state machine.
     */
    private fun runGenerate(args: List<String>) {
        val output = args.getArgValue("--output")
        val packageName = args.getArgValue("--package") ?: "com.example.test"
        val className = args.getArgValue("--class-name") ?: "GeneratedStateMachineTest"
        val initialState = args.getArgValue("--initial-state") ?: "Initial"

        if (output == null) {
            println("Error: --output required")
            return
        }

        println("=".repeat(60))
        println("StateProof Test Generation")
        println("=".repeat(60))
        println()
        println("Output: $output")
        println("Package: $packageName")
        println("Class: $className")
        println("Initial state: $initialState")
        println()

        // Note: In a real integration, stateInfoMap comes from the compiled state machine
        println("Note: To generate tests, provide stateInfoMap programmatically.")
        println("See StateProofTestGenerator in your project for an example.")
    }

    /**
     * Interactive cleanup of obsolete tests.
     */
    private fun runCleanObsolete(args: List<String>) {
        val testDir = args.getArgValue("--test-dir")

        if (testDir == null) {
            println("Error: --test-dir required")
            return
        }

        println("=".repeat(60))
        println("StateProof Clean Obsolete Tests")
        println("=".repeat(60))
        println()

        val testDirFile = File(testDir)
        if (!testDirFile.exists()) {
            println("Error: Test directory not found: $testDir")
            return
        }

        // Find test files with obsolete annotations
        val obsoleteTests = mutableListOf<Pair<File, TestFileParser.ParsedTest>>()

        testDirFile.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val content = file.readText()
                if (content.contains("@StateProofObsolete")) {
                    val parsed = TestFileParser.parseTestFile(file.absolutePath, content)
                    for (test in parsed.tests) {
                        if (test.isObsolete) {
                            obsoleteTests.add(file to test)
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

        obsoleteTests.forEachIndexed { index, (file, test) ->
            println("${index + 1}. ${test.functionName}")
            println("   File: ${file.name}")
            println("   Transitions: ${test.expectedTransitions.take(3).joinToString(" -> ")}...")
            println()
        }

        println("Options:")
        println("  [D] Delete all obsolete tests")
        println("  [R] Review each test individually")
        println("  [K] Keep all (exit without changes)")
        println()
        print("Choice [D/R/K]: ")

        val choice = readLine()?.trim()?.uppercase() ?: "K"

        when (choice) {
            "D" -> {
                println()
                println("Deleting obsolete tests...")
                // In a full implementation, this would remove the test functions
                println("Note: Automatic deletion not yet implemented.")
                println("Please remove obsolete tests manually from your IDE.")
            }
            "R" -> {
                println()
                obsoleteTests.forEachIndexed { index, (file, test) ->
                    println("─".repeat(40))
                    println("Test ${index + 1}/${obsoleteTests.size}: ${test.functionName}")
                    println("File: ${file.absolutePath}")
                    println()
                    println("Expected transitions:")
                    test.expectedTransitions.forEach { println("  - $it") }
                    println()
                    print("Delete this test? [Y/N]: ")
                    val answer = readLine()?.trim()?.uppercase() ?: "N"
                    if (answer == "Y") {
                        println("Marked for deletion.")
                    } else {
                        println("Keeping test.")
                    }
                    println()
                }
                println("Note: Automatic deletion not yet implemented.")
                println("Please remove marked tests manually from your IDE.")
            }
            else -> {
                println("Keeping all tests. Exiting.")
            }
        }
    }

    private fun List<String>.getArgValue(name: String): String? {
        val index = indexOf(name)
        return if (index >= 0 && index < size - 1) get(index + 1) else null
    }
}

/**
 * Programmatic API for running sync operations.
 *
 * Example usage:
 * ```kotlin
 * val stateInfo = getMainStateMachineInfo()  // Your state machine info
 * val result = StateProofSync.sync(
 *     stateInfoMap = stateInfo,
 *     initialState = "Initial",
 *     testDir = File("src/test/kotlin"),
 *     dryRun = false,
 * )
 * println(result.report.summary())
 * ```
 */
object StateProofSync {

    data class SyncResult(
        val report: io.stateproof.sync.SyncReport,
        val filesModified: List<File>,
        val filesCreated: List<File>,
    )

    /**
     * Run sync operation programmatically.
     *
     * @param stateInfoMap Map of state names to their info (from your state machine)
     * @param initialState Name of the initial state
     * @param testDir Directory containing test files
     * @param testFile Single test file (alternative to testDir)
     * @param dryRun If true, report changes without writing files
     * @param config Test generation configuration
     * @return SyncResult with report and modified files
     */
    fun sync(
        stateInfoMap: Map<String, StateInfo>,
        initialState: String,
        testDir: File? = null,
        testFile: File? = null,
        dryRun: Boolean = false,
        config: TestGenConfig = TestGenConfig.DEFAULT,
    ): SyncResult {
        require(testDir != null || testFile != null) {
            "Either testDir or testFile must be provided"
        }

        // Find and parse existing tests
        val testFiles = if (testFile != null) {
            listOf(testFile)
        } else {
            testDir!!.walkTopDown()
                .filter { it.extension == "kt" && it.readText().contains("@Test") }
                .toList()
        }

        val existingTests = mutableMapOf<String, TestFileParser.ParsedTest>()
        for (file in testFiles) {
            val content = file.readText()
            val parsed = TestFileParser.parseTestFile(file.absolutePath, content)
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

        // In a full implementation, write changes to files here
        // For now, just return the report

        return SyncResult(
            report = report,
            filesModified = filesModified,
            filesCreated = filesCreated,
        )
    }

    /**
     * Generate new test file programmatically.
     *
     * @param stateInfoMap Map of state names to their info
     * @param initialState Name of the initial state
     * @param outputFile Output file path
     * @param codeGenConfig Code generation configuration
     * @param testGenConfig Test generation configuration
     * @return Generated test code
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
}
