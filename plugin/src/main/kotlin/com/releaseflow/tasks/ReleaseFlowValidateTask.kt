package com.releaseflow.tasks

import com.releaseflow.ReleaseFlowExtension
import com.releaseflow.util.Logger
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Validates all declared ReleaseFlow environments and prints a summary.
 *
 * Run with: `./gradlew releaseFlowValidate`
 */
abstract class ReleaseFlowValidateTask : DefaultTask() {

    @get:Internal
    lateinit var extension: ReleaseFlowExtension

    @get:Internal
    lateinit var projectRootDir: File

    @TaskAction
    fun validate() {
        Logger.header("ReleaseFlow Validate")

        val allEnvironments = extension.environments
        if (allEnvironments.isEmpty()) {
            Logger.warn("No environments declared in releaseFlow { } block and no releaseflow.yaml found.")
            return
        }

        var allValid = true

        allEnvironments.forEach { (name, config) ->
            val errors = config.validate(projectRootDir)
            if (errors.isEmpty()) {
                Logger.ok("Environment '$name' — configuration is valid")
            } else {
                allValid = false
                Logger.error("Environment '$name' — ${errors.size} error(s):")
                errors.forEach { Logger.error("  $it") }
            }
        }

        if (allValid) {
            Logger.ok("All ${allEnvironments.size} environment(s) validated successfully.")
        } else {
            throw GradleException("ReleaseFlow: one or more environments have invalid configuration. See errors above.")
        }
    }
}
