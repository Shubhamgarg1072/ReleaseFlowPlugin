package com.releaseflow.tasks

import com.releaseflow.storage.OAuthDriveUploader
import com.releaseflow.util.Logger
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * One-time Google Drive sign-in task.
 *
 * Run with: `./gradlew releaseFlowLogin`
 *
 * Opens a browser to Google's OAuth consent screen. After sign-in, a refresh token is
 * saved to `~/.releaseflow/StoredCredential` and used automatically by all future
 * `releaseFlowDeploy*` runs.
 *
 * Optional Gradle properties:
 * - `-Prf.oauth.clientId=...`     — override the default OAuth client ID
 * - `-Prf.oauth.clientSecret=...` — override the default OAuth client secret
 * - `-Prf.oauth.port=8888`        — local port for the OAuth callback listener
 */
abstract class ReleaseFlowLoginTask : DefaultTask() {

    @get:Internal
    var clientIdOverride: String = ""

    @get:Internal
    var clientSecretOverride: String = ""

    @get:Internal
    var port: Int = 8888

    @TaskAction
    fun login() {
        Logger.header("ReleaseFlow Login")

        val uploader = OAuthDriveUploader()
        if (uploader.hasCachedCredentials()) {
            Logger.warn("You are already signed in. Token cached at ~/.releaseflow/")
            Logger.step("To sign in as a different account, run: ./gradlew releaseFlowLogout")
            return
        }

        Logger.step("Opening your browser for Google sign-in...")
        Logger.step("(If the browser doesn't open automatically, copy the URL printed below)")

        val clientId = clientIdOverride.ifBlank { OAuthDriveUploader.DEFAULT_CLIENT_ID }
        val clientSecret = clientSecretOverride.ifBlank { OAuthDriveUploader.DEFAULT_CLIENT_SECRET }

        uploader.authorize(clientId = clientId, clientSecret = clientSecret, port = port)

        Logger.ok("Sign-in successful — token saved to ~/.releaseflow/")
        Logger.step("You can now run: ./gradlew releaseFlowDeployQa")
    }
}
