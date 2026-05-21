plugins {
    kotlin("jvm") version "1.9.22"
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "io.github.Shubhamgarg1072"
version = "1.5.1"

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    website.set("https://github.com/Shubhamgarg1072/ReleaseFlowPlugin")
    vcsUrl.set("https://github.com/Shubhamgarg1072/ReleaseFlowPlugin.git")

    plugins {
        create("releaseFlow") {
            id = "io.github.Shubhamgarg1072.releaseflow"
            implementationClass = "com.releaseflow.ReleaseFlowPlugin"
            displayName = "ReleaseFlow"
            description = "Plug-and-play Android release automation: build → rename → upload to Google Drive or OneDrive → email QA via browser. Zero credentials to manage. OAuth one-time sign-in. Free."
            tags.set(listOf("android", "release", "automation", "google-drive", "onedrive", "release-management", "ci-cd", "deployment"))
        }
    }
}

dependencies {
    // Google Drive (OAuth + Service Account)
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.20.0")

    // Microsoft OneDrive (MSAL OAuth + Graph REST API)
    implementation("com.microsoft.azure:msal4j:1.14.3")

    // Email (SMTP fallback) + YAML config
    implementation("com.sun.mail:jakarta.mail:2.0.1")
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation(gradleTestKit())
}

publishing {
    // java-gradle-plugin automatically creates a "pluginMaven" publication.
    // We only need to add POM metadata and the target repository.
    publications.withType<MavenPublication> {
        pom {
            name.set("ReleaseFlow Gradle Plugin")
            description.set("Plug-and-play Android release automation plugin")
            url.set("https://github.com/Shubhamgarg1072/ReleaseFlowPlugin")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri(
                "https://maven.pkg.github.com/${System.getenv("GITHUB_ACTOR") ?: "Shubhamgarg1072"}/ReleaseFlowPlugin"
            )
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
