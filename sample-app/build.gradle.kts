plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.releaseflow.gradle")   // loaded from local composite build (see settings.gradle.kts)
}

releaseFlow {
    projectName = "SampleApp"

    environment("qa") {
        flavor    = "qa"
        buildType = "debug"

        // Just paste a Drive folder URL — the plugin handles everything else
        driveFolderUrl = "https://drive.google.com/drive/folders/REPLACE_WITH_YOUR_FOLDER_ID"

        // Zero-config email — opens Gmail compose in browser, you click Send
        emailTo = listOf("qa@example.com", "lead@example.com")

        changelogEnabled = true
    }

    environment("staging") {
        flavor    = "staging"
        buildType = "release"

        driveFolderUrl = "https://drive.google.com/drive/folders/REPLACE_WITH_YOUR_FOLDER_ID"
        emailTo        = listOf("staging@example.com")

        changelogEnabled = true
        changelogFormat  = "markdown"
    }

    environment("production") {
        flavor    = "prod"
        buildType = "release"

        driveFolderUrl = "https://drive.google.com/drive/folders/REPLACE_WITH_YOUR_FOLDER_ID"
        emailTo        = listOf("releases@example.com", "cto@example.com")

        changelogEnabled = true
        changelogFormat  = "markdown"
    }
}
