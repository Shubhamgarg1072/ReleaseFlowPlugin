package com.releaseflow.tasks

import com.releaseflow.ReleaseFlowExtension
import com.releaseflow.pipeline.ReleasePipeline
import com.releaseflow.util.Logger
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that executes the full ReleaseFlow pipeline for a single environment.
 *
 * Registered automatically as `releaseFlowDeploy<EnvName>` for each declared environment.
 *
 * Supports optional Gradle properties:
 * - `-PdryRun=true`   — print steps but do not execute build, upload, or email
 * - `-PskipBuild=true` — skip the Gradle assemble step (use existing APK/AAB)
 * - `-PskipUpload=true` — skip the Google Drive upload step
 */
abstract class ReleaseFlowDeployTask : DefaultTask() {

    @get:Internal
    var environmentName: String = ""

    @get:Internal
    lateinit var extension: ReleaseFlowExtension

    @get:Internal
    lateinit var projectRootDir: File

    @get:Input
    @get:Optional
    abstract val dryRun: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val skipBuild: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val skipUpload: Property<Boolean>

    @TaskAction
    fun deploy() {
        val envConfig = extension.getEnvironment(environmentName)
            ?: throw IllegalStateException(
                "ReleaseFlow: no environment named '$environmentName' declared in releaseFlow { } block.\n" +
                "  → Declare it with: environment(\"$environmentName\") { ... }"
            )

        val projectName = extension.projectName.ifBlank { project.name }
        val isDryRun = dryRun.getOrElse(false)
        val isSkipBuild = skipBuild.getOrElse(false)
        val isSkipUpload = skipUpload.getOrElse(false)

        Logger.header("ReleaseFlow → $environmentName${if (isDryRun) " (DRY RUN)" else ""}")

        val errors = envConfig.validate(projectRootDir)
        if (errors.isNotEmpty()) {
            errors.forEach { Logger.error(it) }
            throw IllegalStateException(
                "ReleaseFlow configuration is invalid. Fix the errors above before running."
            )
        }

        ReleasePipeline(
            envConfig = envConfig,
            projectName = projectName,
            projectRootDir = projectRootDir,
            dryRun = isDryRun,
            skipBuild = isSkipBuild,
            skipUpload = isSkipUpload
        ).run()
    }
}
