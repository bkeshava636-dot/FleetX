package com.college.bustracker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object SchedulePdfHelper {

    /**
     * Extracts filename from content Uri.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "route_schedule.pdf"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
        } catch (_: Exception) {}
        return name
    }

    /**
     * Uploads a route schedule PDF to Firebase Storage and returns download URL & filename.
     */
    suspend fun uploadPdf(
        context: Context,
        routeId: String,
        uri: Uri
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri)
        val timestamp = System.currentTimeMillis()
        val path = "route_schedules/${routeId}_$timestamp.pdf"

        // Attempt 1: default FirebaseStorage instance
        val primaryResult = tryUpload(FirebaseStorage.getInstance(), path, uri, fileName)
        if (primaryResult.isSuccess) return@withContext primaryResult

        // Attempt 2: fallback to .appspot.com bucket format
        try {
            val appspotStorage = FirebaseStorage.getInstance("gs://college-bus-tracker-prod.appspot.com")
            val appspotResult = tryUpload(appspotStorage, path, uri, fileName)
            if (appspotResult.isSuccess) return@withContext appspotResult
        } catch (_: Exception) {}

        // Attempt 3: fallback to .firebasestorage.app bucket format
        try {
            val fbaStorage = FirebaseStorage.getInstance("gs://college-bus-tracker-prod.firebasestorage.app")
            val fbaResult = tryUpload(fbaStorage, path, uri, fileName)
            if (fbaResult.isSuccess) return@withContext fbaResult
        } catch (_: Exception) {}

        val error = primaryResult.exceptionOrNull()
        val errText = error?.message ?: ""
        val friendlyMessage = if (errText.contains("does not exist", ignoreCase = true) ||
            errText.contains("404", ignoreCase = true) ||
            errText.contains("Object does not exist at location", ignoreCase = true)) {
            "Cloud Storage is not enabled in your Firebase Console. Please open Firebase Console -> Build -> Storage -> Click 'Get Started'. Or use 'Link Web / Drive URL' to paste a Google Drive link."
        } else {
            error?.message ?: "Upload failed"
        }
        Result.failure(Exception(friendlyMessage))
    }

    private suspend fun tryUpload(
        storage: FirebaseStorage,
        path: String,
        uri: Uri,
        fileName: String
    ): Result<Pair<String, String>> {
        return try {
            val ref = storage.reference.child(path)
            ref.putFile(uri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(Pair(downloadUrl, fileName))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Opens PDF: caches locally, serves via FileProvider, or opens in browser/Drive fallback.
     */
    suspend fun openPdf(
        context: Context,
        pdfUrl: String,
        fileName: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val isDriveOrWebLink = pdfUrl.contains("drive.google.com") ||
                pdfUrl.contains("docs.google.com") ||
                pdfUrl.contains("dropbox.com") ||
                (!pdfUrl.endsWith(".pdf", ignoreCase = true) && !pdfUrl.contains("firebasestorage.googleapis.com"))

            if (isDriveOrWebLink) {
                withContext(Dispatchers.Main) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(pdfUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                }
                return@withContext Result.success(Unit)
            }

            val safeName = (fileName?.takeIf { it.isNotBlank() } ?: "schedule.pdf")
                .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val cacheKey = "${Math.abs(pdfUrl.hashCode())}_$safeName"

            val schedulesDir = File(context.filesDir, "schedules").apply { mkdirs() }
            val localFile = File(schedulesDir, cacheKey)

            // Permanent Disk Cache: Open immediately if already downloaded
            if (!localFile.exists() || localFile.length() == 0L) {
                val connection = (URL(pdfUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    requestMethod = "GET"
                }
                connection.inputStream.use { input ->
                    FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                try {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        localFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Fallback to external browser / Drive PDF viewer
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(pdfUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to load PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            Result.failure(e)
        }
    }
}
