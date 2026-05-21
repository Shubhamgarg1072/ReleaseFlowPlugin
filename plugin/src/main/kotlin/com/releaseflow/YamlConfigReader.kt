package com.releaseflow

import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Reads a `releaseflow.yaml` file and populates a [ReleaseFlowExtension] from it.
 *
 * YAML format:
 * ```yaml
 * project:
 *   name: MyApp
 *
 * environments:
 *   qa:
 *     build:
 *       flavor: qa
 *       type: debug
 *     storage:
 *       google_drive:
 *         root_folder: "QA Builds"
 *         credentials_file: drive-credentials.json
 *     notifications:
 *       email:
 *         to: [qa@company.com]
 *         smtp_host: smtp.gmail.com
 *         smtp_port: 587
 *         username: ${RF_EMAIL_USER}
 *         password: ${RF_EMAIL_PASS}
 *       slack_webhook: ${RF_SLACK_WEBHOOK}
 *     changelog:
 *       enabled: true
 *       format: plain
 * ```
 *
 * `${VAR_NAME}` placeholders in any string value are resolved from [System.getenv].
 * DSL configuration always wins over YAML — this reader only runs when no DSL environments exist.
 */
object YamlConfigReader {

    private val envPlaceholderRegex = Regex("""\$\{([^}]+)}""")

    /**
     * Parses [yamlFile] and populates [extension] with the parsed environments.
     */
    @Suppress("UNCHECKED_CAST")
    fun readInto(yamlFile: File, extension: ReleaseFlowExtension) {
        val yaml = Yaml()
        @Suppress("UNCHECKED_CAST")
        val raw = yamlFile.inputStream().use { stream ->
            yaml.load<Any>(stream) as? Map<String, Any>
        } ?: return

        // Project name
        val project = raw["project"] as? Map<String, Any>
        project?.get("name")?.toString()?.let { extension.projectName = it }

        // Environments
        val environments = raw["environments"] as? Map<String, Any> ?: return

        environments.forEach { (envName, envRaw) ->
            val envMap = envRaw as? Map<String, Any> ?: return@forEach

            extension.environment(envName) { config ->
                // Build
                val build = envMap["build"] as? Map<String, Any>
                build?.get("flavor")?.toString()?.let { config.flavor = resolve(it) }
                build?.get("type")?.toString()?.let { config.buildType = resolve(it) }

                // Storage / Google Drive
                val storage = envMap["storage"] as? Map<String, Any>
                val drive = storage?.get("google_drive") as? Map<String, Any>
                drive?.get("folder_url")?.toString()?.let { config.driveFolderUrl = resolve(it) }
                drive?.get("service_account_json")?.toString()?.let { config.driveServiceAccountJson = resolve(it) }

                // Notifications / Email
                val notifications = envMap["notifications"] as? Map<String, Any>
                val email = notifications?.get("email") as? Map<String, Any>
                email?.get("mode")?.toString()?.let { config.emailMode = resolve(it) }
                email?.get("to")?.let { config.emailTo = parseRecipients(it) }
                email?.get("cc")?.let { config.emailCc = parseRecipients(it) }
                email?.get("bcc")?.let { config.emailBcc = parseRecipients(it) }
                email?.get("smtp_host")?.toString()?.let { config.emailSmtpHost = resolve(it) }
                email?.get("smtp_port")?.toString()?.let {
                    config.emailSmtpPort = it.toIntOrNull() ?: 587
                }
                email?.get("username")?.toString()?.let { config.emailUsername = resolve(it) }
                email?.get("password")?.toString()?.let { config.emailPassword = resolve(it) }

                // Notifications / Slack
                notifications?.get("slack_webhook")?.toString()?.let {
                    config.slackWebhook = resolve(it)
                }

                // Firebase App Distribution
                val firebase = envMap["firebase"] as? Map<String, Any>
                firebase?.get("app_id")?.toString()?.let { config.firebaseAppId = resolve(it) }
                firebase?.get("service_account_json")?.toString()?.let { config.firebaseServiceAccountJson = resolve(it) }
                firebase?.get("tester_emails")?.let { config.firebaseTesterEmails = parseRecipients(it) }
                firebase?.get("groups")?.let { config.firebaseGroups = parseRecipients(it) }
                firebase?.get("release_notes")?.toString()?.let { config.firebaseReleaseNotes = resolve(it) }

                // Changelog
                val changelog = envMap["changelog"] as? Map<String, Any>
                changelog?.get("enabled")?.toString()?.let {
                    config.changelogEnabled = it.equals("true", ignoreCase = true)
                }
                changelog?.get("format")?.toString()?.let { config.changelogFormat = resolve(it) }
            }
        }
    }

    /** Replaces `${ENV_VAR}` placeholders with their environment variable values. */
    private fun resolve(value: String): String =
        envPlaceholderRegex.replace(value) { match ->
            System.getenv(match.groupValues[1]) ?: ""
        }

    /** Accepts either a YAML list (`[a, b]`) or a single string and returns a resolved recipient list. */
    private fun parseRecipients(raw: Any?): List<String> = when (raw) {
        is List<*> -> raw.filterNotNull().map { resolve(it.toString()) }
        is String  -> listOf(resolve(raw))
        else       -> emptyList()
    }
}
