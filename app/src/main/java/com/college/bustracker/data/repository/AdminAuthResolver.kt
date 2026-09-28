package com.college.bustracker.data.repository

/**
 * Pure business logic and resolution rules for Admin authentication,
 * email resolution, and password recovery.
 */
object AdminAuthResolver {

    const val INTERNAL_DOMAIN = "@collegebus.internal"
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /**
     * Checks if the given input has a valid email address format.
     */
    fun isEmail(input: String): Boolean {
        return EMAIL_REGEX.matches(input.trim())
    }

    /**
     * Checks whether an email belongs to the internal placeholder domain.
     */
    fun isInternalEmail(email: String): Boolean {
        return email.trim().endsWith(INTERNAL_DOMAIN, ignoreCase = true)
    }

    /**
     * Resolves the target Firebase Auth email for a Driver login.
     * Always preserves the existing pattern: if already an email, uses it;
     * otherwise appends `@collegebus.internal`.
     */
    fun resolveDriverEmail(driverInput: String): String {
        val clean = driverInput.trim().lowercase()
        return if (clean.contains("@")) clean else "$clean$INTERNAL_DOMAIN"
    }

    /**
     * Resolves the target Firebase Auth email for an Admin login.
     * 1. If admin enters an email directly, use that email.
     * 2. If admin enters an ID (e.g. "admin26") and Firestore returned an email, use the Firestore email.
     * 3. Otherwise fall back to "${cleanId}@collegebus.internal".
     */
    fun resolveAdminAuthEmail(adminInput: String, firestoreEmail: String?): String {
        val clean = adminInput.trim().lowercase()
        if (clean.contains("@")) {
            return clean
        }
        if (!firestoreEmail.isNullOrBlank()) {
            return firestoreEmail.trim()
        }
        return "$clean$INTERNAL_DOMAIN"
    }

    /**
     * Validates whether a Firestore user record belongs to an active Admin.
     */
    fun validateAdminRecord(role: String?, active: Boolean?): Result<Unit> {
        if (role == null || !role.equals("ADMIN", ignoreCase = true)) {
            return Result.failure(SecurityException("Unauthorized: Account does not have ADMIN privileges."))
        }
        if (active != true) {
            return Result.failure(IllegalStateException("Account is deactivated. Contact Administrator."))
        }
        return Result.success(Unit)
    }

    /**
     * Determines if a resolved email can receive an external password reset email.
     * Must be a valid email and must NOT be an internal dummy email.
     */
    fun isEligibleForPasswordReset(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val clean = email.trim()
        if (isInternalEmail(clean)) return false
        return isEmail(clean)
    }

    /**
     * Masks an email address for safe UI feedback without leaking the full address.
     * e.g. "bkeshava636@gmail.com" -> "b***6@gmail.com"
     */
    fun maskEmail(email: String): String {
        val clean = email.trim()
        val atIndex = clean.indexOf('@')
        if (atIndex <= 1) return clean
        val localPart = clean.substring(0, atIndex)
        val domainPart = clean.substring(atIndex)
        val maskedLocal = if (localPart.length <= 2) {
            "${localPart.first()}*"
        } else {
            "${localPart.first()}***${localPart.last()}"
        }
        return "$maskedLocal$domainPart"
    }
}
