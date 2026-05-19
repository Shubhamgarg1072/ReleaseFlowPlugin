package com.releaseflow.util

import java.io.File

/**
 * Thin wrapper around [ProcessBuilder] for running shell commands.
 */
object Shell {

    /**
     * Result of a shell command execution.
     *
     * @property exitCode Process exit code (0 = success).
     * @property stdout   Captured standard output.
     * @property stderr   Captured standard error.
     */
    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    /**
     * Runs [command] in [workingDir], captures stdout and stderr, and returns a [Result].
     *
     * @param command     Command and arguments as a list, e.g. `listOf("git", "log", "--oneline")`.
     * @param workingDir  Directory from which the command is executed.
     * @param timeoutMs   Maximum time to wait in milliseconds (default 10 minutes).
     */
    fun run(
        command: List<String>,
        workingDir: File,
        timeoutMs: Long = 600_000L
    ): Result {
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(false)
            .start()

        // Read both streams concurrently to prevent buffer-full deadlock
        val stdoutFuture = process.inputStream.bufferedReader().readText()
        val stderrText = process.errorStream.bufferedReader().readText()

        val finished = process.waitFor(
            timeoutMs,
            java.util.concurrent.TimeUnit.MILLISECONDS
        )

        if (!finished) {
            process.destroyForcibly()
            return Result(
                exitCode = -1,
                stdout = stdoutFuture,
                stderr = "Process timed out after ${timeoutMs / 1000}s: ${command.joinToString(" ")}"
            )
        }

        return Result(
            exitCode = process.exitValue(),
            stdout = stdoutFuture,
            stderr = stderrText
        )
    }
}
