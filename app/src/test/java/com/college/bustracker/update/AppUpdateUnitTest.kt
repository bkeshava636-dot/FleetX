package com.college.bustracker.update

import com.college.bustracker.update.model.AppReleaseInfo
import com.college.bustracker.update.model.ReleaseAsset
import com.college.bustracker.update.model.UpdateCheckResult
import com.college.bustracker.update.model.VerificationResult
import com.college.bustracker.update.service.ApkVerifier
import com.college.bustracker.update.service.GithubReleaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class AppUpdateUnitTest {

    // ==========================================
    // 1. VERSION COMPARISON & DOWNGRADE SAFETY
    // ==========================================

    @Test
    fun testUpdateOfferedWhenRemoteVersionCodeIsGreater() {
        val installedCode = 1L
        val remoteCode = 2L
        assertTrue("Update must be offered when remote versionCode > installed", remoteCode > installedCode)
    }

    @Test
    fun testNoUpdateWhenVersionCodesAreEqual() {
        val installedCode = 2L
        val remoteCode = 2L
        assertFalse("Update must NOT be offered when version codes match", remoteCode > installedCode)
    }

    @Test
    fun testNeverDowngradeWhenRemoteVersionCodeIsLower() {
        val installedCode = 5L
        val remoteCode = 3L
        assertFalse("Never offer update when remote version is lower (no downgrade)", remoteCode > installedCode)
    }

    // ==========================================
    // 2. GITHUB RELEASE JSON PARSING
    // ==========================================

    @Test
    fun testParseValidStableReleaseWithApkAsset() {
        val json = """
        {
          "tag_name": "v1.0.1",
          "name": "FleetX v1.0.1",
          "draft": false,
          "prerelease": false,
          "published_at": "2026-10-11T12:00:00Z",
          "body": "Bug fixes and tracking improvements.\n\nversionCode: 2\nSHA-256: 01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b",
          "assets": [
            {
              "name": "FleetX-v1.0.1.apk",
              "browser_download_url": "https://github.com/bkeshava636-dot/FleetX/releases/download/v1.0.1/FleetX-v1.0.1.apk",
              "size": 25165824,
              "content_type": "application/vnd.android.package-archive"
            }
          ]
        }
        """.trimIndent()

        val parsed = GithubReleaseParser.parseReleaseJson(json)
        assertNotNull("Should successfully parse valid stable release", parsed)
        assertEquals("1.0.1", parsed!!.versionName)
        assertEquals(2L, parsed.versionCode)
        assertEquals("FleetX-v1.0.1.apk", parsed.apkAsset.name)
        assertEquals("https://github.com/bkeshava636-dot/FleetX/releases/download/v1.0.1/FleetX-v1.0.1.apk", parsed.apkAsset.downloadUrl)
        assertEquals(25165824L, parsed.apkAsset.sizeBytes)
        assertEquals("01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b", parsed.sha256Checksum)
        assertFalse(parsed.isDraft)
        assertFalse(parsed.isPrerelease)
    }

    @Test
    fun testIgnoreDraftReleases() {
        val json = """
        {
          "tag_name": "v1.0.2",
          "draft": true,
          "prerelease": false,
          "body": "Draft notes",
          "assets": [
            {
              "name": "app.apk",
              "browser_download_url": "https://example.com/app.apk",
              "size": 1000
            }
          ]
        }
        """.trimIndent()

        val parsed = GithubReleaseParser.parseReleaseJson(json)
        assertNull("Draft releases must be completely ignored", parsed)
    }

    @Test
    fun testIgnorePrereleases() {
        val json = """
        {
          "tag_name": "v1.0.2-beta1",
          "draft": false,
          "prerelease": true,
          "body": "Beta notes",
          "assets": [
            {
              "name": "app.apk",
              "browser_download_url": "https://example.com/app.apk",
              "size": 1000
            }
          ]
        }
        """.trimIndent()

        val parsed = GithubReleaseParser.parseReleaseJson(json)
        assertNull("Prereleases must be completely ignored", parsed)
    }

    @Test
    fun testIgnoreReleasesWithoutApkAsset() {
        val json = """
        {
          "tag_name": "v1.0.3",
          "draft": false,
          "prerelease": false,
          "body": "Release without apk",
          "assets": [
            {
              "name": "source_code.tar.gz",
              "browser_download_url": "https://example.com/source.tar.gz",
              "size": 500
            }
          ]
        }
        """.trimIndent()

        val parsed = GithubReleaseParser.parseReleaseJson(json)
        assertNull("Releases without an APK asset must be rejected", parsed)
    }

    // ==========================================
    // 3. VERSION CODE & NAME EXTRACTION
    // ==========================================

    @Test
    fun testExtractExplicitVersionCodeFromBody() {
        val body = "Major updates\nversionCode: 42\nEnjoy the update!"
        val code = GithubReleaseParser.extractVersionCode(body, "v1.0.0")
        assertEquals(42L, code)
    }

    @Test
    fun testExtractSemverFallbackVersionCode() {
        val body = "Regular release without explicit versionCode"
        val code1 = GithubReleaseParser.extractVersionCode(body, "v1.0.1")
        assertEquals(10001L, code1)

        val code2 = GithubReleaseParser.extractVersionCode(body, "v2.3.4")
        assertEquals(20304L, code2)
    }

    @Test
    fun testExtractVersionNameStripsVPrefix() {
        val name1 = GithubReleaseParser.extractVersionName("v1.0.5", "")
        assertEquals("1.0.5", name1)

        val name2 = GithubReleaseParser.extractVersionName("1.0.5", "")
        assertEquals("1.0.5", name2)
    }

    // ==========================================
    // 4. SHA-256 CHECKSUM EXTRACTION & VALIDATION
    // ==========================================

    @Test
    fun testExtractSha256ChecksumFromBody() {
        val body = "Check the integrity:\nSHA-256: 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        val sha = GithubReleaseParser.extractSha256(body)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", sha)
    }

    @Test
    fun testCalculateFileSha256() {
        val tempFile = File.createTempFile("test_sha", ".bin")
        try {
            tempFile.writeText("FleetX Campus Bus Tracker Test Payload")
            val computedHash = ApkVerifier.calculateSha256(tempFile)

            // Known sha256 for the string above
            val md = MessageDigest.getInstance("SHA-256")
            val expectedBytes = md.digest("FleetX Campus Bus Tracker Test Payload".toByteArray())
            val expectedHex = expectedBytes.joinToString("") { String.format("%02x", it) }

            assertEquals(expectedHex, computedHash)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testVerifySha256Matches() {
        val tempFile = File.createTempFile("test_sha_match", ".bin")
        try {
            tempFile.writeText("Hello FleetX")
            val hash = ApkVerifier.calculateSha256(tempFile)
            val result = ApkVerifier.verifySha256(tempFile, hash.uppercase()) // Case insensitivity test
            assertTrue("Should succeed on matching hash", result is VerificationResult.Success)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testVerifySha256FailsOnCorruptedFile() {
        val tempFile = File.createTempFile("test_sha_mismatch", ".bin")
        try {
            tempFile.writeText("Corrupted content")
            val wrongHash = "0000000000000000000000000000000000000000000000000000000000000000"
            val result = ApkVerifier.verifySha256(tempFile, wrongHash)
            assertTrue("Should fail on hash mismatch", result is VerificationResult.Failure)
        } finally {
            tempFile.delete()
        }
    }

    // ==========================================
    // 5. SIGNATURE MATCHING LOGIC
    // ==========================================

    @Test
    fun testSignatureMatchingSuccess() {
        val certA = "CertificateA".toByteArray()
        val certB = "CertificateB".toByteArray()

        val installedSignatures = listOf(certA, certB)
        val incomingSignatures = listOf(certA)

        val match = ApkVerifier.signaturesMatch(installedSignatures, incomingSignatures)
        assertTrue("Signature match must be detected when identical certificate byte array is present", match)
    }

    @Test
    fun testSignatureMatchingMismatch() {
        val certInstalled = "OfficialFleetXSigningKey".toByteArray()
        val certUntrusted = "MaliciousOrDifferentKey".toByteArray()

        val installedSignatures = listOf(certInstalled)
        val incomingSignatures = listOf(certUntrusted)

        val match = ApkVerifier.signaturesMatch(installedSignatures, incomingSignatures)
        assertFalse("Must reject when signing certificates do not match", match)
    }
}
