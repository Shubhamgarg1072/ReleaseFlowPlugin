package com.releaseflow.pipeline.steps

import com.releaseflow.EnvironmentConfig
import com.releaseflow.notify.EmailSender
import com.releaseflow.pipeline.StepResult
import com.releaseflow.storage.UploadResult
import com.releaseflow.util.Logger
import java.io.File

/**
 * Sends an HTML + plain-text email notification to all addresses in [envConfig.emailTo].
 *
 * Silently skipped when [envConfig.emailTo] is empty or email credentials are missing.
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
        if (envConfig.emailUsername.isBlank() || envConfig.emailPassword.isBlank()) {
            return StepResult.Skipped(
                "Email credentials not configured — skipping email notification\n" +
                "  → Set emailUsername and emailPassword (or use System.getenv(\"RF_EMAIL_USER\"))"
            )
        }

        Logger.step("Email: sending notification to ${envConfig.emailTo}")

        if (dryRun) {
            Logger.warn("[DRY RUN] Would send email to: ${envConfig.emailTo.joinToString()}")
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
                "Email notification failed: ${e.message}\n" +
                "  SMTP host: ${envConfig.emailSmtpHost}:${envConfig.emailSmtpPort}\n" +
                "  From: ${envConfig.emailUsername}\n" +
                "  To: ${envConfig.emailTo.joinToString()}\n" +
                "  → Verify your Gmail App Password (not your account password)\n" +
                "  → Make sure 2-Step Verification is enabled on your Google account\n" +
                "  → Check that less secure app access is not blocking SMTP",
                cause = e
            )
        }
    }
}
