package com.college.bustracker.ui.admin

/**
 * Pure validation logic for Driver Profile management:
 * - Driver Name validation
 * - Driver ID validation & staff email mapping
 * - Phone Number validation & normalization (Indian standard)
 */
object DriverProfileValidator {

    /**
     * Validates driver display name.
     */
    fun validateName(name: String): Result<String> {
        val clean = name.trim()
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Driver name cannot be blank"))
        }
        if (clean.length < 2) {
            return Result.failure(IllegalArgumentException("Driver name must be at least 2 characters"))
        }
        return Result.success(clean)
    }

    /**
     * Validates Driver ID (staff login identifier).
     * Enforces lowercase, alphanumeric/underscore/hyphen, no spaces, not 'admin'.
     */
    fun validateDriverId(customId: String): Result<String> {
        val clean = customId.trim().lowercase()
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Driver ID cannot be blank"))
        }
        if (clean.contains("admin")) {
            return Result.failure(IllegalArgumentException("Driver ID cannot contain 'admin'"))
        }
        if (!clean.matches(Regex("^[a-z0-9_\\-]+$"))) {
            return Result.failure(IllegalArgumentException("Driver ID may only contain lowercase letters, numbers, hyphens, and underscores"))
        }
        if (clean.length < 3) {
            return Result.failure(IllegalArgumentException("Driver ID must be at least 3 characters"))
        }
        return Result.success(clean)
    }

    /**
     * Validates and normalizes phone number.
     * Accepts:
     * - Blank/null (optional, returns null)
     * - 10-digit Indian mobile numbers (starts with 6, 7, 8, 9)
     * - Numbers with "+91" prefix or leading "0"
     * Normalizes to standard 10-digit format for storage.
     */
    fun validateAndNormalizePhoneNumber(phone: String?): Result<String?> {
        if (phone.isNullOrBlank()) {
            return Result.success(null)
        }
        val trimmed = phone.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }

        val normalized = when {
            digitsOnly.length == 10 -> digitsOnly
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> digitsOnly.substring(2)
            digitsOnly.length == 11 && digitsOnly.startsWith("0") -> digitsOnly.substring(1)
            else -> digitsOnly
        }

        if (normalized.length != 10) {
            return Result.failure(IllegalArgumentException("Phone number must be a valid 10-digit mobile number"))
        }

        if (normalized[0] !in '6'..'9') {
            return Result.failure(IllegalArgumentException("Indian mobile numbers must begin with 6, 7, 8, or 9"))
        }

        return Result.success(normalized)
    }

    /**
     * Maps internal staff ID to Firebase Authentication internal email.
     */
    fun mapStaffEmail(customId: String): String {
        return "${customId.trim().lowercase()}@collegebus.internal"
    }
}
