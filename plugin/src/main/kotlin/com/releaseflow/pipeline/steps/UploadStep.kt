package com.releaseflow.pipeline.steps

import com.releaseflow.EnvironmentConfig
import com.releaseflow.pipeline.StepResult
import com.releaseflow.storage.DriveUploader
import com.releaseflow.storage.OAuthDriveUploader
import com.releaseflow.storage.UploadResult
import com.releaseflow.util.Logger
import java.io.File

/**
 * Uploads the release artifact to Google Drive.
 *
 * **Default:** OAuth user token from `./gradlew releaseFlowLogin`.
 * **Fallback:** Service Account JSON, when [EnvironmentConfig.driveServiceAccountJson] is set.
 */
class UploadStep(
    private val envConfig: EnvironmentConfig,
    private val projectName: String,
    private val artifact: File,
    private val projectRootDir: File,
    private val dryRun: Boolean,
    private val oauthClientId: String = OAuthDriveUploader.DEFAULT_CLIENT_ID,
    private val oauthClientSecret: String = OAuthDriveUploader.DEFAULT_CLIENT_SECRET
) {

    fun execute(): StepResult {
        if (envConfig.driveFolderUrl.isBlank() && envConfig.driveServiceAccountJson.isBlank()) {
            return StepResult.Skipped("driveFolderUrl not set — skipping Drive upload")
        }

        val folderId = envConfig.driveFolderId()
        if (folderId == null && envConfig.driveServiceAccountJson.isBlank()) {
            return StepResult.Failure(
                "driveFolderUrl is set but no valid folder ID could be extracted from it.\n" +
                "  Got: '${envConfig.driveFolderUrl}'\n" +
                "  → Open the folder in Google Drive in your browser\n" +
                "  → Copy the URL — it should contain '/folders/<id>'\n" +
                "  → Set: driveFolderUrl = \"https://drive.google.com/drive/folders/<id>\""
            )
        }

        Logger.step("Upload: ${artifact.name} → Drive folder ${folderId ?: "(service-account mode)"}")

        if (dryRun) {
            Logger.warn("[DRY RUN] Would upload ${artifact.name} to Drive folder $folderId")
            return StepResult.Success(
                UploadResult(
                    viewLink = "https://drive.google.com/dry-run-view-link",
                    downloadLink = "https://drive.google.com/dry-run-download-link",
                    folderPath = "$projectName/${envConfig.name}/2025/May"
                )
            )
        }

        return try {
            val result = if (envConfig.driveServiceAccountJson.isNotBlank()) {
                uploadWithServiceAccount(folderId)
            } else {
                uploadWithOAuth(folderId!!)
            }
            Logger.ok("Uploaded to Drive: ${result.folderPath}")
            StepResult.Success(result)
        } catch (e: Exception) {
            StepResult.Failure(
                "Drive upload failed: ${e.message}\n" +
                "  → If you haven't signed in yet, run: ./gradlew releaseFlowLogin\n" +
                "  → If your session expired, run: ./gradlew releaseFlowLogout && ./gradlew releaseFlowLogin\n" +
                "  → Verify the folder URL is correct and you have edit access to it",
                cause = e
            )
        }
    }

    private fun uploadWithOAuth(folderId: String): UploadResult {
        val uploader = OAuthDriveUploader()
        if (!uploader.hasCachedCredentials()) {
            throw IllegalStateException(
                "Not signed in to Google Drive.\n" +
                "  → Run this first: ./gradlew releaseFlowLogin"
            )
        }
        return uploader.upload(
            artifact = artifact,
            folderId = folderId,
            projectName = projectName,
            envName = envConfig.name,
            clientId = oauthClientId,
            clientSecret = oauthClientSecret
        )
    }

    private fun uploadWithServiceAccount(folderId: String?): UploadResult {
        val saPath = File(projectRootDir, envConfig.driveServiceAccountJson).absolutePath
        val uploader = DriveUploader(saPath)
        // Service Account uploader uses its own folder structure ("rootFolder/project/env/year/month")
        // by name lookup, so we pass the original driveFolderUrl as the root folder name.
        return uploader.upload(
            artifact = artifact,
            rootFolder = folderId ?: envConfig.driveFolderUrl,
            projectName = projectName,
            envName = envConfig.name
        )
    }
}
