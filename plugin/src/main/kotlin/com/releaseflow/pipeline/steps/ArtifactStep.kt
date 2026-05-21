package com.releaseflow.pipeline.steps

import com.releaseflow.EnvironmentConfig
import com.releaseflow.pipeline.StepResult
import com.releaseflow.util.Logger
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Locates the newest APK or AAB produced by the build step and renames it with
 * a timestamped, human-readable filename.
 *
 * Output filename format: `{flavor}-{buildType}-{yyyyMMdd-HHmm}.{ext}`
 * Example: `qa-debug-20250519-1430.apk`
 *
 * When [flavor] is blank the prefix is just the buildType:
 * Example: `debug-20250519-1430.apk`
 */
class ArtifactStep(
    private val envConfig: EnvironmentConfig,
    private val projectRootDir: File,
    private val dryRun: Boolean
) {

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    fun execute(): StepResult {
        Logger.step("Artifact: locating output APK/AAB")

        val outputsDir = File(projectRootDir, "app/build/outputs")
        if (!outputsDir.exists()) {
            return StepResult.Failure(
                "Build outputs directory not found at '${outputsDir.absolutePath}'\n" +
                "  → Make sure the build step completed successfully\n" +
                "  → Verify your project has a module named 'app' with Android build outputs"
            )
        }

        val artifact = outputsDir.walkTopDown()
            .filter { it.isFile && (it.extension == "apk" || it.extension == "aab") }
            .maxByOrNull { it.lastModified() }
            ?: return StepResult.Failure(
                "No APK or AAB found under '${outputsDir.absolutePath}'\n" +
                "  → Run the build step first: ./gradlew assemble${envConfig.buildType.replaceFirstChar { it.uppercase() }}\n" +
                "  → Check that the build variant '${envConfig.flavor}-${envConfig.buildType}' exists"
            )

        Logger.step("Artifact: found ${artifact.name}")

        val newName = buildFilename(artifact.nameWithoutExtension, artifact.extension)
        val renamed = File(artifact.parent, newName)

        if (dryRun) {
            Logger.warn("[DRY RUN] Would rename: ${artifact.nameWithoutExtension} → $newName")
            return StepResult.Success(artifact)
        }

        artifact.renameTo(renamed)
        Logger.ok("Artifact renamed to: $newName")
        return StepResult.Success(renamed)
    }

    private fun buildFilename(artifactName: String, extension: String): String {
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val baseName = artifactName.removeSuffix(".$extension")
        return "$baseName-$timestamp.$extension"
    }
}
