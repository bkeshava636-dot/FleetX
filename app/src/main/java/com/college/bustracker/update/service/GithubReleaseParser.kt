package com.college.bustracker.update.service

import com.college.bustracker.update.model.AppReleaseInfo
import com.college.bustracker.update.model.ReleaseAsset
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

object GithubReleaseParser {

    const val GITHUB_OWNER = "bkeshava636-dot"
    const val GITHUB_REPO = "FleetX"
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val RAW_VERSION_URL = "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/version.json"

    private val SHA256_PATTERN = Pattern.compile("(?i)(?:sha-?256|checksum)[\\s:=]+([a-fA-F0-9]{64})")
    private val VERSION_CODE_PATTERN = Pattern.compile("(?i)(?:versionCode|version_code|code)[\\s:=]+(\\d+)")
    private val SEMVER_PATTERN = Pattern.compile("^[vV]?(\\d+)\\.(\\d+)(?:\\.(\\d+))?")

    /**
     * Parses raw version.json from Fastly CDN (raw.githubusercontent.com) which has no 60 req/hr IP throttle.
     */
    fun parseRawVersionJson(jsonString: String): AppReleaseInfo? {
        return try {
            val json = JSONObject(jsonString)
            val versionCode = json.optLong("versionCode", 0L)
            val versionName = json.optString("versionName", "")
            val downloadUrl = json.optString("downloadUrl", "")
            if (versionCode <= 0 || versionName.isBlank() || downloadUrl.isBlank()) {
                null
            } else {
                val sha256 = json.optString("sha256", "").takeIf { it.isNotBlank() }
                val releaseNotes = json.optString("releaseNotes", "FleetX update available")
                val apkAsset = ReleaseAsset(
                    name = "app-release.apk",
                    downloadUrl = downloadUrl,
                    sizeBytes = json.optLong("sizeBytes", 0L),
                    contentType = "application/vnd.android.package-archive"
                )
                AppReleaseInfo(
                    versionName = versionName,
                    versionCode = versionCode,
                    releaseNotes = releaseNotes,
                    publishedAt = json.optString("publishedAt", ""),
                    apkAsset = apkAsset,
                    sha256Checksum = sha256,
                    isDraft = false,
                    isPrerelease = false
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses a GitHub release JSON string into [AppReleaseInfo].
     * Returns null if the release is a draft, prerelease, or lacks an APK asset.
     */
    fun parseReleaseJson(jsonString: String): AppReleaseInfo? {
        val json = JSONObject(jsonString)
        return parseReleaseObject(json)
    }

    /**
     * Parses a JSON list of releases and returns the latest eligible stable release.
     */
    fun parseReleasesListJson(jsonArrayString: String): AppReleaseInfo? {
        val array = JSONArray(jsonArrayString)
        for (i in 0 until array.length()) {
            val releaseObj = array.getJSONObject(i)
            val info = parseReleaseObject(releaseObj)
            if (info != null) {
                return info
            }
        }
        return null
    }

    private fun parseReleaseObject(json: JSONObject): AppReleaseInfo? {
        val isDraft = json.optBoolean("draft", false)
        val isPrerelease = json.optBoolean("prerelease", false)

        // Strict requirement: ignore draft releases and prereleases
        if (isDraft || isPrerelease) {
            return null
        }

        val tagName = json.optString("tag_name", "").trim()
        val name = json.optString("name", "").trim()
        val body = json.optString("body", "").trim()
        val publishedAt = json.optString("published_at", "").trim()

        val assetsArray = json.optJSONArray("assets") ?: return null
        var apkAsset: ReleaseAsset? = null

        for (i in 0 until assetsArray.length()) {
            val assetObj = assetsArray.getJSONObject(i)
            val assetName = assetObj.optString("name", "")
            if (assetName.endsWith(".apk", ignoreCase = true)) {
                val downloadUrl = assetObj.optString("browser_download_url", "")
                val sizeBytes = assetObj.optLong("size", 0L)
                val contentType = assetObj.optString("content_type", "application/vnd.android.package-archive")
                if (downloadUrl.isNotBlank()) {
                    apkAsset = ReleaseAsset(
                        name = assetName,
                        downloadUrl = downloadUrl,
                        sizeBytes = sizeBytes,
                        contentType = contentType
                    )
                    break
                }
            }
        }

        if (apkAsset == null) {
            return null
        }

        val versionName = extractVersionName(tagName, name)
        val versionCode = extractVersionCode(body, tagName)

        val sha256 = extractSha256(body)

        return AppReleaseInfo(
            versionName = versionName,
            versionCode = versionCode,
            releaseNotes = if (body.isNotBlank()) body else "No release notes provided.",
            publishedAt = publishedAt,
            apkAsset = apkAsset,
            sha256Checksum = sha256,
            isDraft = false,
            isPrerelease = false
        )
    }

    /**
     * Extracts version code from release body metadata, with semantic version fallback.
     */
    fun extractVersionCode(releaseBody: String, tagName: String): Long {
        // 1. Check for explicit `versionCode: 123` in release description/notes
        val matcher = VERSION_CODE_PATTERN.matcher(releaseBody)
        if (matcher.find()) {
            val codeStr = matcher.group(1)
            val parsed = codeStr?.toLongOrNull()
            if (parsed != null && parsed > 0) {
                return parsed
            }
        }

        // 2. Parse semver from tag name e.g. v1.0.1 -> 1 * 10000 + 0 * 100 + 1 = 10001
        val semverMatcher = SEMVER_PATTERN.matcher(tagName.trim())
        if (semverMatcher.find()) {
            val major = semverMatcher.group(1)?.toLongOrNull() ?: 0L
            val minor = semverMatcher.group(2)?.toLongOrNull() ?: 0L
            val patch = semverMatcher.group(3)?.toLongOrNull() ?: 0L
            val computed = (major * 10000) + (minor * 100) + patch
            if (computed > 0) {
                return computed
            }
        }

        // 3. Fallback: single integer tag e.g. "2"
        val rawNum = tagName.trim().removePrefix("v").removePrefix("V").toLongOrNull()
        if (rawNum != null && rawNum > 0) {
            return rawNum
        }

        return 0L
    }

    /**
     * Cleans version name by removing leading 'v' prefix.
     */
    fun extractVersionName(tagName: String, releaseName: String): String {
        val cleanTag = tagName.trim().removePrefix("v").removePrefix("V").trim()
        if (cleanTag.isNotBlank()) return cleanTag
        val cleanName = releaseName.trim().removePrefix("v").removePrefix("V").trim()
        if (cleanName.isNotBlank()) return cleanName
        return "Unknown"
    }

    /**
     * Extracts SHA-256 hash from release body if present.
     */
    fun extractSha256(releaseBody: String): String? {
        val matcher = SHA256_PATTERN.matcher(releaseBody)
        if (matcher.find()) {
            return matcher.group(1)?.lowercase()
        }
        return null
    }
}
