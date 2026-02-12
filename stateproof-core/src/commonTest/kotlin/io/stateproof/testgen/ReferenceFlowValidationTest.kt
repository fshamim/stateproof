package io.stateproof.testgen

import io.stateproof.graph.StateInfo
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Validation test that compares StateProof's path enumeration
 * against a reference main-state-machine test baseline.
 *
 * This proves that StateProof generates the same (or better) test coverage.
 */
class ReferenceFlowValidationTest {

    /**
     * Recreates the reference main state machine info structure.
     * This mirrors an app-level getMainStateMachineInfo() baseline.
     */
    private fun getReferenceStateMachineInfo(): Map<String, StateInfo> {
        val stateMachineInfo = mutableMapOf<String, StateInfo>()

        // State names
        val initial = "Initial"
        val checkProjects = "CheckProjects"
        val importProject = "ImportProject"
        val importing = "Importing"
        val exporting = "Exporting"
        val importSuccessful = "ImportSuccessful"
        val appStarted = "AppStarted"
        val settings = "Settings"
        val loadPoint = "LoadPoint"
        val measurement = "Measurement"
        val appStartedSupport = "AppStartedSupport"
        val appStartedTermsAndConditions = "AppStartedTermsAndConditions"
        val appStartedImprint = "AppStartedImprint"
        val settingsSupport = "SettingsSupport"
        val settingsTermsAndConditions = "SettingsTermsAndConditions"
        val settingsImprint = "SettingsImprint"
        val diagnostics = "Diagnostics"

        // Event names
        val noProjectFound = "NoProjectFound"
        val onAppStart = "OnAppStart"
        val onLoadProject = "OnLoadProject"
        val onLoadFromJson = "OnLoadFromJson"
        val onJsonValidated = "OnJsonValidated"
        val onJsonLoadFailed = "OnJsonLoadFailed"
        val onProjectFound = "OnProjectFound"
        val onProjectLoadFailed = "OnProjectLoadFailed"
        val onToSettings = "OnToSettings"
        val onChangeForcePointSelection = "OnChangeForcePointSelection"
        val onToLoadPoint = "OnToLoadPoint"
        val onConfirmMeasurement = "OnConfirmMeasurement"
        val onDeleteMeasurement = "OnDeleteMeasurement"
        val onToMeasurement = "OnToMeasurement"
        val onToSupport = "OnToSupport"
        val onToTOC = "OnToTOC"
        val onToImprint = "OnToImprint"
        val onToDiagnostics = "OnToDiagnostics"
        val onUpdateSettings = "OnUpdateSettings"
        val onBack = "OnBack"
        val onToImportProject = "OnToImportProject"
        val onToExporting = "OnToExporting"
        val onExportFinished = "OnExportFinished"
        // Note: OnAskToQuit is handled internally (doNotTransition), not a real transition

        // Build reference state machine info used for baseline comparison
        stateMachineInfo[initial] = StateInfo(initial).apply {
            transitions[onAppStart] = checkProjects
        }

        stateMachineInfo[checkProjects] = StateInfo(checkProjects).apply {
            transitions[noProjectFound] = importProject
            transitions[onProjectFound] = appStarted
        }

        stateMachineInfo[importProject] = StateInfo(importProject).apply {
            transitions[onLoadFromJson] = importing
        }

        stateMachineInfo[importing] = StateInfo(importing).apply {
            transitions[onJsonLoadFailed] = importProject
            transitions[onJsonValidated] = importSuccessful
        }

        stateMachineInfo[exporting] = StateInfo(exporting).apply {
            transitions[onExportFinished] = appStarted
        }

        stateMachineInfo[importSuccessful] = StateInfo(importSuccessful).apply {
            transitions[onLoadProject] = appStarted
            transitions[onBack] = importProject
        }

        stateMachineInfo[appStarted] = StateInfo(appStarted).apply {
            transitions[onProjectLoadFailed] = importProject
            transitions[onToSettings] = settings
            transitions[onToLoadPoint] = loadPoint
            transitions[onToSupport] = appStartedSupport
            transitions[onToImprint] = appStartedImprint
            transitions[onToTOC] = appStartedTermsAndConditions
            transitions[onChangeForcePointSelection] = appStarted  // self-transition
            transitions[onToImportProject] = importProject
            transitions[onToExporting] = exporting
        }

        stateMachineInfo[loadPoint] = StateInfo(loadPoint).apply {
            transitions[onToMeasurement] = measurement
            transitions[onDeleteMeasurement] = loadPoint  // self-transition
            transitions[onConfirmMeasurement] = loadPoint  // self-transition
            transitions[onBack] = appStarted
        }

        stateMachineInfo[measurement] = StateInfo(measurement).apply {
            transitions[onBack] = loadPoint
            transitions[onConfirmMeasurement] = loadPoint
        }

        stateMachineInfo[settings] = StateInfo(settings).apply {
            transitions[onBack] = appStarted
            transitions[onToDiagnostics] = diagnostics
            transitions[onToSupport] = settingsSupport
            transitions[onToImprint] = settingsImprint
            transitions[onToTOC] = settingsTermsAndConditions
            transitions[onUpdateSettings] = settings  // self-transition
        }

        stateMachineInfo[diagnostics] = StateInfo(diagnostics).apply {
            transitions[onBack] = settings
        }

        stateMachineInfo[appStartedSupport] = StateInfo(appStartedSupport).apply {
            transitions[onBack] = appStarted
        }

        stateMachineInfo[settingsSupport] = StateInfo(settingsSupport).apply {
            transitions[onBack] = settings
        }

        stateMachineInfo[appStartedImprint] = StateInfo(appStartedImprint).apply {
            transitions[onBack] = appStarted
        }

        stateMachineInfo[settingsImprint] = StateInfo(settingsImprint).apply {
            transitions[onBack] = settings
        }

        stateMachineInfo[appStartedTermsAndConditions] = StateInfo(appStartedTermsAndConditions).apply {
            transitions[onBack] = appStarted
        }

        stateMachineInfo[settingsTermsAndConditions] = StateInfo(settingsTermsAndConditions).apply {
            transitions[onBack] = settings
        }

        return stateMachineInfo
    }

    /**
     * Expected transitions from the reference baseline tests
     * These represent the expected baseline test cases.
     */
    private val referenceExpectedTransitions = listOf(
        // From the baseline MainStateMachineTest expectedTransitions lists
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonLoadFailed_ImportProject"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnProjectLoadFailed_ImportProject"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnProjectLoadFailed_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonLoadFailed_ImportProject"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnProjectLoadFailed_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToLoadPoint_LoadPoint", "LoadPoint_OnToMeasurement_Measurement", "Measurement_OnConfirmMeasurement_LoadPoint"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToLoadPoint_LoadPoint", "LoadPoint_OnToMeasurement_Measurement", "Measurement_OnConfirmMeasurement_LoadPoint"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnChangeForcePointSelection_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnUpdateSettings_Settings"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnChangeForcePointSelection_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnUpdateSettings_Settings"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnBack_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToLoadPoint_LoadPoint", "LoadPoint_OnBack_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToSupport_AppStartedSupport", "AppStartedSupport_OnBack_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToImprint_AppStartedImprint", "AppStartedImprint_OnBack_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToTOC_AppStartedTermsAndConditions", "AppStartedTermsAndConditions_OnBack_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnBack_ImportProject"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnToDiagnostics_Diagnostics", "Diagnostics_OnBack_Settings"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnToSupport_SettingsSupport", "SettingsSupport_OnBack_Settings"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnToImprint_SettingsImprint", "SettingsImprint_OnBack_Settings"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnToTOC_SettingsTermsAndConditions", "SettingsTermsAndConditions_OnBack_Settings"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnProjectLoadFailed_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnBack_ImportProject"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnBack_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToLoadPoint_LoadPoint", "LoadPoint_OnBack_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToSupport_AppStartedSupport", "AppStartedSupport_OnBack_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToImprint_AppStartedImprint", "AppStartedImprint_OnBack_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToTOC_AppStartedTermsAndConditions", "AppStartedTermsAndConditions_OnBack_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnToDiagnostics_Diagnostics", "Diagnostics_OnBack_Settings"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnToSupport_SettingsSupport", "SettingsSupport_OnBack_Settings"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnToImprint_SettingsImprint", "SettingsImprint_OnBack_Settings"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToSettings_Settings", "Settings_OnToTOC_SettingsTermsAndConditions", "SettingsTermsAndConditions_OnBack_Settings"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToLoadPoint_LoadPoint", "LoadPoint_OnDeleteMeasurement_LoadPoint"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToLoadPoint_LoadPoint", "LoadPoint_OnConfirmMeasurement_LoadPoint"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToExporting_Exporting", "Exporting_OnExportFinished_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToLoadPoint_LoadPoint", "LoadPoint_OnToMeasurement_Measurement", "Measurement_OnBack_LoadPoint"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToImportProject_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonLoadFailed_ImportProject"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToImportProject_ImportProject"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToImportProject_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_OnProjectFound_AppStarted", "AppStarted_OnToImportProject_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnBack_ImportProject"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToLoadPoint_LoadPoint", "LoadPoint_OnDeleteMeasurement_LoadPoint"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToLoadPoint_LoadPoint", "LoadPoint_OnConfirmMeasurement_LoadPoint"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToExporting_Exporting", "Exporting_OnExportFinished_AppStarted"),
        listOf("Initial_OnAppStart_CheckProjects", "CheckProjects_NoProjectFound_ImportProject", "ImportProject_OnLoadFromJson_Importing", "Importing_OnJsonValidated_ImportSuccessful", "ImportSuccessful_OnLoadProject_AppStarted", "AppStarted_OnToLoadPoint_LoadPoint", "LoadPoint_OnToMeasurement_Measurement", "Measurement_OnBack_LoadPoint"),
    )

    @Test
    fun stateProof_shouldGenerateSamePathsAsReferenceBaseline() {
        val stateInfo = getReferenceStateMachineInfo()
        val enumerator = SimplePathEnumerator(
            stateInfoMap = stateInfo,
            initialState = "Initial",
            config = TestGenConfig.DEFAULT,  // maxVisitsPerState = 2
        )

        val generatedPaths = enumerator.findAllPaths()
        val generatedTransitions = generatedPaths.map { enumerator.pathToTransitions(it) }.toSet()

        println("Generated ${generatedPaths.size} paths")
        println("Reference baseline has ${referenceExpectedTransitions.size} test cases")

        // Check that all baseline test cases are covered
        var allCovered = true
        for (expected in referenceExpectedTransitions) {
            if (expected !in generatedTransitions) {
                println("MISSING: $expected")
                allCovered = false
            }
        }

        assertTrue(allCovered, "All reference baseline test cases should be covered by StateProof")

        // StateProof may generate MORE paths (which is fine - better coverage)
        println("\nStateProof generates ${generatedPaths.size} paths vs baseline ${referenceExpectedTransitions.size} tests")

        // Show any additional paths StateProof generates
        val additionalPaths = generatedTransitions - referenceExpectedTransitions.toSet()
        if (additionalPaths.isNotEmpty()) {
            println("\nAdditional paths generated by StateProof (${additionalPaths.size}):")
            additionalPaths.take(10).forEach { println("  $it") }
            if (additionalPaths.size > 10) {
                println("  ... and ${additionalPaths.size - 10} more")
            }
        }
    }

    @Test
    fun stateProof_shouldGenerateCorrectTestNames() {
        val stateInfo = getReferenceStateMachineInfo()
        val enumerator = SimplePathEnumerator(
            stateInfoMap = stateInfo,
            initialState = "Initial",
            config = TestGenConfig(hashAlgorithm = TestGenConfig.HashAlgorithm.CRC16),
        )

        val testCases = enumerator.generateTestCases()

        println("Generated ${testCases.size} test cases")
        testCases.take(5).forEach { tc ->
            println("  ${tc.name}")
        }

        // Verify test names follow the expected pattern: _<depth>_<hash>_<path>
        testCases.forEach { tc ->
            assertTrue(tc.name.matches(Regex("_\\d+_[0-9A-F]{4}_.*")),
                "Test name should match pattern: ${tc.name}")
        }
    }

    @Test
    fun stateProof_shouldMatchReferenceBaselinePathCount() {
        val stateInfo = getReferenceStateMachineInfo()
        val enumerator = SimplePathEnumerator(
            stateInfoMap = stateInfo,
            initialState = "Initial",
            config = TestGenConfig.DEFAULT,
        )

        val generatedPaths = enumerator.findAllPaths()

        // StateProof should generate at least as many paths as the baseline
        assertTrue(
            generatedPaths.size >= referenceExpectedTransitions.size,
            "StateProof should generate at least ${referenceExpectedTransitions.size} paths, got ${generatedPaths.size}"
        )
    }

    @Test
    fun generateKotlinCode_shouldProduceValidTestCode() {
        val stateInfo = getReferenceStateMachineInfo()
        val enumerator = SimplePathEnumerator(
            stateInfoMap = stateInfo,
            initialState = "Initial",
            config = TestGenConfig(hashAlgorithm = TestGenConfig.HashAlgorithm.CRC16),
        )

        val testCases = enumerator.generateTestCases()
        val sampleTest = testCases.first()
        val code = sampleTest.generateKotlinCode()

        println("Sample generated test:\n$code")

        assertTrue(code.contains("@Test"), "Generated code should have @Test annotation")
        assertTrue(code.contains("expectedTransitions"), "Generated code should have expectedTransitions")
        assertTrue(code.contains("runBlocking"), "Generated code should use runBlocking")
    }
}
