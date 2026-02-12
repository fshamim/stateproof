package io.stateproof.gradle

import org.gradle.api.Project
import java.io.File
import java.time.Instant

/**
 * Scanner that classifies a project for AI-assisted StateProof onboarding.
 */
object StateProofProjectScanner {

    private val sourceRoots = listOf(
        "src/main",
        "src/commonMain",
        "src/androidMain",
        "src/jvmMain",
    )

    private val stateMachineMarkers = listOf(
        "stateMachine<",
        "StateMachine<",
        "@StateProofStateMachine",
        "SMGraphBuilder",
    )

    private val navMarkers = listOf(
        "StateProofNavHost(",
        "NavHost(",
    )

    fun scan(project: Project): StateProofProjectScanReport {
        val hasAndroidPlugin = project.plugins.hasPlugin("com.android.application") ||
            project.plugins.hasPlugin("com.android.library")
        val hasKmpPlugin = project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        val coordinates = collectDependencyCoordinates(project)

        val hasCompose = coordinates.any {
            it.startsWith("androidx.compose:") || it.startsWith("org.jetbrains.compose:")
        } || hasComposeBuildFeature(project)
        val hasNavigationCompose = coordinates.contains("androidx.navigation:navigation-compose")
        val hasStateProofDependencies = coordinates.any { it.startsWith("io.stateproof:") }

        val detectedStateMachineFiles = findFilesContaining(project, stateMachineMarkers)
        val detectedNavHostFiles = findFilesContaining(project, navMarkers)

        val projectType = when {
            hasAndroidPlugin && hasKmpPlugin -> "ANDROID_KMP"
            hasAndroidPlugin -> "ANDROID_NON_KMP"
            hasKmpPlugin -> "KMP_SHARED"
            project.plugins.hasPlugin("org.jetbrains.kotlin.jvm") || project.plugins.hasPlugin("java") -> "JVM"
            else -> "UNKNOWN"
        }

        val integrationMode = if (
            hasAndroidPlugin &&
            hasCompose &&
            (hasNavigationCompose || detectedNavHostFiles.isNotEmpty())
        ) {
            "SCREENS_AS_STATES"
        } else {
            "STATE_MACHINE_ONLY"
        }

        val recommendedDependencies = buildRecommendedDependencies(integrationMode)
        val recommendedTasks = listOf(
            "stateproofScan",
            "stateproofSyncAll",
            "stateproofDiagrams",
            "stateproofViewer",
            "stateproofWatch",
        )

        val warnings = buildList {
            if (!hasStateProofDependencies) {
                add("StateProof dependencies were not detected in this module.")
            }
            if (integrationMode == "SCREENS_AS_STATES" && !hasNavigationCompose) {
                add("Navigation Compose dependency not detected; add androidx.navigation:navigation-compose.")
            }
            if (detectedStateMachineFiles.isEmpty()) {
                add("No state machine source file markers detected under known source roots.")
            }
            if (!project.plugins.hasPlugin("com.google.devtools.ksp")) {
                add("KSP plugin not detected; auto-discovery needs com.google.devtools.ksp.")
            }
        }.sorted()

        val generatedAtIso = Instant.ofEpochMilli(findScanTimestamp(project)).toString()

        return StateProofProjectScanReport(
            projectType = projectType,
            integrationMode = integrationMode,
            hasAndroidPlugin = hasAndroidPlugin,
            hasKmpPlugin = hasKmpPlugin,
            hasCompose = hasCompose,
            hasNavigationCompose = hasNavigationCompose,
            hasStateProofDependencies = hasStateProofDependencies,
            detectedStateMachineFiles = detectedStateMachineFiles.sorted(),
            detectedNavHostFiles = detectedNavHostFiles.sorted(),
            recommendedDependencies = recommendedDependencies,
            recommendedTasks = recommendedTasks,
            warnings = warnings,
            generatedAtIso = generatedAtIso,
        )
    }

    private fun collectDependencyCoordinates(project: Project): Set<String> {
        return project.configurations
            .flatMap { configuration -> configuration.dependencies }
            .mapNotNull { dep ->
                val group = dep.group ?: return@mapNotNull null
                "$group:${dep.name}"
            }
            .toSortedSet()
    }

    private fun hasComposeBuildFeature(project: Project): Boolean {
        val android = project.extensions.findByName("android") ?: return false
        return try {
            val getBuildFeatures = android.javaClass.methods.firstOrNull {
                it.name == "getBuildFeatures"
            } ?: return false
            val buildFeatures = getBuildFeatures.invoke(android) ?: return false
            val getCompose = buildFeatures.javaClass.methods.firstOrNull {
                it.name == "getCompose"
            } ?: return false
            (getCompose.invoke(buildFeatures) as? Boolean) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun findFilesContaining(project: Project, markers: List<String>): List<String> {
        val matches = mutableListOf<String>()
        val projectDir = project.projectDir

        sourceRoots.map { projectDir.resolve(it) }
            .filter { it.exists() && it.isDirectory }
            .sortedBy { it.absolutePath }
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
                    .sortedBy { it.absolutePath }
                    .forEach { file ->
                        val content = file.readText()
                        if (markers.any { marker -> content.contains(marker) }) {
                            matches.add(file.relativeTo(projectDir).invariantSeparatorsPath)
                        }
                    }
            }

        return matches
    }

    private fun findScanTimestamp(project: Project): Long {
        val candidateRoots = sourceRoots.map { project.projectDir.resolve(it) } +
            listOf(
                project.projectDir.resolve("build.gradle.kts"),
                project.projectDir.resolve("build.gradle"),
                project.projectDir.resolve("settings.gradle.kts"),
                project.projectDir.resolve("settings.gradle"),
            )

        var maxModified = 0L
        candidateRoots
            .filter { it.exists() }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                if (file.isDirectory) {
                    file.walkTopDown()
                        .sortedBy { it.absolutePath }
                        .forEach { entry ->
                            maxModified = maxOf(maxModified, safeLastModified(entry))
                        }
                } else {
                    maxModified = maxOf(maxModified, safeLastModified(file))
                }
            }

        return maxModified
    }

    private fun safeLastModified(file: File): Long {
        return try {
            file.lastModified()
        } catch (_: Exception) {
            0L
        }
    }

    private fun buildRecommendedDependencies(integrationMode: String): List<String> {
        val base = mutableListOf(
            "io.stateproof:stateproof-core-jvm:0.1.0-SNAPSHOT",
            "io.stateproof:stateproof-annotations-jvm:0.1.0-SNAPSHOT",
            "io.stateproof:stateproof-ksp:0.1.0-SNAPSHOT",
            "io.stateproof:stateproof-viewer-jvm:0.1.0-SNAPSHOT (test)",
        )
        if (integrationMode == "SCREENS_AS_STATES") {
            base.add("io.stateproof:stateproof-navigation:0.1.0-SNAPSHOT")
        }
        return base.sorted()
    }
}
