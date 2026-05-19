package com.releaseflow

import com.releaseflow.pipeline.StepResult
import com.releaseflow.pipeline.steps.ArtifactStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Unit tests for [ArtifactStep] — verifies artifact discovery and filename renaming.
 */
class ArtifactStepTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun makeConfig(flavor: String = "qa", buildType: String = "debug") =
        EnvironmentConfig(name = "qa", flavor = flavor, buildType = buildType)

    @Test
    fun `returns failure when outputs directory does not exist`() {
        val config = makeConfig()
        val step = ArtifactStep(config, tempDir.root, dryRun = false)

        val result = step.execute()

        assertTrue("Expected Failure but got $result", result is StepResult.Failure)
        val msg = (result as StepResult.Failure).message
        assertTrue("Error should mention missing directory", msg.contains("not found"))
    }

    @Test
    fun `returns failure when no APK or AAB exists in outputs`() {
        val outputsDir = File(tempDir.root, "app/build/outputs")
        outputsDir.mkdirs()
        // Create a non-APK file to ensure directory exists but has no artifacts
        File(outputsDir, "mapping.txt").writeText("placeholder")

        val config = makeConfig()
        val step = ArtifactStep(config, tempDir.root, dryRun = false)

        val result = step.execute()
        assertTrue("Expected Failure", result is StepResult.Failure)
    }

    @Test
    fun `renames APK with flavor-buildType-timestamp pattern`() {
        val apkDir = File(tempDir.root, "app/build/outputs/apk/qa/debug")
        apkDir.mkdirs()
        val fakeApk = File(apkDir, "app-qa-debug.apk")
        fakeApk.writeText("fake apk bytes")

        val config = makeConfig(flavor = "qa", buildType = "debug")
        val step = ArtifactStep(config, tempDir.root, dryRun = false)

        val result = step.execute()
        assertTrue("Expected Success but got $result", result is StepResult.Success<*>)

        val renamed = (result as StepResult.Success<*>).value as? File
        val today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        assertTrue("Filename should start with 'qa-debug-$today'",
            renamed?.name?.startsWith("qa-debug-$today") == true)
        assertTrue("Filename should end with .apk", renamed?.name?.endsWith(".apk") == true)
    }

    @Test
    fun `omits flavor prefix when flavor is blank`() {
        val apkDir = File(tempDir.root, "app/build/outputs/apk/release")
        apkDir.mkdirs()
        val fakeApk = File(apkDir, "app-release.apk")
        fakeApk.writeText("fake apk bytes")

        val config = makeConfig(flavor = "", buildType = "release")
        val step = ArtifactStep(config, tempDir.root, dryRun = false)

        val result = step.execute()
        assertTrue("Expected Success", result is StepResult.Success<*>)

        val renamed = (result as StepResult.Success<*>).value as? File
        val today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        assertTrue("Filename should start with 'release-$today' (no flavor prefix)",
            renamed?.name?.startsWith("release-$today") == true)
    }

    @Test
    fun `dry run returns original file without renaming`() {
        val apkDir = File(tempDir.root, "app/build/outputs/apk/qa/debug")
        apkDir.mkdirs()
        val fakeApk = File(apkDir, "app-qa-debug.apk")
        fakeApk.writeText("fake")

        val config = makeConfig()
        val step = ArtifactStep(config, tempDir.root, dryRun = true)

        val result = step.execute()
        assertTrue("Expected Success in dry run", result is StepResult.Success<*>)
        val file = (result as StepResult.Success<*>).value as? File
        assertEquals("Dry run must not rename the file", "app-qa-debug.apk", file?.name)
    }
}
