# ReleaseFlow — Android Release Automation Plugin

> One Gradle task. Full release pipeline. **Zero credentials to manage. 100% free.**

ReleaseFlow automates everything between "build approved" and "QA has the APK link in their inbox":
**build → rename → upload to Google Drive OR OneDrive → open Gmail compose → done.**

[![Plugin](https://img.shields.io/badge/Gradle%20Plugin-io.github.Shubhamgarg1072.releaseflow-blue)](https://github.com/Shubhamgarg1072/ReleaseFlowPlugin/packages)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.4.3-orange)](https://github.com/Shubhamgarg1072/ReleaseFlowPlugin/releases)

---

## 💯 Everything is free

| Component                | Free tier                                            |
|--------------------------|------------------------------------------------------|
| Google Drive storage     | **15 GB** per personal Google account                |
| OneDrive storage         | **5 GB** per free Microsoft account (more on M365)   |
| Google Drive API calls   | Free (no quota concerns for release automation)      |
| Microsoft Graph API calls| Free                                                 |
| All libraries used       | Open source (Apache 2.0, MIT)                       |
| OAuth client setup       | Free in both Google Cloud Console and Azure Portal  |
| Email sending            | Uses your own Gmail account (no third-party service) |

**No subscriptions. No paid APIs. No credit card needed anywhere.**

---

## The 3-step setup

```bash
# 1. Sign in once (opens browser — pick Google or OneDrive)
./gradlew releaseFlowLogin            # for Google Drive
# OR
./gradlew releaseFlowLoginOneDrive    # for OneDrive

# 2. Paste a folder URL in build.gradle.kts
#    (works with any Drive or OneDrive folder you have access to)

# 3. Ship it
./gradlew releaseFlowDeployQa
```

That's it. No service-account JSON. No App Passwords. No folder sharing.

---

## What it does

When you run `./gradlew releaseFlowDeployQa`:

1. **Builds** the APK / AAB (`assembleQaDebug`)
2. **Renames** it with a timestamp — `qa-debug-20250519-1430.apk`
3. **Uploads** it to Google Drive or OneDrive (auto-detected from the folder URL) in a `Project / Env / Year / Month` subfolder
4. **Opens Gmail compose** in your browser with subject, recipients, download link, and changelog already filled in
5. **You click Send.** Done.

The pipeline also generates a changelog from git commits since the last tag.

---

## Table of Contents

- [Installation](#installation)
- [Configure your environments](#configure-your-environments)
- [Google Drive vs OneDrive — which to use?](#google-drive-vs-onedrive--which-to-use)
- [How the sign-in flow works](#how-the-sign-in-flow-works)
- [How the email works](#how-the-email-works)
- [Usage commands](#usage-commands)
- [YAML config (optional)](#yaml-config-optional)
- [Headless CI mode](#headless-ci-mode)
- [Local development](#local-development)
- [Publishing new versions](#publishing-new-versions-for-maintainers)
- [License](#license)

---

## Installation

Just **one line** in `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("io.github.Shubhamgarg1072.releaseflow") version "1.4.3"
}
```

That's it. No credentials, no maven repo declarations, no PATs. The plugin is hosted on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.Shubhamgarg1072.releaseflow) — `gradlePluginPortal()` is included by default in every Gradle project.

> 💡 **Backup option:** the plugin is also mirrored to GitHub Packages. If you ever need to use the mirror (private fork, etc.), add the GitHub Packages repo to `pluginManagement { repositories { ... } }` with a `read:packages` PAT — but for the official version, Gradle Plugin Portal needs no setup.

---

## Configure your environments

Just paste folder URLs — the plugin auto-detects whether each is Google Drive or OneDrive.

```kotlin
releaseFlow {
    projectName = "MyApp"

    // Google Drive example
    environment("qa") {
        flavor    = "qa"
        buildType = "debug"
        driveFolderUrl = "https://drive.google.com/drive/folders/1abc123XYZ"
        emailTo  = listOf("qa@company.com", "lead@company.com")
        emailCc  = listOf("pm@company.com")               // optional — visible to everyone
        emailBcc = listOf("archive@company.com")          // optional — hidden from other recipients
        changelogEnabled = true
    }

    // OneDrive example — same field, different URL
    environment("staging") {
        flavor    = "staging"
        buildType = "release"
        driveFolderUrl = "https://1drv.ms/f/s!ABC123xyz"
        emailTo = listOf("staging@company.com")
        changelogEnabled = true
    }

    // Mix and match per environment
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

> 📁 **How to get the folder URL:** Open the target folder in Google Drive or OneDrive (in your browser). Copy the URL from the address bar — that's it. Both providers accept share links and direct URLs.

---

## Google Drive vs OneDrive — which to use?

Both work identically from a plugin-user perspective. Pick based on what your company already uses:

| Feature                  | Google Drive                           | OneDrive                                        |
|--------------------------|----------------------------------------|-------------------------------------------------|
| Free storage             | 15 GB                                  | 5 GB (personal) / 1 TB+ (Microsoft 365)         |
| Sign-in task             | `./gradlew releaseFlowLogin`           | `./gradlew releaseFlowLoginOneDrive`            |
| Folder URL format        | `drive.google.com/drive/folders/<id>`  | `1drv.ms/f/...`, `onedrive.live.com`, SharePoint |
| Works for personal use   | ✅ Free Google account                 | ✅ Free outlook.com / hotmail.com / live.com    |
| Works for business use   | ✅ Google Workspace                    | ✅ Microsoft 365 + SharePoint                   |
| Anonymous download links | ✅                                     | ✅                                              |
| CI-friendly fallback     | Service Account JSON                   | (use OAuth refresh token from CI machine)       |

You can mix providers across environments — e.g. QA on OneDrive, Production on Google Drive.

---

## How the sign-in flow works

The first time you (or any teammate) wants to use ReleaseFlow on a machine:

### For Google Drive folders

```bash
./gradlew releaseFlowLogin
```

Opens your browser → sign in with any Google account → grant Drive access → token saved to `~/.releaseflow/StoredCredential`. **One time per machine, per cloud account.**

### For OneDrive folders

```bash
./gradlew releaseFlowLoginOneDrive
```

Opens your browser → sign in with any Microsoft account (personal or work) → grant Files.ReadWrite → token saved to `~/.releaseflow/onedrive-token-cache.json`.

### Sign out / switch accounts

```bash
./gradlew releaseFlowLogout            # clears Google Drive token
./gradlew releaseFlowLogoutOneDrive    # clears OneDrive token
```

> 🔒 **Permissions requested:**
> - **Google Drive:** `drive.file` scope only — can read/write **only files it creates**, not your other Drive files.
> - **OneDrive:** `Files.ReadWrite` — required for uploads and creating share links.

---

## How the email works

When the pipeline reaches the email step, it builds a Gmail compose URL with:

- **To:** all recipients you listed
- **Subject:** `[MyApp] New QA build — qa-debug-20250519-1430.apk`
- **Body:** download link, folder path, changelog, all pre-filled

…then opens that URL in your default browser. Gmail loads with a draft already populated. **You review. You click Send.**

No App Passwords, no SMTP, no Microsoft Exchange auth. Just one click.

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

> 💡 For CI/automated runs, switch any environment to `emailMode = "smtp"` and provide credentials. See [Headless CI mode](#headless-ci-mode).

---

## Usage commands

```bash
# One-time browser sign-in (pick one based on the folder you're using)
./gradlew releaseFlowLogin              # Google Drive
./gradlew releaseFlowLoginOneDrive      # OneDrive

# Sign out / switch accounts
./gradlew releaseFlowLogout
./gradlew releaseFlowLogoutOneDrive

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

# Skip the cloud upload (email-only delivery)
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
○ Upload: qa-debug-20250519-1430.apk → Google Drive
✓ Uploaded: My Builds/MyApp/qa/2025/May
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
        mode: browser
        to:
          - qa@company.com
        cc:
          - pm@company.com
        bcc:
          - archive@company.com
    changelog:
      enabled: true
      format: plain

  # OneDrive example — same field, just a different URL
  staging:
    build:
      flavor: staging
      type: release
    storage:
      google_drive:
        folder_url: "https://1drv.ms/f/s!ABC123xyz"   # auto-detected as OneDrive
    notifications:
      email:
        mode: browser
        to:
          - staging@company.com
    changelog:
      enabled: true
      format: markdown
```

`${VAR_NAME}` placeholders are resolved from environment variables at build time. The DSL `releaseFlow { }` block always wins when both exist.

---

## Headless CI mode

Browser-based login and Gmail compose can't open a browser on a CI runner. For CI:

### CI Drive uploads — use a Service Account (Google Drive)

```kotlin
environment("qa") {
    flavor = "qa"
    buildType = "debug"
    driveFolderUrl = "https://drive.google.com/drive/folders/1abc123XYZ"
    driveServiceAccountJson = "drive-service-account.json"   // CI-only override
}
```

For OneDrive in CI, copy your locally-generated `~/.releaseflow/onedrive-token-cache.json` to the CI runner as a secret. The refresh token inside renews automatically.

### CI email — use SMTP

```kotlin
environment("qa") {
    emailTo = listOf("qa@company.com")
    emailMode = "smtp"
    emailUsername = System.getenv("RF_EMAIL_USER") ?: ""
    emailPassword = System.getenv("RF_EMAIL_PASS") ?: ""   // Gmail App Password
}
```

Add `RF_EMAIL_USER` and `RF_EMAIL_PASS` as repo secrets in your CI provider.

---

## Local development

The `sample-app/` module uses `includeBuild("../")` — develop and test the plugin without publishing:

```bash
cd sample-app
../gradlew releaseFlowValidate
../gradlew releaseFlowDeployQa -PdryRun=true
```

---

## Publishing new versions (for maintainers)

```bash
# 1. Bump version in gradle.properties and plugin/build.gradle.kts
# 2. Commit and tag
git commit -am "chore: bump version to v1.4.3"
git tag v1.4.3
git push origin main --tags
```

GitHub Actions (`.github/workflows/publish.yml`) auto-publishes to:
- **[Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.Shubhamgarg1072.releaseflow)** — the primary, zero-credentials install path for everyone
- **GitHub Packages** — mirror for private forks or restricted environments

### One-time Plugin Portal setup (maintainer only)

The first publish requires API keys from plugins.gradle.org. The whole process is free:

1. Go to https://plugins.gradle.org/ and **Sign in with GitHub**
2. Open https://plugins.gradle.org/user/me/api-keys → **Generate API key** (give it a name)
3. Copy the **key** and **secret** that appear (you only see them once)
4. In your GitHub repo, go to **Settings → Secrets and variables → Actions → New repository secret** and add:
   - `GRADLE_PUBLISH_KEY` — the API key
   - `GRADLE_PUBLISH_SECRET` — the API secret
5. Push the next `v*` tag — the workflow uses these secrets automatically

> First-time publish of a new plugin id (`io.github.Shubhamgarg1072.releaseflow`) triggers a brief Plugin Portal review (typically a few hours). Subsequent versions of the same plugin publish immediately, with no review.

### OAuth client setup (one-time per cloud provider, free)

The plugin ships with pre-configured OAuth Client IDs so end users never see cloud-portal setup. If you're forking the plugin, create your own free clients once:

**For Google Drive (free):**
1. [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services → Credentials
2. **Create Credentials → OAuth Client ID → Application type: Desktop App**
3. Enable the **Google Drive API** in your project
4. Copy values into `OAuthDriveUploader.kt`

**For OneDrive (free):**
1. [portal.azure.com](https://portal.azure.com) → Azure Active Directory → App registrations
2. **New registration** → Supported account types: **Personal Microsoft accounts and any Azure AD directory**
3. Redirect URI: **Public client/native (mobile & desktop)** → `http://localhost:8989`
4. API permissions → Add → Microsoft Graph → Delegated → `Files.ReadWrite`, `offline_access`
5. Copy the **Application (client) ID** into `OneDriveUploader.kt`

> Both portals are completely free. No subscription, no billing setup required.
> Per [Google](https://developers.google.com/identity/protocols/oauth2/native-app) and [Microsoft](https://learn.microsoft.com/en-us/entra/identity-platform/msal-client-application-configuration), the credentials for Desktop App OAuth clients are not actually secret and can ship with the plugin.

---

## Project structure

```
releaseflow-plugin/
├── plugin/
│   └── src/main/kotlin/com/releaseflow/
│       ├── ReleaseFlowPlugin.kt            ← Plugin<Project> entry point
│       ├── ReleaseFlowExtension.kt         ← DSL: environment("qa") { ... }
│       ├── EnvironmentConfig.kt            ← config + cloud provider auto-detection
│       ├── YamlConfigReader.kt
│       ├── tasks/
│       │   ├── ReleaseFlowLoginTask.kt         ← Google Drive sign-in
│       │   ├── ReleaseFlowLogoutTask.kt
│       │   ├── ReleaseFlowLoginOneDriveTask.kt ← OneDrive sign-in
│       │   ├── ReleaseFlowLogoutOneDriveTask.kt
│       │   ├── ReleaseFlowDeployTask.kt
│       │   └── ReleaseFlowValidateTask.kt
│       ├── pipeline/
│       │   ├── ReleasePipeline.kt
│       │   ├── StepResult.kt
│       │   └── steps/
│       │       ├── BuildStep.kt
│       │       ├── ArtifactStep.kt
│       │       ├── ChangelogStep.kt
│       │       ├── UploadStep.kt           ← dispatches to Google or OneDrive
│       │       └── NotifyStep.kt           ← picks browser vs SMTP email
│       ├── storage/
│       │   ├── OAuthDriveUploader.kt       ← Google Drive OAuth user token
│       │   ├── DriveUploader.kt            ← Google Service Account (CI fallback)
│       │   └── OneDriveUploader.kt         ← Microsoft Graph + MSAL
│       ├── notify/
│       │   ├── BrowserEmailSender.kt       ← opens Gmail compose (default)
│       │   ├── EmailSender.kt              ← JavaMail SMTP (CI fallback)
│       │   └── EmailTemplate.kt
│       └── util/
│           ├── Logger.kt
│           └── Shell.kt
├── sample-app/
├── .github/workflows/publish.yml
└── README.md
```

---

## License

MIT © [Shubham Garg](https://github.com/Shubhamgarg1072)
