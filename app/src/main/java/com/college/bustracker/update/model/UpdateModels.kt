package com.college.bustracker.update.model

import java.io.File

/**
 * Metadata for a downloadable asset in a GitHub release.
 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String
)

/**
 * Fully parsed metadata for an application release published on GitHub.
 */
data class AppReleaseInfo(
    val versionName: String,
    val versionCode: Long,
    val releaseNotes: String,
    val publishedAt: String,
    val apkAsset: ReleaseAsset,
    val sha256Checksum: String? = null,
    val isDraft: Boolean = false,
    val isPrerelease: Boolean = false
)

/**
 * Result of checking for updates against the GitHub Releases API.
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val releaseInfo: AppReleaseInfo,
        val installedVersionCode: Long,
        val installedVersionName: String
    ) : UpdateCheckResult()

    data class UpToDate(
        val installedVersionCode: Long,
        val installedVersionName: String
    ) : UpdateCheckResult()

    data class Error(
        val message: String,
        val isRateLimited: Boolean = false,
        val cause: Throwable? = null
    ) : UpdateCheckResult()
}

/**
 * Lifecycle state of the APK download and verification process.
 */
sealed class DownloadState {
    data object Idle : DownloadState()

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : DownloadState()

    data class Verifying(
        val message: String
    ) : DownloadState()

    data class ReadyToInstall(
        val apkFile: File,
        val releaseInfo: AppReleaseInfo
    ) : DownloadState()

    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : DownloadState()
}

/**
 * Outcome of cryptographic and package integrity verification.
 */
sealed class VerificationResult {
    data object Success : VerificationResult()
    data class Failure(val reason: String) : VerificationResult()
}
