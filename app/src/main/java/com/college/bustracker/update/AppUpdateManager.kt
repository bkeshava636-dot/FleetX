package com.college.bustracker.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.college.bustracker.update.model.AppReleaseInfo
import com.college.bustracker.update.model.DownloadState
import com.college.bustracker.update.model.UpdateCheckResult
import com.college.bustracker.update.model.VerificationResult
import com.college.bustracker.update.service.ApkDownloader
import com.college.bustracker.update.service.ApkVerifier
import com.college.bustracker.update.service.AppUpdateInstaller
import com.college.bustracker.update.service.GithubReleaseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateManager private constructor() {

    companion object {
        val instance: AppUpdateManager by lazy { AppUpdateManager() }

        private const val PREFS_NAME = "fleetx_update_prefs"
        private const val KEY_LAST_CHECK_MS = "key_last_check_ms"
        private const val KEY_DISMISSED_VERSION_CODE = "key_dismissed_version_code"
        private const val MIN_CHECK_INTERVAL_MS = 60 * 1000L // 1 minute
    }

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheckResult: StateFlow<UpdateCheckResult?> = _updateCheckResult.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var activeDownloadJob: Job? = null

    /**
     * Determines the installed application's long versionCode safely across Android versions.
     */
    fun getInstalledVersionCode(context: Context): Long {
        val pm = context.packageManager
        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
    }

    /**
     * Determines the installed application's versionName.
     */
    fun getInstalledVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Checks whether an update is available from GitHub Releases.
     *
     * @param isManual True if initiated explicitly by user (bypasses interval and dismissed version checks).
     */
    suspend fun checkForUpdates(context: Context, isManual: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        val now = System.currentTimeMillis()

        val installedCode = getInstalledVersionCode(context)
        val installedName = getInstalledVersionName(context)

        // Throttle automatic checks to prevent rate limiting
        if (!isManual && (now - lastCheck) < MIN_CHECK_INTERVAL_MS) {
            val cachedResult = _updateCheckResult.value
            if (cachedResult != null) {
                return@withContext cachedResult
            }
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(GithubReleaseParser.LATEST_RELEASE_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "FleetX-Android-UpdateChecker")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val responseCode = connection.responseCode
            var releaseInfo: AppReleaseInfo? = null

            if (responseCode == 403 || responseCode == 429 || responseCode != HttpURLConnection.HTTP_OK) {
                android.util.Log.w("AppUpdateManager", "GitHub API rate limit or error ($responseCode); falling back to Fastly CDN raw version.json")
                releaseInfo = fetchFromRawCdn()
                if (releaseInfo == null && (responseCode == 403 || responseCode == 429)) {
                    val err = UpdateCheckResult.Error(
                        message = "GitHub API rate limit exceeded. Please try again later.",
                        isRateLimited = true
                    )
                    _updateCheckResult.value = err
                    return@withContext err
                } else if (releaseInfo == null && responseCode == 404) {
                    val upToDate = UpdateCheckResult.UpToDate(installedCode, installedName)
                    _updateCheckResult.value = upToDate
                    return@withContext upToDate
                } else if (releaseInfo == null) {
                    val err = UpdateCheckResult.Error("Unable to fetch update info (HTTP $responseCode).")
                    _updateCheckResult.value = err
                    return@withContext err
                }
            } else {
                val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                releaseInfo = GithubReleaseParser.parseReleaseJson(body) ?: fetchFromRawCdn()
            }

            prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply()

            if (releaseInfo == null) {
                val upToDate = UpdateCheckResult.UpToDate(installedCode, installedName)
                _updateCheckResult.value = upToDate
                return@withContext upToDate
            }

            // Only offer update if remote versionCode is strictly greater than installed versionCode
            if (releaseInfo.versionCode > installedCode) {
                val dismissedCode = prefs.getLong(KEY_DISMISSED_VERSION_CODE, -1L)
                val result = UpdateCheckResult.UpdateAvailable(
                    releaseInfo = releaseInfo,
                    installedVersionCode = installedCode,
                    installedVersionName = installedName
                )
                _updateCheckResult.value = result
                return@withContext result
            } else {
                val upToDate = UpdateCheckResult.UpToDate(installedCode, installedName)
                _updateCheckResult.value = upToDate
                return@withContext upToDate
            }
        } catch (e: Exception) {
            val fallback = fetchFromRawCdn()
            if (fallback != null) {
                prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply()
                if (fallback.versionCode > installedCode) {
                    val result = UpdateCheckResult.UpdateAvailable(
                        releaseInfo = fallback,
                        installedVersionCode = installedCode,
                        installedVersionName = installedName
                    )
                    _updateCheckResult.value = result
                    return@withContext result
                } else {
                    val upToDate = UpdateCheckResult.UpToDate(installedCode, installedName)
                    _updateCheckResult.value = upToDate
                    return@withContext upToDate
                }
            }
            val err = UpdateCheckResult.Error(
                message = "Failed to connect to update service. Check your internet connection.",
                cause = e
            )
            _updateCheckResult.value = err
            return@withContext err
        } finally {
            try {
                connection?.disconnect()
            } catch (ignored: Exception) {}
        }
    }

    private fun fetchFromRawCdn(): AppReleaseInfo? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(GithubReleaseParser.RAW_VERSION_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "FleetX-Android-UpdateChecker")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                GithubReleaseParser.parseRawVersionJson(body)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Checks if this version was previously dismissed by the user for automatic prompts.
     */
    fun isVersionDismissed(context: Context, versionCode: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissed = prefs.getLong(KEY_DISMISSED_VERSION_CODE, -1L)
        return dismissed == versionCode
    }

    /**
     * Marks a version as dismissed so automatic prompts will not nag the user.
     */
    fun dismissVersion(context: Context, versionCode: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_DISMISSED_VERSION_CODE, versionCode).apply()
    }

    /**
     * Initiates APK download and automated security/signature verification.
     */
    fun startDownload(
        context: Context,
        releaseInfo: AppReleaseInfo,
        scope: CoroutineScope
    ) {
        if (_downloadState.value is DownloadState.Downloading) {
            return
        }

        activeDownloadJob?.cancel()
        activeDownloadJob = scope.launch {
            _downloadState.value = DownloadState.Downloading(0L, releaseInfo.apkAsset.sizeBytes, 0)

            try {
                val apkFile = ApkDownloader.downloadApk(
                    context = context,
                    downloadUrl = releaseInfo.apkAsset.downloadUrl,
                    versionName = releaseInfo.versionName,
                    expectedSizeBytes = releaseInfo.apkAsset.sizeBytes
                ) { downloaded, total, percent ->
                    _downloadState.value = DownloadState.Downloading(downloaded, total, percent)
                }

                _downloadState.value = DownloadState.Verifying("Verifying APK integrity and signature...")

                val verification = ApkVerifier.verifyApk(
                    context = context,
                    apkFile = apkFile,
                    expectedSha256 = releaseInfo.sha256Checksum
                )

                if (verification is VerificationResult.Failure) {
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }
                    _downloadState.value = DownloadState.Error(verification.reason, canRetry = true)
                } else {
                    _downloadState.value = DownloadState.ReadyToInstall(apkFile, releaseInfo)
                }
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(
                    message = e.message ?: "Download failed. Please try again.",
                    canRetry = true
                )
            }
        }
    }

    /**
     * Cancels an ongoing download.
     */
    fun cancelDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        _downloadState.value = DownloadState.Idle
    }

    /**
     * Launches the Android package installer for a verified APK.
     */
    fun installUpdate(context: Context, apkFile: File) {
        AppUpdateInstaller.launchInstaller(context, apkFile)
    }

    /**
     * Clears error states.
     */
    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }
}
