package com.releaseflow

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Integration tests for [ReleaseFlowPlugin] using Gradle TestKit.
 * Verifies that the plugin applies cleanly and registers the expected tasks.
 */
class ReleaseFlowPluginTest {

    @get:Rule
    val testProjectDir = TemporaryFolder()

    private fun writeFile(path: String, content: String) {
        val file = File(testProjectDir.root, path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `plugin applies without errors and registers validate task`() {
        writeFile("settings.gradle.kts", """rootProject.name = "test-project"""")
        writeFile("build.gradle.kts", """
            plugins {
                id("com.releaseflow.gradle")
            }
            releaseFlow {
                projectName = "TestApp"
                environment("qa") {
                    flavor = "qa"
                    buildType = "debug"
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments("tasks", "--group=release", "--stacktrace")
            .build()

        assertTrue("validate task must be listed",
            result.output.contains("releaseFlowValidate"))
        assertTrue("deploy task for qa must be listed",
            result.output.contains("releaseFlowDeployQa"))
    }

    @Test
    fun `releaseFlowValidate passes for valid minimal configuration`() {
        writeFile("settings.gradle.kts", """rootProject.name = "test-project"""")
        writeFile("build.gradle.kts", """
            plugins {
                id("com.releaseflow.gradle")
            }
            releaseFlow {
                projectName = "ValidApp"
                environment("staging") {
                    flavor = "staging"
                    buildType = "release"
                    // No Drive or email config — should be valid (optional features)
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments("releaseFlowValidate", "--stacktrace")
            .build()

        assertTrue("validate must succeed",
            result.output.contains("configuration is valid") ||
            result.task(":releaseFlowValidate")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `multiple environments register multiple deploy tasks`() {
        writeFile("settings.gradle.kts", """rootProject.name = "test-project"""")
        writeFile("build.gradle.kts", """
            plugins {
                id("com.releaseflow.gradle")
            }
            releaseFlow {
                environment("qa") { buildType = "debug" }
                environment("staging") { buildType = "release" }
                environment("production") { buildType = "release" }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
            .withArguments("tasks", "--group=release")
            .build()

        assertTrue(result.output.contains("releaseFlowDeployQa"))
        assertTrue(result.output.contains("releaseFlowDeployStaging"))
        assertTrue(result.output.contains("releaseFlowDeployProduction"))
    }
}
