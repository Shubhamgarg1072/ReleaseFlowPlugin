package com.releaseflow.notify

import com.releaseflow.storage.UploadResult
import com.releaseflow.util.Logger
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URLEncoder

/**
 * Builds a Gmail compose URL with subject, recipients, and body pre-filled,
 * and opens it in the user's default browser.
 *
 * **No credentials, no SMTP setup required** — the user just reviews the message
 * in Gmail's compose window and clicks **Send**.
 *
 * When the host has no graphical desktop (e.g. a CI runner), the URL is printed
 * to stdout instead so the user can open it manually or copy/paste.
 */
class BrowserEmailSender {

    /**
     * Composes and opens the release notification in the user's default browser.
     */
    fun send(
        projectName: String,
        envName: String,
        artifact: File?,
        uploadResult: UploadResult?,
        changelog: List<String>,
        recipients: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList()
    ) {
        val subject = "[$projectName] New ${envName.uppercase()} build — ${artifact?.name ?: "release"}"
        val body = buildBody(projectName, envName, artifact, uploadResult, changelog)

        val gmailUrl = buildGmailComposeUrl(
            to = recipients.joinToString(","),
            cc = cc.joinToString(","),
            bcc = bcc.joinToString(","),
            subject = subject,
            body = body
        )

        if (openInBrowser(gmailUrl)) {
            Logger.ok("Gmail compose opened in your browser — review and click Send.")
        } else {
            Logger.warn("Could not open browser. Copy this URL into your browser to compose the email:")
            println()
            println(gmailUrl)
            println()
        }
    }

    private fun buildBody(
        projectName: String,
        envName: String,
        artifact: File?,
        uploadResult: UploadResult?,
        changelog: List<String>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Hi team,")
        sb.appendLine()
        sb.appendLine("A new $projectName ${envName.uppercase()} build is ready:")
        sb.appendLine()
        sb.appendLine("  Artifact: ${artifact?.name ?: "(no artifact)"}")

        if (uploadResult != null) {
            sb.appendLine()
            sb.appendLine("📥 Download:")
            sb.appendLine("  ${uploadResult.downloadLink}")
            sb.appendLine()
            sb.appendLine("📁 Open in Drive:")
            sb.appendLine("  ${uploadResult.viewLink}")
            sb.appendLine()
            sb.appendLine("Folder: ${uploadResult.folderPath}")
            sb.appendLine("(Future ${envName.uppercase()} builds will land in this folder.)")
        }

        if (changelog.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📋 Changelog:")
            changelog.forEach { sb.appendLine("  $it") }
        }

        sb.appendLine()
        sb.appendLine("— Sent by ReleaseFlow")
        return sb.toString()
    }

    private fun buildGmailComposeUrl(
        to: String,
        cc: String,
        bcc: String,
        subject: String,
        body: String
    ): String {
        val sb = StringBuilder("https://mail.google.com/mail/?view=cm&fs=1")
        sb.append("&to=").append(URLEncoder.encode(to, "UTF-8"))
        if (cc.isNotBlank()) sb.append("&cc=").append(URLEncoder.encode(cc, "UTF-8"))
        if (bcc.isNotBlank()) sb.append("&bcc=").append(URLEncoder.encode(bcc, "UTF-8"))
        sb.append("&su=").append(URLEncoder.encode(subject, "UTF-8"))
        sb.append("&body=").append(URLEncoder.encode(body, "UTF-8"))
        return sb.toString()
    }

    /**
     * Opens [url] in the OS default browser.
     * Returns false if no graphical desktop is available (typical on CI).
     */
    private fun openInBrowser(url: String): Boolean = try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}
