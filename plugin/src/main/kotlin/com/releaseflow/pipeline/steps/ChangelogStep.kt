package com.releaseflow.pipeline.steps

import com.releaseflow.pipeline.StepResult
import com.releaseflow.util.Logger
import com.releaseflow.util.Shell
import java.io.File

/**
 * Generates a changelog from git history between the last tag and HEAD.
 *
 * If no tags exist, falls back to the last 20 commits.
 * Commit hashes are stripped; each entry is formatted as a bullet point.
 *
 * Formats:
 * - "plain"    → "• Add login screen"
 * - "markdown" → "- Add login screen"
 */
class ChangelogStep(
    private val projectRootDir: File,
    private val enabled: Boolean,
    private val format: String
) {

    fun execute(): StepResult {
        if (!enabled) {
            return StepResult.Skipped("changelogEnabled = false")
        }

        Logger.step("Changelog: reading git history")

        val lastTag = findLastTag()
        val range = if (lastTag.isNullOrBlank()) {
            Logger.warn("No git tags found — using last 20 commits for changelog")
            "HEAD~20..HEAD"
        } else {
            Logger.step("Changelog: from tag $lastTag to HEAD")
            "$lastTag..HEAD"
        }

        val result = Shell.run(
            listOf("git", "log", "--oneline", "--no-merges", range),
            workingDir = projectRootDir
        )

        if (result.exitCode != 0) {
            return StepResult.Failure(
                "git log failed (exit ${result.exitCode}): ${result.stderr.trim()}\n" +
                "  → Ensure you are inside a git repository\n" +
                "  → Check that 'git' is on your PATH"
            )
        }

        val bullet = if (format == "markdown") "-" else "•"
        val entries = result.stdout.trim().lines()
            .filter { it.isNotBlank() }
            .map { line ->
                // Strip the leading short hash (7 hex chars + space)
                val stripped = line.replaceFirst(Regex("^[0-9a-f]{7,}\\s+"), "").trim()
                "$bullet $stripped"
            }

        if (entries.isEmpty()) {
            return StepResult.Skipped("No commits found in range $range")
        }

        Logger.ok("Changelog: ${entries.size} commit(s)")
        return StepResult.Success(entries)
    }

    private fun findLastTag(): String? {
        val result = Shell.run(
            listOf("git", "describe", "--tags", "--abbrev=0"),
            workingDir = projectRootDir
        )
        return if (result.exitCode == 0) result.stdout.trim() else null
    }
}
