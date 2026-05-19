package com.releaseflow.pipeline

/**
 * Outcome of a single pipeline step.
 */
sealed class StepResult {
    /** Step completed successfully, carrying an optional payload value. */
    data class Success<T>(val value: T) : StepResult()

    /** Step was intentionally skipped (e.g. optional feature not configured). */
    data class Skipped(val reason: String) : StepResult()

    /** Step failed with a user-facing message. */
    data class Failure(val message: String, val cause: Throwable? = null) : StepResult()
}
