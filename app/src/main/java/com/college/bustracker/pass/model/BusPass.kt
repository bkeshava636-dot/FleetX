package com.college.bustracker.pass.model

import java.io.File

/**
 * Encapsulates the locally saved student bus pass files and metadata.
 * Physical pass issued by college; this is the student's high-quality digital copy.
 */
data class BusPass(
    val frontImagePath: String,
    val backImagePath: String,
    val frontFileSize: Long,
    val backFileSize: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
    val receiptImagePath: String? = null,
    val receiptFileSize: Long = 0L
) {
    fun frontFile(): File = File(frontImagePath)
    fun backFile(): File = File(backImagePath)
    fun receiptFile(): File? = receiptImagePath?.let { File(it) }

    fun hasReceipt(): Boolean = receiptFile()?.let { it.exists() && it.length() > 0 } ?: false

    fun isComplete(): Boolean {
        val f = frontFile()
        val b = backFile()
        return f.exists() && f.length() > 0 && b.exists() && b.length() > 0
    }
}
