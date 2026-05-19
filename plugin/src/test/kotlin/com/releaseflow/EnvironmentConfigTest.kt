package com.releaseflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [EnvironmentConfig] — focuses on the Drive folder URL parsing and validation logic.
 */
class EnvironmentConfigTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `driveFolderId extracts ID from standard Drive folder URL`() {
        val config = EnvironmentConfig(
            name = "qa",
            driveFolderUrl = "https://drive.google.com/drive/folders/1abc123XYZ_-def456"
        )
        assertEquals("1abc123XYZ_-def456", config.driveFolderId())
    }

    @Test
    fun `driveFolderId extracts ID from URL with query parameters`() {
        val config = EnvironmentConfig(
            name = "qa",
            driveFolderUrl = "https://drive.google.com/drive/folders/1abc123XYZ?usp=sharing"
        )
        assertEquals("1abc123XYZ", config.driveFolderId())
    }

    @Test
    fun `driveFolderId extracts ID from URL with user path segment`() {
        val config = EnvironmentConfig(
            name = "qa",
            driveFolderUrl = "https://drive.google.com/drive/u/0/folders/1abc123XYZ"
        )
        assertEquals("1abc123XYZ", config.driveFolderId())
    }

    @Test
    fun `driveFolderId accepts a bare folder ID`() {
        val config = EnvironmentConfig(
            name = "qa",
            driveFolderUrl = "1abc123XYZ_def-456"
        )
        assertEquals("1abc123XYZ_def-456", config.driveFolderId())
    }

    @Test
    fun `driveFolderId returns null for blank URL`() {
        val config = EnvironmentConfig(name = "qa", driveFolderUrl = "")
        assertNull(config.driveFolderId())
    }

    @Test
    fun `driveFolderId returns null for malformed URL`() {
        val config = EnvironmentConfig(
            name = "qa",
            driveFolderUrl = "https://drive.google.com/some/other/path"
        )
        assertNull(config.driveFolderId())
    }

    @Test
    fun `validate passes when no optional features are configured`() {
        val config = EnvironmentConfig(name = "qa", buildType = "debug")
        val errors = config.validate(tempDir.root)
        assertTrue("Expected no errors for minimal config, got: $errors", errors.isEmpty())
    }

    @Test
    fun `validate fails when driveFolderUrl is malformed`() {
        val config = EnvironmentConfig(
            name = "qa",
            buildType = "debug",
            driveFolderUrl = "not-a-real-url-or-id !!!"
        )
        val errors = config.validate(tempDir.root)
        assertTrue("Expected at least one error, got none", errors.isNotEmpty())
        assertTrue("Error should mention folder URL", errors.any { it.contains("driveFolderUrl") })
    }

    @Test
    fun `validate passes with valid Drive folder URL and no email config`() {
        val config = EnvironmentConfig(
            name = "qa",
            buildType = "debug",
            driveFolderUrl = "https://drive.google.com/drive/folders/1abc123XYZ"
        )
        val errors = config.validate(tempDir.root)
        assertTrue("Expected no errors, got: $errors", errors.isEmpty())
    }

    @Test
    fun `validate passes with browser email mode and no credentials`() {
        val config = EnvironmentConfig(
            name = "qa",
            buildType = "debug",
            emailTo = listOf("qa@company.com"),
            emailMode = "browser"
            // No emailUsername or emailPassword needed for browser mode!
        )
        val errors = config.validate(tempDir.root)
        assertTrue("Browser mode should not require credentials, got: $errors", errors.isEmpty())
    }

    @Test
    fun `validate fails for smtp mode without credentials`() {
        val config = EnvironmentConfig(
            name = "qa",
            buildType = "debug",
            emailTo = listOf("qa@company.com"),
            emailMode = "smtp"
            // Missing emailUsername and emailPassword
        )
        val errors = config.validate(tempDir.root)
        assertTrue("Expected credential errors", errors.isNotEmpty())
        assertTrue(errors.any { it.contains("emailUsername") })
        assertTrue(errors.any { it.contains("emailPassword") })
    }
}
