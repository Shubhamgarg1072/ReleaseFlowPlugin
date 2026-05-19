package com.releaseflow

import org.gradle.api.Action
import org.gradle.api.Project

/**
 * The `releaseFlow { }` DSL extension that developers configure in their build.gradle.kts.
 *
 * Example:
 * ```kotlin
 * releaseFlow {
 *     projectName = "MyApp"
 *     environment("qa") {
 *         flavor = "qa"
 *         buildType = "debug"
 *         driveFolderUrl = "https://drive.google.com/drive/folders/1abc123..."
 *         emailTo = listOf("qa@company.com")
 *         changelogEnabled = true
 *     }
 * }
 * ```
 */
open class ReleaseFlowExtension(private val project: Project) {

    /** Display name of the project. Defaults to Gradle project name. */
    var projectName: String = project.name

    private val _environments = mutableMapOf<String, EnvironmentConfig>()

    /** Read-only view of all declared environments. */
    val environments: Map<String, EnvironmentConfig>
        get() = _environments.toMap()

    /**
     * Declares a named release environment and configures it via [block].
     *
     * @param name Environment identifier, e.g. "qa", "staging", "production".
     * @param block Lambda that configures the [EnvironmentConfig] for this environment.
     */
    fun environment(name: String, block: Action<EnvironmentConfig>) {
        val config = _environments.getOrPut(name) { EnvironmentConfig(name = name) }
        block.execute(config)
    }

    /** Returns the [EnvironmentConfig] for [name], or null if not declared. */
    fun getEnvironment(name: String): EnvironmentConfig? = _environments[name]
}
