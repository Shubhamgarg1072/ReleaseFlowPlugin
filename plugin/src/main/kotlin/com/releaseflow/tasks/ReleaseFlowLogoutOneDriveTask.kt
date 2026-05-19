package com.releaseflow.tasks

import com.releaseflow.storage.OneDriveUploader
import com.releaseflow.util.Logger
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Clears the cached OneDrive OAuth token.
 *
 * Run with: `./gradlew releaseFlowLogoutOneDrive`
 */
abstract class ReleaseFlowLogoutOneDriveTask : DefaultTask() {

    @TaskAction
    fun logout() {
        Logger.header("ReleaseFlow Logout (OneDrive)")
        val uploader = OneDriveUploader()

        if (!uploader.hasCachedCredentials()) {
            Logger.warn("No cached OneDrive credentials found — you are already logged out.")
            return
        }

        val deleted = uploader.logout()
        if (deleted) {
            Logger.ok("Logged out of OneDrive.")
            Logger.step("Next deploy that uses a OneDrive URL will prompt for sign-in again.")
        } else {
            Logger.error("Failed to delete ~/.releaseflow/${OneDriveUploader.TOKEN_CACHE_FILENAME}")
        }
    }
}
