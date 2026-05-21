package com.releaseflow.storage

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.microsoft.aad.msal4j.IAuthenticationResult
import com.microsoft.aad.msal4j.InteractiveRequestParameters
import com.microsoft.aad.msal4j.ITokenCacheAccessAspect
import com.microsoft.aad.msal4j.ITokenCacheAccessContext
import com.microsoft.aad.msal4j.PublicClientApplication
import com.microsoft.aad.msal4j.SilentParameters
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.Base64

/**
 * Uploads files to Microsoft OneDrive using MSAL OAuth 2.0 with a one-time browser sign-in.
 *
 * Works with **free personal Microsoft accounts** (outlook.com / live.com), as well as
 * Microsoft 365 work/school accounts and SharePoint sites.
 *
 * The token is cached at `~/.releaseflow/onedrive-token-cache.json` and auto-refreshed.
 * Users authenticate once via `./gradlew releaseFlowLoginOneDrive`.
 *
 * Uses the Microsoft Graph REST API directly (no Graph SDK dependency to keep size down).
 */
class OneDriveUploader {

    companion object {
        /**
         * Azure App Registration Client ID for the ReleaseFlow plugin.
         *
         * **Maintainer setup (one time, all free):**
         * 1. Go to https://portal.azure.com → Azure Active Directory → App registrations
         * 2. **New registration**:
         *    - Name: `ReleaseFlow`
         *    - Supported account types: **Personal Microsoft accounts and any Azure AD directory** (multitenant + personal)
         *    - Redirect URI: **Public client/native (mobile & desktop)** → `http://localhost:8989`
         * 3. After creation, go to **API permissions** → Add → Microsoft Graph → Delegated:
         *    - `Files.ReadWrite`
         *    - `offline_access`
         * 4. Copy the **Application (client) ID** below, OR override via gradle.properties:
         *    `rf.onedrive.clientId=...`
         */
        const val DEFAULT_CLIENT_ID = "be4018f8-427c-4953-84c6-585fa7266ce0"

        /** Multi-tenant authority — supports both personal Microsoft accounts and work/school accounts. */
        private const val AUTHORITY = "https://login.microsoftonline.com/common"

        /** Directory where OneDrive OAuth tokens are persisted. */
        val CREDENTIAL_DIR: File = File(System.getProperty("user.home"), ".releaseflow")

        /** Filename for the persisted MSAL token cache. */
        const val TOKEN_CACHE_FILENAME: String = "onedrive-token-cache.json"

        /** Default redirect URI for the OAuth callback (must match the Azure app registration). */
        const val DEFAULT_REDIRECT_URI: String = "http://localhost:8989"

        /** Microsoft Graph scopes needed for file upload + offline refresh. */
        private val SCOPES: Set<String> = setOf("Files.ReadWrite", "offline_access")

        private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    private fun tokenCacheFile(): File {
        CREDENTIAL_DIR.mkdirs()
        return File(CREDENTIAL_DIR, TOKEN_CACHE_FILENAME)
    }

    /**
     * Authorizes the user. Opens a browser for sign-in on first run; subsequent runs use the
     * cached refresh token silently.
     */
    fun authorize(
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: String = DEFAULT_REDIRECT_URI
    ): IAuthenticationResult {
        if (clientId.startsWith("YOUR_")) {
            throw IllegalStateException(
                "ReleaseFlow OneDrive client is not configured.\n" +
                "  → Plugin maintainer: register an app at https://portal.azure.com\n" +
                "  → Replace DEFAULT_CLIENT_ID in OneDriveUploader.kt\n" +
                "  → Or override per-project via gradle.properties: rf.onedrive.clientId=..."
            )
        }

        val cacheAspect = FileTokenCacheAspect(tokenCacheFile())

        val app = PublicClientApplication.builder(clientId)
            .authority(AUTHORITY)
            .setTokenCacheAccessAspect(cacheAspect)
            .build()

        // Try silent token acquisition first using any cached account
        val accounts = app.accounts.get()
        if (accounts.isNotEmpty()) {
            try {
                val silent = SilentParameters.builder(SCOPES, accounts.first()).build()
                return app.acquireTokenSilently(silent).get()
            } catch (e: Exception) {
                // Fall through to interactive
            }
        }

        // Interactive sign-in via local server
        val interactive = InteractiveRequestParameters.builder(URI(redirectUri))
            .scopes(SCOPES)
            .build()
        return app.acquireToken(interactive).get()
    }

    /** Returns true if a OneDrive token cache file exists locally. */
    fun hasCachedCredentials(): Boolean {
        val cache = File(CREDENTIAL_DIR, TOKEN_CACHE_FILENAME)
        return cache.exists() && cache.length() > 0
    }

    /** Deletes the cached OneDrive token. */
    fun logout(): Boolean = File(CREDENTIAL_DIR, TOKEN_CACHE_FILENAME).delete()

    /**
     * Uploads [artifact] to OneDrive in the folder identified by the shareable [folderUrl].
     * Auto-creates `projectName/envName/year/month` subfolders inside it.
     */
    fun upload(
        artifact: File,
        folderUrl: String,
        projectName: String,
        envName: String,
        clientId: String = DEFAULT_CLIENT_ID
    ): UploadResult {
        val auth = authorize(clientId)
        val token = auth.accessToken()

        // Step 1: Resolve the shared folder URL → drive item + drive ID
        val sharedFolder = resolveSharedFolder(token, folderUrl)
        val driveId = sharedFolder["parentReference"].asJsonObject["driveId"].asString
        val rootFolderId = sharedFolder["id"].asString
        val rootFolderName = sharedFolder["name"].asString

        // Step 2: Ensure release/version/env subfolder hierarchy
        val versionName = extractVersionName(artifact.name)
        val subSegments = listOf(projectName, versionName, envName)
        val targetFolderId = ensureSubfolders(token, driveId, rootFolderId, subSegments)
        val folderPath = "$rootFolderName/${subSegments.joinToString("/")}"

        // Step 3: Upload file (small → PUT, large → upload session)
        val uploadedItem = if (artifact.length() < 4 * 1024 * 1024) {
            uploadSmall(token, driveId, targetFolderId, artifact)
        } else {
            uploadLarge(token, driveId, targetFolderId, artifact)
        }

        // Step 4: Create anonymous view link
        val itemId = uploadedItem["id"].asString
        val shareLink = createAnonymousLink(token, driveId, itemId)

        // OneDrive's webUrl is browser-viewable. For direct download we use the `?download=1` trick.
        val viewLink = shareLink
        val downloadLink = appendDownloadParam(shareLink)

        return UploadResult(
            viewLink = viewLink,
            downloadLink = downloadLink,
            folderPath = folderPath
        )
    }

    // ============ Graph REST helpers ============

    private fun extractVersionName(filename: String): String =
        Regex("""v(\d+\.\d+(?:\.\d+)*)""").find(filename)?.groupValues?.get(1) ?: "unknown"

    private fun resolveSharedFolder(token: String, folderUrl: String): JsonObject {
        // Microsoft Graph uses a special URL-safe base64 encoding for sharing URLs.
        // See https://docs.microsoft.com/en-us/graph/api/shares-get
        val encoded = "u!" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(folderUrl.toByteArray(Charsets.UTF_8))

        val response = sendJson(
            HttpRequest.newBuilder()
                .uri(URI("$GRAPH_BASE/shares/$encoded/driveItem"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
        )
        return response
    }

    private fun ensureSubfolders(
        token: String,
        driveId: String,
        parentId: String,
        segments: List<String>
    ): String {
        var currentParent = parentId
        for (segment in segments) {
            currentParent = ensureSingleFolder(token, driveId, currentParent, segment)
        }
        return currentParent
    }

    private fun ensureSingleFolder(
        token: String,
        driveId: String,
        parentId: String,
        folderName: String
    ): String {
        // Look up by name first
        val lookup = sendJson(
            HttpRequest.newBuilder()
                .uri(URI("$GRAPH_BASE/drives/$driveId/items/$parentId/children?\$filter=name eq '$folderName'&\$select=id,name"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build(),
            failOnError = false
        )
        val existing = lookup.getAsJsonArray("value")
        if (existing != null && existing.size() > 0) {
            return existing[0].asJsonObject["id"].asString
        }

        // Create new folder
        val body = """{"name":"${jsonEscape(folderName)}","folder":{},"@microsoft.graph.conflictBehavior":"rename"}"""
        val created = sendJson(
            HttpRequest.newBuilder()
                .uri(URI("$GRAPH_BASE/drives/$driveId/items/$parentId/children"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        )
        return created["id"].asString
    }

    private fun uploadSmall(
        token: String,
        driveId: String,
        folderId: String,
        artifact: File
    ): JsonObject {
        val encodedName = URLEncoder.encode(artifact.name, "UTF-8").replace("+", "%20")
        return sendJson(
            HttpRequest.newBuilder()
                .uri(URI("$GRAPH_BASE/drives/$driveId/items/$folderId:/$encodedName:/content"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(artifact.toPath()))
                .build()
        )
    }

    private fun uploadLarge(
        token: String,
        driveId: String,
        folderId: String,
        artifact: File
    ): JsonObject {
        val encodedName = URLEncoder.encode(artifact.name, "UTF-8").replace("+", "%20")

        // Step 1: Create upload session
        val sessionBody = """{"item":{"@microsoft.graph.conflictBehavior":"rename","name":"${jsonEscape(artifact.name)}"}}"""
        val session = sendJson(
            HttpRequest.newBuilder()
                .uri(URI("$GRAPH_BASE/drives/$driveId/items/$folderId:/$encodedName:/createUploadSession"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(sessionBody))
                .build()
        )
        val uploadUrl = session["uploadUrl"].asString

        // Step 2: Upload chunks (10 MB at a time, max recommended by Microsoft for reliability)
        val chunkSize = 10 * 1024 * 1024
        val total = artifact.length()
        var offset = 0L

        Files.newInputStream(artifact.toPath()).use { stream ->
            val buffer = ByteArray(chunkSize)
            while (offset < total) {
                val read = stream.read(buffer)
                if (read <= 0) break
                val end = offset + read - 1
                val chunk = buffer.copyOfRange(0, read)

                val response = httpClient.send(
                    HttpRequest.newBuilder()
                        .uri(URI(uploadUrl))
                        .header("Content-Length", read.toString())
                        .header("Content-Range", "bytes $offset-$end/$total")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(chunk))
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                )

                if (response.statusCode() !in 200..299) {
                    throw IllegalStateException("OneDrive chunk upload failed (HTTP ${response.statusCode()}): ${response.body()}")
                }

                offset += read

                // Final chunk returns the DriveItem JSON
                if (offset >= total) {
                    return JsonParser.parseString(response.body()).asJsonObject
                }
            }
        }
        throw IllegalStateException("Upload completed but no final DriveItem was returned")
    }

    private fun createAnonymousLink(token: String, driveId: String, itemId: String): String {
        val body = """{"type":"view","scope":"anonymous"}"""
        val response = sendJson(
            HttpRequest.newBuilder()
                .uri(URI("$GRAPH_BASE/drives/$driveId/items/$itemId/createLink"))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        )
        return response.getAsJsonObject("link")["webUrl"].asString
    }

    private fun appendDownloadParam(url: String): String =
        if (url.contains("?")) "$url&download=1" else "$url?download=1"

    // ============ HTTP helper ============

    private fun sendJson(request: HttpRequest, failOnError: Boolean = true): JsonObject {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            if (!failOnError) return JsonObject()
            throw IllegalStateException(
                "Microsoft Graph request failed (HTTP ${response.statusCode()})\n" +
                "  URL: ${request.uri()}\n" +
                "  Body: ${response.body().take(500)}"
            )
        }
        return JsonParser.parseString(response.body()).asJsonObject
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    /**
     * Persists the MSAL token cache to disk so subsequent runs can refresh silently.
     */
    private class FileTokenCacheAspect(private val file: File) : ITokenCacheAccessAspect {
        override fun beforeCacheAccess(context: ITokenCacheAccessContext) {
            if (file.exists() && file.length() > 0) {
                try {
                    context.tokenCache().deserialize(file.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    // Corrupt cache — ignore and let MSAL prompt for fresh auth
                }
            }
        }

        override fun afterCacheAccess(context: ITokenCacheAccessContext) {
            if (context.hasCacheChanged()) {
                file.writeText(context.tokenCache().serialize(), Charsets.UTF_8)
            }
        }
    }
}
