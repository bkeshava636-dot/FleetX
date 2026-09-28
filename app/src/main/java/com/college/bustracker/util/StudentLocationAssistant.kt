package com.college.bustracker.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.os.Looper
import android.util.Log
import com.college.bustracker.CollegeBusApp
import com.college.bustracker.data.model.LocationSource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manages the explicit opt-in lifecycle of a secondary student contributor.
 * Samples GPS at a conservative battery-friendly rate (15 seconds) and uploads
 * exclusively to location_sources/{busId}/{sourceId}.
 */
object StudentLocationAssistant {

    private const val TAG = "StudentLocationAssistant"
    private const val PREFS_NAME = "student_contributor_prefs"
    private const val KEY_STUDENT_SOURCE_ID = "student_source_id"

    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

    private val _contributedBusId = MutableStateFlow<String?>(null)
    val contributedBusId: StateFlow<String?> = _contributedBusId.asStateFlow()

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var activeBusId: String = ""
    private var studentSourceId: String = ""
    private var appContext: Context? = null

    private fun getOrCreateSourceId(context: Context): String {
        val auth = FirebaseAuth.getInstance()
        val authUid = auth.currentUser?.uid
        if (!authUid.isNullOrBlank()) {
            return authUid
        }
        try {
            auth.signInAnonymously()
        } catch (ignored: Exception) {}
        val currentUid = auth.currentUser?.uid
        if (!currentUid.isNullOrBlank()) {
            return currentUid
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var cachedId = prefs.getString(KEY_STUDENT_SOURCE_ID, null)
        if (cachedId.isNullOrBlank()) {
            cachedId = "student_${UUID.randomUUID().toString().take(12)}"
            prefs.edit().putString(KEY_STUDENT_SOURCE_ID, cachedId).apply()
        }
        return cachedId
    }

    @SuppressLint("MissingPermission")
    fun startSharing(context: Context, busId: String) {
        if (busId.isBlank()) {
            Log.w(TAG, "Cannot start sharing: busId is blank")
            return
        }

        if (!CollegeCalendarService.isWorkingDay()) {
            Log.w(TAG, "Cannot start sharing: Today is a college holiday")
            return
        }

        // If already sharing for this bus, ignore duplicate calls
        if (_isSharing.value && activeBusId == busId) {
            return
        }

        // If currently sharing for another bus, stop first
        if (_isSharing.value) {
            stopSharing()
        }

        activeBusId = busId
        studentSourceId = getOrCreateSourceId(context)
        fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            15_000L // 15 seconds conservative battery cadence
        ).apply {
            setMinUpdateIntervalMillis(10_000L)
            setMaxUpdateDelayMillis(20_000L)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onNewStudentLocation(location)
            }
        }

        try {
            fusedClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            _isSharing.value = true
            _contributedBusId.value = busId
            appContext = context.applicationContext
            try {
                com.college.bustracker.service.StudentContributorService.startService(context, busId)
            } catch (ignored: Exception) {}
            Log.i(TAG, "Student location sharing STARTED for bus $busId as source $studentSourceId")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing: ${e.message}")
            stopSharing()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start student location updates: ${e.message}")
            stopSharing()
        }
    }

    private fun onNewStudentLocation(location: Location) {
        if (!CollegeCalendarService.isWorkingDay()) {
            Log.w(TAG, "Holiday detected during contribution: Stopping student sharing")
            stopSharing()
            return
        }

        // Validation: reject zero coordinates or low-accuracy fixes (> 40 meters)
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            Log.d(TAG, "GPS_REJECTED: Zero coordinates")
            return
        }
        if (location.hasAccuracy() && location.accuracy > 40.0f) {
            Log.d(TAG, "GPS_REJECTED: Low student accuracy: ${location.accuracy}m > 40m")
            return
        }

        val source = LocationSource(
            busId = activeBusId,
            sourceId = studentSourceId,
            sourceType = "SECONDARY_STUDENT",
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            speed = location.speed,
            bearing = location.bearing,
            timestamp = System.currentTimeMillis(),
            active = true
        )

        CollegeBusApp.instance.trackingRepository.contributeStudentLocation(source)
        try {
            AutomaticRideManager.onContributorLocationFix(location)
        } catch (ignored: Exception) {}
    }

    fun stopSharing() {
        val busToClean = activeBusId
        val sourceToClean = studentSourceId

        locationCallback?.let {
            try {
                fusedClient?.removeLocationUpdates(it)
            } catch (ignored: Exception) {}
        }

        locationCallback = null
        fusedClient = null
        activeBusId = ""
        _isSharing.value = false
        _contributedBusId.value = null

        if (busToClean.isNotBlank() && sourceToClean.isNotBlank()) {
            CollegeBusApp.instance.trackingRepository.removeStudentLocation(busToClean, sourceToClean)
            Log.i(TAG, "Student location sharing STOPPED for bus $busToClean")
        }

        appContext?.let { ctx ->
            try {
                com.college.bustracker.service.StudentContributorService.stopService(ctx)
            } catch (ignored: Exception) {}
        }
        appContext = null
    }
}
