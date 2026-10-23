package com.college.bustracker.data.repository

import android.content.Context
import android.util.Log
import com.college.bustracker.data.model.Bus
import com.college.bustracker.data.model.UserProfile
import com.college.bustracker.data.model.UserRole
import com.college.bustracker.ui.admin.DriverProfileValidator
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Interface for server-side Firebase Authentication account deletion.
 * Allows unit testing without contacting live Cloud Functions.
 */
interface ServerAuthDeleter {
    suspend fun deleteUserByUid(driverUid: String): Result<Unit>
}

/**
 * Production implementation calling the HTTPS Callable Cloud Function 'deleteDriverAccount'.
 * Deletes user by UID server-side using Firebase Admin SDK.
 */
class FirebaseFunctionsAuthDeleter(
    private val functionsProvider: () -> FirebaseFunctions = { FirebaseFunctions.getInstance("asia-south1") }
) : ServerAuthDeleter {
    override suspend fun deleteUserByUid(driverUid: String): Result<Unit> {
        return try {
            val data = hashMapOf("driverUid" to driverUid)
            functionsProvider().getHttpsCallable("deleteDriverAccount")
                .call(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Interface for server-side Firebase Authentication email/ID update.
 * Allows unit testing without contacting live Cloud Functions.
 */
interface ServerAuthUpdater {
    suspend fun updateDriverEmail(driverUid: String, newEmail: String): Result<Unit>
}

/**
 * Production implementation calling the HTTPS Callable Cloud Function 'updateDriverEmail'.
 * Updates user email server-side using Firebase Admin SDK.
 */
class FirebaseFunctionsAuthUpdater(
    private val functionsProvider: () -> FirebaseFunctions = { FirebaseFunctions.getInstance("asia-south1") }
) : ServerAuthUpdater {
    override suspend fun updateDriverEmail(driverUid: String, newEmail: String): Result<Unit> {
        return try {
            val data = hashMapOf("driverUid" to driverUid, "newEmail" to newEmail)
            functionsProvider().getHttpsCallable("updateDriverEmail")
                .call(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Interface for server-side Driver password reset.
 * Allows unit testing without contacting live Cloud Functions.
 */
interface ServerDriverPasswordResetter {
    suspend fun resetDriverPassword(driverUid: String, newPassword: String): Result<Unit>
}

/**
 * Production implementation calling the HTTPS Callable Cloud Function 'resetDriverPassword'.
 * Resets driver password server-side using Firebase Admin SDK.
 */
class FirebaseFunctionsDriverPasswordResetter(
    private val functionsProvider: () -> FirebaseFunctions = { FirebaseFunctions.getInstance("asia-south1") }
) : ServerDriverPasswordResetter {
    override suspend fun resetDriverPassword(driverUid: String, newPassword: String): Result<Unit> {
        return try {
            val data = hashMapOf("driverUid" to driverUid, "newPassword" to newPassword)
            functionsProvider().getHttpsCallable("resetDriverPassword")
                .call(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class BusRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val serverAuthDeleter: ServerAuthDeleter = FirebaseFunctionsAuthDeleter(),
    private val serverAuthUpdater: ServerAuthUpdater = FirebaseFunctionsAuthUpdater(),
    private val serverDriverPasswordResetter: ServerDriverPasswordResetter = FirebaseFunctionsDriverPasswordResetter()
) {

    private val _localBuses = MutableStateFlow<List<Bus>>(emptyList())

    companion object {
        private const val TAG = "BusRepository"
        private const val PREFS_CACHE = "firestore_cache_metadata"
        private const val KEY_LAST_BUS_SYNC_MS = "last_bus_sync_ms"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes stale-while-revalidate
    }

    /**
     * Authoritatively synchronizes a driver's authorization record in Firebase Realtime Database (/users/{driverUid}).
     * Guarantees all required fields (role='DRIVER', active, name, assignedBusId) are written to satisfy RTDB rules.
     */
    suspend fun syncDriverAuthorizationToRtdb(
        driverUid: String,
        role: String = "DRIVER",
        active: Boolean = true,
        name: String = "",
        assignedBusId: String? = null
    ) {
        if (driverUid.isBlank()) return
        try {
            val rtdbMap = hashMapOf<String, Any?>(
                "role" to role,
                "active" to active,
                "name" to name,
                "assignedBusId" to assignedBusId
            )
            database.getReference("users").child(driverUid).setValue(rtdbMap).await()
            Log.d(TAG, "RTDB_AUTH_SYNCED: driver=$driverUid, bus=$assignedBusId, active=$active")
        } catch (e: Exception) {
            Log.w(TAG, "RTDB_AUTH_SYNC_FAILED: driver=$driverUid, error=${e.message}")
        }
    }

    fun getCachedBus(busId: String): Bus? = _localBuses.value.find { it.busId == busId }

    fun getBusesFlow(forceRefresh: Boolean = false): Flow<List<Bus>> = callbackFlow {
        // 1. Immediately emit in-memory cached buses if available
        if (_localBuses.value.isNotEmpty()) {
            trySend(_localBuses.value)
        }

        val context = try { com.college.bustracker.CollegeBusApp.instance } catch (_: Exception) { null }
        val prefs = context?.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val lastSyncMs = prefs?.getLong(KEY_LAST_BUS_SYNC_MS, 0L) ?: 0L
        val now = System.currentTimeMillis()
        val isCacheValid = !forceRefresh && (now - lastSyncMs < CACHE_TTL_MS)

        var cacheLoaded = false
        if (isCacheValid) {
            try {
                val cacheSnap = firestore.collection("buses").get(Source.CACHE).await()
                if (cacheSnap != null && !cacheSnap.isEmpty) {
                    val list = cacheSnap.documents.mapNotNull { doc ->
                        doc.toObject(Bus::class.java)?.let { bus ->
                            bus.copy(busId = bus.busId.ifBlank { doc.id })
                        }
                    }.sortedWith(compareBy({ it.numericBusNumber }, { it.busNumber }))
                    if (list.isNotEmpty()) {
                        _localBuses.value = list
                        trySend(list)
                        cacheLoaded = true
                        Log.d(TAG, "FIRESTORE_CACHE_HIT: Loaded ${list.size} buses from Source.CACHE (0 server reads)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "FIRESTORE_CACHE_MISS: Source.CACHE read failed (${e.message}), falling back to SERVER")
            }
        }

        val shouldFetchServer = forceRefresh || !cacheLoaded || (now - lastSyncMs >= CACHE_TTL_MS)
        if (shouldFetchServer) {
            try {
                val serverSnap = firestore.collection("buses").get(Source.SERVER).await()
                val list = serverSnap.documents.mapNotNull { doc ->
                    doc.toObject(Bus::class.java)?.let { bus ->
                        bus.copy(busId = bus.busId.ifBlank { doc.id })
                    }
                }.sortedWith(compareBy({ it.numericBusNumber }, { it.busNumber }))
                _localBuses.value = list
                prefs?.edit()?.putLong(KEY_LAST_BUS_SYNC_MS, now)?.apply()
                trySend(list)
                Log.d(TAG, "FIRESTORE_SERVER_SYNC: Synced ${list.size} buses from Source.SERVER")
            } catch (e: Exception) {
                Log.e(TAG, "FIRESTORE_SERVER_ERROR: Failed to fetch buses from SERVER: ${e.message}")
                if (_localBuses.value.isNotEmpty()) {
                    trySend(_localBuses.value)
                }
            }
        }

        // Attach snapshot listener only on explicit forceRefresh (e.g. Admin editing fleet)
        val registration = if (forceRefresh) {
            firestore.collection("buses").addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Bus::class.java)?.let { bus ->
                            bus.copy(busId = bus.busId.ifBlank { doc.id })
                        }
                    }.sortedWith(compareBy({ it.numericBusNumber }, { it.busNumber }))
                    _localBuses.value = list
                    trySend(list)
                }
            }
        } else null

        awaitClose { registration?.remove() }
    }

    /**
     * Real-time stream of all DRIVER accounts from Firestore /users (both active and disabled).
     */
    fun getAllDriversFlow(): Flow<List<UserProfile>> = callbackFlow {
        val registration = firestore.collection("users")
            .whereEqualTo("role", "DRIVER")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val drivers = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    val customId = doc.getString("customId") ?: ""
                    val name = doc.getString("name") ?: customId.ifBlank { "Driver" }
                    val email = doc.getString("email") ?: ""
                    val assignedBusId = doc.getString("assignedBusId")
                    val phoneNumber = doc.getString("phoneNumber")
                    val active = doc.getBoolean("active") ?: true
                    UserProfile(
                        uid = uid,
                        customId = customId,
                        name = name,
                        email = email,
                        role = UserRole.DRIVER,
                        assignedBusId = assignedBusId,
                        phoneNumber = phoneNumber,
                        active = active
                    )
                }
                trySend(drivers)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Real-time stream of active DRIVER accounts from Firestore /users.
     * Strictly filters for role == "DRIVER" and active == true.
     */
    fun getDriversFlow(): Flow<List<UserProfile>> = callbackFlow {
        val registration = firestore.collection("users")
            .whereEqualTo("role", "DRIVER")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val drivers = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    val customId = doc.getString("customId") ?: ""
                    val name = doc.getString("name") ?: customId.ifBlank { "Driver" }
                    val email = doc.getString("email") ?: ""
                    val assignedBusId = doc.getString("assignedBusId")
                    val phoneNumber = doc.getString("phoneNumber")
                    val active = doc.getBoolean("active") ?: true
                    UserProfile(
                        uid = uid,
                        customId = customId,
                        name = name,
                        email = email,
                        role = UserRole.DRIVER,
                        assignedBusId = assignedBusId,
                        phoneNumber = phoneNumber,
                        active = active
                    )
                }
                trySend(drivers)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Creates a new DRIVER account:
     * 1. Validates unique Driver ID, inputs, and normalized phone number.
     * 2. Creates Firebase Auth user using an isolated secondary FirebaseApp so current Admin session stays intact.
     * 3. Creates Firestore /users/{uid} profile with phoneNumber.
     * 4. Creates Realtime Database /users/{uid} authorization record.
     * 5. If assignedBusId is specified, assigns driver to the bus.
     */
    suspend fun createDriverAccount(
        context: Context,
        name: String,
        customId: String,
        password: String,
        active: Boolean = true,
        phoneNumber: String? = null,
        assignedBusId: String? = null
    ): Result<UserProfile> {
        val nameRes = DriverProfileValidator.validateName(name)
        if (nameRes.isFailure) return Result.failure(nameRes.exceptionOrNull()!!)
        val cleanName = nameRes.getOrThrow()

        val idRes = DriverProfileValidator.validateDriverId(customId)
        if (idRes.isFailure) return Result.failure(idRes.exceptionOrNull()!!)
        val cleanId = idRes.getOrThrow()

        val phoneRes = DriverProfileValidator.validateAndNormalizePhoneNumber(phoneNumber)
        if (phoneRes.isFailure) return Result.failure(phoneRes.exceptionOrNull()!!)
        val cleanPhone = phoneRes.getOrThrow()

        val cleanPassword = password.trim()
        if (cleanPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters long"))
        }

        // Check uniqueness in Firestore
        try {
            val existing = firestore.collection("users")
                .whereEqualTo("customId", cleanId)
                .get()
                .await()
            if (!existing.isEmpty) {
                return Result.failure(IllegalArgumentException("Driver ID '$cleanId' already exists"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val internalEmail = DriverProfileValidator.mapStaffEmail(cleanId)
        val secondaryAppName = "DriverCreation-${System.currentTimeMillis()}"
        var secondaryApp: FirebaseApp? = null
        try {
            secondaryApp = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, secondaryAppName)
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

            val authResult = secondaryAuth.createUserWithEmailAndPassword(internalEmail, cleanPassword).await()

            val newUid = authResult.user?.uid
                ?: return Result.failure(IllegalStateException("Failed to obtain UID for created driver"))

            try {
                secondaryAuth.signOut()
            } catch (ignored: Exception) {}

            // Create Firestore user profile (No plaintext passwords stored)
            val profileMap = hashMapOf<String, Any?>(
                "uid" to newUid,
                "customId" to cleanId,
                "name" to cleanName,
                "email" to internalEmail,
                "role" to "DRIVER",
                "active" to active,
                "assignedBusId" to assignedBusId,
                "phoneNumber" to cleanPhone
            )
            firestore.collection("users").document(newUid).set(profileMap).await()

            // Synchronize RTDB authorization record
            syncDriverAuthorizationToRtdb(
                driverUid = newUid,
                role = "DRIVER",
                active = active,
                name = cleanName,
                assignedBusId = assignedBusId
            )

            // If bus assignment was requested, update bus document
            if (!assignedBusId.isNullOrBlank()) {
                assignDriver(assignedBusId, newUid, cleanName)
            }

            val createdProfile = UserProfile(
                uid = newUid,
                customId = cleanId,
                name = cleanName,
                email = internalEmail,
                role = UserRole.DRIVER,
                assignedBusId = assignedBusId,
                phoneNumber = cleanPhone,
                active = active
            )
            return Result.success(createdProfile)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            try {
                secondaryApp?.delete()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Updates an existing driver profile:
     * - Name, Phone Number, Active status, Assigned Bus, and Driver ID.
     * - Preserves Firebase Auth UID.
     * - Synchronizes Firestore, RTDB, and bus fleet assignments.
     * - If Driver ID changed, updates Firebase Auth email via ServerAuthUpdater and ensures uniqueness.
     */
    suspend fun updateDriverProfile(
        originalDriver: UserProfile,
        newName: String,
        newCustomId: String,
        newPhoneNumber: String?,
        newActive: Boolean,
        newAssignedBusId: String?
    ): Result<UserProfile> {
        val nameRes = DriverProfileValidator.validateName(newName)
        if (nameRes.isFailure) return Result.failure(nameRes.exceptionOrNull()!!)
        val cleanName = nameRes.getOrThrow()

        val idRes = DriverProfileValidator.validateDriverId(newCustomId)
        if (idRes.isFailure) return Result.failure(idRes.exceptionOrNull()!!)
        val cleanCustomId = idRes.getOrThrow()

        val phoneRes = DriverProfileValidator.validateAndNormalizePhoneNumber(newPhoneNumber)
        if (phoneRes.isFailure) return Result.failure(phoneRes.exceptionOrNull()!!)
        val cleanPhone = phoneRes.getOrThrow()

        val driverUid = originalDriver.uid
        if (driverUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Driver UID is required for update"))
        }

        // Prevent deactivating if driver remains assigned to a bus
        if (!newActive && !newAssignedBusId.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Driver '$cleanName' cannot be deactivated while assigned to bus '$newAssignedBusId'. Please unassign the bus first."))
        }

        val idChanged = !cleanCustomId.equals(originalDriver.customId, ignoreCase = true)
        val newEmail = DriverProfileValidator.mapStaffEmail(cleanCustomId)

        // 1. If Driver ID changed, verify uniqueness across other accounts and update Firebase Auth email
        if (idChanged) {
            try {
                val existing = firestore.collection("users")
                    .whereEqualTo("customId", cleanCustomId)
                    .get()
                    .await()
                val duplicate = existing.documents.any { it.id != driverUid }
                if (duplicate) {
                    return Result.failure(IllegalArgumentException("Driver ID '$cleanCustomId' already exists"))
                }
            } catch (e: Exception) {
                return Result.failure(e)
            }

            // Update Firebase Auth email server-side via ServerAuthUpdater
            val authUpdateRes = serverAuthUpdater.updateDriverEmail(driverUid, newEmail)
            if (authUpdateRes.isFailure) {
                return Result.failure(authUpdateRes.exceptionOrNull() ?: Exception("Failed to update driver login email"))
            }
        }

        // 2. Update Firestore user profile
        try {
            val updates = hashMapOf<String, Any?>(
                "name" to cleanName,
                "customId" to cleanCustomId,
                "email" to newEmail,
                "phoneNumber" to cleanPhone,
                "active" to newActive,
                "assignedBusId" to newAssignedBusId
            )
            firestore.collection("users").document(driverUid).update(updates).await()

            // 3. Synchronize RTDB authorization record
            syncDriverAuthorizationToRtdb(
                driverUid = driverUid,
                role = "DRIVER",
                active = newActive,
                name = cleanName,
                assignedBusId = newAssignedBusId
            )

            // 4. Handle bus assignment changes
            val oldBusId = originalDriver.assignedBusId
            val busAssignmentChanged = oldBusId != newAssignedBusId

            if (busAssignmentChanged) {
                // If previously assigned to a bus, unassign from old bus
                if (!oldBusId.isNullOrBlank()) {
                    unassignDriver(oldBusId)
                }
                // If assigned to a new bus, assign to new bus
                if (!newAssignedBusId.isNullOrBlank()) {
                    assignDriver(newAssignedBusId, driverUid, cleanName)
                }
            } else if (!newAssignedBusId.isNullOrBlank() && cleanName != originalDriver.name) {
                // If bus stayed the same but driver name was updated, update driverName in bus document
                firestore.collection("buses").document(newAssignedBusId).update("driverName", cleanName).await()
            }

            val updatedProfile = originalDriver.copy(
                name = cleanName,
                customId = cleanCustomId,
                email = newEmail,
                phoneNumber = cleanPhone,
                active = newActive,
                assignedBusId = newAssignedBusId
            )
            return Result.success(updatedProfile)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Activates or disables a driver account.
     * Prevents deactivating a driver if currently assigned to a bus.
     */
    suspend fun toggleDriverActive(driver: UserProfile, isActive: Boolean): Result<Unit> {
        if (!isActive && !driver.assignedBusId.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Driver '${driver.name}' is currently assigned to bus '${driver.assignedBusId}'. Please unassign the driver from the bus before deactivating."))
        }

        return try {
            firestore.collection("users").document(driver.uid).update("active", isActive).await()
            syncDriverAuthorizationToRtdb(
                driverUid = driver.uid,
                role = "DRIVER",
                active = isActive,
                name = driver.name,
                assignedBusId = driver.assignedBusId
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addOrUpdateBus(bus: Bus) {
        val current = _localBuses.value.toMutableList()
        val index = current.indexOfFirst { it.busId == bus.busId }
        if (index >= 0) {
            current[index] = bus
        } else {
            current.add(bus)
        }
        _localBuses.value = current

        try {
            kotlinx.coroutines.withTimeoutOrNull(1500L) {
                firestore.collection("buses").document(bus.busId).set(bus).await()
            }
        } catch (ignored: Exception) {}
    }

    suspend fun toggleBusActive(busId: String, isActive: Boolean) {
        val current = _localBuses.value.map {
            if (it.busId == busId) it.copy(active = isActive) else it
        }
        _localBuses.value = current
        try {
            kotlinx.coroutines.withTimeoutOrNull(1500L) {
                firestore.collection("buses").document(busId).update("active", isActive).await()
            }
        } catch (ignored: Exception) {}
    }

    suspend fun updateBusRoute(busId: String, newRouteId: String): Result<Unit> {
        val current = _localBuses.value.map {
            if (it.busId == busId) it.copy(routeId = newRouteId) else it
        }
        _localBuses.value = current
        return try {
            firestore.collection("buses").document(busId).update("routeId", newRouteId).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Assigns an active DRIVER account to a bus.
     * Updates:
     * 1. Firestore /buses/{busId} (driverId & driverName)
     * 2. Firestore /users/{driverId}.assignedBusId
     * 3. Realtime Database /users/{driverId}.assignedBusId
     * Also clears any previous driver assignment for this bus.
     */
    suspend fun assignDriver(busId: String, driverId: String, driverName: String) {
        // Find if this bus already had a different driver assigned
        val previousDriverId = _localBuses.value.find { it.busId == busId }?.driverId

        val current = _localBuses.value.map {
            if (it.busId == busId) it.copy(driverId = driverId, driverName = driverName) else it
        }
        _localBuses.value = current

        try {
            kotlinx.coroutines.withTimeoutOrNull(3000L) {
                // If bus had a previous different driver, remove their assignment
                if (!previousDriverId.isNullOrBlank() && previousDriverId != driverId) {
                    try {
                        firestore.collection("users").document(previousDriverId)
                            .update("assignedBusId", null).await()
                        val prevDriverDoc = firestore.collection("users").document(previousDriverId).get().await()
                        syncDriverAuthorizationToRtdb(
                            driverUid = previousDriverId,
                            role = prevDriverDoc.getString("role") ?: "DRIVER",
                            active = prevDriverDoc.getBoolean("active") ?: true,
                            name = prevDriverDoc.getString("name") ?: "",
                            assignedBusId = null
                        )
                    } catch (ignored: Exception) {}
                }

                // 1. Update bus fleet record in Firestore
                firestore.collection("buses").document(busId).update(
                    mapOf("driverId" to driverId, "driverName" to driverName)
                ).await()

                // 2. Synchronize driver assignedBusId in Firestore users collection
                firestore.collection("users").document(driverId).update("assignedBusId", busId).await()

                // 3. Synchronize driver authorization in Realtime Database users node for RTDB security rules
                val currentDriverDoc = firestore.collection("users").document(driverId).get().await()
                syncDriverAuthorizationToRtdb(
                    driverUid = driverId,
                    role = currentDriverDoc.getString("role") ?: "DRIVER",
                    active = currentDriverDoc.getBoolean("active") ?: true,
                    name = driverName.ifBlank { currentDriverDoc.getString("name") ?: "" },
                    assignedBusId = busId
                )
            }
        } catch (ignored: Exception) {}
    }

    /**
     * Unassigns driver from a bus.
     * Sets driverId & driverName to null in /buses/{busId} and clears assignedBusId in Firestore and RTDB.
     */
    suspend fun unassignDriver(busId: String) {
        val targetBus = _localBuses.value.find { it.busId == busId }
        val driverId = targetBus?.driverId

        val current = _localBuses.value.map {
            if (it.busId == busId) it.copy(driverId = null, driverName = null) else it
        }
        _localBuses.value = current

        try {
            kotlinx.coroutines.withTimeoutOrNull(3000L) {
                // 1. Clear bus fleet record in Firestore
                firestore.collection("buses").document(busId).update(
                    mapOf("driverId" to null, "driverName" to null)
                ).await()

                // 2. Clear driver assignedBusId in Firestore and RTDB if driver was assigned
                if (!driverId.isNullOrBlank()) {
                    try {
                        firestore.collection("users").document(driverId)
                            .update("assignedBusId", null).await()
                        val driverDoc = firestore.collection("users").document(driverId).get().await()
                        syncDriverAuthorizationToRtdb(
                            driverUid = driverId,
                            role = driverDoc.getString("role") ?: "DRIVER",
                            active = driverDoc.getBoolean("active") ?: true,
                            name = driverDoc.getString("name") ?: "",
                            assignedBusId = null
                        )
                    } catch (ignored: Exception) {}
                }
            }
        } catch (ignored: Exception) {}
    }

    /**
     * Permanently deletes a bus from the fleet:
     * 1. Unassigns any assigned driver (clears Firestore & RTDB users node).
     * 2. Removes RTDB live_locations/{busId} node.
     * 3. Removes RTDB location_sources/{busId} node.
     * 4. Deletes Firestore /buses/{busId} document.
     * 5. Updates local cached state immediately.
     */
    suspend fun deleteBus(busId: String): Result<Unit> {
        val targetBus = _localBuses.value.find { it.busId == busId }
        val driverId = targetBus?.driverId

        // Optimistically update local cache
        _localBuses.value = _localBuses.value.filter { it.busId != busId }

        return try {
            kotlinx.coroutines.withTimeoutOrNull(5000L) {
                // 1. Unassign driver if assigned
                if (!driverId.isNullOrBlank()) {
                    try {
                        firestore.collection("users").document(driverId)
                            .update("assignedBusId", null).await()
                        val driverDoc = firestore.collection("users").document(driverId).get().await()
                        syncDriverAuthorizationToRtdb(
                            driverUid = driverId,
                            role = driverDoc.getString("role") ?: "DRIVER",
                            active = driverDoc.getBoolean("active") ?: true,
                            name = driverDoc.getString("name") ?: "",
                            assignedBusId = null
                        )
                    } catch (ignored: Exception) {}
                }

                // 2. Clean up RTDB live location & contributor nodes
                try {
                    database.getReference("live_locations").child(busId).removeValue().await()
                    database.getReference("location_sources").child(busId).removeValue().await()
                } catch (ignored: Exception) {}

                // 3. Delete Firestore document
                firestore.collection("buses").document(busId).delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes a driver account:
     * 1. Authorization check: Verifies current authenticated user is an active ADMIN.
     * 2. Target validation: Verifies target user profile is not an ADMIN.
     * 3. Server-side deletion: Calls Cloud Function / ServerAuthDeleter to delete Firebase Auth account by UID.
     *    - If server deletion fails, fails fast without modifying database state.
     * 4. Unassigns driver from any assigned bus (/buses/{busId}).
     * 5. Clears RTDB /users/{driverId} node.
     * 6. Deletes Firestore /users/{driverId} document.
     *
     * Never uses passwords or signs into the driver account on the client.
     */
    suspend fun deleteDriver(driverId: String, context: Context? = null): Result<Unit> {
        val assignedBuses = _localBuses.value.filter { it.driverId == driverId }

        return try {
            val res = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                // 1. Authorization check: Caller must be an active ADMIN
                val currentUid = auth.currentUser?.uid
                    ?: return@withTimeoutOrNull Result.failure<Unit>(SecurityException("Authentication required to delete staff accounts."))
                val callerDoc = firestore.collection("users").document(currentUid).get().await()
                val callerRole = callerDoc.getString("role")
                val callerActive = callerDoc.getBoolean("active") ?: false
                if (callerRole != "ADMIN" || !callerActive) {
                    return@withTimeoutOrNull Result.failure<Unit>(SecurityException("Only active administrators are authorized to delete driver accounts."))
                }

                // 2. Target validation: Ensure target is not an administrator
                val targetDoc = firestore.collection("users").document(driverId).get().await()
                if (targetDoc.exists()) {
                    val targetRole = targetDoc.getString("role")
                    if (targetRole == "ADMIN") {
                        return@withTimeoutOrNull Result.failure<Unit>(IllegalArgumentException("Administrator accounts cannot be deleted."))
                    }
                }

                // 3. Server-side Firebase Auth user deletion by UID
                val serverResult = serverAuthDeleter.deleteUserByUid(driverId)
                if (serverResult.isFailure) {
                    return@withTimeoutOrNull Result.failure<Unit>(
                        serverResult.exceptionOrNull() ?: Exception("Server-side deletion failed")
                    )
                }

                // 4. Unassign from all buses if assigned
                for (bus in assignedBuses) {
                    try {
                        unassignDriver(bus.busId)
                    } catch (ignored: Exception) {}
                }

                // Check any bus documents in Firestore referencing this driverId
                try {
                    val busesQuery = firestore.collection("buses").whereEqualTo("driverId", driverId).get().await()
                    for (doc in busesQuery.documents) {
                        try {
                            firestore.collection("buses").document(doc.id).update(
                                mapOf("driverId" to null, "driverName" to null)
                            ).await()
                        } catch (ignored: Exception) {}
                    }
                } catch (ignored: Exception) {}

                // 5. Clear RTDB user node
                try {
                    database.getReference("users").child(driverId).removeValue().await()
                } catch (ignored: Exception) {}

                // 6. Delete Firestore user document
                firestore.collection("users").document(driverId).delete().await()

                Result.success(Unit)
            }
            res ?: Result.failure(Exception("Driver deletion timed out."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Security Data Cleanup Migration:
     * Safely strips legacy plaintext 'authPassword' fields from all Firestore /users documents
     * using FieldValue.delete().
     *
     * Invariants:
     * - Must be executed by an active ADMIN.
     * - Does NOT read or log password values.
     * - Completely removes the field from Firestore documents.
     */
    suspend fun sanitizeLegacyDriverPasswords(): Result<Int> {
        return try {
            val currentUid = auth.currentUser?.uid
                ?: return Result.failure(SecurityException("Authentication required."))
            val callerDoc = firestore.collection("users").document(currentUid).get().await()
            val callerRole = callerDoc.getString("role")
            val callerActive = callerDoc.getBoolean("active") ?: false
            if (callerRole != "ADMIN" || !callerActive) {
                return Result.failure(SecurityException("Only active administrators can perform data sanitization."))
            }

            val querySnapshot = firestore.collection("users").get().await()
            var sanitizedCount = 0
            val batch = firestore.batch()

            for (doc in querySnapshot.documents) {
                if (doc.contains("authPassword")) {
                    batch.update(doc.reference, "authPassword", FieldValue.delete())
                    sanitizedCount++
                }
            }

            if (sanitizedCount > 0) {
                batch.commit().await()
            }
            Result.success(sanitizedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resets a driver's Firebase Authentication password via secure Cloud Function.
     * Enforces caller Admin authorization, target driver role, and password constraints server-side.
     * Preserves driver UID, profile data, and bus assignments.
     */
    suspend fun resetDriverPassword(driverUid: String, newPassword: String): Result<Unit> {
        if (driverUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Driver UID cannot be blank."))
        }
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters long."))
        }
        return serverDriverPasswordResetter.resetDriverPassword(driverUid.trim(), newPassword)
    }
}
