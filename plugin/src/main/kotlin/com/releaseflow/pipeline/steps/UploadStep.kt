package com.releaseflow.pipeline.steps

import com.releaseflow.EnvironmentConfig
import com.releaseflow.pipeline.StepResult
import com.releaseflow.storage.DriveUploader
import com.releaseflow.storage.UploadResult
import com.releaseflow.util.Logger
import java.io.File

/**
 * Uploads the release artifact to Google Drive using a Service Account.
 */
class UploadStep(
    private val envConfig: EnvironmentConfig,
    private val projectName: String,
    private val artifact: File,
    private val dryRun: Boolean
) {

    fun execute(): StepResult {
        if (envConfig.driveRootFolder.isBlank()) {
            return StepResult.Skipped("driveRootFolder not configured — skipping Drive upload")
        }
        if (envConfig.driveCredentials.isBlank()) {
            return StepResult.Skipped(
                "driveCredentials not configured — skipping Drive upload\n" +
                "  → Set driveCredentials = \"drive-credentials.json\" in your releaseFlow block"
            )
        }

        Logger.step("Upload: ${artifact.name} → ${envConfig.driveRootFolder}")

        if (dryRun) {
            Logger.warn("[DRY RUN] Would upload ${artifact.name} to Google Drive folder '${envConfig.driveRootFolder}'")
            return StepResult.Success(
                UploadResult(
                    viewLink = "https://drive.google.com/dry-run-view-link",
                    downloadLink = "https://drive.google.com/dry-run-download-link",
                    folderPath = "${envConfig.driveRootFolder}/$projectName/${envConfig.name}/2025/May"
                )
            )
        }

        return try {
            val uploader = DriveUploader(envConfig.driveCredentials)
            val result: UploadResult = uploader.upload(
                artifact = artifact,
                rootFolder = envConfig.driveRootFolder,
                projectName = projectName,
                envName = envConfig.name
            )
            Logger.ok("Uploaded to Drive: ${result.folderPath}")
            StepResult.Success(result)
        } catch (e: Exception) {
            StepResult.Failure(
                "Drive upload failed: ${e.message}\n" +
                "  Artifact: ${artifact.absolutePath}\n" +
                "  Credentials: ${envConfig.driveCredentials}\n" +
                "  → Verify the Service Account JSON is valid and not expired\n" +
                "  → Share the target Drive folder with the service account email\n" +
                "  → Ensure the Google Drive API is enabled in your Cloud project",
                cause = e
            )
        }
    }
}
