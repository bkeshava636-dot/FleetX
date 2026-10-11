package com.college.bustracker.update.service

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object ApkDownloader {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 8192

    /**
     * Downloads an APK from the specified [downloadUrl] into the app's internal cache directory.
     * Follows HTTP 301/302 redirects automatically up to 5 hops.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        versionName: String,
        expectedSizeBytes: Long = 0L,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, percent: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply {
            if (!exists()) mkdirs()
        }

        val targetFile = File(updatesDir, "FleetX_v${versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.apk")
        val tempFile = File(updatesDir, "${targetFile.name}.download")

        if (tempFile.exists()) {
            tempFile.delete()
        }

        var currentUrl = downloadUrl
        var hops = 0
        var connection: HttpURLConnection? = null

        try {
            while (hops < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "FleetX-Android-UpdateChecker")
                    setRequestProperty("Accept", "application/octet-stream")
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308
                ) {
                    val redirectUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (redirectUrl.isNullOrBlank()) {
                        throw IllegalStateException("Received HTTP redirect ($responseCode) without Location header.")
                    }
                    currentUrl = redirectUrl
                    hops++
                    continue
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Failed to download update: HTTP $responseCode")
                }

                break
            }

            val conn = connection ?: throw IllegalStateException("Failed to establish download connection.")
            val contentLength = conn.contentLengthLong.let { len ->
                if (len > 0) len else expectedSizeBytes
            }

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesDownloaded = 0L
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!coroutineContext.isActive) {
                            throw CancellationException("Download cancelled by user.")
                        }
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val percent = if (contentLength > 0) {
                            ((bytesDownloaded * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        onProgress(bytesDownloaded, contentLength, percent)
                    }
                    output.flush()
                }
            }

            if (contentLength > 0 && tempFile.length() != contentLength) {
                tempFile.delete()
                throw IllegalStateException("Downloaded file size mismatch. Expected: $contentLength, Received: ${tempFile.length()}")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                // If rename fails, copy & delete
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            return@withContext targetFile
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        } finally {
            try {
                connection?.disconnect()
            } catch (ignored: Exception) {}
        }
    }
}
