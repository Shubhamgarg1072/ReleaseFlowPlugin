package com.releaseflow.tasks

import com.releaseflow.storage.OAuthDriveUploader
import com.releaseflow.util.Logger
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Clears the cached Google Drive OAuth token, forcing a fresh sign-in on the next deploy.
 *
 * Run with: `./gradlew releaseFlowLogout`
 */
abstract class ReleaseFlowLogoutTask : DefaultTask() {

    @TaskAction
    fun logout() {
        Logger.header("ReleaseFlow Logout")
        val uploader = OAuthDriveUploader()

        if (!uploader.hasCachedCredentials()) {
            Logger.warn("No cached credentials found — you are already logged out.")
            return
        }

        val deleted = uploader.logout()
        if (deleted) {
            Logger.ok("Logged out — cached token removed from ~/.releaseflow/")
            Logger.step("Next deploy will prompt for sign-in again.")
        } else {
            Logger.error("Failed to remove cached token. Try deleting ~/.releaseflow/StoredCredential manually.")
        }
    }
}
