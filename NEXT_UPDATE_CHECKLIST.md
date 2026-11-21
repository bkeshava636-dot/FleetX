# FleetX — Next Updates Checklist

This document tracks completed architectural fixes, deployments, and feature requirements across Android and Firebase backend.

---

## 📋 Status of High-Priority Update Items

### 1. Fix & Deploy "Bus Started / Online" Push Notifications
- [x] **Deploy Backend Cloud Trigger:**
  - Function `onLiveLocationWritten` (asia-south1) successfully deployed to Firebase Cloud Functions in production (`college-bus-tracker-prod`).
  - Command:
    ```bash
    npx firebase-tools deploy --only functions:onLiveLocationWritten --project college-bus-tracker-prod
    ```
- [x] **Harden Trigger against Initial GPS Zero-Coordinates:**
  - `processBusStartNotification` in `backend/functions/index.js` validates coordinates first.
  - Initial `(0.0, 0.0)` packet is rejected (`INVALID_COORDINATES`) without prematurely marking the bus as actively streaming.
  - Subsequent packet with valid coordinates properly triggers notification dispatch without being blocked by `ALREADY_ACTIVE`.
  - Added freshness check: previous `ACTIVE` sessions older than 15 minutes are treated as ended, allowing fresh notifications when a new session begins.
  - Verified with 10/10 automated tests passing in `backend/functions/test_bus_start_notification.js`.
- [x] **Client Verification & Permissions:**
  - Verified `BusTopicManager.formatBusStartTopic` and `updateSubscription` manage subscriptions to `bus_start_{busId}` cleanly.
  - Android 13+ `POST_NOTIFICATIONS` runtime permission requested at application startup in `MainActivity.kt`.
  - Notifications configured with `NotificationCompat.PRIORITY_HIGH` on channel `bus_alerts_channel`.

---

### 2. Stop-by-Stop Priority for Student Fallback Tracking
*When the driver's phone is offline or GPS is stale (>45s), fallback to tracking students based on their sequential boarding stop order (Stop 1 → Stop 2 → Stop 3 → Stop 4).*

- [x] **Data Model Extension (`LocationSource`):**
  - Added `stopId: String = ""` and `stopOrder: Int = 999` to `LocationSource.kt` with default parameters to preserve complete backward compatibility.
- [x] **Client Telemetry Attachment (`StudentLocationAssistant`):**
  - Updated `StudentLocationAssistant` to query authoritative saved stop ID and order from `StudentPreferences` (with fallback to cached route stops).
  - Transmits `stopId` and `stopOrder` to RTDB `/location_sources/{busId}/{sourceId}`.
- [x] **Hierarchical Beacon Arbitration (`StudentBeaconArbitrator`):**
  - Updated atomic RTDB transaction in `bus_beacons/{busId}`:
    - Records `stopOrder` and `accuracy` alongside `sourceId`.
    - Enables stop-order preemption: if an active lease holder boarded at Stop 3 or has no stop order (999), a newly boarding student at Stop 1 with accurate GPS (≤ 40m) preempts the lease.
    - Preserves GPS accuracy and freshness constraints to prevent stale Stop 1 fixes from overriding fresh Stop 2 fixes.
- [x] **Backend Fusion Engine Update (`index.js`):**
  - In `processStudentFallbackWrite()`, validated student sources are sorted by `stopOrder ASC` (Stop 1 → Stop 2 → Stop 3...), followed by accuracy and freshness.
  - `fuseCluster()` selects cluster representative prioritizing earliest stop order.
  - Verified with 13/13 automated tests passing in `backend/functions/test_student_fallback.js`.

---

### 3. Fast Cold Launch & Empty Cache Fallback ("Loading Fleet..." delay)
- [x] **Lightweight Default Fleet Seed:**
  - Generated `app/src/main/assets/bundled_fleet_seed.json` containing 25 fleet buses and 20 verified routes with BITM college stop coordinates.
  - Implemented `BundledFleetSeed.kt` parser.
- [x] **Zero-Delay Startup & 3-Second Timeout:**
  - `BusRepository.getBusesFlow()` and `RouteRepository.getRoutesFlow()` emit bundled fleet data immediately on cold launch if in-memory cache is empty (< 0.1s UI readiness).
  - Wrapped Firestore `Source.SERVER` queries in `withTimeoutOrNull(3000L)` to eliminate indefinite hanging on slow 2G/cellular connections.
  - Server responses cleanly refresh cached and displayed fleet data without UI flickering.

---

### 4. Correct Bus Status After Operating Window Ends
*When the bus operating window is over or today is a college holiday, the student app strictly displays "Bus has not started" and hides old GPS timestamps.*

- [x] **Operating Window Enforcement:**
  - Updated `LiveLocation?.getDisplayState(nowMillis, isWithinOperatingWindow)` to return `TrackingDisplayState.INACTIVE` whenever outside operating windows.
  - In `StudentViewModel.kt`, reactive 5-second ticker and live flow check `ScheduleHelper.isWithinOperatingWindow()` and `CollegeCalendarService.isWorkingDay()`.
  - When outside operating hours or on a holiday, `trackingState` is set to `TrackingDisplayState.INACTIVE`, `_lastUpdateText` is cleared, and ETAs are cleared.
  - `StudentHomeScreen` and `StudentMapScreen` display `"Bus has not started"` instead of `"Location updated X minutes ago"`.
  - In `StudentMapScreen`, live bus marker is hidden when outside operating window to prevent displaying stale bus locations.
  - Verified with unit tests in `Task1To4VerificationTest.kt`.
