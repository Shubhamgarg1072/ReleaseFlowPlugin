package com.releaseflow.pipeline.steps

import com.releaseflow.EnvironmentConfig
import com.releaseflow.EnvironmentConfig.CloudProvider
import com.releaseflow.pipeline.StepResult
import com.releaseflow.storage.DriveUploader
import com.releaseflow.storage.OAuthDriveUploader
import com.releaseflow.storage.OneDriveUploader
import com.releaseflow.storage.UploadResult
import com.releaseflow.util.Logger
import java.io.File

/**
 * Uploads the release artifact to cloud storage.
 *
 * **Provider is auto-detected from the URL:**
 * - `drive.google.com` URLs → Google Drive (OAuth via `releaseFlowLogin`)
 * - `1drv.ms` / `onedrive.live.com` / `sharepoint.com` URLs → OneDrive (OAuth via `releaseFlowLoginOneDrive`)
 *
 * Service Account (Google Drive only) is supported as a CI fallback via `driveServiceAccountJson`.
 */
class UploadStep(
    private val envConfig: EnvironmentConfig,
    private val projectName: String,
    private val artifact: File,
    private val projectRootDir: File,
    private val dryRun: Boolean,
    private val googleClientId: String = OAuthDriveUploader.DEFAULT_CLIENT_ID,
    private val googleClientSecret: String = OAuthDriveUploader.DEFAULT_CLIENT_SECRET,
    private val oneDriveClientId: String = OneDriveUploader.DEFAULT_CLIENT_ID
) {

    fun execute(): StepResult {
        if (envConfig.driveFolderUrl.isBlank() && envConfig.driveServiceAccountJson.isBlank()) {
            return StepResult.Skipped("driveFolderUrl not set — skipping cloud upload")
        }

        val provider = envConfig.cloudProvider()
        Logger.step("Upload: ${artifact.name} → ${provider.displayName()}")

        if (dryRun) {
            Logger.warn("[DRY RUN] Would upload ${artifact.name} to ${provider.displayName()}")
            return StepResult.Success(
                UploadResult(
                    viewLink = "https://dry-run/view",
                    downloadLink = "https://dry-run/download",
                    folderPath = "$projectName/${envConfig.name}/2025/May"
                )
            )
        }

        return try {
            val result = when (provider) {
                CloudProvider.GOOGLE_DRIVE -> uploadToGoogleDrive()
                CloudProvider.ONE_DRIVE    -> uploadToOneDrive()
                CloudProvider.UNKNOWN      -> return StepResult.Failure(
                    "Unrecognized cloud folder URL: '${envConfig.driveFolderUrl}'\n" +
                    "  Supported: Google Drive (drive.google.com), OneDrive (1drv.ms, onedrive.live.com, sharepoint.com)"
                )
            }
            Logger.ok("Uploaded: ${result.folderPath}")
            StepResult.Success(result)
        } catch (e: Exception) {
            StepResult.Failure(uploadErrorMessage(provider, e), cause = e)
        }
    }

    private fun uploadToGoogleDrive(): UploadResult {
        if (envConfig.driveServiceAccountJson.isNotBlank()) {
            val saPath = File(projectRootDir, envConfig.driveServiceAccountJson).absolutePath
            return DriveUploader(saPath).upload(
                artifact = artifact,
                rootFolder = envConfig.driveFolderId() ?: envConfig.driveFolderUrl,
                projectName = projectName,
                envName = envConfig.name
            )
        }

        val folderId = envConfig.driveFolderId()
            ?: throw IllegalStateException("Could not extract folder ID from Google Drive URL: '${envConfig.driveFolderUrl}'")

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
            clientId = googleClientId,
            clientSecret = googleClientSecret
        )
    }

    private fun uploadToOneDrive(): UploadResult {
        val uploader = OneDriveUploader()
        if (!uploader.hasCachedCredentials()) {
            throw IllegalStateException(
                "Not signed in to OneDrive.\n" +
                "  → Run this first: ./gradlew releaseFlowLoginOneDrive"
            )
        }
        return uploader.upload(
            artifact = artifact,
            folderUrl = envConfig.driveFolderUrl,
            projectName = projectName,
            envName = envConfig.name,
            clientId = oneDriveClientId
        )
    }

    private fun uploadErrorMessage(provider: CloudProvider, e: Exception): String = when (provider) {
        CloudProvider.GOOGLE_DRIVE -> """
            |Google Drive upload failed: ${e.message}
            |  → If you haven't signed in yet, run: ./gradlew releaseFlowLogin
            |  → If your session expired: ./gradlew releaseFlowLogout && ./gradlew releaseFlowLogin
            |  → Verify you have edit access to the folder
        """.trimMargin()
        CloudProvider.ONE_DRIVE -> """
            |OneDrive upload failed: ${e.message}
            |  → If you haven't signed in yet, run: ./gradlew releaseFlowLoginOneDrive
            |  → If your session expired: ./gradlew releaseFlowLogoutOneDrive && ./gradlew releaseFlowLoginOneDrive
            |  → Verify you have edit access to the OneDrive folder
        """.trimMargin()
        CloudProvider.UNKNOWN -> "Upload failed: ${e.message}"
    }

    private fun CloudProvider.displayName(): String = when (this) {
        CloudProvider.GOOGLE_DRIVE -> "Google Drive"
        CloudProvider.ONE_DRIVE    -> "OneDrive"
        CloudProvider.UNKNOWN      -> "Unknown"
    }
}
