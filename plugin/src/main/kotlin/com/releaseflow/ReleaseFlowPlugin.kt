package com.releaseflow

import com.releaseflow.tasks.ReleaseFlowDeployTask
import com.releaseflow.tasks.ReleaseFlowLoginTask
import com.releaseflow.tasks.ReleaseFlowLogoutTask
import com.releaseflow.tasks.ReleaseFlowValidateTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Entry point for the ReleaseFlow Gradle plugin.
 *
 * Applies the `releaseFlow { }` DSL extension and registers one deploy task per
 * declared environment, plus a shared validate task.
 */
class ReleaseFlowPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "releaseFlow",
            ReleaseFlowExtension::class.java,
            project
        )

        // Register the validate task eagerly — it doesn't depend on environments
        project.tasks.register("releaseFlowValidate", ReleaseFlowValidateTask::class.java) { task ->
            task.group = "release"
            task.description = "Validate all ReleaseFlow environment configurations"
            task.extension = extension
            task.projectRootDir = project.rootDir
        }

        // Register login/logout tasks — no DSL config needed
        project.tasks.register("releaseFlowLogin", ReleaseFlowLoginTask::class.java) { task ->
            task.group = "release"
            task.description = "One-time Google Drive sign-in (opens browser)"
            task.clientIdOverride = project.findProperty("rf.oauth.clientId")?.toString() ?: ""
            task.clientSecretOverride = project.findProperty("rf.oauth.clientSecret")?.toString() ?: ""
            task.port = project.findProperty("rf.oauth.port")?.toString()?.toIntOrNull() ?: 8888
        }
        project.tasks.register("releaseFlowLogout", ReleaseFlowLogoutTask::class.java) { task ->
            task.group = "release"
            task.description = "Clear the cached Drive OAuth token"
        }

        // After DSL evaluation: read YAML (if present), merge, register per-env deploy tasks
        project.afterEvaluate {
            val yamlFile = project.rootDir.resolve("releaseflow.yaml")
            if (yamlFile.exists() && extension.environments.isEmpty()) {
                YamlConfigReader.readInto(yamlFile, extension)
            }

            extension.environments.forEach { (envName, _) ->
                val taskName = "releaseFlowDeploy${envName.replaceFirstChar { it.uppercase() }}"
                project.tasks.register(taskName, ReleaseFlowDeployTask::class.java) { task ->
                    task.group = "release"
                    task.description = "Build and deploy $envName via ReleaseFlow"
                    task.environmentName = envName
                    task.extension = extension
                    task.projectRootDir = project.rootDir

                    // Wire optional Gradle properties: -PdryRun=true etc.
                    task.dryRun.set(
                        project.findProperty("dryRun")?.toString()?.toBoolean() ?: false
                    )
                    task.skipBuild.set(
                        project.findProperty("skipBuild")?.toString()?.toBoolean() ?: false
                    )
                    task.skipUpload.set(
                        project.findProperty("skipUpload")?.toString()?.toBoolean() ?: false
                    )
                }
            }
        }
    }
}
