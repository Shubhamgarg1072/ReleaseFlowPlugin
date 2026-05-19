package com.releaseflow.tasks

import com.releaseflow.storage.OneDriveUploader
import com.releaseflow.util.Logger
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * One-time Microsoft OneDrive sign-in task.
 *
 * Run with: `./gradlew releaseFlowLoginOneDrive`
 *
 * Opens a browser to Microsoft's OAuth consent screen. After sign-in, the token
 * is cached at `~/.releaseflow/onedrive-token-cache.json` and used automatically
 * by subsequent deploys whose `driveFolderUrl` points to OneDrive.
 *
 * Works with **free personal Microsoft accounts** (outlook.com, hotmail.com, live.com)
 * as well as Microsoft 365 work/school accounts.
 */
abstract class ReleaseFlowLoginOneDriveTask : DefaultTask() {

    @get:Internal
    var clientIdOverride: String = ""

    @get:Internal
    var redirectUriOverride: String = ""

    @TaskAction
    fun login() {
        Logger.header("ReleaseFlow Login (OneDrive)")

        val uploader = OneDriveUploader()
        if (uploader.hasCachedCredentials()) {
            Logger.warn("You are already signed in to OneDrive.")
            Logger.step("To switch accounts, run: ./gradlew releaseFlowLogoutOneDrive")
            return
        }

        Logger.step("Opening browser for Microsoft sign-in...")
        Logger.step("(If the browser doesn't open, copy the URL printed below into your browser)")

        val clientId = clientIdOverride.ifBlank { OneDriveUploader.DEFAULT_CLIENT_ID }
        val redirectUri = redirectUriOverride.ifBlank { OneDriveUploader.DEFAULT_REDIRECT_URI }

        uploader.authorize(clientId = clientId, redirectUri = redirectUri)

        Logger.ok("Sign-in successful — token cached at ~/.releaseflow/")
        Logger.step("You can now run: ./gradlew releaseFlowDeployQa")
    }
}
