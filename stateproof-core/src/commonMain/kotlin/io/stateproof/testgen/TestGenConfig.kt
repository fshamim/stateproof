package io.stateproof.testgen

/**
 * Configuration for test case generation.
 *
 * @param maxVisitsPerState Maximum times each state can be visited during path enumeration.
 *                          Higher values capture more cycle scenarios but increase test count.
 *                          Default is 2 (captures return-to-state scenarios).
 * @param maxPathDepth Optional limit on path length (number of transitions).
 *                     Null means unlimited depth.
 * @param includeTerminalPaths Whether to include paths that end at states with no outgoing transitions.
 * @param hashAlgorithm Algorithm used for generating unique test identifiers.
 */
data class TestGenConfig(
    val maxVisitsPerState: Int = 2,
    val maxPathDepth: Int? = null,
    val includeTerminalPaths: Boolean = true,
    val hashAlgorithm: HashAlgorithm = HashAlgorithm.CRC32,
) {
    /**
     * Hash algorithms for test case identification.
     */
    enum class HashAlgorithm {
        /** 16-bit CRC - 65K unique values, risk of collision with large graphs */
        CRC16,
        /** 32-bit CRC - 4B unique values, recommended default */
        CRC32,
    }

    companion object {
        /** Default configuration with maxVisitsPerState=2 and CRC32 hashing */
        val DEFAULT = TestGenConfig()

        /** Shallow traversal - visit each state only once */
        val SHALLOW = TestGenConfig(maxVisitsPerState = 1)

        /** Deep traversal - visit each state up to 3 times */
        val DEEP = TestGenConfig(maxVisitsPerState = 3)
    }

    init {
        require(maxVisitsPerState >= 1) { "maxVisitsPerState must be at least 1" }
        require(maxPathDepth == null || maxPathDepth >= 1) { "maxPathDepth must be at least 1 if specified" }
    }
}
