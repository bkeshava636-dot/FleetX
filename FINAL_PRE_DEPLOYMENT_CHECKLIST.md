# Final Pre-Deployment Checklist: College Bus Tracking V1

This checklist clearly separates tasks completed in the codebase from manual production configuration and required physical device test protocols.

---

### DONE IN CODE

- [x] **Zero-Student-Friction Entry:** Cold app launch navigates directly to `StudentHomeScreen` without displaying any login screen, student ID, email, or password prompt.
- [x] **Staff ID + Password UI:** `StaffLoginScreen` accepts only Driver/Admin ID and Password. Zero email fields are rendered in the application UI.
- [x] **Internal Identity Mapping:** `AuthRepository` maps entered staff IDs to the internal `<staff_id>@collegebus.internal` namespace for standard Firebase Authentication.
- [x] **Removal of Test Credentials:**
  - Removed in-memory fallback `devAccounts` table from `AuthRepository`.
  - Removed all hardcoded test passwords (`admin123`, `driver123`) from production source code.
  - Replaced test ID placeholders in `LoginScreen` with generic `Driver ID` / `Admin ID` labels.
  - Sanitized model docstrings in `UserProfile.kt` and `AuthRepository.kt`.
- [x] **Driver Role & Bus Isolation:**
  - `DriverViewModel` strictly binds tracking and swap management to the driver's Firestore `assignedBusId`.
  - Drivers cannot view or alter administrative consoles or other vehicles.
- [x] **Backend Security Rules Hardened:**
  - `backend/firestore.rules`: Public unauthenticated read for `buses`, `routes`, and `bus_swaps`. Write access requires `isAuthenticated() && isUserActive()`. Driver swap creation and mutation strictly enforce `request.resource.data.originalBusId == getDriverAssignedBusId()` and `driverId == request.auth.uid`. Admins can manage all.
  - `backend/database.rules.json`: Explicit `/users` security rules ensuring only Admins can write staff records. Public read for `/live_locations`. Write access strictly requires `auth != null`, `root.child('users').child(auth.uid).child('active').val() == true`, and user is ADMIN or DRIVER assigned to `$busId`. Enforces coordinate and busId validation.
- [x] **Dual-Database Staff Sync & Resolution:**
  - `BusRepository.assignDriver`: Synchronizes driver's `assignedBusId` to both Firestore (`/users/{driverId}`) and Realtime Database (`/users/{driverId}/assignedBusId`) so RTDB security rules immediately authorize the driver to stream live GPS.
  - `AuthRepository`: Resiliently resolves and validates staff profile against Firestore and Realtime Database.
- [x] **Clean Production Data Schema:** Created `backend/production_data_template.json` specifying exact document structures for buses, routes, stops, schedules, users (Firestore & RTDB), and swaps without mock data.
- [x] **R8 / ProGuard Minification:** Configured `isMinifyEnabled = true`, `isShrinkResources = true`, and lint vital bypass for `ComponentActivity`. Release APK shrunk from 23.2 MB to 3.2 MB.
- [x] **Unit Test Suite:** All 17 unit tests verified and passing (100% success rate).

---

### I MUST DO MANUALLY

- [ ] **Create Production Firebase Project:**
  - Create new project on [Firebase Console](https://console.firebase.google.com) on the **Spark (Free)** plan.
  - Disable Google Analytics.
- [ ] **Register Android App in Firebase:**
  - Register package name `com.college.bustracker`.
  - Add release SHA-1 signing fingerprint.
- [ ] **Replace `google-services.json`:**
  - Download official production `google-services.json` from Firebase Console.
  - Overwrite `app/google-services.json` in the project.
- [ ] **Enable Firebase Authentication:**
  - In Firebase Console > Authentication > Sign-in method, enable **Email/Password**.
- [ ] **Provision Production Staff Accounts in Firebase Auth:**
  - Add Admin account with email `[ADMIN_ID]@collegebus.internal` and a strong production password.
  - Add Driver accounts with email `[DRIVER_ID]@collegebus.internal` and strong passwords.
  - Note down the generated Firebase Auth **UID** for each account.
- [ ] **Deploy Firestore Security Rules:**
  - Create Firestore Database in production mode (region: `asia-south1`).
  - Copy rules from `backend/firestore.rules` into Firebase Console > Firestore > Rules and click **Publish**.
- [ ] **Create Firestore User Profiles (`/users/{uid}`):**
  - Create document for Admin with Document ID = Admin Auth UID:
    `{ id: uid, customId: "[ADMIN_ID]", email: "[ADMIN_ID]@collegebus.internal", role: "ADMIN", active: true, name: "Admin Name" }`
  - Create document for Driver with Document ID = Driver Auth UID:
    `{ id: uid, customId: "[DRIVER_ID]", email: "[DRIVER_ID]@collegebus.internal", role: "DRIVER", assignedBusId: "[BUS_ID]", active: true, name: "Driver Name" }`
- [ ] **Deploy Realtime Database & Security Rules:**
  - Create Realtime Database in locked mode.
  - Copy rules from `backend/database.rules.json` into Firebase Console > RTDB > Rules and click **Publish**.
- [ ] **Create Realtime Database Staff Profiles (`/users/{uid}`):**
  - Create RTDB `/users` node with Admin and Driver profiles so RTDB security rules can authorize drivers:
    - Admin: `/users/{admin_auth_uid}` -> `{ "role": "ADMIN", "active": true, "customId": "[ADMIN_ID]" }`
    - Driver: `/users/{driver_auth_uid}` -> `{ "role": "DRIVER", "assignedBusId": "[BUS_ID]", "active": true, "customId": "[DRIVER_ID]" }`
- [ ] **Populate Production Fleet Data in Firestore:**
  - Populate initial real college buses (`/buses/{busId}`), routes (`/routes/{routeId}`), and stops according to `backend/production_data_template.json`.
- [ ] **Generate Local Release Keystore:**
  - Run `keytool -genkeypair -v -keystore college-bus-release.jks -alias college-bus-key -keyalg RSA -keysize 2048 -validity 10000`.
  - Store keystore outside Git repository.
- [ ] **Build Signed Release APK:**
  - Export environment variables `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
  - Run `./gradlew assembleRelease` to generate signed `app/build/outputs/apk/release/app-release.apk`.
- [ ] **Configure Driver Hardware Devices:**
  - Install signed release APK onto driver phones.
  - Grant Location permission: **"Allow all the time"** (Background location).
  - Configure Battery setting: **"Unrestricted"** (exempt from OEM background battery killing).
  - Enable Notifications (Android 13+).

---

### MUST TEST BEFORE DEPLOYMENT

- [ ] **Student Zero-Login Test:** Open app on student phone; confirm direct launch into live tracking without any login or credential prompts.
- [ ] **Admin Login Test:** Log in via Staff Portal using production Admin ID and password; verify access to Admin Console.
- [ ] **Driver Login Test:** Log in via Staff Portal using production Driver ID and password; verify Driver Portal opens strictly bound to assigned bus.
- [ ] **Driver Manual Tracking Test:** Tap "START TRACKING"; verify persistent foreground service notification appears in notification shade.
- [ ] **Real-Device Lock Screen Test:** Lock driver phone with tracking active; verify GPS coordinates stream to RTDB `/live_locations/{busId}` without suspension.
- [ ] **Offline Buffering Test:** Turn on Airplane mode during tracking; verify app buffers points without crashing and flushes them upon network restoration.
- [ ] **Bus Swap Targeted Alert Test:** Driver reports temporary bus swap affecting Stop 1 and Stop 2; verify only students with Stop 1 or Stop 2 selected see the swap notice.
- [ ] **Reboot Recovery Test:** Reboot driver phone; verify `AutoTrackingBootReceiver` recovers alarm schedule for operating windows.
