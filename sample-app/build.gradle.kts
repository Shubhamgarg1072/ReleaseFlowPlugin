plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.releaseflow.gradle")   // loaded from local composite build (see settings.gradle.kts)
}

releaseFlow {
    projectName = "SampleApp"

    // Google Drive example — paste a Drive folder URL.
    // First-time setup: ./gradlew releaseFlowLogin
    environment("qa") {
        flavor    = "qa"
        buildType = "debug"
        driveFolderUrl = "https://drive.google.com/drive/folders/REPLACE_WITH_YOUR_GOOGLE_DRIVE_FOLDER_ID"
        emailTo        = listOf("qa@example.com", "lead@example.com")
        changelogEnabled = true
    }

    // OneDrive example — paste a OneDrive folder URL.
    // First-time setup: ./gradlew releaseFlowLoginOneDrive
    // Works with free personal Microsoft accounts AND Microsoft 365 business accounts.
    environment("staging") {
        flavor    = "staging"
        buildType = "release"
        driveFolderUrl = "https://1drv.ms/f/s!REPLACE_WITH_YOUR_ONEDRIVE_SHARE_LINK"
        emailTo        = listOf("staging@example.com")
        changelogEnabled = true
        changelogFormat  = "markdown"
    }

    // Mix and match — production uses Google Drive again
    environment("production") {
        flavor    = "prod"
        buildType = "release"
        driveFolderUrl = "https://drive.google.com/drive/folders/REPLACE_WITH_YOUR_PROD_FOLDER_ID"
        emailTo        = listOf("releases@example.com", "cto@example.com")
        changelogEnabled = true
        changelogFormat  = "markdown"
    }
}
