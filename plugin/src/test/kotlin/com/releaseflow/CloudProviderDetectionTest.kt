package com.releaseflow

import com.releaseflow.EnvironmentConfig.CloudProvider
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [EnvironmentConfig.cloudProvider] — automatic cloud provider detection
 * from the folder URL.
 */
class CloudProviderDetectionTest {

    private fun providerFor(url: String): CloudProvider =
        EnvironmentConfig(name = "test", driveFolderUrl = url).cloudProvider()

    @Test
    fun `Google Drive standard URL is detected`() {
        assertEquals(
            CloudProvider.GOOGLE_DRIVE,
            providerFor("https://drive.google.com/drive/folders/1abc123")
        )
    }

    @Test
    fun `Google Drive URL with query string is detected`() {
        assertEquals(
            CloudProvider.GOOGLE_DRIVE,
            providerFor("https://drive.google.com/drive/folders/1abc123?usp=sharing")
        )
    }

    @Test
    fun `Google Drive URL with user path is detected`() {
        assertEquals(
            CloudProvider.GOOGLE_DRIVE,
            providerFor("https://drive.google.com/drive/u/0/folders/1abc123")
        )
    }

    @Test
    fun `bare folder ID is treated as Google Drive`() {
        assertEquals(
            CloudProvider.GOOGLE_DRIVE,
            providerFor("1abc123XYZ_def-456")
        )
    }

    @Test
    fun `OneDrive short link is detected`() {
        assertEquals(
            CloudProvider.ONE_DRIVE,
            providerFor("https://1drv.ms/f/s!ABC123xyz")
        )
    }

    @Test
    fun `OneDrive personal long URL is detected`() {
        assertEquals(
            CloudProvider.ONE_DRIVE,
            providerFor("https://onedrive.live.com/?cid=abc&id=ABC%21123&authkey=foo")
        )
    }

    @Test
    fun `SharePoint business URL is detected as OneDrive`() {
        assertEquals(
            CloudProvider.ONE_DRIVE,
            providerFor("https://contoso-my.sharepoint.com/personal/user/Documents/Releases")
        )
    }

    @Test
    fun `blank URL returns UNKNOWN`() {
        assertEquals(CloudProvider.UNKNOWN, providerFor(""))
    }

    @Test
    fun `unrelated URL returns UNKNOWN`() {
        assertEquals(
            CloudProvider.UNKNOWN,
            providerFor("https://dropbox.com/sh/abc123/file.apk")
        )
    }

    @Test
    fun `Google Drive folderId extraction returns null for OneDrive URLs`() {
        val config = EnvironmentConfig(
            name = "qa",
            driveFolderUrl = "https://1drv.ms/f/s!ABC123"
        )
        assertEquals(null, config.driveFolderId())
    }
}
