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
    /** List of recipients. If empty, the email step is skipped. */
    var emailTo: List<String> = emptyList(),

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
    var slackWebhook: String = ""
) {
    /**
     * Extracts the Drive folder ID from [driveFolderUrl].
     *
     * Accepts URLs like:
     * - `https://drive.google.com/drive/folders/1abc123xyz`
     * - `https://drive.google.com/drive/folders/1abc123xyz?usp=sharing`
     * - `https://drive.google.com/drive/u/0/folders/1abc123xyz`
     * - just the bare ID: `1abc123xyz`
     */
    fun driveFolderId(): String? {
        if (driveFolderUrl.isBlank()) return null
        val match = Regex("/folders/([a-zA-Z0-9_-]+)").find(driveFolderUrl)
        if (match != null) return match.groupValues[1]
        // Treat as a bare folder ID if no slashes
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

        // Drive validation
        if (driveFolderUrl.isNotBlank() && driveFolderId() == null) {
            errors += """
                |environment('$name'): driveFolderUrl is not a valid Drive folder URL
                |  Got: '$driveFolderUrl'
                |  → Open the folder in Google Drive in your browser
                |  → Copy the URL from the address bar (it contains "/folders/<id>")
                |  → Paste it as: driveFolderUrl = "https://drive.google.com/drive/folders/<id>"
            """.trimMargin()
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
