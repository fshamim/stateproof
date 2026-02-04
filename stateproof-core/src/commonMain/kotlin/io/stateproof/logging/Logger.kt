package io.stateproof.logging

/**
 * Platform-agnostic logging interface for StateProof.
 *
 * Implementations can delegate to platform-specific logging (Android Log, console, etc.)
 */
interface Logger {
    /**
     * Logs a message with an optional tag.
     *
     * @param tag Optional tag for categorizing the log message
     */
    fun String.log(tag: String = "StateProof")

    /**
     * Logs a message with the default tag.
     */
    fun String.log()
}

/**
 * A no-op logger that discards all log messages.
 * Useful for production or when logging is not needed.
 */
object NoOpLogger : Logger {
    override fun String.log(tag: String) {
        // No-op
    }

    override fun String.log() {
        // No-op
    }
}

/**
 * A simple logger that prints to standard output.
 * Useful for debugging and testing.
 */
class PrintLogger(private val defaultTag: String = "StateProof") : Logger {
    override fun String.log(tag: String) {
        println("[$tag] $this")
    }

    override fun String.log() {
        println("[$defaultTag] $this")
    }
}
