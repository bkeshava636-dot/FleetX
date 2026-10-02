package com.college.bustracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.college.bustracker.data.repository.StudentPreferences
import com.college.bustracker.service.PushNotificationService
import com.college.bustracker.ui.navigation.AppNavHost
import com.college.bustracker.ui.student.StudentViewModel
import com.college.bustracker.ui.theme.CollegeBusTrackerTheme
import com.college.bustracker.ui.theme.CollegeSurface

class MainActivity : ComponentActivity() {

    private val studentViewModel: StudentViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Fine / Coarse location and notification permissions handled
        try {
            com.college.bustracker.service.AutoTrackingScheduler.checkAndTriggerAutoTracking(this)
        } catch (ignored: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try {
            com.college.bustracker.service.AutoTrackingScheduler.checkAndTriggerAutoTracking(this)
        } catch (ignored: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleNotificationIntent(intent)

        // Request runtime permissions for location and notifications
        requestRequiredPermissions()

        setContent {
            CollegeBusTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CollegeSurface
                ) {
                    AppNavHost(studentViewModel = studentViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val busId = intent.getStringExtra(PushNotificationService.EXTRA_BUS_ID)
        val stopId = intent.getStringExtra(PushNotificationService.EXTRA_STOP_ID)
        if (!busId.isNullOrBlank()) {
            StudentPreferences.saveSelection(this, busId, stopId)
            studentViewModel.selectBusById(busId, stopId)
        }
        if (com.college.bustracker.BuildConfig.DEBUG && intent.hasExtra("EXTRA_MORNING_START")) {
            val morningStart = intent.getStringExtra("EXTRA_MORNING_START") ?: "07:00"
            val morningEnd = intent.getStringExtra("EXTRA_MORNING_END") ?: "09:00"
            val eveningStart = intent.getStringExtra("EXTRA_EVENING_START") ?: "16:00"
            val eveningEnd = intent.getStringExtra("EXTRA_EVENING_END") ?: "18:00"
            val newSchedule = com.college.bustracker.data.model.RouteSchedule(
                morning = com.college.bustracker.data.model.ScheduleWindow(morningStart, morningEnd, true),
                evening = com.college.bustracker.data.model.ScheduleWindow(eveningStart, eveningEnd, true)
            )
            com.college.bustracker.service.AutoTrackingScheduler.saveSchedule(this, newSchedule)
            com.college.bustracker.service.AutoTrackingScheduler.scheduleAlarms(this, newSchedule)
            com.college.bustracker.service.AutoTrackingScheduler.checkAndTriggerAutoTracking(this)
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}
