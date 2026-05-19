# ReleaseFlow — Android Release Automation Plugin

> One Gradle task. Full release pipeline. **Zero credentials to manage.**

ReleaseFlow automates everything between "build approved" and "QA has the APK link in their inbox":
**build → rename → upload to Drive → open Gmail compose → done.**

[![Plugin](https://img.shields.io/badge/Gradle%20Plugin-com.releaseflow.gradle-blue)](https://github.com/Shubhamgarg1072/ReleaseFlowPlugin/packages)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.1.0-orange)](https://github.com/Shubhamgarg1072/ReleaseFlowPlugin/releases)

---

## The 3-step setup

```bash
# 1. Sign in to Google Drive once (opens browser)
./gradlew releaseFlowLogin

# 2. Paste a Drive folder URL in your build.gradle.kts
#    (see config example below)

# 3. Ship it
./gradlew releaseFlowDeployQa
```

**That's it.** No service account JSON. No Gmail App Passwords. No folder sharing.
Just sign in once → paste folder URL → run deploy.

---

## What it does

When you run `./gradlew releaseFlowDeployQa`:

1. **Builds** the APK / AAB (`assembleQaDebug`)
2. **Renames** it with a timestamp — `qa-debug-20250519-1430.apk`
3. **Uploads** it to Drive in a `Project / Env / Year / Month` subfolder
4. **Opens Gmail compose** in your browser with subject, recipients, download link, and changelog already filled in
5. **You click Send.** Done.

The pipeline also generates a changelog from git commits since the last tag.

---

## Table of Contents

- [Installation](#installation)
- [Configure your environments](#configure-your-environments)
- [How the OAuth login works](#how-the-oauth-login-works)
- [How the email works](#how-the-email-works)
- [Usage commands](#usage-commands)
- [YAML config (optional)](#yaml-config-optional)
- [Headless CI mode](#headless-ci-mode)
- [Local development](#local-development)
- [Publishing new versions](#publishing-new-versions-for-maintainers)
- [License](#license)

---

## Installation

### Step 1 — Add the plugin repo to `settings.gradle.kts`

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

The token needs only the **`read:packages`** scope.
Generate at: **GitHub → Settings → Developer settings → Personal access tokens (classic)**

### Step 3 — Apply the plugin in `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("com.releaseflow.gradle") version "1.1.0"
}
```

That's the install. Now configure your environments and sign in.

---

## Configure your environments

In `app/build.gradle.kts`:

```kotlin
releaseFlow {
    projectName = "MyApp"

    environment("qa") {
        flavor    = "qa"
        buildType = "debug"

        // 👇 Just paste a Drive folder URL. Copy it from your browser address bar.
        driveFolderUrl = "https://drive.google.com/drive/folders/1abc123XYZ"

        // 👇 Recipients. Gmail compose opens with these already in the To: field.
        emailTo = listOf("qa@company.com", "lead@company.com")

        changelogEnabled = true
    }

    environment("staging") {
        flavor    = "staging"
        buildType = "release"
        driveFolderUrl = "https://drive.google.com/drive/folders/1xyz456ABC"
        emailTo = listOf("staging@company.com")
        changelogEnabled = true
    }

    environment("production") {
        flavor    = "prod"
        buildType = "release"
        driveFolderUrl = "https://drive.google.com/drive/folders/1prod789DEF"
        emailTo = listOf("releases@company.com")
        changelogEnabled = true
        changelogFormat  = "markdown"
    }
}
```

**That's the whole config.** No SMTP host, no usernames, no passwords, no JSON files.

> 📁 **How to get the folder URL:** Open the target folder in Google Drive (in your browser). The address bar shows `https://drive.google.com/drive/folders/<long-id>?usp=sharing`. Copy the whole URL.

---

## How the OAuth login works

The first time you (or any teammate) wants to use ReleaseFlow on a machine:

```bash
./gradlew releaseFlowLogin
```

This will:

1. Open your default browser to Google's sign-in page
2. Ask you to choose a Google account and grant Drive access
3. Save a refresh token to `~/.releaseflow/StoredCredential` on your machine
4. Use that token automatically for every future deploy — **no need to sign in again**

To switch accounts: `./gradlew releaseFlowLogout` then `./gradlew releaseFlowLogin`.

> 🔒 **What permissions does the plugin request?**
> Only `drive.file` scope — it can read/write **only the files it creates**. It can't see your other Drive files.

---

## How the email works

When the pipeline reaches the email step, it builds a Gmail compose URL with:

- **To:** all recipients you listed
- **Subject:** `[MyApp] New QA build — qa-debug-20250519-1430.apk`
- **Body:** download link, Drive folder path, changelog, all pre-filled

…then opens that URL in your default browser. Gmail loads with a new draft already populated.

**You review it. You click Send. Done.**

No App Passwords, no SMTP credentials, no "less secure apps" toggles.

### Example email body

```
Hi team,

A new MyApp QA build is ready:

  Artifact: qa-debug-20250519-1430.apk

📥 Download:
  https://drive.google.com/uc?export=download&id=...

📁 Open in Drive:
  https://drive.google.com/file/d/.../view

Folder: My Builds/MyApp/qa/2025/May
(Future QA builds will land in this folder.)

📋 Changelog:
  • Fix login crash on Android 14
  • Add dark mode toggle to settings
  • Update Firebase SDK to 32.1.0

— Sent by ReleaseFlow
```

> 💡 **For CI/automated runs**, switch any environment to `emailMode = "smtp"` and provide credentials. See [Headless CI mode](#headless-ci-mode) below.

---

## Usage commands

```bash
# One-time browser sign-in (Drive)
./gradlew releaseFlowLogin

# Sign out / switch accounts
./gradlew releaseFlowLogout

# Deploy
./gradlew releaseFlowDeployQa
./gradlew releaseFlowDeployStaging
./gradlew releaseFlowDeployProduction

# Validate all environments without running the pipeline
./gradlew releaseFlowValidate

# Dry run — prints every step, executes nothing
./gradlew releaseFlowDeployQa -PdryRun=true

# Reuse existing APK, skip the Gradle build step
./gradlew releaseFlowDeployQa -PskipBuild=true

# Skip the Drive upload (email-only delivery)
./gradlew releaseFlowDeployQa -PskipUpload=true
```

**In Android Studio:** Gradle panel → **Tasks → release** → double-click any task.

### Pipeline output

```
▶ ReleaseFlow → qa
○ Build: assembleQaDebug
✓ Build completed
○ Artifact: locating output APK/AAB
✓ Artifact renamed to: qa-debug-20250519-1430.apk
○ Changelog: reading git history
✓ Changelog: 12 commit(s)
○ Upload: qa-debug-20250519-1430.apk → Drive folder 1abc123XYZ
✓ Uploaded to Drive: My Builds/MyApp/qa/2025/May
○ Email: opening Gmail compose in browser for [qa@company.com, lead@company.com]
✓ Gmail compose opened in your browser — review and click Send.
▶ ReleaseFlow pipeline complete ✓
```

---

## YAML config (optional)

If you prefer to keep release config out of `build.gradle.kts`, create `releaseflow.yaml` in your project root:

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
        folder_url: "https://drive.google.com/drive/folders/1abc123XYZ"
    notifications:
      email:
        mode: browser   # or "smtp" for headless CI
        to:
          - qa@company.com
          - lead@company.com
    changelog:
      enabled: true
      format: plain
```

`${VAR_NAME}` placeholders are resolved from environment variables at build time. The DSL `releaseFlow { }` block always wins when both exist.

---

## Headless CI mode

Browser-based login and Gmail compose can't open a browser on a CI runner. For CI:

### CI Drive uploads — use a Service Account

```kotlin
environment("qa") {
    flavor = "qa"
    buildType = "debug"
    driveFolderUrl = "https://drive.google.com/drive/folders/1abc123XYZ"

    // CI-only: point to a Service Account JSON (overrides OAuth token)
    driveServiceAccountJson = "drive-service-account.json"

    // ...
}
```

Share the Drive folder with the service account email (`name@project.iam.gserviceaccount.com`) once.

### CI email — use SMTP

```kotlin
environment("qa") {
    // ...
    emailTo = listOf("qa@company.com")
    emailMode = "smtp"      // ← switch from default "browser"
    emailUsername = System.getenv("RF_EMAIL_USER") ?: ""
    emailPassword = System.getenv("RF_EMAIL_PASS") ?: ""   // Gmail App Password
}
```

Add `RF_EMAIL_USER` and `RF_EMAIL_PASS` as repo secrets in your CI provider.

---

## Local development

The `sample-app/` module uses `includeBuild("../")` — you can develop and test the plugin without publishing:

```bash
cd sample-app
../gradlew releaseFlowValidate
../gradlew releaseFlowDeployQa -PdryRun=true
```

The local plugin source compiles and runs in-place. No publish step needed during development.

---

## Publishing new versions (for maintainers)

```bash
# 1. Bump version in gradle.properties and plugin/build.gradle.kts
# 2. Commit and tag
git commit -am "chore: bump version to v1.2.0"
git tag v1.2.0
git push origin main --tags
```

GitHub Actions (`.github/workflows/publish.yml`) auto-publishes to GitHub Packages on every `v*` tag.

### OAuth client setup (one-time, for maintainers only)

The plugin ships with a pre-configured OAuth Client ID so end users never see Google Cloud setup. If you're forking the plugin, you'll need to create your own OAuth client once:

1. [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services → Credentials
2. **Create Credentials → OAuth Client ID → Application type: Desktop App**
3. Enable the **Google Drive API** for your project
4. Copy the Client ID and Secret into `OAuthDriveUploader.kt`:
   ```kotlin
   const val DEFAULT_CLIENT_ID = "your-id.apps.googleusercontent.com"
   const val DEFAULT_CLIENT_SECRET = "your-secret"
   ```

> Per [Google's OAuth docs](https://developers.google.com/identity/protocols/oauth2/native-app), the client secret for Desktop App OAuth clients is not actually secret and can ship with the plugin.

---

## Project structure

```
releaseflow-plugin/
├── plugin/                          ← the Gradle plugin source
│   └── src/main/kotlin/com/releaseflow/
│       ├── ReleaseFlowPlugin.kt           ← Plugin<Project> entry point
│       ├── ReleaseFlowExtension.kt        ← DSL: environment("qa") { ... }
│       ├── EnvironmentConfig.kt           ← config + driveFolderId() parser
│       ├── YamlConfigReader.kt            ← releaseflow.yaml parser
│       ├── tasks/
│       │   ├── ReleaseFlowLoginTask.kt    ← one-time browser sign-in
│       │   ├── ReleaseFlowLogoutTask.kt   ← clear cached token
│       │   ├── ReleaseFlowDeployTask.kt   ← per-environment deploy
│       │   └── ReleaseFlowValidateTask.kt ← config validation
│       ├── pipeline/
│       │   ├── ReleasePipeline.kt         ← orchestrates all steps
│       │   ├── StepResult.kt              ← Success / Skipped / Failure
│       │   └── steps/
│       │       ├── BuildStep.kt           ← runs ./gradlew assemble*
│       │       ├── ArtifactStep.kt        ← find + timestamp-rename APK/AAB
│       │       ├── ChangelogStep.kt       ← git log since last tag
│       │       ├── UploadStep.kt          ← picks OAuth vs Service Account
│       │       └── NotifyStep.kt          ← picks browser vs SMTP email
│       ├── storage/
│       │   ├── OAuthDriveUploader.kt      ← OAuth user token (default)
│       │   └── DriveUploader.kt           ← Service Account (CI fallback)
│       ├── notify/
│       │   ├── BrowserEmailSender.kt      ← opens Gmail compose (default)
│       │   ├── EmailSender.kt             ← JavaMail SMTP (CI fallback)
│       │   └── EmailTemplate.kt           ← HTML + plain-text templates
│       └── util/
│           ├── Logger.kt                  ← ANSI colored terminal output
│           └── Shell.kt                   ← ProcessBuilder wrapper
├── sample-app/                      ← demo project (composite build)
├── .github/workflows/publish.yml    ← auto-publish on v* tag
└── README.md
```

---

## License

MIT © [Shubham Garg](https://github.com/Shubhamgarg1072)
