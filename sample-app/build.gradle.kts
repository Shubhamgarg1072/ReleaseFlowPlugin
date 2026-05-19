plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.releaseflow.gradle")   // loaded from local composite build (see settings.gradle.kts)
}

releaseFlow {
    projectName = "SampleApp"

    environment("qa") {
        flavor          = "qa"
        buildType       = "debug"
        driveRootFolder = "QA Builds"
        driveCredentials = "drive-credentials.json"
        emailTo         = listOf("qa@example.com", "lead@example.com")
        emailSmtpHost   = "smtp.gmail.com"
        emailSmtpPort   = 587
        emailUsername   = System.getenv("RF_EMAIL_USER") ?: ""
        emailPassword   = System.getenv("RF_EMAIL_PASS") ?: ""
        changelogEnabled = true
        changelogFormat  = "plain"
        slackWebhook    = System.getenv("RF_SLACK_WEBHOOK") ?: ""
    }

    environment("staging") {
        flavor          = "staging"
        buildType       = "release"
        driveRootFolder = "Staging Builds"
        driveCredentials = "drive-credentials.json"
        emailTo         = listOf("staging@example.com")
        emailUsername   = System.getenv("RF_EMAIL_USER") ?: ""
        emailPassword   = System.getenv("RF_EMAIL_PASS") ?: ""
        changelogEnabled = true
        changelogFormat  = "markdown"
    }

    environment("production") {
        flavor          = "prod"
        buildType       = "release"
        driveRootFolder = "Production Builds"
        driveCredentials = "drive-credentials.json"
        emailTo         = listOf("releases@example.com", "cto@example.com")
        emailUsername   = System.getenv("RF_EMAIL_USER") ?: ""
        emailPassword   = System.getenv("RF_EMAIL_PASS") ?: ""
        changelogEnabled = true
        changelogFormat  = "markdown"
        slackWebhook    = System.getenv("RF_SLACK_WEBHOOK") ?: ""
    }
}
