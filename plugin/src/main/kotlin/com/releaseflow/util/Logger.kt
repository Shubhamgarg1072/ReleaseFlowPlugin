package com.releaseflow.util

/**
 * Prints colored, consistently-formatted terminal output using ANSI escape codes.
 *
 * Falls back to plain text when the terminal does not support ANSI (e.g. CI with no color flag).
 */
object Logger {

    private val ansiSupported: Boolean by lazy {
        System.getenv("TERM") != "dumb" && System.console() != null ||
            System.getenv("CI") != null ||
            System.getenv("GITHUB_ACTIONS") != null
    }

    private const val RESET  = "[0m"
    private const val BOLD   = "[1m"
    private const val CYAN   = "[36m"
    private const val GREEN  = "[32m"
    private const val YELLOW = "[33m"
    private const val RED    = "[31m"
    private const val WHITE  = "[37m"

    /** Cyan bold section header: `▶ ReleaseFlow → qa` */
    fun header(message: String) = print("▶ $message", CYAN + BOLD)

    /** White step indicator: `○ Build: assembleQaDebug` */
    fun step(message: String) = print("○ $message", WHITE)

    /** Green success line: `✓ Build completed` */
    fun ok(message: String) = print("✓ $message", GREEN)

    /** Yellow warning line: `⚠ Drive upload skipped` */
    fun warn(message: String) = print("⚠ $message", YELLOW)

    /** Red error line: `✗ Email notification failed: ...` */
    fun error(message: String) = print("✗ $message", RED)

    private fun print(message: String, ansiCode: String) {
        val formatted = if (ansiSupported) "$ansiCode$message$RESET" else message
        println(formatted)
    }
}
