package com.releaseflow.pipeline.steps

import com.releaseflow.EnvironmentConfig
import com.releaseflow.notify.BrowserEmailSender
import com.releaseflow.notify.EmailSender
import com.releaseflow.pipeline.StepResult
import com.releaseflow.storage.UploadResult
import com.releaseflow.util.Logger
import java.io.File

/**
 * Sends a release notification email.
 *
 * - `emailMode = "browser"` (default): opens Gmail compose in the user's browser
 *   with subject, recipients, and body pre-filled. The user clicks Send. **No credentials needed.**
 * - `emailMode = "smtp"`: sends programmatically via SMTP using JavaMail. Requires
 *   `emailUsername` and `emailPassword` (Gmail App Password). Useful for CI.
 *
 * Silently skipped when [EnvironmentConfig.emailTo] is empty.
 */
class NotifyStep(
    private val envConfig: EnvironmentConfig,
    private val projectName: String,
    private val artifact: File?,
    private val uploadResult: UploadResult?,
    private val changelog: List<String>,
    private val dryRun: Boolean
) {

    fun execute(): StepResult {
        if (envConfig.emailTo.isEmpty()) {
            return StepResult.Skipped("emailTo not configured — skipping email notification")
        }

        return when (envConfig.emailMode) {
            "browser" -> sendBrowser()
            "smtp"    -> sendSmtp()
            else      -> StepResult.Failure("Unknown emailMode '${envConfig.emailMode}' — must be \"browser\" or \"smtp\"")
        }
    }

    private fun sendBrowser(): StepResult {
        Logger.step("Email: opening Gmail compose in browser for ${envConfig.emailTo}")

        if (dryRun) {
            Logger.warn("[DRY RUN] Would open Gmail compose with recipients: ${envConfig.emailTo.joinToString()}")
            return StepResult.Success(Unit)
        }

        return try {
            BrowserEmailSender().send(
                projectName = projectName,
                envName = envConfig.name,
                artifact = artifact,
                uploadResult = uploadResult,
                changelog = changelog,
                recipients = envConfig.emailTo
            )
            StepResult.Success(Unit)
        } catch (e: Exception) {
            StepResult.Failure(
                "Browser email failed: ${e.message}\n" +
                "  → For headless environments (CI), switch to emailMode = \"smtp\"",
                cause = e
            )
        }
    }

    private fun sendSmtp(): StepResult {
        if (envConfig.emailUsername.isBlank() || envConfig.emailPassword.isBlank()) {
            return StepResult.Skipped(
                "SMTP credentials not configured — skipping email\n" +
                "  → For zero-config email, switch to emailMode = \"browser\" (default)"
            )
        }

        Logger.step("Email: sending via SMTP to ${envConfig.emailTo}")

        if (dryRun) {
            Logger.warn("[DRY RUN] Would send SMTP email to: ${envConfig.emailTo.joinToString()}")
            return StepResult.Success(Unit)
        }

        return try {
            EmailSender(envConfig).send(
                projectName = projectName,
                artifact = artifact,
                uploadResult = uploadResult,
                changelog = changelog
            )
            StepResult.Success(Unit)
        } catch (e: Exception) {
            StepResult.Failure(
                "SMTP email failed: ${e.message}\n" +
                "  SMTP host: ${envConfig.emailSmtpHost}:${envConfig.emailSmtpPort}\n" +
                "  From: ${envConfig.emailUsername}\n" +
                "  → Verify your Gmail App Password (not your account password)\n" +
                "  → Or switch to emailMode = \"browser\" for credential-free sending",
                cause = e
            )
        }
    }
}
