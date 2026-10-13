package com.college.bustracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.college.bustracker.data.repository.AuthRepository
import com.college.bustracker.data.repository.BusRepository
import com.college.bustracker.data.repository.BusSwapRepository
import com.college.bustracker.data.repository.RouteRepository
import com.college.bustracker.data.repository.TrackingRepository
import com.college.bustracker.util.NetworkMonitor
import com.college.bustracker.util.OsmConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import kotlinx.coroutines.SupervisorJob

class CollegeBusApp : Application() {

    companion object {
        const val TRACKING_CHANNEL_ID = "tracking_service_channel"
        const val ALERTS_CHANNEL_ID = "bus_alerts_channel"
        const val ANNOUNCEMENTS_CHANNEL_ID = "announcements_channel"

        lateinit var instance: CollegeBusApp
            private set
    }

    // Managed application-level coroutine scope
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Application-level repositories (Clean lightweight service locator)
    lateinit var authRepository: AuthRepository
    lateinit var busRepository: BusRepository
    lateinit var routeRepository: RouteRepository
    lateinit var busSwapRepository: BusSwapRepository
    lateinit var trackingRepository: TrackingRepository
    lateinit var calendarRepository: com.college.bustracker.data.repository.CalendarRepository
    lateinit var operatingScheduleRepository: com.college.bustracker.data.repository.OperatingScheduleRepository
    lateinit var stopRequestRepository: com.college.bustracker.data.repository.StopRequestRepository
    lateinit var temporaryAssignmentRepository: com.college.bustracker.data.repository.TemporaryAssignmentRepository
    lateinit var announcementRepository: com.college.bustracker.data.repository.AnnouncementRepository
    lateinit var networkMonitor: NetworkMonitor

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Install persistent fatal crash logger
        val defaultUncaught = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("FATAL_CRASH", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                crashFile.writeText(android.util.Log.getStackTraceString(throwable))
            } catch (ignored: Exception) {}
            defaultUncaught?.uncaughtException(thread, throwable)
        }

        // 1. Initialize OpenStreetMap Tile Configuration (User-Agent, offline caching)
        OsmConfigHelper.initialize(this)

        // Initialize Locale preference
        com.college.bustracker.util.LocaleHelper.init(this)

        // 2. Setup Notification Channels
        createNotificationChannels()

        // 3. Initialize Repositories
        authRepository = AuthRepository(this)
        busRepository = BusRepository()
        routeRepository = RouteRepository()
        busSwapRepository = BusSwapRepository()
        trackingRepository = TrackingRepository()
        calendarRepository = com.college.bustracker.data.repository.CalendarRepository()
        operatingScheduleRepository = com.college.bustracker.data.repository.OperatingScheduleRepository()
        stopRequestRepository = com.college.bustracker.data.repository.StopRequestRepository()
        temporaryAssignmentRepository = com.college.bustracker.data.repository.TemporaryAssignmentRepository()
        announcementRepository = com.college.bustracker.data.repository.AnnouncementRepository()
        networkMonitor = NetworkMonitor(this)

        // 4. Initialize Calendar & Operating Schedule Services and keep caches reactive
        com.college.bustracker.util.CollegeCalendarService.init(this)
        com.college.bustracker.util.OperatingScheduleService.init(this)

        appScope.launch {
            calendarRepository.getCalendarExceptionsFlow().collect { exceptions ->
                com.college.bustracker.util.CollegeCalendarService.updateCachedExceptions(exceptions, this@CollegeBusApp)
            }
        }
        appScope.launch {
            operatingScheduleRepository.getOperatingScheduleFlow().collect { schedule ->
                com.college.bustracker.util.OperatingScheduleService.updateCachedSchedule(schedule, this@CollegeBusApp)
                // Sync active auto-tracking alarms with the authoritative schedule
                val prefs = getSharedPreferences(com.college.bustracker.service.AutoTrackingScheduler.PREF_FILE, Context.MODE_PRIVATE)
                val isDriverAutoEnabled = prefs.getBoolean(com.college.bustracker.service.AutoTrackingScheduler.KEY_AUTO_ENABLED, false)
                if (isDriverAutoEnabled) {
                    com.college.bustracker.service.AutoTrackingScheduler.saveSchedule(this@CollegeBusApp, schedule)
                    com.college.bustracker.service.AutoTrackingScheduler.scheduleAlarms(this@CollegeBusApp, schedule)
                    com.college.bustracker.service.AutoTrackingScheduler.checkAndTriggerAutoTracking(this@CollegeBusApp)
                }

                val ridePrefs = getSharedPreferences(com.college.bustracker.service.StudentAutoTrackingScheduler.PREFS_NAME, Context.MODE_PRIVATE)
                val isStudentAutoEnabled = ridePrefs.getBoolean("auto_ride_sharing_enabled", false)
                if (isStudentAutoEnabled) {
                    com.college.bustracker.service.StudentAutoTrackingScheduler.saveSchedule(this@CollegeBusApp, schedule)
                    com.college.bustracker.service.StudentAutoTrackingScheduler.scheduleAlarms(this@CollegeBusApp, schedule)
                }
            }
        }
        com.college.bustracker.util.AutomaticRideManager.init(this)

        // 5. Initialize Driver Location State Monitor for automatic GPS tracking recovery
        com.college.bustracker.util.DriverLocationStateMonitor.init(this)

        // 6. Initialize Background GPS Diagnostic System
        com.college.bustracker.diagnostics.GpsDiagnosticManager.init(this)
        com.college.bustracker.diagnostics.GpsDiagnosticManager.onProcessStarted(this)

        // 7. Register Activity Lifecycle Tracker for diagnostics
        registerDiagnosticActivityCallbacks()

        try {
            com.college.bustracker.service.AutoTrackingScheduler.checkAndTriggerAutoTracking(this)
        } catch (e: Exception) {
            android.util.Log.w("CollegeBusApp", "Safe checkAndTriggerAutoTracking skipped on app create: ${e.message}")
        }
    }

    private var activeActivityCount = 0

    private fun registerDiagnosticActivityCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {
                activeActivityCount++
            }
            override fun onActivityResumed(activity: android.app.Activity) {
                com.college.bustracker.diagnostics.GpsDiagnosticManager.onActivityResumed(this@CollegeBusApp, activity.javaClass.simpleName)
            }
            override fun onActivityPaused(activity: android.app.Activity) {
                com.college.bustracker.diagnostics.GpsDiagnosticManager.onActivityPaused(this@CollegeBusApp, activity.javaClass.simpleName)
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                activeActivityCount = (activeActivityCount - 1).coerceAtLeast(0)
                if (activeActivityCount == 0) {
                    com.college.bustracker.diagnostics.GpsDiagnosticManager.onAppMovedToBackground(this@CollegeBusApp)
                }
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Tracking service channel (low importance so it doesn't beep continuously)
            val trackingChannel = NotificationChannel(
                TRACKING_CHANNEL_ID,
                getString(R.string.channel_tracking_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_tracking_desc)
                setShowBadge(false)
            }

            // High-priority alerts channel for bus swap notifications
            val alertsChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alerts_desc)
                enableVibration(true)
            }

            // High-priority channel for campus announcements & timing changes
            val announcementsChannel = NotificationChannel(
                ANNOUNCEMENTS_CHANNEL_ID,
                getString(R.string.channel_announcements_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_announcements_desc)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(trackingChannel)
            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(announcementsChannel)
        }
    }
}
