package com.college.bustracker.update.service

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.college.bustracker.update.model.VerificationResult
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Arrays

object ApkVerifier {

    /**
     * Computes the SHA-256 checksum of a file in lowercase hex string format.
     */
    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    /**
     * Verifies that the downloaded APK matches the expected SHA-256 hash.
     */
    fun verifySha256(file: File, expectedHash: String?): VerificationResult {
        if (expectedHash.isNullOrBlank()) {
            return VerificationResult.Success
        }
        val actualHash = try {
            calculateSha256(file)
        } catch (e: Exception) {
            return VerificationResult.Failure("Failed to calculate SHA-256: ${e.message}")
        }

        return if (actualHash.equals(expectedHash.trim(), ignoreCase = true)) {
            VerificationResult.Success
        } else {
            VerificationResult.Failure(
                "SHA-256 mismatch. Expected: ${expectedHash.trim().lowercase()}, Found: $actualHash"
            )
        }
    }

    /**
     * Verifies that the APK's package name matches the installed application.
     */
    fun verifyPackageName(context: Context, apkFile: File): VerificationResult {
        val pm = context.packageManager
        val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: return VerificationResult.Failure("Unable to parse downloaded APK file structure.")

        val expectedPackage = context.packageName
        val actualPackage = archiveInfo.packageName

        return if (expectedPackage == actualPackage) {
            VerificationResult.Success
        } else {
            VerificationResult.Failure(
                "Package name mismatch: APK contains '$actualPackage', expected '$expectedPackage'"
            )
        }
    }

    /**
     * Verifies that the APK signing certificate matches the currently installed application.
     * Prevents malicious or mismatched APK installations.
     */
    fun verifySigningCertificate(context: Context, apkFile: File): VerificationResult {
        val pm = context.packageManager
        try {
            // 1. Retrieve installed signatures
            val installedSignatures = getInstalledSignatures(context, pm)
            if (installedSignatures.isEmpty()) {
                return VerificationResult.Failure("Could not extract signing certificates from installed app.")
            }

            // 2. Retrieve downloaded APK signatures
            val apkSignatures = getApkSignatures(pm, apkFile)
            if (apkSignatures.isEmpty()) {
                return VerificationResult.Failure("Could not extract signing certificates from downloaded APK.")
            }

            // 3. Compare signature digests
            val matchFound = signaturesMatch(installedSignatures, apkSignatures)
            return if (matchFound) {
                VerificationResult.Success
            } else {
                VerificationResult.Failure(
                    "Signing certificate mismatch! The update APK is signed with a different key than the installed app."
                )
            }
        } catch (e: Exception) {
            return VerificationResult.Failure("Certificate verification failed with error: ${e.message}")
        }
    }

    /**
     * Complete verification pipeline: SHA-256 hash -> Package Name -> Signing Certificate.
     */
    fun verifyApk(context: Context, apkFile: File, expectedSha256: String?): VerificationResult {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            return VerificationResult.Failure("APK file does not exist or is empty.")
        }

        val shaResult = verifySha256(apkFile, expectedSha256)
        if (shaResult is VerificationResult.Failure) {
            return shaResult
        }

        val packageResult = verifyPackageName(context, apkFile)
        if (packageResult is VerificationResult.Failure) {
            return packageResult
        }

        val certResult = verifySigningCertificate(context, apkFile)
        if (certResult is VerificationResult.Failure) {
            return certResult
        }

        return VerificationResult.Success
    }

    private fun getInstalledSignatures(context: Context, pm: PackageManager): List<ByteArray> {
        val list = mutableListOf<ByteArray>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNING_CERTIFICATES
            }
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, flags as PackageManager.PackageInfoFlags)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flags as Int)
            }
            val signers = info.signingInfo?.apkContentsSigners
            if (signers != null) {
                for (sig in signers) {
                    list.add(sig.toByteArray())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val signatures = info.signatures
            if (signatures != null) {
                for (sig in signatures) {
                    list.add(sig.toByteArray())
                }
            }
        }
        return list
    }

    private fun getApkSignatures(pm: PackageManager, apkFile: File): List<ByteArray> {
        val list = mutableListOf<ByteArray>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info?.signingInfo?.apkContentsSigners
            if (signers != null && signers.isNotEmpty()) {
                for (sig in signers) {
                    list.add(sig.toByteArray())
                }
                return list
            }
        }

        // Fallback for pre-P or archive parsing fallback
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
        val signatures = info?.signatures
        if (signatures != null) {
            for (sig in signatures) {
                list.add(sig.toByteArray())
            }
        }
        return list
    }

    /**
     * Compares signatures by byte content or SHA-256 fingerprint.
     */
    fun signaturesMatch(installed: List<ByteArray>, incoming: List<ByteArray>): Boolean {
        for (inst in installed) {
            val instHash = sha256Bytes(inst)
            for (inc in incoming) {
                val incHash = sha256Bytes(inc)
                if (Arrays.equals(instHash, incHash)) {
                    return true
                }
            }
        }
        return false
    }

    private fun sha256Bytes(bytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes)
    }
}
