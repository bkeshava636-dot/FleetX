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
        try {
            val fileName = getFileName(context, uri)
            val storageRef = FirebaseStorage.getInstance().reference
                .child("route_schedules")
                .child("${routeId}_${System.currentTimeMillis()}.pdf")

            storageRef.putFile(uri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Result.success(Pair(downloadUrl, fileName))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Opens PDF: caches locally, serves via FileProvider, or opens in browser fallback.
     */
    suspend fun openPdf(
        context: Context,
        pdfUrl: String,
        fileName: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val safeName = (fileName?.takeIf { it.isNotBlank() } ?: "schedule_${pdfUrl.hashCode()}.pdf")
                .replace("[^a-zA-Z0-9._-]".toRegex(), "_")

            val cacheDir = File(context.cacheDir, "schedules").apply { mkdirs() }
            val localFile = File(cacheDir, safeName)

            // Download only if not cached or empty
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
