package com.releaseflow.pipeline

import com.releaseflow.EnvironmentConfig
import com.releaseflow.pipeline.steps.*
import com.releaseflow.pipeline.steps.FirebaseStep
import com.releaseflow.util.Logger
import org.gradle.api.GradleException
import java.io.File

/**
 * Orchestrates the sequential release pipeline steps for a single environment.
 *
 * Steps:
 *  1. BuildStep      — run Gradle assemble task
 *  2. ArtifactStep   — locate and rename the output APK/AAB
 *  3. ChangelogStep  — generate changelog from git log
 *  4. UploadStep     — upload artifact to Google Drive
 *  5. NotifyStep     — send HTML email notification
 */
class ReleasePipeline(
    private val envConfig: EnvironmentConfig,
    private val projectName: String,
    private val projectRootDir: File,
    private val dryRun: Boolean = false,
    private val skipBuild: Boolean = false,
    private val skipUpload: Boolean = false
) {

    fun run() {
        // Step 1: Build
        val artifact: File? = if (skipBuild) {
            Logger.step("Build step skipped (--skip-build)")
            findExistingArtifact()
        } else {
            when (val result = BuildStep(envConfig, projectRootDir, dryRun).execute()) {
                is StepResult.Success<*> -> {
                    Logger.ok("Build completed")
                    // After build, locate artifact
                    when (val artifactResult = ArtifactStep(envConfig, projectRootDir, dryRun).execute()) {
                        is StepResult.Success<*> -> artifactResult.value as? File
                        is StepResult.Skipped -> { Logger.warn("Artifact: ${artifactResult.reason}"); null }
                        is StepResult.Failure -> {
                            Logger.error("Artifact step failed: ${artifactResult.message}")
                            throw GradleException(artifactResult.message)
                        }
                    }
                }
                is StepResult.Skipped -> { Logger.warn("Build: ${result.reason}"); findExistingArtifact() }
                is StepResult.Failure -> {
                    Logger.error("Build failed: ${result.message}")
                    throw GradleException("Build step failed: ${result.message}", result.cause)
                }
            }
        }

        // Step 2: Changelog
        val changelog: List<String> = when (val result = ChangelogStep(projectRootDir, envConfig.changelogEnabled, envConfig.changelogFormat).execute()) {
            is StepResult.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                (result.value as? List<String>) ?: emptyList()
            }
            is StepResult.Skipped -> { Logger.step("Changelog: ${result.reason}"); emptyList() }
            is StepResult.Failure -> { Logger.warn("Changelog generation failed: ${result.message}"); emptyList() }
        }

        // Step 3: Upload to Drive
        val driveConfigured = envConfig.driveFolderUrl.isNotBlank() || envConfig.driveServiceAccountJson.isNotBlank()
        val uploadResult = if (skipUpload || !driveConfigured) {
            if (!driveConfigured) {
                Logger.warn("Drive upload skipped — driveFolderUrl not configured")
            } else {
                Logger.step("Upload step skipped (--skip-upload)")
            }
            null
        } else if (artifact == null) {
            Logger.warn("Drive upload skipped — no artifact found")
            null
        } else {
            when (val result = UploadStep(envConfig, projectName, artifact, projectRootDir, dryRun).execute()) {
                is StepResult.Success<*> -> {
                    Logger.ok("Upload completed")
                    result.value as? com.releaseflow.storage.UploadResult
                }
                is StepResult.Skipped -> { Logger.warn("Upload: ${result.reason}"); null }
                is StepResult.Failure -> {
                    Logger.error("Upload failed: ${result.message}")
                    null // Non-fatal — continue to email with no Drive link
                }
            }
        }

        // Step 4: Firebase App Distribution
        if (artifact != null) {
            when (val result = FirebaseStep(envConfig, artifact, projectRootDir, changelog, dryRun).execute()) {
                is StepResult.Success<*> -> Unit
                is StepResult.Skipped   -> Logger.step("Firebase: ${result.reason}")
                is StepResult.Failure   -> Logger.warn("Firebase upload failed: ${result.message}")
            }
        }

        // Step 5: Email notification
        when (val result = NotifyStep(envConfig, projectName, artifact, uploadResult, changelog, dryRun).execute()) {
            is StepResult.Success<*> -> Logger.ok("Email notification sent")
            is StepResult.Skipped -> Logger.warn("Email: ${result.reason}")
            is StepResult.Failure -> Logger.warn("Email notification failed: ${result.message}")
        }

        Logger.header("ReleaseFlow pipeline complete ✓")
    }

    private fun findExistingArtifact(): File? {
        val outputsDir = File(projectRootDir, "app/build/outputs")
        if (!outputsDir.exists()) return null
        return outputsDir.walkTopDown()
            .filter { it.extension == "apk" || it.extension == "aab" }
            .maxByOrNull { it.lastModified() }
    }
}
