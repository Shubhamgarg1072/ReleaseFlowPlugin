# ReleaseFlow — Android Release Automation Plugin

> One Gradle task. Full release pipeline.

ReleaseFlow automates everything between "build approved" and "QA has the APK link in their inbox" — without any manual steps, shell scripts, or CI magic.

[![Plugin](https://img.shields.io/badge/Gradle%20Plugin-com.releaseflow.gradle-blue)](https://github.com/Shubhamgarg1072/ReleaseFlowPlugin/packages)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.0-orange)](https://github.com/Shubhamgarg1072/ReleaseFlowPlugin/releases)

---

## What it does

Given one command — `./gradlew releaseFlowDeployQa` — the plugin:

- **Builds** the APK or AAB for your configured flavor and build type (`assembleQaDebug`, etc.)
- **Renames** the artifact with a timestamped, human-readable filename: `qa-debug-20250519-1430.apk`
- **Uploads** to Google Drive, automatically creating a dated folder: `QA Builds/MyApp/qa/2025/May/`
- **Sends** a styled HTML email with a one-click **Download APK** button and an **Open in Drive** link
- **Generates** a changelog from git commits since the last tag and includes it in the email
- **Validates** all configuration upfront with actionable error messages before running anything

Dry-run mode, skip flags, and YAML config are all supported out of the box.

---

## Table of Contents

- [Installation (3 steps)](#installation-3-steps)
- [Configure environments](#configure-environments)
- [Usage](#usage)
- [Google Drive setup](#google-drive-setup-5-minutes)
- [Gmail App Password setup](#gmail-setup-2-minutes)
- [YAML config (alternative to DSL)](#yaml-config-alternative-to-the-gradle-dsl)
- [What the email looks like](#what-the-email-looks-like)
- [Publishing a new version](#publishing-a-new-version-for-maintainers)
- [Project structure](#project-structure)
- [License](#license)

---

## Installation (3 steps)

### Step 1 — Add GitHub Packages to `settings.gradle.kts`

Open `settings.gradle.kts` at your Android project root and add the plugin repository:

```kotlin
pluginManagement {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Shubhamgarg1072/ReleaseFlowPlugin")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR") ?: ""
                password = providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
```

### Step 2 — Add your GitHub credentials to `~/.gradle/gradle.properties`

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.token=YOUR_GITHUB_TOKEN
```

> The token needs only the **`read:packages`** scope.
> Generate one at: **GitHub → Settings → Developer settings → Personal access tokens (classic)**

This file lives in your home directory, never in the project — so credentials are never committed to source control.

### Step 3 — Apply the plugin in `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("com.releaseflow.gradle") version "1.0.0"
}
```

That's it — the plugin is installed. Now [configure your environments](#configure-environments).

---

## Configure environments

Add the `releaseFlow { }` block to your `app/build.gradle.kts`:

```kotlin
releaseFlow {
    projectName = "MyApp"    // shown in email subject and Drive folder; defaults to project.name

    environment("qa") {
        // --- Build ---
        flavor    = "qa"       // your Android product flavor
        buildType = "debug"    // matches your buildTypes

        // --- Google Drive upload ---
        driveRootFolder  = "QA Builds"               // top-level Drive folder (must already exist)
        driveCredentials = "drive-credentials.json"  // Service Account JSON at project root

        // --- Email notification ---
        emailTo       = listOf("qa@company.com", "lead@company.com")
        emailSmtpHost = "smtp.gmail.com"
        emailSmtpPort = 587
        emailUsername = System.getenv("RF_EMAIL_USER") ?: ""   // your Gmail address
        emailPassword = System.getenv("RF_EMAIL_PASS") ?: ""   // Gmail App Password (16 chars)

        // --- Changelog ---
        changelogEnabled = true
        changelogFormat  = "plain"    // "plain" (• bullets) or "markdown" (- dashes)
    }

    environment("staging") {
        flavor    = "staging"
        buildType = "release"

        driveRootFolder  = "Staging Builds"
        driveCredentials = "drive-credentials.json"

        emailTo       = listOf("staging@company.com")
        emailUsername = System.getenv("RF_EMAIL_USER") ?: ""
        emailPassword = System.getenv("RF_EMAIL_PASS") ?: ""

        changelogEnabled = true
        changelogFormat  = "markdown"
    }

    environment("production") {
        flavor    = "prod"
        buildType = "release"

        driveRootFolder  = "Production Releases"
        driveCredentials = "drive-credentials.json"

        emailTo       = listOf("releases@company.com", "cto@company.com")
        emailUsername = System.getenv("RF_EMAIL_USER") ?: ""
        emailPassword = System.getenv("RF_EMAIL_PASS") ?: ""

        changelogEnabled = true
        changelogFormat  = "markdown"
    }
}
```

**All Drive and email settings are optional.** If `driveRootFolder` is blank the upload step is silently skipped. If `emailTo` is empty the email step is silently skipped. Only the build step always runs.

### Export your secrets

```bash
# Add to ~/.zshrc or ~/.bashrc (or your CI secret store)
export RF_EMAIL_USER="releases@company.com"
export RF_EMAIL_PASS="xxxx xxxx xxxx xxxx"    # Gmail App Password (16 chars)
```

---

## Usage

```bash
# Deploy to QA (build + rename + upload + email + changelog)
./gradlew releaseFlowDeployQa

# Deploy to Staging
./gradlew releaseFlowDeployStaging

# Deploy to Production
./gradlew releaseFlowDeployProduction

# Validate all environment configs (no build, no upload)
./gradlew releaseFlowValidate

# Dry run — prints every step, executes nothing
./gradlew releaseFlowDeployQa -PdryRun=true

# Reuse an existing APK, skip the Gradle build step
./gradlew releaseFlowDeployQa -PskipBuild=true

# Skip the Google Drive upload (email-only delivery)
./gradlew releaseFlowDeployQa -PskipUpload=true
```

**In Android Studio:** open the **Gradle** side panel → expand **Tasks → release** → double-click any `releaseFlowDeploy*` task.

### Terminal output when you run it

```
▶ ReleaseFlow → qa
○ Build: assembleQaDebug
✓ Build completed
○ Artifact: locating output APK/AAB
✓ Artifact renamed to: qa-debug-20250519-1430.apk
○ Changelog: reading git history
✓ Changelog: 12 commit(s)
○ Upload: qa-debug-20250519-1430.apk → QA Builds
✓ Uploaded to Drive: QA Builds/MyApp/qa/2025/May
○ Email: sending notification to [qa@company.com, lead@company.com]
✓ Email notification sent
▶ ReleaseFlow pipeline complete ✓
```

---

## Google Drive setup (5 minutes)

> You only do this once. After setup every environment pointing to the same credentials file uses it automatically.

**1. Open [Google Cloud Console](https://console.cloud.google.com) and select or create a project**

**2. Enable the Google Drive API**
- APIs & Services → Enable APIs & Services → search "Google Drive API" → Enable

**3. Create a Service Account**
- IAM & Admin → Service Accounts → **Create Service Account**
- Name it anything (e.g. `releaseflow-uploader`) → Done

**4. Create a JSON key**
- Click the service account → **Keys** tab → **Add Key → Create new key → JSON**
- A JSON file downloads automatically

**5. Place the JSON file in your project root**
```bash
mv ~/Downloads/your-project-xxxx.json ./drive-credentials.json
```

**6. Share your Drive folder with the service account**
- Open [Google Drive](https://drive.google.com) → right-click your target folder → **Share**
- Paste the `client_email` from inside the JSON file  
  Example: `releaseflow-uploader@your-project.iam.gserviceaccount.com`
- Grant **Editor** access → Share

**7. Add to `.gitignore`**
```gitignore
drive-credentials.json
```
> Never commit service account keys to source control.

---

## Gmail setup (2 minutes)

**1. Enable 2-Step Verification** on your Google account (required for App Passwords)
- [myaccount.google.com/security](https://myaccount.google.com/security) → 2-Step Verification → Turn On

**2. Create an App Password**
- Same Security page → **App Passwords** → App name: `ReleaseFlow` → **Create**
- Copy the 16-character password (spaces are fine)

**3. Export it**
```bash
export RF_EMAIL_PASS="xxxx xxxx xxxx xxxx"
```

---

## YAML config (alternative to the Gradle DSL)

Create `releaseflow.yaml` in your project root. The plugin reads it when no environments are declared in the `releaseFlow { }` block. **DSL always wins over YAML.**

```yaml
project:
  name: MyApp

environments:
  qa:
    build:
      flavor: qa
      type: debug
    storage:
      google_drive:
        root_folder: "QA Builds"
        credentials_file: drive-credentials.json
    notifications:
      email:
        to:
          - qa@company.com
          - lead@company.com
        smtp_host: smtp.gmail.com
        smtp_port: 587
        username: ${RF_EMAIL_USER}
        password: ${RF_EMAIL_PASS}
      slack_webhook: ${RF_SLACK_WEBHOOK}
    changelog:
      enabled: true
      format: plain

  staging:
    build:
      flavor: staging
      type: release
    storage:
      google_drive:
        root_folder: "Staging Builds"
        credentials_file: drive-credentials.json
    notifications:
      email:
        to: [staging@company.com]
        username: ${RF_EMAIL_USER}
        password: ${RF_EMAIL_PASS}
    changelog:
      enabled: true
      format: markdown
```

`${VAR_NAME}` placeholders in any value are resolved from environment variables at build time.

---

## What the email looks like

Each recipient gets a multipart HTML + plain-text email:

| Element | Details |
|---|---|
| **Color-coded banner** | Blue for `qa`, orange for `staging`, red for `production` |
| **Environment badge** | Pill label with the environment name |
| **Artifact filename** | Monospace: `qa-debug-20250519-1430.apk` |
| **Download APK button** | Large colored button — direct download, no sign-in needed |
| **Open in Drive button** | Smaller button — opens the Drive folder |
| **Drive path** | `QA Builds/MyApp/qa/2025/May` |
| **Folder note** | "Future QA builds will appear in this folder" |
| **Changelog** | Bullet list of git commits since last tag (when `changelogEnabled = true`) |
| **Footer** | "Sent by ReleaseFlow" |

---

## Local development (composite build)

The `sample-app/` module uses `includeBuild("../")` so you can test the plugin without publishing:

```bash
cd sample-app
./gradlew releaseFlowValidate
./gradlew releaseFlowDeployQa -PdryRun=true
```

The local plugin source is compiled and used directly. No publish step needed during development.

---

## Publishing a new version (for maintainers)

```bash
# 1. Bump version in gradle.properties and plugin/build.gradle.kts
# 2. Commit and tag
git add .
git commit -m "chore: bump version to v1.1.0"
git tag v1.1.0
git push origin main --tags
```

GitHub Actions (`.github/workflows/publish.yml`) picks up the `v*` tag and publishes to GitHub Packages automatically. Check the **Actions** tab for progress.

---

## Project structure

```
releaseflow-plugin/
├── plugin/                          ← the Gradle plugin source
│   └── src/main/kotlin/com/releaseflow/
│       ├── ReleaseFlowPlugin.kt     ← Plugin<Project> entry point
│       ├── ReleaseFlowExtension.kt  ← DSL: environment("qa") { ... }
│       ├── EnvironmentConfig.kt     ← config data class + validate()
│       ├── YamlConfigReader.kt      ← releaseflow.yaml parser with ${ENV} support
│       ├── tasks/
│       │   ├── ReleaseFlowDeployTask.kt    ← one task per environment
│       │   └── ReleaseFlowValidateTask.kt  ← validation-only task
│       ├── pipeline/
│       │   ├── ReleasePipeline.kt          ← orchestrates all steps
│       │   ├── StepResult.kt               ← Success / Skipped / Failure sealed class
│       │   └── steps/
│       │       ├── BuildStep.kt            ← runs ./gradlew assemble*
│       │       ├── ArtifactStep.kt         ← find + timestamp-rename APK/AAB
│       │       ├── ChangelogStep.kt        ← git log since last tag
│       │       ├── UploadStep.kt           ← delegates to DriveUploader
│       │       └── NotifyStep.kt           ← delegates to EmailSender
│       ├── storage/DriveUploader.kt        ← Google Drive Service Account upload
│       ├── notify/
│       │   ├── EmailSender.kt              ← JavaMail SMTP multipart sender
│       │   └── EmailTemplate.kt           ← HTML + plain-text email templates
│       └── util/
│           ├── Logger.kt                   ← ANSI colored terminal output
│           └── Shell.kt                   ← ProcessBuilder wrapper
├── sample-app/                      ← demo project using composite build
│   ├── build.gradle.kts             ← full DSL example with 3 environments
│   ├── releaseflow.yaml             ← YAML alternative example
│   └── settings.gradle.kts         ← includeBuild("../") composite setup
├── .github/workflows/publish.yml    ← auto-publish to GitHub Packages on v* tag
├── gradle.properties                ← group, version, pluginId
└── README.md
```

---

## License

MIT © [Shubham Garg](https://github.com/Shubhamgarg1072)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
