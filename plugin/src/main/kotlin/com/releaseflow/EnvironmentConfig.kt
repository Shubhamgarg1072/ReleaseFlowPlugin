package com.releaseflow

import java.io.File

/**
 * Holds all configuration for a single named release environment (e.g. "qa", "staging").
 * Populated either via the Gradle DSL or parsed from releaseflow.yaml.
 */
data class EnvironmentConfig(
    val name: String,
    var flavor: String = "",
    var buildType: String = "debug",

    // === Google Drive (primary: OAuth browser login) ===
    /**
     * URL of a Google Drive folder. Just paste any Drive folder URL from your browser.
     * Example: `https://drive.google.com/drive/folders/1abc123xyz`
     *
     * The plugin uploads APKs into a `projectName/envName/year/month` subfolder hierarchy
     * inside this folder. The folder must be writable by the account you sign in with via
     * `./gradlew releaseFlowLogin`.
     */
    var driveFolderUrl: String = "",

    /**
     * Optional: path to a Service Account JSON for non-interactive CI use.
     * When set, the plugin uses this instead of the OAuth user token.
     * Most users should leave this blank and use `releaseFlowLogin` instead.
     */
    var driveServiceAccountJson: String = "",

    // === Email notification ===
    /** Primary recipients (To: field). If empty, the email step is skipped. */
    var emailTo: List<String> = emptyList(),

    /** Carbon-copy recipients (Cc: field). Visible to all recipients. */
    var emailCc: List<String> = emptyList(),

    /** Blind carbon-copy recipients (Bcc: field). Hidden from other recipients. */
    var emailBcc: List<String> = emptyList(),

    /**
     * How to send the email:
     * - `"browser"` (default): open Gmail compose in your browser, pre-filled. You click Send. **No credentials needed.**
     * - `"smtp"`: send programmatically via SMTP. Requires emailUsername + emailPassword. Useful for CI.
     */
    var emailMode: String = "browser",

    // SMTP fields — only used when emailMode = "smtp"
    var emailSmtpHost: String = "smtp.gmail.com",
    var emailSmtpPort: Int = 587,
    var emailUsername: String = "",
    var emailPassword: String = "",

    // === Changelog ===
    var changelogEnabled: Boolean = false,
    var changelogFormat: String = "plain",   // "plain" or "markdown"

    // === Slack (optional) ===
    var slackWebhook: String = "",

    // === Firebase App Distribution (optional) ===
    /**
     * Firebase app ID from the Firebase Console (Project Settings → General → Your apps).
     * Format: `1:PROJECT_NUMBER:android:APP_HASH`
     * Example: `1:123456789:android:abcdef1234567890`
     *
     * When set, the pipeline uploads the artifact to Firebase App Distribution
     * in addition to (not instead of) the Drive/OneDrive upload.
     */
    var firebaseAppId: String = "",

    /**
     * Path (relative to project root) to a Firebase service account JSON file.
     * Download from Firebase Console → Project Settings → Service accounts → Generate new private key.
     * The service account must have the "Firebase App Distribution Admin" role.
     */
    var firebaseServiceAccountJson: String = "",

    /** Tester email addresses to notify when a new build is distributed. */
    var firebaseTesterEmails: List<String> = emptyList(),

    /**
     * Firebase App Distribution tester group aliases to notify.
     * Create groups in Firebase Console → App Distribution → Testers & Groups.
     */
    var firebaseGroups: List<String> = emptyList(),

    /**
     * Custom release notes for the Firebase release.
     * If blank, the git changelog (if enabled) is used automatically.
     */
    var firebaseReleaseNotes: String = ""
) {
    /**
     * Identifies which cloud provider [driveFolderUrl] points to, by URL pattern.
     */
    enum class CloudProvider { GOOGLE_DRIVE, ONE_DRIVE, UNKNOWN }

    /**
     * Detects the cloud storage provider from [driveFolderUrl].
     *
     * - Google Drive: URL contains `drive.google.com`, or bare folder ID without dots
     * - OneDrive: URL contains `1drv.ms`, `onedrive.live.com`, or `sharepoint.com`
     */
    fun cloudProvider(): CloudProvider = when {
        driveFolderUrl.isBlank() -> CloudProvider.UNKNOWN
        driveFolderUrl.contains("drive.google.com") -> CloudProvider.GOOGLE_DRIVE
        driveFolderUrl.contains("1drv.ms") ||
            driveFolderUrl.contains("onedrive.live.com") ||
            driveFolderUrl.contains("sharepoint.com") -> CloudProvider.ONE_DRIVE
        // Bare Google Drive folder ID (alphanumeric, no domain)
        !driveFolderUrl.contains(".") && driveFolderUrl.matches(Regex("[a-zA-Z0-9_-]+")) -> CloudProvider.GOOGLE_DRIVE
        else -> CloudProvider.UNKNOWN
    }

    /**
     * Extracts the Google Drive folder ID from [driveFolderUrl].
     * Returns null if the URL is not a Google Drive URL.
     *
     * Accepts URLs like:
     * - `https://drive.google.com/drive/folders/1abc123xyz`
     * - `https://drive.google.com/drive/folders/1abc123xyz?usp=sharing`
     * - `https://drive.google.com/drive/u/0/folders/1abc123xyz`
     * - bare folder ID: `1abc123xyz`
     */
    fun driveFolderId(): String? {
        if (cloudProvider() != CloudProvider.GOOGLE_DRIVE) return null
        val match = Regex("/folders/([a-zA-Z0-9_-]+)").find(driveFolderUrl)
        if (match != null) return match.groupValues[1]
        return if (!driveFolderUrl.contains("/") && driveFolderUrl.matches(Regex("[a-zA-Z0-9_-]+"))) {
            driveFolderUrl
        } else null
    }

    /**
     * Returns a list of human-readable error messages. An empty list means the config is valid.
     */
    fun validate(projectRootDir: File): List<String> {
        val errors = mutableListOf<String>()

        if (buildType.isBlank()) {
            errors += "environment('$name'): buildType must not be blank (e.g. \"debug\" or \"release\")"
        }

        // Drive validation — detect which cloud provider and validate accordingly
        if (driveFolderUrl.isNotBlank()) {
            when (cloudProvider()) {
                CloudProvider.UNKNOWN -> errors += """
                    |environment('$name'): driveFolderUrl is not a recognized cloud folder URL
                    |  Got: '$driveFolderUrl'
                    |  Supported formats:
                    |    Google Drive: https://drive.google.com/drive/folders/<id>
                    |    OneDrive:     https://1drv.ms/f/<id> or https://onedrive.live.com/?... or SharePoint URL
                    |  → Open the folder in your browser, copy the URL from the address bar, and paste it here
                """.trimMargin()
                CloudProvider.GOOGLE_DRIVE -> {
                    if (driveFolderId() == null) {
                        errors += """
                            |environment('$name'): Google Drive URL is malformed
                            |  Got: '$driveFolderUrl'
                            |  → The URL must contain '/folders/<id>'
                            |  → Example: https://drive.google.com/drive/folders/1abc123xyz
                        """.trimMargin()
                    }
                }
                CloudProvider.ONE_DRIVE -> {
                    // OneDrive URLs vary widely (1drv.ms shortlinks, onedrive.live.com, SharePoint),
                    // and we use the full URL with Graph's /shares endpoint, so no further parsing required.
                }
            }
        }

        if (driveServiceAccountJson.isNotBlank()) {
            val credFile = File(projectRootDir, driveServiceAccountJson)
            if (!credFile.exists()) {
                errors += """
                    |environment('$name'): driveServiceAccountJson file not found at '${credFile.absolutePath}'
                    |  → Most users should leave this blank and use './gradlew releaseFlowLogin' instead
                    |  → Service Account JSON is only needed for headless CI environments
                """.trimMargin()
            }
        }

        // Email validation
        if (emailMode !in listOf("browser", "smtp")) {
            errors += "environment('$name'): emailMode must be \"browser\" or \"smtp\", got \"$emailMode\""
        }

        if (emailMode == "smtp" && emailTo.isNotEmpty()) {
            if (emailUsername.isBlank()) {
                errors += """
                    |environment('$name'): emailUsername is required when emailMode = "smtp"
                    |  → For zero-config email, switch to emailMode = "browser" (default)
                    |  → Or set emailUsername = System.getenv("RF_EMAIL_USER") and export RF_EMAIL_USER
                """.trimMargin()
            }
            if (emailPassword.isBlank()) {
                errors += """
                    |environment('$name'): emailPassword is required when emailMode = "smtp"
                    |  → For zero-config email, switch to emailMode = "browser" (default)
                    |  → Or create a Gmail App Password and export RF_EMAIL_PASS
                """.trimMargin()
            }
        }

        if (changelogFormat !in listOf("plain", "markdown")) {
            errors += "environment('$name'): changelogFormat must be \"plain\" or \"markdown\", got \"$changelogFormat\""
        }

        return errors
    }
}
