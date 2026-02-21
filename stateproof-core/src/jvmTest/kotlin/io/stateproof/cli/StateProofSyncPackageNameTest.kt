package io.stateproof.cli

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StateProofSyncPackageNameTest {

    @Test
    fun inferPackageName_supportsKmpDesktopTestPath() {
        val file = File(
            "/tmp/workspace/composeApp/src/desktopTest/kotlin/generated/stateproof/GeneratedStateMachineTest.kt",
        )

        assertEquals("generated.stateproof", StateProofSync.inferPackageName(file))
    }

    @Test
    fun resolvePackageNameForTargetFile_usesInferredWhenBlank() {
        val file = File(
            "/tmp/workspace/module/src/commonTest/kotlin/io/example/generated/GeneratedMainStateMachineTest.kt",
        )

        val resolved = StateProofSync.resolvePackageNameForTargetFile("", file)
        assertEquals("io.example.generated", resolved)
    }

    @Test
    fun resolvePackageNameForTargetFile_rejectsMismatchWhenSourceSetIsKnown() {
        val file = File(
            "/tmp/workspace/composeApp/src/desktopTest/kotlin/generated/stateproof/GeneratedStateMachineTest.kt",
        )

        val error = assertFailsWith<IllegalArgumentException> {
            StateProofSync.resolvePackageNameForTargetFile(
                requestedPackage = "io.stateproof.sample.generated",
                targetFile = file,
            )
        }
        assertTrue(error.message.orEmpty().contains("does not match inferred package"))
    }

    @Test
    fun resolvePackageNameForTargetFile_allowsExplicitPackageWhenPathCannotBeInferred() {
        val file = File("/tmp/workspace/custom/location/GeneratedStateMachineTest.kt")

        val resolved = StateProofSync.resolvePackageNameForTargetFile(
            requestedPackage = "io.stateproof.sample.generated",
            targetFile = file,
        )
        assertEquals("io.stateproof.sample.generated", resolved)
    }
}
