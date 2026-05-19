package com.releaseflow.notify

import com.releaseflow.storage.UploadResult
import java.io.File

/**
 * Generates both HTML and plain-text email bodies for release notifications.
 */
object EmailTemplate {

    private val envColors = mapOf(
        "qa"          to "#2980b9",
        "staging"     to "#e67e22",
        "production"  to "#c0392b",
        "prod"        to "#c0392b"
    )

    private fun badgeColor(envName: String): String =
        envColors[envName.lowercase()] ?: "#7f8c8d"

    /**
     * Renders the full HTML email body.
     */
    fun html(
        projectName: String,
        envName: String,
        artifact: File?,
        uploadResult: UploadResult?,
        changelog: List<String>
    ): String {
        val color = badgeColor(envName)
        val artifactName = artifact?.name ?: "N/A"
        val changelogHtml = if (changelog.isNotEmpty()) {
            val items = changelog.joinToString("") { "<li>${htmlEscape(it.trimStart('•', '-').trim())}</li>" }
            """
            <div style="margin:24px 0;">
              <h3 style="color:#2c3e50;font-size:15px;margin-bottom:8px;">📋 Changelog</h3>
              <ul style="margin:0;padding-left:20px;color:#555;font-size:14px;line-height:1.7;">
                $items
              </ul>
            </div>
            """.trimIndent()
        } else ""

        val driveSection = if (uploadResult != null) {
            """
            <div style="margin:24px 0;">
              <h3 style="color:#2c3e50;font-size:15px;margin-bottom:12px;">📁 Google Drive</h3>
              <p style="margin:0 0 8px;color:#555;font-size:13px;">
                Location: <code style="background:#f4f4f4;padding:2px 6px;border-radius:3px;font-family:monospace;">${htmlEscape(uploadResult.folderPath)}</code>
              </p>
              <p style="margin:0;color:#888;font-size:12px;">Future ${envName.uppercase()} builds will appear in this folder.</p>
            </div>
            <div style="margin:24px 0;">
              <a href="${uploadResult.downloadLink}"
                 style="display:inline-block;background:$color;color:#fff;text-decoration:none;
                        padding:12px 28px;border-radius:6px;font-size:15px;font-weight:bold;margin-right:12px;">
                ⬇ Download APK
              </a>
              <a href="${uploadResult.viewLink}"
                 style="display:inline-block;background:#ecf0f1;color:#2c3e50;text-decoration:none;
                        padding:12px 20px;border-radius:6px;font-size:13px;">
                Open in Drive
              </a>
            </div>
            """.trimIndent()
        } else ""

        return """
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"></head>
<body style="margin:0;padding:0;background:#f5f5f5;font-family:Arial,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0">
    <tr><td align="center" style="padding:30px 15px;">
      <table width="600" cellpadding="0" cellspacing="0"
             style="background:#fff;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,0.08);overflow:hidden;max-width:600px;">

        <!-- Header banner -->
        <tr><td style="background:$color;padding:24px 32px;">
          <h1 style="margin:0;color:#fff;font-size:22px;font-weight:bold;">
            🚀 $projectName — New ${envName.uppercase()} Build
          </h1>
          <p style="margin:6px 0 0;color:rgba(255,255,255,0.85);font-size:13px;">
            ReleaseFlow automated delivery
          </p>
        </td></tr>

        <!-- Body -->
        <tr><td style="padding:28px 32px;">

          <!-- Environment badge -->
          <div style="margin-bottom:20px;">
            <span style="display:inline-block;background:$color;color:#fff;
                         border-radius:20px;padding:5px 14px;font-size:12px;font-weight:bold;
                         text-transform:uppercase;letter-spacing:1px;">
              $envName
            </span>
          </div>

          <!-- Artifact info -->
          <div style="background:#f8f9fa;border-left:4px solid $color;border-radius:4px;padding:14px 16px;margin-bottom:20px;">
            <p style="margin:0 0 4px;font-size:12px;color:#888;text-transform:uppercase;letter-spacing:0.5px;">Artifact</p>
            <code style="font-size:14px;color:#2c3e50;font-family:'Courier New',monospace;">$artifactName</code>
          </div>

          $driveSection
          $changelogHtml

        </td></tr>

        <!-- Footer -->
        <tr><td style="border-top:1px solid #eee;padding:16px 32px;">
          <p style="margin:0;color:#aaa;font-size:12px;">Sent by ReleaseFlow</p>
        </td></tr>

      </table>
    </td></tr>
  </table>
</body>
</html>
        """.trimIndent()
    }

    /**
     * Renders the plain-text email body (mirrors the HTML content without markup).
     */
    fun plain(
        projectName: String,
        envName: String,
        artifact: File?,
        uploadResult: UploadResult?,
        changelog: List<String>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("$projectName — New ${envName.uppercase()} Build")
        sb.appendLine("=" .repeat(50))
        sb.appendLine()
        sb.appendLine("Environment : ${envName.uppercase()}")
        sb.appendLine("Artifact    : ${artifact?.name ?: "N/A"}")

        if (uploadResult != null) {
            sb.appendLine()
            sb.appendLine("Download APK : ${uploadResult.downloadLink}")
            sb.appendLine("Open in Drive: ${uploadResult.viewLink}")
            sb.appendLine("Drive folder : ${uploadResult.folderPath}")
            sb.appendLine("Future ${envName.uppercase()} builds will appear in this folder.")
        }

        if (changelog.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Changelog:")
            changelog.forEach { sb.appendLine("  $it") }
        }

        sb.appendLine()
        sb.appendLine("Sent by ReleaseFlow")
        return sb.toString()
    }

    private fun htmlEscape(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
