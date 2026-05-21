package com.releaseflow.storage

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.model.Permission
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.io.File
import java.io.FileInputStream

/**
 * Uploads a file to Google Drive using a Service Account JSON key.
 *
 * Folder hierarchy: `{rootFolder}/{projectName}/{envName}/{year}/{month}`
 * Example:          `QA Builds/MyApp/qa/2025/May`
 *
 * After upload, sets a public reader permission so recipients can open the link
 * without needing a Google account.
 */
class DriveUploader(private val credentialsPath: String) {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = GoogleNetHttpTransport.newTrustedTransport()

    /**
     * Uploads [artifact] to the Drive folder hierarchy and returns an [UploadResult]
     * with shareable view and download links.
     */
    fun upload(
        artifact: File,
        rootFolder: String,
        projectName: String,
        envName: String
    ): UploadResult {
        val service = buildDriveService()
        val versionName = extractVersionName(artifact.name)
        val folderPath = "$rootFolder/$projectName/$versionName/$envName"
        val folderId = ensureFolderPath(service, listOf(rootFolder, projectName, versionName, envName))

        val fileMetadata = DriveFile().apply {
            name = artifact.name
            parents = listOf(folderId)
        }

        val mediaContent = com.google.api.client.http.FileContent(detectMimeType(artifact), artifact)

        val uploaded = service.files().create(fileMetadata, mediaContent)
            .setFields("id, webViewLink, webContentLink")
            .execute()

        // Make the file publicly readable
        val permission = Permission().apply {
            type = "anyone"
            role = "reader"
        }
        service.permissions().create(uploaded.id, permission).execute()

        // Re-fetch with links since webContentLink may not appear on creation
        val fileWithLinks = service.files().get(uploaded.id)
            .setFields("id, webViewLink, webContentLink, name")
            .execute()

        val viewLink = fileWithLinks.webViewLink ?: "https://drive.google.com/file/d/${uploaded.id}/view"
        val downloadLink = "https://drive.google.com/uc?export=download&id=${uploaded.id}"

        return UploadResult(
            viewLink = viewLink,
            downloadLink = downloadLink,
            folderPath = folderPath
        )
    }

    private fun extractVersionName(filename: String): String =
        Regex("""v(\d+\.\d+(?:\.\d+)*)""").find(filename)?.groupValues?.get(1) ?: "unknown"

    private fun buildDriveService(): Drive {
        val credFile = File(credentialsPath)
        if (!credFile.exists()) {
            throw IllegalArgumentException(
                "Service Account credentials file not found at '${credFile.absolutePath}'\n" +
                "  → Download a Service Account JSON from https://console.cloud.google.com\n" +
                "  → Enable the Google Drive API in your Cloud project\n" +
                "  → Share the target Drive folder with the service account email"
            )
        }

        val credentials = FileInputStream(credFile).use { stream ->
            GoogleCredentials.fromStream(stream).createScoped(listOf(DriveScopes.DRIVE))
        }

        return Drive.Builder(transport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName("ReleaseFlow")
            .build()
    }

    /**
     * Walks the folder path, creating any folder that doesn't already exist.
     * Returns the Drive folder ID of the deepest folder.
     */
    private fun ensureFolderPath(service: Drive, segments: List<String>): String {
        var parentId = "root"

        for (segment in segments) {
            val query = "name = '${segment.replace("'", "\\'")}' and mimeType = 'application/vnd.google-apps.folder'" +
                " and '$parentId' in parents and trashed = false"

            val result = service.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .execute()

            parentId = if (result.files.isNotEmpty()) {
                result.files.first().id
            } else {
                val folderMeta = DriveFile().apply {
                    name = segment
                    mimeType = "application/vnd.google-apps.folder"
                    parents = listOf(parentId)
                }
                service.files().create(folderMeta)
                    .setFields("id")
                    .execute().id
            }
        }

        return parentId
    }

    private fun detectMimeType(file: File): String = when (file.extension.lowercase()) {
        "aab" -> "application/octet-stream"
        "apk" -> "application/vnd.android.package-archive"
        else  -> "application/octet-stream"
    }
}

/**
 * Result of a successful Drive upload.
 */
data class UploadResult(
    /** URL to view the file in Google Drive (requires no sign-in after permission grant). */
    val viewLink: String,
    /** Direct download URL. */
    val downloadLink: String,
    /** Human-readable Drive folder path, e.g. `QA Builds/MyApp/qa/2025/May`. */
    val folderPath: String
)
