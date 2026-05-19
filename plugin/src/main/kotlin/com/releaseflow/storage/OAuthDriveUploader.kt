package com.releaseflow.storage

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.Permission
import com.google.api.services.drive.model.File as DriveFile
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Uploads files to Google Drive using OAuth 2.0 with a one-time browser sign-in.
 *
 * The token is cached at `~/.releaseflow/StoredCredential` and auto-refreshed.
 * Users authenticate once via `./gradlew releaseFlowLogin`; all subsequent uploads use the cached token.
 *
 * No service account, no folder sharing, no Google Cloud Console setup required by end users —
 * the plugin maintainer ships pre-configured OAuth client credentials.
 */
class OAuthDriveUploader {

    companion object {
        /**
         * OAuth 2.0 Client ID for the ReleaseFlow plugin.
         *
         * For "Installed Application" OAuth clients, the client_secret is not actually secret per
         * Google's guidance: https://developers.google.com/identity/protocols/oauth2/native-app
         *
         * **Maintainer setup (one time):**
         * 1. Go to https://console.cloud.google.com → APIs & Services → Credentials
         * 2. Create OAuth Client ID → Application type: Desktop App
         * 3. Copy the client ID and secret below, OR override via gradle.properties:
         *    `rf.oauth.clientId=...` and `rf.oauth.clientSecret=...`
         */
        const val DEFAULT_CLIENT_ID = "YOUR_OAUTH_CLIENT_ID.apps.googleusercontent.com"
        const val DEFAULT_CLIENT_SECRET = "YOUR_OAUTH_CLIENT_SECRET"

        /** Directory where OAuth tokens are persisted (in the user's home folder). */
        val CREDENTIAL_DIR: File = File(System.getProperty("user.home"), ".releaseflow")

        private val SCOPES = listOf(DriveScopes.DRIVE_FILE)
        private val APPLICATION_NAME = "ReleaseFlow"
    }

    private val transport = GoogleNetHttpTransport.newTrustedTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    /**
     * Authorizes the user — opens a browser, prompts for sign-in, persists the refresh token.
     * Returns immediately if a valid cached token already exists.
     *
     * @param clientId     OAuth Client ID, defaults to the embedded plugin credential.
     * @param clientSecret OAuth Client Secret, defaults to the embedded plugin credential.
     * @param port         Local port to use for the OAuth redirect listener (default: 8888).
     */
    fun authorize(
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET,
        port: Int = 8888
    ): Credential {
        if (clientId == DEFAULT_CLIENT_ID && clientId.startsWith("YOUR_")) {
            throw IllegalStateException(
                "ReleaseFlow OAuth client is not configured.\n" +
                "  → Plugin maintainer: create an OAuth Client (Desktop App) at\n" +
                "    https://console.cloud.google.com/apis/credentials\n" +
                "  → Replace DEFAULT_CLIENT_ID and DEFAULT_CLIENT_SECRET in OAuthDriveUploader.kt\n" +
                "  → Or override per-project via gradle.properties:\n" +
                "      rf.oauth.clientId=...\n" +
                "      rf.oauth.clientSecret=..."
            )
        }

        val clientSecrets = GoogleClientSecrets().setInstalled(
            GoogleClientSecrets.Details().apply {
                this.clientId = clientId
                this.clientSecret = clientSecret
            }
        )

        CREDENTIAL_DIR.mkdirs()
        val flow = GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory, clientSecrets, SCOPES)
            .setDataStoreFactory(FileDataStoreFactory(CREDENTIAL_DIR))
            .setAccessType("offline")
            .build()

        val receiver = LocalServerReceiver.Builder().setPort(port).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    /**
     * Returns true if a cached OAuth token exists. Does not validate that it is still valid.
     */
    fun hasCachedCredentials(): Boolean =
        File(CREDENTIAL_DIR, "StoredCredential").exists()

    /**
     * Deletes the cached OAuth token, forcing the next [authorize] call to re-prompt for login.
     */
    fun logout(): Boolean =
        File(CREDENTIAL_DIR, "StoredCredential").delete()

    /**
     * Uploads [artifact] into the Drive folder identified by [folderId], inside a
     * `projectName/envName/year/month` subfolder hierarchy. Auto-creates missing subfolders.
     *
     * Sets the file's permission to "anyone with link → reader" so email recipients can download
     * without signing in.
     */
    fun upload(
        artifact: File,
        folderId: String,
        projectName: String,
        envName: String,
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String = DEFAULT_CLIENT_SECRET
    ): UploadResult {
        val credential = authorize(clientId, clientSecret)
        val service = Drive.Builder(transport, jsonFactory, credential)
            .setApplicationName(APPLICATION_NAME)
            .build()

        val now = LocalDateTime.now()
        val monthName = now.format(DateTimeFormatter.ofPattern("MMMM"))
        val subPath = listOf(projectName, envName, now.year.toString(), monthName)

        val rootFolderName = lookupFolderName(service, folderId)
        val folderPath = "$rootFolderName/${subPath.joinToString("/")}"
        val targetFolderId = ensureSubfolders(service, folderId, subPath)

        val fileMetadata = DriveFile().apply {
            name = artifact.name
            parents = listOf(targetFolderId)
        }
        val mediaContent = FileContent(detectMimeType(artifact), artifact)

        val uploaded = service.files().create(fileMetadata, mediaContent)
            .setFields("id, webViewLink")
            .execute()

        // Make the file readable by anyone with the link
        val permission = Permission().apply {
            type = "anyone"
            role = "reader"
        }
        service.permissions().create(uploaded.id, permission).execute()

        val fileWithLinks = service.files().get(uploaded.id)
            .setFields("id, webViewLink")
            .execute()

        val viewLink = fileWithLinks.webViewLink ?: "https://drive.google.com/file/d/${uploaded.id}/view"
        val downloadLink = "https://drive.google.com/uc?export=download&id=${uploaded.id}"

        return UploadResult(
            viewLink = viewLink,
            downloadLink = downloadLink,
            folderPath = folderPath
        )
    }

    private fun lookupFolderName(service: Drive, folderId: String): String =
        try {
            service.files().get(folderId).setFields("name").execute().name ?: "Drive"
        } catch (e: Exception) {
            "Drive"
        }

    /**
     * Walks [segments] under [rootFolderId], creating any folder that doesn't already exist.
     * Returns the ID of the deepest folder.
     */
    private fun ensureSubfolders(service: Drive, rootFolderId: String, segments: List<String>): String {
        var parentId = rootFolderId
        for (segment in segments) {
            val escapedName = segment.replace("'", "\\'")
            val query = "name = '$escapedName' and mimeType = 'application/vnd.google-apps.folder'" +
                " and '$parentId' in parents and trashed = false"

            val existing = service.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .execute()

            parentId = if (existing.files.isNotEmpty()) {
                existing.files.first().id
            } else {
                val folderMeta = DriveFile().apply {
                    name = segment
                    mimeType = "application/vnd.google-apps.folder"
                    parents = listOf(parentId)
                }
                service.files().create(folderMeta).setFields("id").execute().id
            }
        }
        return parentId
    }

    private fun detectMimeType(file: File): String = when (file.extension.lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "aab" -> "application/octet-stream"
        else  -> "application/octet-stream"
    }
}
