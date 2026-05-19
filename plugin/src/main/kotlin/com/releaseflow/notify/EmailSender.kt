package com.releaseflow.notify

import com.releaseflow.EnvironmentConfig
import com.releaseflow.storage.UploadResult
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.io.File
import java.util.Properties

/**
 * Sends a multipart (HTML + plain text) email via SMTP using JavaMail.
 *
 * Credentials are read from [EnvironmentConfig.emailUsername] and
 * [EnvironmentConfig.emailPassword]. For Gmail, the password must be an
 * App Password (not the account password).
 */
class EmailSender(private val envConfig: EnvironmentConfig) {

    /**
     * Composes and sends the release notification email.
     *
     * @param projectName Display name shown in the email subject and body.
     * @param artifact    The APK/AAB file that was produced (null if build was skipped).
     * @param uploadResult Drive upload outcome, or null when Drive upload was skipped.
     * @param changelog   List of formatted changelog lines, or empty if disabled.
     */
    fun send(
        projectName: String,
        artifact: File?,
        uploadResult: UploadResult?,
        changelog: List<String>
    ) {
        val session = buildSession()

        val htmlBody = EmailTemplate.html(
            projectName = projectName,
            envName = envConfig.name,
            artifact = artifact,
            uploadResult = uploadResult,
            changelog = changelog
        )
        val plainBody = EmailTemplate.plain(
            projectName = projectName,
            envName = envConfig.name,
            artifact = artifact,
            uploadResult = uploadResult,
            changelog = changelog
        )

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(envConfig.emailUsername, "$projectName ReleaseFlow"))
            envConfig.emailTo.forEach { addRecipient(Message.RecipientType.TO, InternetAddress(it)) }
            envConfig.emailCc.forEach { addRecipient(Message.RecipientType.CC, InternetAddress(it)) }
            envConfig.emailBcc.forEach { addRecipient(Message.RecipientType.BCC, InternetAddress(it)) }
            subject = "[$projectName] New ${envConfig.name.uppercase()} build — ${artifact?.name ?: "release"}"

            val plainPart = MimeBodyPart().apply { setText(plainBody, "utf-8") }
            val htmlPart = MimeBodyPart().apply { setContent(htmlBody, "text/html; charset=utf-8") }

            // RFC 2046: last alternative preferred — put HTML last
            val multipart = MimeMultipart("alternative").apply {
                addBodyPart(plainPart)
                addBodyPart(htmlPart)
            }
            setContent(multipart)
        }

        Transport.send(message)
    }

    private fun buildSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", envConfig.emailSmtpHost)
            put("mail.smtp.port", envConfig.emailSmtpPort.toString())
            put("mail.smtp.ssl.trust", envConfig.emailSmtpHost)
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
        }

        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(envConfig.emailUsername, envConfig.emailPassword)
        })
    }
}
