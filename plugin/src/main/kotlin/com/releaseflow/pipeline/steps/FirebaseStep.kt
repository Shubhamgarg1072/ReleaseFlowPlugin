package com.releaseflow.pipeline.steps

import com.releaseflow.EnvironmentConfig
import com.releaseflow.pipeline.StepResult
import com.releaseflow.storage.FirebaseUploader
import com.releaseflow.util.Logger
import com.google.gson.JsonParser
import java.io.File

/**
 * Uploads the release artifact to Firebase App Distribution and distributes it to testers.
 *
 * Runs alongside the Drive/OneDrive upload step — both can be active for the same environment.
 * Skipped automatically if [EnvironmentConfig.firebaseAppId] is blank.
 *
 * Requires a Firebase service account JSON with the Firebase App Distribution role.
 */
class FirebaseStep(
    private val envConfig: EnvironmentConfig,
    private val artifact: File,
    private val projectRootDir: File,
    private val changelog: List<String>,
    private val dryRun: Boolean
) {

    fun execute(): StepResult {
        if (envConfig.firebaseServiceAccountJson.isBlank()) {
            return StepResult.Skipped("firebaseServiceAccountJson not set — skipping Firebase App Distribution")
        }

        val serviceAccountFile = File(projectRootDir, envConfig.firebaseServiceAccountJson)
        if (!serviceAccountFile.exists()) {
            return StepResult.Failure(
                "Firebase service account JSON not found at '${serviceAccountFile.absolutePath}'\n" +
                "  → Download it from Firebase Console → Project Settings → Service accounts"
            )
        }

        // Auto-detect app ID from google-services.json if not explicitly set
        val appId = envConfig.firebaseAppId.ifBlank { readAppIdFromGoogleServices() }
            ?: return StepResult.Failure(
                "Could not determine Firebase app ID.\n" +
                "  → Either set firebaseAppId in your environment config\n" +
                "  → Or ensure app/google-services.json exists in your project"
            )

        val releaseNotes = buildReleaseNotes()
        val testerSummary = buildTesterSummary()

        Logger.step("Firebase: uploading ${artifact.name}${if (testerSummary.isNotBlank()) " → $testerSummary" else ""}")

        if (dryRun) {
            Logger.warn("[DRY RUN] Would upload to Firebase App Distribution (appId: $appId)")
            return StepResult.Success(Unit)
        }

        return try {
            val result = FirebaseUploader(serviceAccountFile.absolutePath).upload(
                artifact      = artifact,
                appId         = appId,
                testerEmails  = envConfig.firebaseTesterEmails,
                groupAliases  = envConfig.firebaseGroups,
                releaseNotes  = releaseNotes
            )
            Logger.ok("Firebase: uploaded — ${result.firebaseConsoleUri ?: result.releaseName}")
            StepResult.Success(result)
        } catch (e: Exception) {
            StepResult.Failure(
                "Firebase App Distribution upload failed: ${e.message}\n" +
                "  → Verify firebaseAppId is correct (format: 1:PROJECT_NUMBER:android:APP_HASH)\n" +
                "  → Verify the service account has the 'Firebase App Distribution Admin' role\n" +
                "  → Verify Firebase App Distribution is enabled in the Firebase Console",
                cause = e
            )
        }
    }

    private fun readAppIdFromGoogleServices(): String? {
        val googleServicesFile = File(projectRootDir, "app/google-services.json")
        if (!googleServicesFile.exists()) return null
        return try {
            val json = JsonParser.parseReader(googleServicesFile.reader()).asJsonObject
            json.getAsJsonArray("client")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("client_info")
                ?.get("mobilesdk_app_id")
                ?.asString
        } catch (e: Exception) {
            null
        }
    }

    private fun buildReleaseNotes(): String {
        // User-defined notes take priority; fall back to git changelog
        if (envConfig.firebaseReleaseNotes.isNotBlank()) return envConfig.firebaseReleaseNotes
        if (changelog.isEmpty()) return ""
        return changelog.joinToString("\n") { "• $it" }
    }

    private fun buildTesterSummary(): String = buildList {
        if (envConfig.firebaseTesterEmails.isNotEmpty()) add("${envConfig.firebaseTesterEmails.size} tester(s)")
        if (envConfig.firebaseGroups.isNotEmpty()) add("${envConfig.firebaseGroups.size} group(s)")
    }.joinToString(", ")
}
