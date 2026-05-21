package com.releaseflow.storage

import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Uploads APKs/AABs to Firebase App Distribution using a service account JSON.
 *
 * Authenticates via Google OAuth2 service account, uploads the artifact,
 * waits for Firebase to process it, optionally sets release notes,
 * then distributes to specified tester emails and/or groups.
 *
 * Users get a Firebase invite email with a download link.
 */
class FirebaseUploader(private val serviceAccountJsonPath: String) {

    private val gson = Gson()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    data class FirebaseUploadResult(
        val releaseName: String,
        val firebaseConsoleUri: String?
    )

    /**
     * Uploads [artifact] to Firebase App Distribution for [appId], distributes to
     * [testerEmails] and [groupAliases], and sets [releaseNotes] on the release.
     */
    fun upload(
        artifact: File,
        appId: String,
        testerEmails: List<String> = emptyList(),
        groupAliases: List<String> = emptyList(),
        releaseNotes: String = ""
    ): FirebaseUploadResult {
        val token = getAccessToken()
        val projectNumber = extractProjectNumber(appId)
        val encodedAppId = URLEncoder.encode(appId, "UTF-8")

        val operationName = uploadArtifact(token, projectNumber, encodedAppId, artifact)
        val releaseName = waitForProcessing(token, operationName)

        if (releaseNotes.isNotBlank()) {
            updateReleaseNotes(token, releaseName, releaseNotes)
        }

        if (testerEmails.isNotEmpty() || groupAliases.isNotEmpty()) {
            distribute(token, releaseName, testerEmails, groupAliases)
        }

        val consoleUri = "https://console.firebase.google.com/project/_/appdistribution/app/$appId/releases"
        return FirebaseUploadResult(releaseName = releaseName, firebaseConsoleUri = consoleUri)
    }

    private fun getAccessToken(): String {
        val credentials = GoogleCredentials
            .fromStream(FileInputStream(serviceAccountJsonPath))
            .createScoped("https://www.googleapis.com/auth/firebase")
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }

    private fun extractProjectNumber(appId: String): String {
        // appId format: 1:PROJECT_NUMBER:android:APP_HASH
        val parts = appId.split(":")
        require(parts.size >= 2) { "Invalid Firebase appId format: '$appId'. Expected format: 1:PROJECT_NUMBER:android:APP_HASH" }
        return parts[1]
    }

    private fun uploadArtifact(token: String, projectNumber: String, encodedAppId: String, artifact: File): String {
        val url = "https://firebaseappdistribution.googleapis.com/upload/v1/projects/$projectNumber/apps/$encodedAppId/releases:upload"

        val request = HttpRequest.newBuilder()
            .uri(URI(url))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/octet-stream")
            .header("X-Goog-Upload-File-Name", artifact.name)
            .header("X-Goog-Upload-Protocol", "raw")
            .POST(HttpRequest.BodyPublishers.ofFile(artifact.toPath()))
            .timeout(Duration.ofMinutes(10))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Firebase upload failed (${response.statusCode()}): ${response.body()}"
        }

        val json = JsonParser.parseString(response.body()).asJsonObject
        return json.get("name").asString
    }

    private fun waitForProcessing(token: String, operationName: String, maxWaitSeconds: Int = 300): String {
        val url = "https://firebaseappdistribution.googleapis.com/v1/$operationName"
        val deadline = System.currentTimeMillis() + maxWaitSeconds * 1000L

        while (System.currentTimeMillis() < deadline) {
            val request = HttpRequest.newBuilder()
                .uri(URI(url))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val json = JsonParser.parseString(response.body()).asJsonObject

            if (json.get("done")?.asBoolean == true) {
                val response2 = json.getAsJsonObject("response")
                return response2?.get("name")?.asString
                    ?: throw IllegalStateException("Firebase processing done but release name missing: ${response.body()}")
            }

            Thread.sleep(3000)
        }

        throw IllegalStateException("Timed out waiting for Firebase App Distribution to process the upload (${maxWaitSeconds}s)")
    }

    private fun updateReleaseNotes(token: String, releaseName: String, notes: String) {
        val url = "https://firebaseappdistribution.googleapis.com/v1/$releaseName?updateMask=releaseNotes"
        val body = gson.toJson(mapOf("releaseNotes" to mapOf("text" to notes)))

        val request = HttpRequest.newBuilder()
            .uri(URI(url))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Firebase update release notes failed (${response.statusCode()}): ${response.body()}"
        }
    }

    private fun distribute(token: String, releaseName: String, testerEmails: List<String>, groupAliases: List<String>) {
        val url = "https://firebaseappdistribution.googleapis.com/v1/$releaseName:distribute"
        val bodyMap = mutableMapOf<String, Any>()
        if (testerEmails.isNotEmpty()) bodyMap["testerEmails"] = testerEmails
        if (groupAliases.isNotEmpty()) bodyMap["groupAliases"] = groupAliases

        val request = HttpRequest.newBuilder()
            .uri(URI(url))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(bodyMap)))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Firebase distribute failed (${response.statusCode()}): ${response.body()}"
        }
    }
}
