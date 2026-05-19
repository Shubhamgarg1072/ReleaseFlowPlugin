package com.releaseflow.pipeline.steps

import com.releaseflow.EnvironmentConfig
import com.releaseflow.pipeline.StepResult
import com.releaseflow.util.Logger
import com.releaseflow.util.Shell
import java.io.File

/**
 * Runs the Gradle assemble task for the configured flavor and buildType.
 *
 * Task name derivation:
 * - flavor="qa", buildType="debug" → `assembleQaDebug`
 * - flavor="",   buildType="release" → `assembleRelease`
 */
class BuildStep(
    private val envConfig: EnvironmentConfig,
    private val projectRootDir: File,
    private val dryRun: Boolean
) {

    fun execute(): StepResult {
        val taskName = buildTaskName()
        Logger.step("Build: $taskName")

        if (dryRun) {
            Logger.warn("[DRY RUN] Would run: ./gradlew $taskName")
            return StepResult.Success(Unit)
        }

        val gradlew = resolveGradlew()
        val result = Shell.run(listOf(gradlew, taskName), workingDir = projectRootDir)

        return if (result.exitCode == 0) {
            StepResult.Success(Unit)
        } else {
            StepResult.Failure(
                "Gradle build failed (exit ${result.exitCode})\n" +
                "  Command: $gradlew $taskName\n" +
                "  Stderr: ${result.stderr.trim().lines().take(20).joinToString("\n  ")}\n" +
                "  → Check your flavor/buildType configuration and run the task manually to see the full error."
            )
        }
    }

    private fun buildTaskName(): String {
        val flavor = envConfig.flavor.trim()
        val type = envConfig.buildType.trim().replaceFirstChar { it.uppercase() }
        return if (flavor.isBlank()) {
            "assemble$type"
        } else {
            "assemble${flavor.replaceFirstChar { it.uppercase() }}$type"
        }
    }

    private fun resolveGradlew(): String {
        val gradlewFile = File(projectRootDir, if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "gradlew")
        return if (gradlewFile.exists()) gradlewFile.absolutePath else "gradle"
    }
}
