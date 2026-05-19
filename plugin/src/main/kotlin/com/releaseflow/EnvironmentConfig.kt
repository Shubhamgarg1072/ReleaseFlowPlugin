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

    // Google Drive
    var driveRootFolder: String = "",
    var driveCredentials: String = "",

    // Email / SMTP
    var emailTo: List<String> = emptyList(),
    var emailSmtpHost: String = "smtp.gmail.com",
    var emailSmtpPort: Int = 587,
    var emailUsername: String = "",
    var emailPassword: String = "",

    // Changelog
    var changelogEnabled: Boolean = false,
    var changelogFormat: String = "plain",   // "plain" or "markdown"

    // Slack
    var slackWebhook: String = ""
) {
    /**
     * Returns a list of human-readable error messages. An empty list means the config is valid.
     */
    fun validate(projectRootDir: File): List<String> {
        val errors = mutableListOf<String>()

        if (buildType.isBlank()) {
            errors += "environment('$name'): buildType must not be blank (e.g. \"debug\" or \"release\")"
        }

        // Drive validation — only required when driveRootFolder is set
        if (driveRootFolder.isNotBlank()) {
            if (driveCredentials.isBlank()) {
                errors += """
                    |environment('$name'): driveCredentials is required when driveRootFolder is set
                    |  → Download a Service Account JSON from https://console.cloud.google.com
                    |  → Share your Drive folder with the service account email
                    |  → Set driveCredentials = "drive-credentials.json" in your releaseFlow block
                """.trimMargin()
            } else {
                val credFile = File(projectRootDir, driveCredentials)
                if (!credFile.exists()) {
                    errors += """
                        |environment('$name'): driveCredentials file not found at '${credFile.absolutePath}'
                        |  → Download a Service Account JSON from https://console.cloud.google.com
                        |  → Place it at the path specified in driveCredentials
                        |  → Share your Drive folder with the service account email address inside the JSON
                    """.trimMargin()
                }
            }
        }

        // Email validation — only required when emailTo is set
        if (emailTo.isNotEmpty()) {
            if (emailUsername.isBlank()) {
                errors += """
                    |environment('$name'): emailUsername is required when emailTo is set
                    |  → Set emailUsername = System.getenv("RF_EMAIL_USER") ?: ""
                    |  → Export RF_EMAIL_USER="releases@company.com" in your shell or CI secrets
                """.trimMargin()
            }
            if (emailPassword.isBlank()) {
                errors += """
                    |environment('$name'): emailPassword is required when emailTo is set
                    |  → Enable Gmail 2FA → App Passwords → create a "ReleaseFlow" password
                    |  → Set emailPassword = System.getenv("RF_EMAIL_PASS") ?: ""
                    |  → Export RF_EMAIL_PASS="xxxx xxxx xxxx xxxx" in your shell or CI secrets
                """.trimMargin()
            }
        }

        if (changelogFormat !in listOf("plain", "markdown")) {
            errors += "environment('$name'): changelogFormat must be \"plain\" or \"markdown\", got \"$changelogFormat\""
        }

        return errors
    }
}
