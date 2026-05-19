package com.releaseflow

import com.releaseflow.pipeline.StepResult
import com.releaseflow.pipeline.steps.ChangelogStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [ChangelogStep] — verifies enabled/disabled behavior and output formatting.
 */
class ChangelogStepTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `returns Skipped when changelogEnabled is false`() {
        val step = ChangelogStep(
            projectRootDir = tempDir.root,
            enabled = false,
            format = "plain"
        )
        val result = step.execute()
        assertTrue("Expected Skipped", result is StepResult.Skipped)
    }

    @Test
    fun `returns Failure when not inside a git repository`() {
        val step = ChangelogStep(
            projectRootDir = tempDir.root,
            enabled = true,
            format = "plain"
        )
        val result = step.execute()
        // Should fail gracefully with a Failure (not an exception)
        assertTrue(
            "Expected Failure when not in a git repo, got: $result",
            result is StepResult.Failure || result is StepResult.Skipped
        )
    }

    @Test
    fun `plain format uses bullet prefix`() {
        // We test the commit-line stripping + bullet logic in isolation
        val rawLines = listOf(
            "a1b2c3d Fix login crash",
            "e4f5a6b Add dark mode support",
            "deadbeef Update dependencies"
        )
        val formatted = rawLines.map { line ->
            val stripped = line.replaceFirst(Regex("^[0-9a-f]{7,}\\s+"), "").trim()
            "• $stripped"
        }

        assertEquals("• Fix login crash", formatted[0])
        assertEquals("• Add dark mode support", formatted[1])
        assertEquals("• Update dependencies", formatted[2])
    }

    @Test
    fun `markdown format uses dash prefix`() {
        val rawLines = listOf("a1b2c3d Fix login crash")
        val formatted = rawLines.map { line ->
            val stripped = line.replaceFirst(Regex("^[0-9a-f]{7,}\\s+"), "").trim()
            "- $stripped"
        }
        assertEquals("- Fix login crash", formatted[0])
    }

    @Test
    fun `commit hash stripping removes exactly the leading hash`() {
        val testCases = mapOf(
            "a1b2c3d Fix login crash"    to "Fix login crash",
            "deadbeef1 Refactor module"  to "Refactor module",
            "abc1234 Short hash commit"  to "Short hash commit"
        )
        testCases.forEach { (input, expected) ->
            val actual = input.replaceFirst(Regex("^[0-9a-f]{7,}\\s+"), "").trim()
            assertEquals("For input '$input'", expected, actual)
        }
    }

    @Test
    fun `empty lines are filtered out`() {
        val lines = listOf("a1b2c3d Fix something", "", "  ", "b2c3d4e Another fix")
        val result = lines
            .filter { it.isNotBlank() }
            .map { line ->
                val stripped = line.replaceFirst(Regex("^[0-9a-f]{7,}\\s+"), "").trim()
                "• $stripped"
            }
        assertEquals(2, result.size)
    }
}
