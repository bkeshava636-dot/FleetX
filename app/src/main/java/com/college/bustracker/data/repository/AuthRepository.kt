package com.college.bustracker.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Interface for server-side Admin authentication and recovery resolution.
 * Allows unit testing without contacting live Cloud Functions.
 */
interface ServerAdminAuthResolver {
    suspend fun resolveAdminEmail(adminId: String): Result<String>
    suspend fun requestAdminPasswordReset(adminId: String): Result<Unit>
}

/**
 * Production implementation calling secure HTTPS Callable Cloud Functions:
 * - 'resolveAdminLogin' for admin email resolution without exposing Firestore profiles.
 * - 'requestAdminPasswordReset' for server-side password reset email dispatch.
 */
class FirebaseFunctionsAdminAuthResolver(
    private val functionsProvider: () -> FirebaseFunctions = { FirebaseFunctions.getInstance() }
) : ServerAdminAuthResolver {
    override suspend fun resolveAdminEmail(adminId: String): Result<String> {
        return try {
            val data = hashMapOf("adminId" to adminId)
            val result = functionsProvider().getHttpsCallable("resolveAdminLogin")
                .call(data)
                .await()
            val resMap = result.data as? Map<*, *>
            val email = resMap?.get("email") as? String
            if (!email.isNullOrBlank()) {
                Result.success(email.trim())
            } else {
                Result.failure(SecurityException("Invalid Staff ID or Password."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun requestAdminPasswordReset(adminId: String): Result<Unit> {
        return try {
            val data = hashMapOf("adminId" to adminId)
            functionsProvider().getHttpsCallable("requestAdminPasswordReset")
                .call(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            // Generic success to prevent account enumeration
            Result.success(Unit)
        }
    }
}

class AuthRepository(
    private val context: Context? = null,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val serverAdminAuthResolver: ServerAdminAuthResolver = FirebaseFunctionsAdminAuthResolver()
) {

    constructor(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        database: FirebaseDatabase
    ) : this(null, auth, firestore, database, FirebaseFunctionsAdminAuthResolver())

    constructor(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        database: FirebaseDatabase,
        serverAdminAuthResolver: ServerAdminAuthResolver
    ) : this(null, auth, firestore, database, serverAdminAuthResolver)

    constructor() : this(null, FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(), FirebaseDatabase.getInstance(), FirebaseFunctionsAdminAuthResolver())

    companion object {
        private const val TAG = "AuthRepository"
        private const val PREFS_NAME = "staff_session_prefs"
        private const val KEY_UID = "session_uid"
        private const val KEY_CUSTOM_ID = "session_custom_id"
        private const val KEY_NAME = "session_name"
        private const val KEY_EMAIL = "session_email"
        private const val KEY_ROLE = "session_role"
        private const val KEY_ASSIGNED_BUS = "session_assigned_bus"
        private const val KEY_ACTIVE = "session_active"
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun getPrefs(): SharedPreferences? {
        val ctx = context ?: try { com.college.bustracker.CollegeBusApp.instance } catch (e: Exception) { null }
        return ctx?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Active session profile
    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    init {
        restoreSessionFromPrefs()
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                if (_currentProfile.value != null) {
                    clearSessionPrefs()
                    _currentProfile.value = null
                }
            } else if (_currentProfile.value == null) {
                restoreSessionFromPrefs()
            }
        }
    }

    private fun restoreSessionFromPrefs() {
        val currentFirebaseUser = auth.currentUser
        val sp = getPrefs()
        if (sp != null) {
            val savedUid = sp.getString(KEY_UID, null)
            if (savedUid != null && (currentFirebaseUser == null || currentFirebaseUser.uid == savedUid)) {
                val customId = sp.getString(KEY_CUSTOM_ID, "") ?: ""
                val name = sp.getString(KEY_NAME, "") ?: ""
                val email = sp.getString(KEY_EMAIL, currentFirebaseUser?.email ?: "") ?: ""
                val roleStr = sp.getString(KEY_ROLE, null)
                val assignedBus = sp.getString(KEY_ASSIGNED_BUS, null)
                val active = sp.getBoolean(KEY_ACTIVE, true)

                if (roleStr != null && active) {
                    val restoredProfile = UserProfile(
                        uid = savedUid,
                        customId = customId,
                        name = name.ifBlank { savedUid },
                        email = email,
                        role = UserRole.fromString(roleStr),
                        assignedBusId = assignedBus,
                        active = active
                    )
                    _currentProfile.value = restoredProfile
                    Log.d(TAG, "Restored staff session for ${restoredProfile.role}: ${restoredProfile.customId} (${restoredProfile.uid})")

                    // Asynchronously verify backend state (active, bus assignment updates, etc.)
                    repositoryScope.launch {
                        verifySessionWithBackend(savedUid)
                    }
                    return
                }
            }
        }
        if (currentFirebaseUser == null && sp != null) {
            clearSessionPrefs()
        }
    }

    private suspend fun verifySessionWithBackend(uid: String) {
        try {
            var roleStr: String? = null
            var isActive: Boolean? = null
            var assignedBusId: String? = null
            var customId: String? = null
            var name: String? = null

            // Check Firestore first
            try {
                val doc = withTimeoutOrNull(4000L) {
                    firestore.collection("users").document(uid).get().await()
                }
                if (doc != null && doc.exists()) {
                    roleStr = doc.getString("role")
                    isActive = doc.getBoolean("active")
                    assignedBusId = doc.getString("assignedBusId")
                    customId = doc.getString("customId")
                    name = doc.getString("name")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore verification error: ${e.message}")
            }

            // Fallback to RTDB
            if (roleStr == null) {
                try {
                    val rtdbSnap = withTimeoutOrNull(4000L) {
                        database.getReference("users").child(uid).get().await()
                    }
                    if (rtdbSnap != null && rtdbSnap.exists()) {
                        roleStr = rtdbSnap.child("role").getValue(String::class.java)
                        isActive = rtdbSnap.child("active").getValue(Boolean::class.java)
                        assignedBusId = rtdbSnap.child("assignedBusId").getValue(String::class.java)
                        customId = rtdbSnap.child("customId").getValue(String::class.java)
                        name = rtdbSnap.child("name").getValue(String::class.java)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "RTDB verification error: ${e.message}")
                }
            }

            // If account was deleted or deactivated
            if (isActive == false) {
                Log.w(TAG, "User $uid deactivated on backend. Logging out.")
                logout()
                return
            }

            if (roleStr != null) {
                val current = _currentProfile.value
                val updatedProfile = UserProfile(
                    uid = uid,
                    customId = customId ?: current?.customId ?: "",
                    name = name ?: current?.name ?: "",
                    email = current?.email ?: "",
                    role = UserRole.fromString(roleStr),
                    assignedBusId = assignedBusId,
                    active = true
                )
                _currentProfile.value = updatedProfile
                saveSessionPrefs(updatedProfile)
                Log.d(TAG, "Backend verified session updated for $uid, assignedBus=$assignedBusId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in verifySessionWithBackend: ${e.message}")
        }
    }

    private fun saveSessionPrefs(profile: UserProfile) {
        getPrefs()?.edit()?.apply {
            putString(KEY_UID, profile.uid)
            putString(KEY_CUSTOM_ID, profile.customId)
            putString(KEY_NAME, profile.name)
            putString(KEY_EMAIL, profile.email)
            putString(KEY_ROLE, profile.role.name)
            putString(KEY_ASSIGNED_BUS, profile.assignedBusId)
            putBoolean(KEY_ACTIVE, profile.active)
            apply()
        }
    }

    private fun clearSessionPrefs() {
        getPrefs()?.edit()?.clear()?.apply()
    }

    fun getUserProfileFlow(): Flow<UserProfile?> = callbackFlow {
        trySend(_currentProfile.value)

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                trySend(null)
            } else {
                firestore.collection("users").document(user.uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val roleStr = doc.getString("role") ?: "STUDENT"
                            val profile = UserProfile(
                                uid = user.uid,
                                customId = doc.getString("customId") ?: "",
                                name = doc.getString("name") ?: user.displayName ?: "User",
                                email = user.email ?: "",
                                role = UserRole.fromString(roleStr),
                                assignedBusId = doc.getString("assignedBusId"),
                                preferredBusId = doc.getString("preferredBusId"),
                                preferredStopId = doc.getString("preferredStopId"),
                                active = doc.getBoolean("active") ?: true
                            )
                            _currentProfile.value = profile
                            saveSessionPrefs(profile)
                            trySend(profile)
                        } else {
                            trySend(_currentProfile.value)
                        }
                    }
                    .addOnFailureListener {
                        trySend(_currentProfile.value)
                    }
            }
        }

        auth.addAuthStateListener(authListener)
        awaitClose { auth.removeAuthStateListener(authListener) }
    }

    /**
     * Authenticates Admin or Driver using their ID (or Email) and password.
     * - DRIVER: Maps ID to `${id}@collegebus.internal` (or uses direct email if entered).
     * - ADMIN: Server-side Cloud Function resolves `customId` to the real Firebase Auth email
     *   (e.g. admin26 -> bkeshava636@gmail.com) without unauthenticated Firestore exposure.
     */
    suspend fun loginWithId(
        userIdInput: String,
        passwordInput: String,
        expectedRole: UserRole
    ): Result<UserProfile> {
        val cleanId = userIdInput.trim().lowercase()
        val cleanPassword = passwordInput.trim()

        if (cleanId.isBlank() || cleanPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("ID and Password cannot be blank"))
        }

        // Resolve auth email
        val authEmail: String
        if (expectedRole == UserRole.ADMIN) {
            if (cleanId.contains("@")) {
                authEmail = cleanId
            } else {
                val serverResult = serverAdminAuthResolver.resolveAdminEmail(cleanId)
                if (serverResult.isSuccess) {
                    authEmail = serverResult.getOrThrow()
                } else {
                    val ex = serverResult.exceptionOrNull()
                    val msg = ex?.message ?: ""
                    if (msg.contains("deactivated", ignoreCase = true)) {
                        return Result.failure(IllegalStateException("Account is deactivated. Contact Administrator."))
                    }
                    if (msg.contains("ADMIN privileges", ignoreCase = true)) {
                        return Result.failure(SecurityException("Unauthorized: Account does not have ADMIN privileges."))
                    }
                    // Generic fallback ensures Firebase Auth produces "Invalid Staff ID or Password."
                    authEmail = "$cleanId@collegebus.internal"
                }
            }
        } else {
            authEmail = AdminAuthResolver.resolveDriverEmail(cleanId)
        }

        // 1. Authenticate with Firebase Authentication
        val authResult = try {
            auth.signInWithEmailAndPassword(authEmail, cleanPassword).await()
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            return if (msg.contains("password", ignoreCase = true) || msg.contains("credential", ignoreCase = true) || msg.contains("user", ignoreCase = true)) {
                Result.failure(SecurityException("Invalid Staff ID or Password."))
            } else {
                Result.failure(SecurityException("Authentication failed: ${e.message ?: "Network error"}"))
            }
        }

        val authUid = authResult.user?.uid
            ?: return Result.failure(SecurityException("Authentication failed: User record not found."))

        // 2. Fetch and verify staff user profile from Firestore or Realtime Database
        return try {
            var roleStr: String? = null
            var isActive: Boolean? = null
            var assignedBusId: String? = null
            var customId: String? = null
            var name: String? = null
            var docEmail: String? = null

            // Try Cloud Firestore first
            try {
                val doc = withTimeoutOrNull(2500L) {
                    firestore.collection("users").document(authUid).get().await()
                }
                if (doc != null && doc.exists()) {
                    roleStr = doc.getString("role")
                    isActive = doc.getBoolean("active")
                    assignedBusId = doc.getString("assignedBusId")
                    customId = doc.getString("customId")
                    name = doc.getString("name")
                    docEmail = doc.getString("email")
                }
            } catch (ignored: Exception) {}

            // If Firestore doc missing or offline, check Realtime Database /users/{authUid}
            if (roleStr == null) {
                try {
                    val rtdbSnap = withTimeoutOrNull(2500L) {
                        database.getReference("users").child(authUid).get().await()
                    }
                    if (rtdbSnap != null && rtdbSnap.exists()) {
                        roleStr = rtdbSnap.child("role").getValue(String::class.java)
                        isActive = rtdbSnap.child("active").getValue(Boolean::class.java)
                        assignedBusId = rtdbSnap.child("assignedBusId").getValue(String::class.java)
                        customId = rtdbSnap.child("customId").getValue(String::class.java)
                        name = rtdbSnap.child("name").getValue(String::class.java)
                        docEmail = rtdbSnap.child("email").getValue(String::class.java)
                    }
                } catch (ignored: Exception) {}
            }

            if (roleStr == null) {
                auth.signOut()
                return Result.failure(SecurityException("Staff authorization profile not found in system."))
            }

            if (isActive != true) {
                auth.signOut()
                return Result.failure(IllegalStateException("Account is deactivated. Contact Administrator."))
            }

            val actualRole = UserRole.fromString(roleStr)

            if (actualRole != expectedRole && actualRole != UserRole.ADMIN) {
                auth.signOut()
                return Result.failure(SecurityException("Unauthorized: Account does not have $expectedRole privileges."))
            }

            val finalEmail = docEmail ?: authResult.user?.email ?: authEmail
            val profile = UserProfile(
                uid = authUid,
                customId = customId ?: cleanId,
                name = name ?: cleanId.replaceFirstChar { it.uppercase() },
                email = finalEmail,
                role = actualRole,
                assignedBusId = assignedBusId,
                active = true
            )
            _currentProfile.value = profile
            saveSessionPrefs(profile)
            Result.success(profile)
        } catch (e: Exception) {
            auth.signOut()
            Result.failure(SecurityException("Failed to verify user profile: ${e.message}"))
        }
    }

    /**
     * Sends a password reset email for an Administrator account.
     * Invokes secure server-side Cloud Function 'requestAdminPasswordReset'.
     * Always returns generic confirmation to prevent user enumeration.
     */
    suspend fun sendAdminPasswordReset(adminIdOrEmailInput: String): Result<Unit> {
        val cleanInput = adminIdOrEmailInput.trim().lowercase()
        if (cleanInput.isBlank()) {
            return Result.failure(IllegalArgumentException("Admin ID or Email cannot be blank"))
        }

        if (cleanInput.contains("@")) {
            if (!AdminAuthResolver.isEmail(cleanInput)) {
                return Result.failure(IllegalArgumentException("Please enter a valid email address."))
            }
            if (AdminAuthResolver.isInternalEmail(cleanInput)) {
                return Result.failure(IllegalStateException("This account uses an internal ID without an external email address configured. Please contact the system administrator."))
            }
        }

        return serverAdminAuthResolver.requestAdminPasswordReset(cleanInput)
    }

    fun getActiveProfile(): UserProfile? {
        return _currentProfile.value
    }

    fun logout() {
        val ctx = context ?: try { com.college.bustracker.CollegeBusApp.instance } catch (e: Exception) { null }
        if (ctx != null) {
            try {
                com.college.bustracker.service.AutoTrackingScheduler.onDriverLogout(ctx)
            } catch (ignored: Exception) {}
            try {
                com.college.bustracker.service.TrackingServiceHelper.stopTracking(ctx, "DRIVER_LOGGED_OUT", "AuthRepository.logout")
            } catch (ignored: Exception) {}
        }
        clearSessionPrefs()
        try {
            auth.signOut()
        } catch (ignored: Exception) {}
        _currentProfile.value = null
    }
}

