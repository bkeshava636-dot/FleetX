const functions = require("firebase-functions");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const regionalFunctions = functions.region("asia-south1");

/**
 * Sanitizes strings for valid FCM topic format: [a-zA-Z0-9-_.~%]+
 */
function sanitizeTopic(str) {
  return String(str || "").trim().replace(/[^a-zA-Z0-9-_.~%]/g, "_");
}

/**
 * Generates the FCM topic name for an original bus and affected stop.
 * Target pattern: bus_{originalBusId}_stop_{stopId}
 */
function buildBusStopTopic(originalBusId, stopId) {
  const cleanBus = sanitizeTopic(originalBusId);
  const cleanStop = sanitizeTopic(stopId);
  return `bus_${cleanBus}_stop_${cleanStop}`;
}

/**
 * Core business logic for evaluating and sending bus swap notifications.
 * Extracted as a pure testable function taking db and messaging instances.
 */
async function processBusSwapWrite(change, context, db = admin.firestore(), messaging = admin.messaging()) {
  const swapId = context && context.params ? context.params.swapId : "unknown";

  // Document deleted
  if (!change.after || !change.after.exists) {
    console.log(`[onBusSwapWritten] Swap document ${swapId} deleted. No action required.`);
    return null;
  }

  const afterData = change.after.data() || {};
  const beforeData = (change.before && change.before.exists) ? change.before.data() : null;

  const status = String(afterData.status || "ACTIVE").toUpperCase();
  const originalBusId = String(afterData.originalBusId || "").trim();
  const replacementBusId = String(afterData.replacementBusId || "").trim();
  const routeId = String(afterData.routeId || "").trim();
  const dateStr = String(afterData.date || "").trim();
  const affectedStopIds = Array.isArray(afterData.affectedStopIds) ? afterData.affectedStopIds : [];

  // =========================================================================
  // 1. HOLIDAY COMPATIBILITY (Requirement 13)
  // =========================================================================
  if (dateStr) {
    try {
      const calDoc = await db.collection("calendar_exceptions").doc(dateStr).get();
      if (calDoc.exists) {
        const calData = calDoc.data() || {};
        if (calData.type === "FULL_DAY_HOLIDAY" || calData.isHoliday === true) {
          // If the swap explicitly requests suppression on holiday, skip notifications.
          // Otherwise, manual staff dispatches (ACTIVE or CANCELLED) always take precedence to notify students.
          if (afterData.suppressOnHoliday === true) {
            console.log(`[onBusSwapWritten] Date ${dateStr} is a FULL_DAY_HOLIDAY (${calData.name || 'Holiday'}) and suppressOnHoliday is set. Skipping push notifications.`);
            return { skipped: true, reason: "FULL_DAY_HOLIDAY" };
          }
          console.log(`[onBusSwapWritten] Date ${dateStr} has calendar exception (${calData.name || 'Holiday'}), but manual bus swap (${status}) is active. Proceeding with push notifications.`);
        }
      }
    } catch (err) {
      console.warn(`[onBusSwapWritten] Warning checking holiday calendar for ${dateStr}:`, err.message);
    }
  }

  // =========================================================================
  // 2. CANCELLATION NOTIFICATION (Requirement 11)
  // =========================================================================
  if (status === "CANCELLED" && beforeData && beforeData.status === "ACTIVE") {
    if (afterData.cancellationNotified === true) {
      console.log(`[onBusSwapWritten] Cancellation already notified for swap ${swapId}.`);
      return { skipped: true, reason: "ALREADY_CANCELLED" };
    }

    const previouslyNotified = Array.isArray(afterData.notifiedStopIds) ? afterData.notifiedStopIds : [];
    if (previouslyNotified.length === 0) {
      console.log(`[onBusSwapWritten] No stops were previously notified for swap ${swapId}.`);
      return { skipped: true, reason: "NO_PRIOR_NOTIFICATIONS" };
    }

    // Resolve stop names from route if available
    const stopNameMap = new Map();
    if (routeId) {
      try {
        const routeDoc = await db.collection("routes").doc(routeId).get();
        if (routeDoc.exists) {
          const stops = routeDoc.data().stops || [];
          stops.forEach(s => {
            if (s.stopId) stopNameMap.set(s.stopId, s.name || s.stopId);
          });
        }
      } catch (err) {
        console.warn(`[onBusSwapWritten] Warning fetching route ${routeId}:`, err.message);
      }
    }

    const cancellationPromises = previouslyNotified.map(async (stopId) => {
      const stopName = stopNameMap.get(stopId) || stopId;
      const topic = buildBusStopTopic(originalBusId, stopId);

      const message = {
        topic: topic,
        notification: {
          title: "🚌 Bus Change Cancelled",
          body: `${originalBusId} is back in service for ${stopName} today.`
        },
        data: {
          notificationType: "BUS_SWAP",
          swapId: String(swapId),
          originalBusId: String(originalBusId),
          replacementBusId: String(replacementBusId),
          stopId: String(stopId),
          stopName: String(stopName),
          date: String(dateStr),
          status: "CANCELLED"
        },
        android: {
          priority: "high",
          notification: {
            channelId: "bus_alerts_channel",
            priority: "high",
            defaultSound: true,
            defaultVibrateTimings: true
          }
        }
      };

      try {
        await messaging.send(message);
        console.log(`[onBusSwapWritten] Sent cancellation notice to topic ${topic}`);
      } catch (err) {
        console.error(`[onBusSwapWritten] Error sending cancellation notice to topic ${topic}:`, err.message);
      }
    });

    await Promise.all(cancellationPromises);

    if (change.after.ref && typeof change.after.ref.update === "function") {
      await change.after.ref.update({
        cancellationNotified: true,
        cancellationNotifiedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    }

    return { success: true, type: "CANCELLED", notifiedCount: previouslyNotified.length };
  }

  // If status is not ACTIVE, nothing to notify
  if (status !== "ACTIVE") {
    console.log(`[onBusSwapWritten] Swap ${swapId} status is ${status}. Skipping.`);
    return { skipped: true, reason: `STATUS_${status}` };
  }

  if (!originalBusId || !replacementBusId) {
    console.log(`[onBusSwapWritten] Swap ${swapId} missing required parameters. Skipping.`);
    return { skipped: true, reason: "INVALID_PARAMETERS" };
  }

  const scopeType = String(afterData.scopeType || "").toUpperCase();

  // =========================================================================
  // 3. VALID STOP VALIDATION (Requirement 15) & SCOPE RESOLUTION
  // =========================================================================
  const validStopMap = new Map();
  const allRouteStopIds = [];
  if (routeId) {
    try {
      const routeDoc = await db.collection("routes").doc(routeId).get();
      if (routeDoc.exists) {
        const stops = routeDoc.data().stops || [];
        stops.forEach(s => {
          if (s.stopId) {
            validStopMap.set(s.stopId, s.name || s.stopId);
            allRouteStopIds.push(s.stopId);
          }
        });
      }
    } catch (err) {
      console.warn(`[onBusSwapWritten] Could not verify route ${routeId}:`, err.message);
    }
  }

  let effectiveStops = [];
  if (scopeType === "ALL_STOPS") {
    effectiveStops = allRouteStopIds.length > 0 ? allRouteStopIds : affectedStopIds;
  } else if (scopeType === "SELECTED_STOPS") {
    effectiveStops = affectedStopIds;
  } else {
    // Default / legacy fallback
    effectiveStops = affectedStopIds.length > 0 ? affectedStopIds : allRouteStopIds;
  }

  if (effectiveStops.length === 0) {
    console.log(`[onBusSwapWritten] Swap ${swapId} has no stops to notify.`);
    return { skipped: true, reason: "NO_STOPS" };
  }

  // Filter affected stops: If route stops are known, enforce membership
  const verifiedStopIds = effectiveStops.filter(stopId => {
    if (validStopMap.size > 0 && !validStopMap.has(stopId)) {
      console.warn(`[onBusSwapWritten] Warning: Stop ${stopId} not found on route ${routeId}. Filtering out.`);
      return false;
    }
    return true;
  });

  if (verifiedStopIds.length === 0) {
    console.log(`[onBusSwapWritten] No valid stops to notify for swap ${swapId}.`);
    return { skipped: true, reason: "NO_VALID_STOPS" };
  }

  // =========================================================================
  // 4. DEDUPLICATION & IDEMPOTENCY (Requirement 9 & 10)
  // =========================================================================
  const beforeReplacement = beforeData ? String(beforeData.replacementBusId || "").trim() : "";
  const isReplacementChanged = beforeData && beforeData.status === "ACTIVE" && beforeReplacement !== "" && beforeReplacement !== replacementBusId;
  const alreadyNotifiedStops = isReplacementChanged ? [] : (Array.isArray(afterData.notifiedStopIds) ? afterData.notifiedStopIds : []);
  const stopsToNotify = verifiedStopIds.filter(stopId => !alreadyNotifiedStops.includes(stopId));

  if (stopsToNotify.length === 0) {
    console.log(`[onBusSwapWritten] All affected stops already notified for swap ${swapId}. Skipping.`);
    return { skipped: true, reason: "ALREADY_NOTIFIED" };
  }

  // =========================================================================
  // 5. SEND NOTIFICATIONS (Requirement 2, 6, 7)
  // =========================================================================
  const sendPromises = stopsToNotify.map(async (stopId) => {
    const stopName = validStopMap.get(stopId) || stopId;
    const topic = buildBusStopTopic(originalBusId, stopId);

    const message = {
      topic: topic,
      notification: {
        title: "🚌 Bus Change Alert",
        body: `${replacementBusId} is replacing ${originalBusId} today for ${stopName}.`
      },
      data: {
        notificationType: "BUS_SWAP",
        swapId: String(swapId),
        originalBusId: String(originalBusId),
        replacementBusId: String(replacementBusId),
        stopId: String(stopId),
        stopName: String(stopName),
        date: String(dateStr),
        status: "ACTIVE"
      },
      android: {
        priority: "high",
        notification: {
          channelId: "bus_alerts_channel",
          priority: "high",
          defaultSound: true,
          defaultVibrateTimings: true
        }
      }
    };

    try {
      const response = await messaging.send(message);
      console.log(`[onBusSwapWritten] FCM send SUCCESS: stop=${stopId}, topic=${topic}, messageId=${response}`);
      return stopId;
    } catch (err) {
      console.error(`[onBusSwapWritten] FCM send FAILED: stop=${stopId}, topic=${topic}, error=${err.message}`);
      return null;
    }
  });

  const sendResults = await Promise.all(sendPromises);
  const successfullySentStops = sendResults.filter(Boolean);

  if (successfullySentStops.length > 0 && change.after.ref && typeof change.after.ref.update === "function") {
    await change.after.ref.update({
      notifiedStopIds: isReplacementChanged ? successfullySentStops : admin.firestore.FieldValue.arrayUnion(...successfullySentStops),
      notificationSentAt: admin.firestore.FieldValue.serverTimestamp()
    });
  }

  return {
    success: true,
    notifiedStops: successfullySentStops,
    notifiedCount: successfullySentStops.length
  };
}

/**
 * Firestore Trigger on bus_swaps/{swapId}
 * Triggered on swap creation, update, or cancellation.
 * Collocated in asia-south1 matching the production Firestore database.
 */
exports.onBusSwapWritten = regionalFunctions.firestore
  .document("bus_swaps/{swapId}")
  .onWrite(async (change, context) => {
    return processBusSwapWrite(change, context, admin.firestore(), admin.messaging());
  });

/**
 * deleteDriverAccount
 * Secure server-side HTTPS Callable Cloud Function for Driver Deletion.
 */
exports.deleteDriverAccount = regionalFunctions.https.onCall(async (data, context) => {
  // 1. Verify caller authentication
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication is required to perform this action."
    );
  }

  const callerUid = context.auth.uid;

  // 2. Verify caller is an authorized active Administrator in Firestore
  const callerDoc = await admin.firestore().collection("users").doc(callerUid).get();
  if (!callerDoc.exists) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Caller authorization profile not found."
    );
  }

  const callerData = callerDoc.data() || {};
  if (callerData.role !== "ADMIN" || callerData.active !== true) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only active administrators are authorized to delete staff accounts."
    );
  }

  // 3. Validate driverUid parameter
  const driverUid = data && data.driverUid ? String(data.driverUid).trim() : null;
  if (!driverUid) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "A valid driverUid must be provided."
    );
  }

  // 4. Protection: Cannot delete own administrator account
  if (driverUid === callerUid) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Administrators cannot delete their own active account."
    );
  }

  // 5. Protection: Cannot delete another administrator account via this endpoint
  const targetDoc = await admin.firestore().collection("users").doc(driverUid).get();
  if (targetDoc.exists) {
    const targetData = targetDoc.data() || {};
    if (targetData.role === "ADMIN") {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Administrator accounts cannot be deleted via the driver management endpoint."
      );
    }
  }

  // 6. Delete user by UID from Firebase Authentication
  try {
    await admin.auth().deleteUser(driverUid);
    console.log(`[deleteDriverAccount] Successfully deleted user ${driverUid} from Firebase Auth by admin ${callerUid}`);
    return {
      success: true,
      driverUid: driverUid,
      message: `Driver user ${driverUid} deleted from Firebase Authentication.`
    };
  } catch (error) {
    if (error.code === "auth/user-not-found") {
      console.log(`[deleteDriverAccount] User ${driverUid} already absent from Firebase Auth. Proceeding.`);
      return {
        success: true,
        driverUid: driverUid,
        message: "User was already absent from Firebase Authentication."
      };
    }
    console.error(`[deleteDriverAccount] Error deleting user ${driverUid}:`, error);
    throw new functions.https.HttpsError(
      "internal",
      error.message || "Failed to delete user from Firebase Authentication."
    );
  }
});

/**
 * updateDriverEmail
 * Secure server-side HTTPS Callable Cloud Function for Driver ID / Login Email Update.
 * Allows an authenticated active Administrator to update a driver's Firebase Auth login email
 * while strictly preserving their UID, password, and account history.
 */
exports.updateDriverEmail = regionalFunctions.https.onCall(async (data, context) => {
  // 1. Verify caller authentication
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication is required to perform this action."
    );
  }

  const callerUid = context.auth.uid;

  // 2. Verify caller is an authorized active Administrator in Firestore
  const callerDoc = await admin.firestore().collection("users").doc(callerUid).get();
  if (!callerDoc.exists) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Caller authorization profile not found."
    );
  }

  const callerData = callerDoc.data() || {};
  if (callerData.role !== "ADMIN" || callerData.active !== true) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only active administrators are authorized to update staff credentials."
    );
  }

  // 3. Validate parameters
  const driverUid = data && data.driverUid ? String(data.driverUid).trim() : null;
  const newEmail = data && data.newEmail ? String(data.newEmail).trim().toLowerCase() : null;
  if (!driverUid || !newEmail) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Valid driverUid and newEmail must be provided."
    );
  }

  // 4. Update email in Firebase Authentication
  try {
    await admin.auth().updateUser(driverUid, { email: newEmail });
    console.log(`[updateDriverEmail] Successfully updated email for ${driverUid} to ${newEmail} by admin ${callerUid}`);
    return {
      success: true,
      driverUid: driverUid,
      newEmail: newEmail,
      message: `Updated Firebase Auth email for driver ${driverUid}`
    };
  } catch (error) {
    console.error(`[updateDriverEmail] Error updating user email for ${driverUid}:`, error);
    throw new functions.https.HttpsError(
      "internal",
      error.message || "Failed to update driver email in Firebase Authentication."
    );
  }
});

/**
 * Helper to dispatch password reset email using Firebase Auth REST API (sendOobCode).
 * Uses the client Web API Key without leaking credentials.
 */
function sendPasswordResetEmailViaRest(email) {
  return new Promise((resolve, reject) => {
    const apiKey = process.env.WEB_API_KEY;
    if (!apiKey) {
      throw new Error("WEB_API_KEY is not configured");
    }
    const https = require("https");
    const postData = JSON.stringify({
      requestType: "PASSWORD_RESET",
      email: email
    });
    const req = https.request(`https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${apiKey}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(postData)
      }
    }, (res) => {
      let body = "";
      res.on("data", chunk => body += chunk);
      res.on("end", () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(true);
        } else {
          console.warn(`[sendPasswordResetEmailViaRest] Non-2xx response: ${res.statusCode} ${body}`);
          resolve(false);
        }
      });
    });
    req.on("error", (err) => {
      console.error("[sendPasswordResetEmailViaRest] Error:", err.message);
      resolve(false);
    });
    req.write(postData);
    req.end();
  });
}

/**
 * resolveAdminLogin
 * Secure server-side HTTPS Callable Cloud Function for Admin ID -> Firebase Auth Email Resolution.
 * Allows an unauthenticated client to resolve an Admin ID (e.g. 'admin26') strictly to the real
 * Firebase Auth email ('bkeshava636@gmail.com') without exposing Firestore profile records.
 */
exports.resolveAdminLogin = regionalFunctions.https.onCall(async (data, context) => {
  const adminId = data && data.adminId ? String(data.adminId).trim().toLowerCase() : null;
  if (!adminId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Administrator ID must be provided."
    );
  }

  // Look up admin profile in Firestore using Firebase Admin SDK
  let querySnapshot = await admin.firestore().collection("users")
    .where("customId", "==", adminId)
    .limit(1)
    .get();

  if (querySnapshot.empty && adminId.includes("@")) {
    querySnapshot = await admin.firestore().collection("users")
      .where("email", "==", adminId)
      .limit(1)
      .get();
  }

  if (querySnapshot.empty) {
    // If not found, return generic error or fallback internal email for direct Firebase Auth check
    if (adminId.includes("@")) {
      return { email: adminId };
    }
    return { email: `${adminId}@collegebus.internal` };
  }

  const userDoc = querySnapshot.docs[0];
  const userData = userDoc.data() || {};

  // Verify role is strictly ADMIN
  if (userData.role !== "ADMIN") {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Unauthorized: Account does not have ADMIN privileges."
    );
  }

  // Verify account is active
  if (userData.active !== true) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Account is deactivated. Contact Administrator."
    );
  }

  const realEmail = userData.email || (adminId.includes("@") ? adminId : `${adminId}@collegebus.internal`);
  return { email: String(realEmail).trim() };
});

/**
 * requestAdminPasswordReset
 * Secure server-side HTTPS Callable Cloud Function for Admin Password Recovery.
 * Resolves Admin ID to registered external email and triggers Firebase password reset email.
 * Always returns a generic confirmation response to prevent account enumeration.
 */
exports.requestAdminPasswordReset = regionalFunctions.https.onCall(async (data, context) => {
  const adminId = data && data.adminId ? String(data.adminId).trim().toLowerCase() : null;
  const genericResponse = {
    success: true,
    message: "If an active administrator account exists, a password reset email has been sent."
  };

  if (!adminId) {
    return genericResponse;
  }

  try {
    let querySnapshot = await admin.firestore().collection("users")
      .where("customId", "==", adminId)
      .limit(1)
      .get();

    if (querySnapshot.empty && adminId.includes("@")) {
      querySnapshot = await admin.firestore().collection("users")
        .where("email", "==", adminId)
        .limit(1)
        .get();
    }

    if (querySnapshot.empty) {
      console.log(`[requestAdminPasswordReset] No record found for identifier: ${adminId}`);
      return genericResponse;
    }

    const userData = querySnapshot.docs[0].data() || {};
    if (userData.role !== "ADMIN" || userData.active !== true) {
      console.log(`[requestAdminPasswordReset] Identifier ${adminId} is not an active ADMIN`);
      return genericResponse;
    }

    const targetEmail = userData.email ? String(userData.email).trim() : (adminId.includes("@") ? adminId : null);
    if (!targetEmail || targetEmail.endsWith("@collegebus.internal")) {
      console.log(`[requestAdminPasswordReset] Target email is internal or empty: ${targetEmail}`);
      return genericResponse;
    }

    await sendPasswordResetEmailViaRest(targetEmail);
    console.log(`[requestAdminPasswordReset] Dispatched password reset email for admin ${adminId}`);
    return genericResponse;
  } catch (err) {
    console.error(`[requestAdminPasswordReset] Error processing request for ${adminId}:`, err);
    return genericResponse;
  }
});

/**
 * resetDriverPassword
 * Secure server-side HTTPS Callable Cloud Function for Driver Password Reset.
 * Allows an authenticated active Administrator to reset a driver's Firebase Auth password
 * while strictly preserving their UID, Driver ID, name, bus assignment, and profile data.
 */
exports.resetDriverPassword = regionalFunctions.https.onCall(async (data, context) => {
  // 1. Verify caller authentication
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication is required to perform this action."
    );
  }

  const callerUid = context.auth.uid;

  // 2. Verify caller is an authorized active Administrator in Firestore
  const callerDoc = await admin.firestore().collection("users").doc(callerUid).get();
  if (!callerDoc.exists) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Caller authorization profile not found."
    );
  }

  const callerData = callerDoc.data() || {};
  if (callerData.role !== "ADMIN" || callerData.active !== true) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only active administrators are authorized to reset driver passwords."
    );
  }

  // 3. Validate input parameters
  const driverUid = data && data.driverUid ? String(data.driverUid).trim() : null;
  const newPassword = data && data.newPassword ? String(data.newPassword) : null;

  if (!driverUid) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Target driver UID must be provided."
    );
  }

  if (!newPassword || newPassword.length < 6) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Password must be at least 6 characters long."
    );
  }

  // 4. Fetch target driver profile from Firestore and verify role is DRIVER
  const targetDoc = await admin.firestore().collection("users").doc(driverUid).get();
  if (!targetDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "Target driver profile not found."
    );
  }

  const targetData = targetDoc.data() || {};
  if (targetData.role !== "DRIVER") {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Unauthorized: Target user is not a driver. Password reset can only be performed for driver accounts."
    );
  }

  // 5. Update password in Firebase Authentication
  try {
    await admin.auth().updateUser(driverUid, { password: newPassword });
    console.log(`[resetDriverPassword] Successfully reset password for driver ${driverUid} by admin ${callerUid}`);
    return {
      success: true,
      message: "Driver password reset successfully."
    };
  } catch (error) {
    console.error(`[resetDriverPassword] Error resetting password for driver ${driverUid}:`, error.message);
    throw new functions.https.HttpsError(
      "internal",
      error.message || "Failed to reset driver password in Firebase Authentication."
    );
  }
});

// =========================================================================
// STUDENT GPS SERVER-SIDE FALLBACK & MULTI-SOURCE FUSION ENGINE
// =========================================================================

const DRIVER_STALE_THRESHOLD_MS = 45000;
const DRIVER_MAX_ACCURACY_METERS = 40.0;
const STUDENT_STALE_THRESHOLD_MS = 30000;
const STUDENT_MAX_ACCURACY_METERS = 40.0;
const MAX_SPEED_MPS = 35.0; // ~126 km/h max plausible velocity
const CLUSTER_RADIUS_METERS = 50.0; // 50m consensus radius

/**
 * Calculates great-circle distance between two points in meters using the Haversine formula.
 */
function calculateDistanceMeters(lat1, lon1, lat2, lon2) {
  const r = 6371000.0;
  const dLat = (lat2 - lat1) * Math.PI / 180.0;
  const dLon = (lon2 - lon1) * Math.PI / 180.0;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180.0) * Math.cos(lat2 * Math.PI / 180.0) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return r * c;
}

/**
 * Enforces kinematic velocity constraints to reject impossible teleportation/jumps.
 */
function isKinematicallyPlausible(prevLat, prevLng, prevTimestamp, newLat, newLng, newTimestamp, maxSpeedMps = MAX_SPEED_MPS) {
  if (!prevLat || !prevLng || !prevTimestamp || prevLat === 0.0 || prevLng === 0.0 || prevTimestamp <= 0) {
    return true;
  }
  const elapsedSec = Math.max(1000, newTimestamp - prevTimestamp) / 1000.0;
  if (elapsedSec > 120.0) return true; // Reset jump check if gap > 2 minutes
  const distanceMeters = calculateDistanceMeters(prevLat, prevLng, newLat, newLng);
  const impliedSpeed = distanceMeters / elapsedSec;
  return impliedSpeed <= maxSpeedMps;
}

/**
 * Deduplicates multiple fixes from identical sourceIds and filters sybil/clone coordinates.
 */
function deduplicateStudentSources(sources) {
  if (!sources || sources.length <= 1) return sources || [];

  // 1. Group by sourceId and retain newest fix
  const byId = new Map();
  for (const s of sources) {
    const existing = byId.get(s.sourceId);
    if (!existing || (s.timestamp || 0) > (existing.timestamp || 0)) {
      byId.set(s.sourceId, s);
    }
  }
  const uniqueById = Array.from(byId.values());
  if (uniqueById.length <= 1) return uniqueById;

  // 2. Clone coordinate defense (< 1m, same speed, identical timestamp)
  const result = [];
  for (const s of uniqueById) {
    const isCloned = result.some(existing => {
      const dist = calculateDistanceMeters(existing.latitude, existing.longitude, s.latitude, s.longitude);
      return dist < 1.0 && Math.abs((existing.speed || 0) - (s.speed || 0)) < 0.01 && existing.timestamp === s.timestamp;
    });
    if (!isCloned) {
      result.push(s);
    }
  }
  return result;
}

/**
 * Spatial clustering using 50m radius and accuracy-weighted majority scoring.
 */
function findConsensusCluster(sources, previousLocation, radiusMeters = CLUSTER_RADIUS_METERS) {
  if (!sources || sources.length <= 1) return sources || [];

  const candidateClusters = sources.map(candidate => {
    return sources.filter(other => {
      return calculateDistanceMeters(
        candidate.latitude, candidate.longitude,
        other.latitude, other.longitude
      ) <= radiusMeters;
    });
  });

  let bestCluster = candidateClusters[0];
  let bestScore = -1.0;

  for (const cluster of candidateClusters) {
    const count = cluster.length;
    let weightSum = 0.0;
    for (const s of cluster) {
      const acc = Math.max(1.0, s.accuracy || 1.0);
      weightSum += 1.0 / (acc * acc);
    }

    let score = count * 1000.0 + weightSum;

    if (previousLocation && previousLocation.latitude && previousLocation.latitude !== 0.0 && previousLocation.lastUpdated > 0) {
      const centerLat = cluster.reduce((sum, s) => sum + s.latitude, 0) / count;
      const centerLng = cluster.reduce((sum, s) => sum + s.longitude, 0) / count;
      const distToPrev = calculateDistanceMeters(
        previousLocation.latitude, previousLocation.longitude,
        centerLat, centerLng
      );
      score += (2000.0 / (distToPrev + 10.0));
    }

    if (score > bestScore) {
      bestScore = score;
      bestCluster = cluster;
    }
  }

  return (bestCluster && bestCluster.length > 0) ? bestCluster : sources;
}

/**
 * Fuses clustered student coordinates using inverse-variance weighted centroid.
 */
function fuseCluster(cluster) {
  if (!cluster || cluster.length === 0) return null;
  if (cluster.length === 1) return cluster[0];

  let totalWeight = 0.0;
  let weightedLat = 0.0;
  let weightedLng = 0.0;
  let weightedSpeed = 0.0;
  let maxTimestamp = 0;

  for (const s of cluster) {
    const acc = Math.max(1.0, s.accuracy || 1.0);
    const weight = 1.0 / (acc * acc);
    totalWeight += weight;
    weightedLat += s.latitude * weight;
    weightedLng += s.longitude * weight;
    weightedSpeed += (s.speed || 0.0) * weight;
    if (s.timestamp > maxTimestamp) {
      maxTimestamp = s.timestamp;
    }
  }

  const fusedLat = weightedLat / totalWeight;
  const fusedLng = weightedLng / totalWeight;
  const fusedSpeed = weightedSpeed / totalWeight;
  const fusedAccuracy = Math.max(3.0, Math.sqrt(1.0 / totalWeight));

  let representative = cluster[0];
  for (const s of cluster) {
    const repOrder = (typeof representative.stopOrder === "number" && !isNaN(representative.stopOrder) && representative.stopOrder >= 0) ? representative.stopOrder : 999;
    const sOrder = (typeof s.stopOrder === "number" && !isNaN(s.stopOrder) && s.stopOrder >= 0) ? s.stopOrder : 999;
    if (sOrder < repOrder) {
      representative = s;
    } else if (sOrder === repOrder && (s.timestamp || 0) >= (representative.timestamp || 0)) {
      representative = s;
    }
  }

  return {
    busId: representative.busId,
    sourceId: `fused_cluster_${cluster.length}`,
    sourceType: "SECONDARY_STUDENT",
    latitude: fusedLat,
    longitude: fusedLng,
    accuracy: fusedAccuracy,
    speed: fusedSpeed,
    bearing: representative.bearing || 0.0,
    timestamp: maxTimestamp,
    active: true
  };
}

/**
 * Checks for duplicate writes within 3 seconds with identical coordinates and status.
 */
function isDuplicateWrite(prev, candidate) {
  if (!prev) return false;
  return (
    prev.latitude === candidate.latitude &&
    prev.longitude === candidate.longitude &&
    prev.trackingStatus === candidate.trackingStatus &&
    Math.abs(candidate.lastUpdated - prev.lastUpdated) < 3000
  );
}

/**
 * Core business logic for evaluating student GPS fallback writes.
 * Pure testable function taking change, context, and RTDB instance.
 */
async function processStudentFallbackWrite(change, context, rtdb = admin.database(), nowTime = null) {
  const busId = context && context.params ? context.params.busId : null;
  const sourceId = context && context.params ? context.params.sourceId : null;

  if (!busId) {
    console.log("[AUTO_FALLBACK] Missing busId parameter in context.");
    return { skipped: true, reason: "MISSING_BUS_ID" };
  }

  // If node was deleted, no action
  if (!change.after || !change.after.exists()) {
    console.log(`[AUTO_FALLBACK] Node deleted for bus ${busId} / source ${sourceId}. No action.`);
    return { skipped: true, reason: "NODE_DELETED" };
  }

  const now = nowTime !== null ? nowTime : Date.now();

  // 1. Fetch current live_location and location_sources for the affected bus
  const [liveLocSnapshot, sourcesSnapshot] = await Promise.all([
    rtdb.ref(`live_locations/${busId}`).once("value"),
    rtdb.ref(`location_sources/${busId}`).once("value")
  ]);

  const prevLiveLoc = liveLocSnapshot.exists() ? liveLocSnapshot.val() : null;
  const rawSourcesMap = sourcesSnapshot.exists() ? (sourcesSnapshot.val() || {}) : {};

  // 2. Evaluate Primary Driver Health
  // Driver is healthy if:
  // (a) Current live_location was updated by driver within 45s, or
  // (b) A PRIMARY_DRIVER source node in location_sources has a fresh (<45s), accurate (<=40m) non-zero fix
  let isDriverHealthy = false;

  if (prevLiveLoc &&
      prevLiveLoc.trackingStatus === "ACTIVE" &&
      prevLiveLoc.sourceType !== "SECONDARY_STUDENT_FALLBACK" &&
      prevLiveLoc.latitude !== 0.0 &&
      prevLiveLoc.longitude !== 0.0 &&
      inRange(now - (prevLiveLoc.lastUpdated || 0), 0, DRIVER_STALE_THRESHOLD_MS)) {
    isDriverHealthy = true;
  }

  // Check raw sources for an active PRIMARY_DRIVER fix
  for (const key of Object.keys(rawSourcesMap)) {
    const src = rawSourcesMap[key];
    if (src &&
        src.sourceType === "PRIMARY_DRIVER" &&
        src.active === true &&
        src.latitude !== 0.0 &&
        src.longitude !== 0.0 &&
        (src.accuracy || 999.0) <= DRIVER_MAX_ACCURACY_METERS &&
        inRange(now - (src.timestamp || 0), 0, DRIVER_STALE_THRESHOLD_MS)) {
      isDriverHealthy = true;
      break;
    }
  }

  // 3. Collect and validate student contributors
  const studentSources = [];
  for (const key of Object.keys(rawSourcesMap)) {
    const s = rawSourcesMap[key];
    if (s && s.sourceType === "SECONDARY_STUDENT") {
      studentSources.push(s);
    }
  }

  const validStudents = studentSources.filter(s => {
    return (
      s.active === true &&
      s.sourceType === "SECONDARY_STUDENT" &&
      String(s.busId || "").trim() === busId &&
      s.sourceId && String(s.sourceId).trim().length > 0 &&
      s.latitude !== 0.0 && s.longitude !== 0.0 &&
      s.latitude >= -90.0 && s.latitude <= 90.0 &&
      s.longitude >= -180.0 && s.longitude <= 180.0 &&
      (s.accuracy || 0.0) > 0.1 && (s.accuracy || 0.0) <= STUDENT_MAX_ACCURACY_METERS &&
      inRange(now - (s.timestamp || 0), 0, STUDENT_STALE_THRESHOLD_MS) &&
      (s.timestamp || 0) <= (now + 5000) &&
      (s.speed || 0.0) >= 0.0 && (s.speed || 0.0) <= MAX_SPEED_MPS &&
      isKinematicallyPlausible(
        prevLiveLoc ? prevLiveLoc.latitude : 0.0,
        prevLiveLoc ? prevLiveLoc.longitude : 0.0,
        prevLiveLoc ? (prevLiveLoc.lastUpdated || 0) : 0,
        s.latitude,
        s.longitude,
        s.timestamp || now,
        MAX_SPEED_MPS
      )
    );
  });

  // Deduplicate student sources
  const deduplicatedStudents = deduplicateStudentSources(validStudents);

  // Stop-by-stop sequential priority:
  // Sort candidate students by stopOrder ASC (Stop 1 -> Stop 2 -> Stop 3 -> Stop 4...)
  // Tie-breaker 1: GPS accuracy (lower/more precise is better)
  // Tie-breaker 2: Freshness (newer timestamp is better)
  deduplicatedStudents.sort((a, b) => {
    const orderA = (typeof a.stopOrder === "number" && !isNaN(a.stopOrder) && a.stopOrder >= 0) ? a.stopOrder : 999;
    const orderB = (typeof b.stopOrder === "number" && !isNaN(b.stopOrder) && b.stopOrder >= 0) ? b.stopOrder : 999;
    if (orderA !== orderB) {
      return orderA - orderB;
    }
    const accA = a.accuracy || 999.0;
    const accB = b.accuracy || 999.0;
    if (accA !== accB) {
      return accA - accB;
    }
    return (b.timestamp || 0) - (a.timestamp || 0);
  });

  // If driver is healthy, preserve driver primary authority
  if (isDriverHealthy) {
    console.log(`[AUTO_FALLBACK] busId=${busId} driverStatus=HEALTHY validStudentCount=${deduplicatedStudents.length} selectedSource=PRIMARY_DRIVER published=false reason=HEALTHY_DRIVER timestamp=${now}`);
    return {
      published: false,
      driverStatus: "HEALTHY",
      selectedSource: "PRIMARY_DRIVER",
      reason: "HEALTHY_DRIVER",
      validStudentCount: deduplicatedStudents.length
    };
  }

  // Driver is STALE or OFFLINE
  if (deduplicatedStudents.length === 0) {
    console.log(`[AUTO_FALLBACK] busId=${busId} driverStatus=STALE validStudentCount=0 selectedSource=NONE published=false reason=NO_VALID_STUDENT_SOURCES timestamp=${now}`);
    return {
      published: false,
      driverStatus: "STALE",
      selectedSource: "NONE",
      reason: "NO_VALID_STUDENT_SOURCES",
      validStudentCount: 0
    };
  }

  // 4. Consensus Clustering & Weighted Fusion
  const consensusCluster = (deduplicatedStudents.length === 1)
    ? deduplicatedStudents
    : findConsensusCluster(deduplicatedStudents, prevLiveLoc, CLUSTER_RADIUS_METERS);

  const fusedFix = (consensusCluster.length === 1)
    ? consensusCluster[0]
    : fuseCluster(consensusCluster);

  if (!fusedFix) {
    console.log(`[AUTO_FALLBACK] busId=${busId} driverStatus=STALE validStudentCount=${deduplicatedStudents.length} selectedSource=NONE published=false reason=FUSION_FAILED timestamp=${now}`);
    return {
      published: false,
      driverStatus: "STALE",
      selectedSource: "NONE",
      reason: "FUSION_FAILED",
      validStudentCount: deduplicatedStudents.length
    };
  }

  const candidateLiveLocation = {
    busId: busId,
    latitude: fusedFix.latitude,
    longitude: fusedFix.longitude,
    speed: fusedFix.speed || 0.0,
    bearing: fusedFix.bearing || 0.0,
    trackingStatus: "ACTIVE",
    mode: prevLiveLoc && prevLiveLoc.mode ? prevLiveLoc.mode : "AUTO",
    routeId: prevLiveLoc && prevLiveLoc.routeId ? prevLiveLoc.routeId : "",
    driverId: prevLiveLoc && prevLiveLoc.driverId ? prevLiveLoc.driverId : "",
    sourceType: "SECONDARY_STUDENT_FALLBACK",
    lastUpdated: fusedFix.timestamp || now
  };

  // 5. Duplicate Check
  if (isDuplicateWrite(prevLiveLoc, candidateLiveLocation)) {
    console.log(`[AUTO_FALLBACK] busId=${busId} driverStatus=STALE validStudentCount=${consensusCluster.length} selectedSource=SECONDARY_STUDENT_FALLBACK published=false reason=DUPLICATE_WRITE timestamp=${now}`);
    return {
      published: false,
      driverStatus: "STALE",
      selectedSource: "SECONDARY_STUDENT_FALLBACK",
      reason: "DUPLICATE_WRITE",
      validStudentCount: consensusCluster.length
    };
  }

  // 6. Write fallback to /live_locations/{busId}
  await rtdb.ref(`live_locations/${busId}`).set(candidateLiveLocation);

  console.log(`[AUTO_FALLBACK] busId=${busId} driverStatus=STALE validStudentCount=${consensusCluster.length} selectedSource=SECONDARY_STUDENT_FALLBACK published=true reason=DRIVER_STALE timestamp=${now}`);

  return {
    published: true,
    driverStatus: "STALE",
    selectedSource: "SECONDARY_STUDENT_FALLBACK",
    reason: "DRIVER_STALE",
    validStudentCount: consensusCluster.length,
    location: candidateLiveLocation
  };
}

/**
 * Helper to check whether a number is in range [min, max] inclusive.
 */
function inRange(val, min, max) {
  return typeof val === "number" && !isNaN(val) && val >= min && val <= max;
}

/**
 * RTDB Trigger on /location_sources/{busId}/{sourceId}
 * Reacts whenever a student (or driver) updates their contributor node in RTDB.
 */
exports.onLocationSourceWritten = regionalFunctions.database
  .ref("/location_sources/{busId}/{sourceId}")
  .onWrite(async (change, context) => {
    return processStudentFallbackWrite(change, context, admin.database());
  });

/**
 * =========================================================================
 * TEMPORARY DRIVER REPLACEMENT SYSTEM
 * =========================================================================
 */

/**
 * getEffectiveDriver
 * Resolves the authoritative effective driver for a given bus at currentTime.
 * If an active non-cancelled temporary assignment exists, returns the replacement driver.
 * Otherwise, returns the permanent driver.
 */
async function getEffectiveDriver(busId, currentTime = Date.now(), db = admin.firestore()) {
  const cleanBusId = String(busId || "").trim();
  if (!cleanBusId) return null;

  try {
    const assignmentsSnap = await db.collection("temporary_assignments")
      .where("busId", "==", cleanBusId)
      .where("cancelledAt", "==", null)
      .get();

    let activeTemporary = null;
    assignmentsSnap.forEach((doc) => {
      const data = doc.data() || {};
      if (data.status === "CANCELLED" || data.cancelledAt != null) return;
      const start = Number(data.startAt) || 0;
      const end = Number(data.endAt) || 0;
      if (currentTime >= start && currentTime < end) {
        activeTemporary = { id: doc.id, ...data };
      }
    });

    if (activeTemporary) {
      return {
        busId: cleanBusId,
        isTemporary: true,
        driverUid: activeTemporary.replacementDriverUid || "",
        driverId: activeTemporary.replacementDriverId || "",
        driverName: activeTemporary.replacementDriverName || "Driver",
        effectiveDriverUid: activeTemporary.replacementDriverUid || "",
        effectiveDriverName: activeTemporary.replacementDriverName || "Driver",
        driverPhone: activeTemporary.replacementDriverPhone || null,
        startAt: activeTemporary.startAt,
        endAt: activeTemporary.endAt,
        temporaryAssignmentId: activeTemporary.id,
        operatorEpoch: activeTemporary.operatorEpoch || activeTemporary.startAt
      };
    }

    // Fall back to permanent driver
    const busDoc = await db.collection("buses").doc(cleanBusId).get();
    const busData = busDoc.exists ? busDoc.data() : {};
    return {
      busId: cleanBusId,
      isTemporary: false,
      driverUid: busData.driverId || "",
      driverId: busData.driverId || "",
      driverName: busData.driverName || "Driver",
      effectiveDriverUid: busData.driverId || "",
      effectiveDriverName: busData.driverName || "Driver",
      driverPhone: busData.driverPhone || null,
      startAt: null,
      endAt: null,
      temporaryAssignmentId: null,
      operatorEpoch: 0
    };
  } catch (err) {
    console.error(`[getEffectiveDriver] Error resolving for bus ${cleanBusId}:`, err.message);
    return null;
  }
}

/**
 * Pure testable logic for creating a temporary assignment.
 */
async function processCreateTemporaryAssignment(data, callerUid, db = admin.firestore(), rtdb = admin.database()) {
  if (!callerUid) {
    throw new Error("Authentication required to create a temporary replacement.");
  }

  const busId = String(data.busId || "").trim();
  const replacementDriverUid = String(data.replacementDriverUid || "").trim();
  const startAt = Number(data.startAt) || 0;
  const endAt = Number(data.endAt) || 0;

  if (!busId) throw new Error("Bus ID is required.");
  if (!replacementDriverUid) throw new Error("Replacement driver is required.");
  if (startAt <= 0 || endAt <= 0 || startAt >= endAt) {
    throw new Error("Invalid time window: startAt must be before endAt.");
  }
  if (endAt <= Date.now()) {
    throw new Error("End time must be in the future.");
  }

  // 1. Verify caller in Firestore
  const callerDoc = await db.collection("users").doc(callerUid).get();
  if (!callerDoc.exists) throw new Error("Caller profile not found.");
  const caller = callerDoc.data() || {};
  if (caller.active !== true) throw new Error("Caller account is deactivated.");

  const isCallerAdmin = caller.role === "ADMIN";
  const isCallerDriver = caller.role === "DRIVER";

  if (!isCallerAdmin && !isCallerDriver) {
    throw new Error("Only active Drivers and Administrators can create temporary replacements.");
  }

  // 2. Driver-specific authorization: must be permanently assigned to this bus
  if (isCallerDriver && caller.assignedBusId !== busId) {
    throw new Error(`Permission denied: Driver can only create replacements for their permanently assigned bus (${caller.assignedBusId}).`);
  }

  // 3. Prevent replacing with self
  if (callerUid === replacementDriverUid) {
    throw new Error("A driver cannot select themselves as a temporary replacement.");
  }

  // 4. Verify replacement driver exists, is active, and is a DRIVER
  const replacementDoc = await db.collection("users").doc(replacementDriverUid).get();
  if (!replacementDoc.exists) throw new Error("Selected replacement driver does not exist.");
  const replacement = replacementDoc.data() || {};
  if (replacement.active !== true) {
    throw new Error("Selected replacement driver account is inactive.");
  }
  if (replacement.role !== "DRIVER") {
    throw new Error("Replacement operator must have the DRIVER role (cannot assign Administrator).");
  }

  // 5. Fetch bus details for permanent driver info
  const busDoc = await db.collection("buses").doc(busId).get();
  const busData = busDoc.exists ? busDoc.data() : {};
  const permanentDriverUid = busData.driverId || (isCallerDriver ? callerUid : "");
  const permanentDriverName = busData.driverName || (isCallerDriver ? caller.name : "Driver");
  const permanentDriverId = caller.customId || permanentDriverUid;

  if (replacementDriverUid === permanentDriverUid) {
    throw new Error("Replacement driver cannot be the same as the permanent driver.");
  }

  // 6. Conflict checks (Overlap protection)
  // Check 6a: Overlap on the same bus
  const busAssignmentsSnap = await db.collection("temporary_assignments")
    .where("busId", "==", busId)
    .where("cancelledAt", "==", null)
    .get();

  busAssignmentsSnap.forEach((doc) => {
    const d = doc.data() || {};
    const eStart = Number(d.startAt) || 0;
    const eEnd = Number(d.endAt) || 0;
    if (startAt < eEnd && endAt > eStart) {
      throw new Error(`Conflict: A temporary replacement already exists for ${busId} during this period.`);
    }
  });

  // Check 6b: Replacement driver operating another bus at the same time
  const driverAssignmentsSnap = await db.collection("temporary_assignments")
    .where("replacementDriverUid", "==", replacementDriverUid)
    .where("cancelledAt", "==", null)
    .get();

  driverAssignmentsSnap.forEach((doc) => {
    const d = doc.data() || {};
    const eStart = Number(d.startAt) || 0;
    const eEnd = Number(d.endAt) || 0;
    if (startAt < eEnd && endAt > eStart) {
      throw new Error(`Conflict: Replacement driver ${replacement.name || replacementDriverUid} is already operating bus ${d.busId} during this period.`);
    }
  });

  // 7. Create assignment record
  const assignmentRef = db.collection("temporary_assignments").doc();
  const assignmentId = assignmentRef.id;
  const now = Date.now();

  const reason = String(data.reason || "").trim();

  const assignmentData = {
    assignmentId,
    busId,
    permanentDriverId: String(permanentDriverId || "").trim(),
    permanentDriverUid: String(permanentDriverUid || "").trim(),
    permanentDriverName: String(permanentDriverName || "").trim(),
    replacementDriverId: String(replacement.customId || replacementDriverUid).trim(),
    replacementDriverUid: String(replacementDriverUid).trim(),
    replacementDriverName: String(replacement.name || "Driver").trim(),
    replacementDriverPhone: replacement.phoneNumber || null,
    reason,
    startAt,
    endAt,
    createdByUid: callerUid,
    createdByRole: caller.role,
    createdAt: now,
    updatedAt: now,
    cancelledAt: null,
    cancelledByUid: null,
    cancelledByRole: null,
    status: "ACTIVE",
    operatorEpoch: now
  };

  await assignmentRef.set(assignmentData);

  // Write permanent driver replacement history
  try {
    await db.collection("driver_replacement_history").doc(assignmentId).set({
      historyId: assignmentId,
      assignmentId,
      busId,
      previousDriverUid: String(permanentDriverUid || "").trim(),
      previousDriverName: String(permanentDriverName || "").trim(),
      replacementDriverUid: String(replacementDriverUid).trim(),
      replacementDriverName: String(replacement.name || "Driver").trim(),
      reason,
      startAt,
      endAt,
      effectiveFrom: startAt,
      effectiveUntil: endAt,
      changedByUid: callerUid,
      changedByName: String(caller.name || caller.email || "Admin").trim(),
      status: "ACTIVE",
      createdAt: now,
      cleared: false
    });
  } catch (err) {
    console.warn(`[processCreateTemporaryAssignment] Warning writing driver_replacement_history:`, err.message);
  }

  // 8. Synchronize Realtime Database /bus_operators/{busId}
  if (rtdb) {
    try {
      await rtdb.ref(`bus_operators/${busId}`).set({
        busId,
        permanentDriverUid: assignmentData.permanentDriverUid,
        temporaryDriverUid: assignmentData.replacementDriverUid,
        hasTemporary: true,
        startAt: assignmentData.startAt,
        endAt: assignmentData.endAt,
        temporaryAssignmentId: assignmentId,
        operatorEpoch: assignmentData.operatorEpoch
      });
    } catch (e) {
      console.warn(`[processCreateTemporaryAssignment] Warning syncing RTDB bus_operators/${busId}:`, e.message);
    }
  }

  console.log(`[TEMPORARY_ASSIGNMENT_CREATED] id=${assignmentId} bus=${busId} perm=${permanentDriverUid} temp=${replacementDriverUid} start=${startAt} end=${endAt}`);
  return { success: true, assignment: assignmentData };
}

/**
 * Pure testable logic for cancelling a temporary assignment.
 */
async function processCancelTemporaryAssignment(assignmentId, callerUid, db = admin.firestore(), rtdb = admin.database()) {
  if (!callerUid) throw new Error("Authentication required.");
  if (!assignmentId) throw new Error("Assignment ID is required.");

  const docRef = db.collection("temporary_assignments").doc(assignmentId);
  const doc = await docRef.get();
  if (!doc.exists) throw new Error("Assignment not found.");

  const data = doc.data() || {};
  if (data.status === "CANCELLED" || data.cancelledAt != null) {
    return { success: true, alreadyCancelled: true, assignmentId };
  }

  const callerDoc = await db.collection("users").doc(callerUid).get();
  if (!callerDoc.exists) throw new Error("Caller profile not found.");
  const caller = callerDoc.data() || {};
  if (caller.active !== true) throw new Error("Caller account is deactivated.");

  const isCallerAdmin = caller.role === "ADMIN";
  const isCallerDriver = caller.role === "DRIVER";

  if (!isCallerAdmin && !isCallerDriver) {
    throw new Error("Permission denied: Only Administrators and Drivers can cancel temporary replacements.");
  }

  if (isCallerDriver) {
    // 1. The driver must be the permanent driver of the affected bus
    if (data.permanentDriverUid !== callerUid) {
      throw new Error("Permission denied: Only the permanent driver of this bus is authorized to cancel this replacement.");
    }
    // 2. Driver cannot cancel replacements for another bus
    if (caller.assignedBusId !== data.busId) {
      throw new Error("Permission denied: Driver cannot cancel replacements for another bus.");
    }
  }

  const now = Date.now();
  const cancelledByRole = isCallerAdmin ? "ADMIN" : "DRIVER";

  await docRef.update({
    cancelledAt: now,
    status: "CANCELLED",
    cancelledByUid: callerUid,
    cancelledByRole: cancelledByRole,
    updatedAt: now
  });

  try {
    await db.collection("driver_replacement_history").doc(assignmentId).update({
      status: "CANCELLED",
      cancelledAt: now,
      cancelledByUid: callerUid
    });
  } catch (err) {
    console.warn(`[processCancelTemporaryAssignment] Warning updating driver_replacement_history:`, err.message);
  }

  // Synchronize RTDB /bus_operators/{busId}
  // Immediately restore permanent driver as effective operator, revoke temporary driver GPS write permission,
  // restore permanent driver GPS write permission, and bump epoch.
  if (rtdb && data.busId) {
    try {
      await rtdb.ref(`bus_operators/${data.busId}`).update({
        hasTemporary: false,
        temporaryDriverUid: null,
        cancelledAt: now,
        operatorEpoch: now
      });
    } catch (e) {
      console.warn(`[processCancelTemporaryAssignment] Warning clearing RTDB bus_operators/${data.busId}:`, e.message);
    }
  }

  console.log(`[TEMPORARY_ASSIGNMENT_CANCELLED] id=${assignmentId} bus=${data.busId} cancelledBy=${callerUid} role=${cancelledByRole}`);
  return { success: true, assignmentId };
}

/**
 * HTTPS Callable Cloud Function for creating a temporary driver replacement.
 */
exports.createTemporaryAssignment = regionalFunctions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
  }
  try {
    return await processCreateTemporaryAssignment(data, context.auth.uid, admin.firestore(), admin.database());
  } catch (err) {
    console.error("[createTemporaryAssignment] Error:", err.message);
    throw new functions.https.HttpsError("invalid-argument", err.message);
  }
});

/**
 * HTTPS Callable Cloud Function for cancelling a temporary driver replacement.
 */
exports.cancelTemporaryAssignment = regionalFunctions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
  }
  const assignmentId = data && data.assignmentId ? String(data.assignmentId).trim() : null;
  try {
    return await processCancelTemporaryAssignment(assignmentId, context.auth.uid, admin.firestore(), admin.database());
  } catch (err) {
    console.error("[cancelTemporaryAssignment] Error:", err.message);
    throw new functions.https.HttpsError("invalid-argument", err.message);
  }
});

/**
 * HTTPS Callable Cloud Function for resolving the authoritative effective driver.
 */
exports.getEffectiveDriver = regionalFunctions.https.onCall(async (data, context) => {
  const busId = data && data.busId ? String(data.busId).trim() : null;
  const currentTime = data && data.currentTime ? Number(data.currentTime) : Date.now();
  if (!busId) {
    throw new functions.https.HttpsError("invalid-argument", "Bus ID is required.");
  }
  return await getEffectiveDriver(busId, currentTime, admin.firestore());
});

/**
 * Firestore Trigger on temporary_assignments/{assignmentId}
 * Keeps RTDB /bus_operators/{busId} synchronized if modified directly via Admin SDK or Firestore.
 */
exports.onTemporaryAssignmentWritten = regionalFunctions.firestore
  .document("temporary_assignments/{assignmentId}")
  .onWrite(async (change, context) => {
    const after = change.after.exists ? change.after.data() : null;
    const before = change.before.exists ? change.before.data() : null;
    const busId = (after && after.busId) || (before && before.busId);
    if (!busId) return null;

    const rtdb = admin.database();
    if (!after || after.cancelledAt != null || after.status === "CANCELLED") {
      // Deleted or cancelled
      return rtdb.ref(`bus_operators/${busId}`).update({
        hasTemporary: false,
        temporaryDriverUid: null,
        cancelledAt: Date.now(),
        operatorEpoch: Date.now()
      });
    } else {
      return rtdb.ref(`bus_operators/${busId}`).set({
        busId,
        permanentDriverUid: after.permanentDriverUid || "",
        temporaryDriverUid: after.replacementDriverUid || "",
        hasTemporary: true,
        startAt: after.startAt || 0,
        endAt: after.endAt || 0,
        temporaryAssignmentId: after.assignmentId || context.params.assignmentId,
        operatorEpoch: after.operatorEpoch || Date.now()
      });
    }
  });

/**
 * Core business logic for processing and dispatching Announcement notifications.
 */
async function processAnnouncementWrite(change, context, db = admin.firestore(), messaging = admin.messaging()) {
  const announcementId = context && context.params ? context.params.announcementId : "unknown";
  if (!change.after || !change.after.exists) {
    return null;
  }

  const data = change.after.data() || {};
  const isPublished = data.isPublished === true || data.status === "PUBLISHED" || data.status === "ACTIVE";
  if (!isPublished) {
    return { skipped: true, reason: "NOT_PUBLISHED" };
  }

  if (data.notified === true) {
    return { skipped: true, reason: "ALREADY_NOTIFIED" };
  }

  const titleEn = data.titleEn || data.title || "Campus Announcement";
  const titleKn = data.titleKn || titleEn;
  const contentEn = data.contentEn || data.content || data.body || "";
  const contentKn = data.contentKn || contentEn;
  const priority = (data.priority || "NORMAL").toUpperCase();
  const category = (data.category || "GENERAL").toUpperCase();
  const targetAudience = (data.targetAudience || "ALL").toUpperCase();
  const targetBusId = data.targetBusId ? sanitizeTopic(data.targetBusId) : "";
  const targetRouteId = data.targetRouteId ? sanitizeTopic(data.targetRouteId) : "";

  const topicsToNotify = new Set();
  if (targetBusId) {
    topicsToNotify.add(`announcements_bus_${targetBusId}`);
  } else if (targetRouteId) {
    topicsToNotify.add(`announcements_route_${targetRouteId}`);
  } else {
    if (targetAudience === "DRIVERS_ONLY" || targetAudience === "DRIVERS") {
      topicsToNotify.add("announcements_drivers");
    } else if (targetAudience === "STUDENTS_ONLY" || targetAudience === "STUDENTS") {
      topicsToNotify.add("announcements_students");
    } else {
      topicsToNotify.add("announcements_all");
      topicsToNotify.add("announcements_students");
    }
  }

  const sendPromises = Array.from(topicsToNotify).map(async (topic) => {
    const payload = {
      topic: topic,
      notification: {
        title: titleEn,
        body: contentEn
      },
      data: {
        notificationType: "ANNOUNCEMENT",
        announcementId: announcementId,
        EXTRA_ANNOUNCEMENT_ID: announcementId,
        title: titleEn,
        titleKn: titleKn,
        content: contentEn,
        contentKn: contentKn,
        priority: priority,
        category: category,
        targetAudience: targetAudience,
        targetBusId: targetBusId,
        targetRouteId: targetRouteId,
        timestamp: String(Date.now())
      },
      android: {
        priority: priority === "URGENT" ? "high" : "normal",
        notification: {
          channelId: "announcements_channel",
          sound: "default"
        }
      }
    };
    try {
      await messaging.send(payload);
      console.log(`[onAnnouncementWritten] Dispatched announcement ${announcementId} to topic ${topic}`);
    } catch (e) {
      console.warn(`[onAnnouncementWritten] Error sending to topic ${topic}:`, e.message);
    }
  });

  await Promise.all(sendPromises);

  // Mark announcement document as notified
  try {
    await change.after.ref.update({
      notified: true,
      notifiedAt: Date.now()
    });
  } catch (e) {
    console.warn(`[onAnnouncementWritten] Error marking announcement ${announcementId} notified:`, e.message);
  }

  return { success: true, announcementId, topics: Array.from(topicsToNotify) };
}

/**
 * Firestore Trigger for announcements collection
 */
exports.onAnnouncementWritten = regionalFunctions.firestore
  .document("announcements/{announcementId}")
  .onWrite(async (change, context) => {
    return processAnnouncementWrite(change, context, admin.firestore(), admin.messaging());
  });

/**
 * Core business logic for processing and dispatching Bus Started / Online notifications.
 * Triggered when a bus node in RTDB /live_locations/{busId} transitions to ACTIVE.
 */
async function processBusStartNotification(change, context, db = admin.database(), messaging = admin.messaging(), cooldownMs = 30 * 60 * 1000) {
  const busId = context && context.params ? context.params.busId : "unknown";

  if (!change.after || !change.after.exists()) {
    return { skipped: true, reason: "DELETED" };
  }

  const after = change.after.val() || {};
  const before = change.before && change.before.exists() ? change.before.val() : null;

  const isNowActive = after.trackingStatus === "ACTIVE";
  if (!isNowActive) {
    return { skipped: true, reason: "NOT_ACTIVE" };
  }

  // Validate latitude and longitude coordinates
  const lat = Number(after.latitude || 0);
  const lon = Number(after.longitude || 0);
  if (isNaN(lat) || isNaN(lon) || (lat === 0 && lon === 0) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
    return { skipped: true, reason: "INVALID_COORDINATES" };
  }

  const now = Date.now();

  // Determine if the bus was actively streaming valid coordinates before.
  // A bus was actively streaming if:
  // 1. `before` exists and had trackingStatus === "ACTIVE"
  // 2. `before` coordinates were valid (not 0,0 and in physical range)
  // 3. `before` was not stale (e.g. updated within 15 minutes)
  let wasActivelyStreaming = false;
  if (before && before.trackingStatus === "ACTIVE") {
    const beforeLat = Number(before.latitude || 0);
    const beforeLon = Number(before.longitude || 0);
    const hasBeforeValidCoords = !isNaN(beforeLat) && !isNaN(beforeLon) &&
      (beforeLat !== 0 || beforeLon !== 0) &&
      Math.abs(beforeLat) <= 90 && Math.abs(beforeLon) <= 180;

    const beforeTime = Number(before.lastUpdated || before.timestamp || 0);
    const isBeforeStale = beforeTime > 0 && (now - beforeTime) > 15 * 60 * 1000;

    if (hasBeforeValidCoords && !isBeforeStale) {
      wasActivelyStreaming = true;
    }
  }

  // If the bus was already actively streaming valid coordinates, this is an ongoing stream update; do not spam
  if (wasActivelyStreaming) {
    return { skipped: true, reason: "ALREADY_ACTIVE" };
  }

  const cleanBusId = sanitizeTopic(busId);

  // Check cooldown in RTDB /bus_notifications/{cleanBusId}/lastStartedAt
  const notifRef = db.ref(`/bus_notifications/${cleanBusId}/lastStartedAt`);
  const snap = await notifRef.once("value");
  const lastStartedAt = Number(snap.val() || 0);

  if (now - lastStartedAt < cooldownMs) {
    console.log(`[onLiveLocationWritten] Cooldown active for ${cleanBusId}. Last notified ${Math.round((now - lastStartedAt) / 1000)}s ago.`);
    return { skipped: true, reason: "COOLDOWN_ACTIVE" };
  }

  // Update lastStartedAt timestamp
  await notifRef.set(now);

  const topic = `bus_start_${cleanBusId}`;
  const busDisplayName = after.busNumber || busId;

  const message = {
    topic: topic,
    notification: {
      title: "🚌 Bus Started",
      body: `${busDisplayName} is now en route and tracking live.`
    },
    data: {
      notificationType: "BUS_STARTED",
      busId: String(busId),
      busNumber: String(busDisplayName),
      timestamp: String(now)
    },
    android: {
      priority: "high",
      notification: {
        channelId: "bus_alerts_channel",
        priority: "high",
        defaultSound: true,
        defaultVibrateTimings: true
      }
    }
  };

  try {
    await messaging.send(message);
    console.log(`[onLiveLocationWritten] Sent bus start notification to topic ${topic} for ${busId}`);
    return { success: true, topic, busId };
  } catch (err) {
    console.error(`[onLiveLocationWritten] Error sending start notification to topic ${topic}:`, err.message);
    return { error: err.message, topic, busId };
  }
}

/**
 * RTDB Trigger on /live_locations/{busId}
 * Reacts whenever a bus transitions to ACTIVE status with valid coordinates.
 */
exports.onLiveLocationWritten = regionalFunctions.database
  .ref("/live_locations/{busId}")
  .onWrite(async (change, context) => {
    return processBusStartNotification(change, context, admin.database(), admin.messaging());
  });

// Exports for unit testing
exports.sanitizeTopic = sanitizeTopic;
exports.buildBusStopTopic = buildBusStopTopic;
exports.processBusSwapWrite = processBusSwapWrite;
exports.processAnnouncementWrite = processAnnouncementWrite;
exports.processBusStartNotification = processBusStartNotification;
exports.sendPasswordResetEmailViaRest = sendPasswordResetEmailViaRest;
exports.calculateDistanceMeters = calculateDistanceMeters;
exports.isKinematicallyPlausible = isKinematicallyPlausible;
exports.deduplicateStudentSources = deduplicateStudentSources;
exports.findConsensusCluster = findConsensusCluster;
exports.fuseCluster = fuseCluster;
exports.isDuplicateWrite = isDuplicateWrite;
exports.processStudentFallbackWrite = processStudentFallbackWrite;
exports.DRIVER_STALE_THRESHOLD_MS = DRIVER_STALE_THRESHOLD_MS;
exports.STUDENT_STALE_THRESHOLD_MS = STUDENT_STALE_THRESHOLD_MS;
exports.getEffectiveDriver = getEffectiveDriver;
exports.processCreateTemporaryAssignment = processCreateTemporaryAssignment;
exports.processCancelTemporaryAssignment = processCancelTemporaryAssignment;

// =========================================================================
// STUDENT STOP-COORDINATE CROWDSOURCING & ADMINISTRATOR REVIEW (Milestone 3)
// =========================================================================

/**
 * Core business logic for submitting student stop coordinates.
 */
async function processSubmitStopCoordinates(data, context, db = admin.firestore()) {
  const routeId = data && typeof data.routeId === "string" ? data.routeId.trim() : "";
  const stopId = data && typeof data.stopId === "string" ? data.stopId.trim() : "";
  const proposedLat = Number(data && data.proposedLatitude);
  const proposedLng = Number(data && data.proposedLongitude);
  const source = data && data.source === "GPS" ? "GPS" : "MANUAL";
  const accuracyMeters = data && typeof data.accuracyMeters === "number" && !isNaN(data.accuracyMeters) ? data.accuracyMeters : null;
  const studentNotes = data && typeof data.studentNotes === "string" ? data.studentNotes.trim().slice(0, 500) : null;

  if (!routeId || !stopId) {
    throw new functions.https.HttpsError("invalid-argument", "routeId and stopId are required.");
  }

  if (isNaN(proposedLat) || isNaN(proposedLng) || proposedLat < -90 || proposedLat > 90 || proposedLng < -180 || proposedLng > 180) {
    throw new functions.https.HttpsError("invalid-argument", "Valid geographic coordinates are required (lat [-90, 90], lng [-180, 180]).");
  }

  if (proposedLat === 0 && proposedLng === 0) {
    throw new functions.https.HttpsError("invalid-argument", "Coordinates (0, 0) cannot be submitted as a bus stop location.");
  }

  if (source === "GPS" && accuracyMeters !== null && accuracyMeters < 0) {
    throw new functions.https.HttpsError("invalid-argument", "GPS accuracy must be a positive number in metres.");
  }

  // Verify route and stop exist in Firestore
  const routeRef = db.collection("routes").doc(routeId);
  const routeDoc = await routeRef.get();
  if (!routeDoc.exists) {
    throw new functions.https.HttpsError("not-found", `Route ${routeId} does not exist.`);
  }

  const routeData = routeDoc.data() || {};
  const stops = Array.isArray(routeData.stops) ? routeData.stops : [];
  const targetStop = stops.find((s) => s && s.stopId === stopId);
  if (!targetStop) {
    throw new functions.https.HttpsError("not-found", `Stop ${stopId} not found in route ${routeId}.`);
  }

  // Duplicate / Spam Protection: Check for recent pending submission for the exact same stop within last 10 minutes
  const tenMinutesAgo = Date.now() - (10 * 60 * 1000);
  try {
    const recentSubmissionsQuery = await db.collection("stop_location_submissions")
      .where("routeId", "==", routeId)
      .where("stopId", "==", stopId)
      .where("status", "==", "PENDING")
      .get();

    let isSpamDuplicate = false;
    recentSubmissionsQuery.forEach((doc) => {
      const sData = doc.data() || {};
      const subTime = sData.submittedAt ? (sData.submittedAt.toMillis ? sData.submittedAt.toMillis() : Number(sData.submittedAt)) : 0;
      if (subTime > tenMinutesAgo) {
        const latDiff = Math.abs(sData.proposedLatitude - proposedLat);
        const lngDiff = Math.abs(sData.proposedLongitude - proposedLng);
        if (latDiff < 0.00005 && lngDiff < 0.00005) {
          isSpamDuplicate = true;
        }
      }
    });

    if (isSpamDuplicate) {
      throw new functions.https.HttpsError(
        "already-exists",
        "An identical pending coordinate submission for this bus stop was recently received and is awaiting administrator review."
      );
    }
  } catch (err) {
    if (err instanceof functions.https.HttpsError) throw err;
    console.warn("[processSubmitStopCoordinates] Warning querying recent submissions:", err.message);
  }

  // Prepare submission document - sanitize and force status to PENDING
  const submissionData = {
    routeId,
    routeName: String(routeData.name || routeId),
    stopId,
    stopName: String(targetStop.name || stopId),
    stopOrder: typeof targetStop.order === "number" ? targetStop.order : 0,
    proposedLatitude: proposedLat,
    proposedLongitude: proposedLng,
    accuracyMeters: accuracyMeters,
    source,
    status: "PENDING",
    submittedAt: admin.firestore.FieldValue.serverTimestamp(),
    existingLatitude: typeof targetStop.latitude === "number" ? targetStop.latitude : null,
    existingLongitude: typeof targetStop.longitude === "number" ? targetStop.longitude : null,
    studentNotes: studentNotes || null,
    reviewedBy: null,
    reviewedAt: null,
    reviewNotes: null,
    finalLatitude: null,
    finalLongitude: null,
  };

  const docRef = await db.collection("stop_location_submissions").add(submissionData);
  console.log(`[submitStopCoordinates] Submitted proposal ${docRef.id} for route ${routeId}, stop ${stopId}`);
  return { success: true, submissionId: docRef.id };
}

/**
 * Core business logic for reviewing (approving or rejecting) student stop coordinates.
 */
async function processReviewStopCoordinateSubmission(data, context, db = admin.firestore()) {
  // 1. Verify caller authentication
  if (!context || !context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
  }

  const callerUid = context.auth.uid;

  // 2. Verify caller is active Administrator
  const callerDoc = await db.collection("users").doc(callerUid).get();
  if (!callerDoc.exists) {
    throw new functions.https.HttpsError("permission-denied", "Caller profile not found.");
  }
  const callerData = callerDoc.data() || {};
  if (callerData.role !== "ADMIN" || callerData.active !== true) {
    throw new functions.https.HttpsError("permission-denied", "Only active administrators can review submissions.");
  }

  // 3. Validate input parameters
  const submissionId = data && typeof data.submissionId === "string" ? data.submissionId.trim() : "";
  const action = data && (data.action === "APPROVE" || data.action === "REJECT") ? data.action : null;
  const reviewNotes = data && typeof data.reviewNotes === "string" ? data.reviewNotes.trim().slice(0, 1000) : null;

  if (!submissionId || !action) {
    throw new functions.https.HttpsError("invalid-argument", "Valid submissionId and action (APPROVE or REJECT) are required.");
  }

  const submissionRef = db.collection("stop_location_submissions").doc(submissionId);

  // 4. Run Transaction
  return await db.runTransaction(async (transaction) => {
    const subDoc = await transaction.get(submissionRef);
    if (!subDoc.exists) {
      throw new functions.https.HttpsError("not-found", `Submission ${submissionId} not found.`);
    }

    const subData = subDoc.data() || {};
    if (subData.status !== "PENDING") {
      throw new functions.https.HttpsError("failed-precondition", `Submission has already been reviewed (${subData.status}).`);
    }

    const routeId = subData.routeId;
    const stopId = subData.stopId;
    const routeRef = db.collection("routes").doc(routeId);
    const routeDoc = await transaction.get(routeRef);

    if (!routeDoc.exists) {
      throw new functions.https.HttpsError("not-found", `Route ${routeId} not found.`);
    }

    const routeData = routeDoc.data() || {};
    const stops = Array.isArray(routeData.stops) ? routeData.stops : [];
    const stopIndex = stops.findIndex((s) => s && s.stopId === stopId);

    if (stopIndex === -1) {
      throw new functions.https.HttpsError("not-found", `Stop ${stopId} not found in route ${routeId}.`);
    }

    const now = admin.firestore.FieldValue.serverTimestamp();

    if (action === "APPROVE") {
      const adjustedLat = data && typeof data.adjustedLatitude === "number" && !isNaN(data.adjustedLatitude)
        ? data.adjustedLatitude
        : subData.proposedLatitude;
      const adjustedLng = data && typeof data.adjustedLongitude === "number" && !isNaN(data.adjustedLongitude)
        ? data.adjustedLongitude
        : subData.proposedLongitude;

      if (adjustedLat < -90 || adjustedLat > 90 || adjustedLng < -180 || adjustedLng > 180 || (adjustedLat === 0 && adjustedLng === 0)) {
        throw new functions.https.HttpsError("invalid-argument", "Invalid approval coordinates.");
      }

      // Update matching stop in array, strictly preserving all other properties and order
      const updatedStops = stops.map((item, idx) => {
        if (idx === stopIndex) {
          return {
            ...item,
            latitude: adjustedLat,
            longitude: adjustedLng,
            visibleOnMap: true,
          };
        }
        return item;
      });

      // Write updated stops array to route
      transaction.update(routeRef, {
        stops: updatedStops,
        updatedAt: now,
      });

      // Update submission status to APPROVED
      transaction.update(submissionRef, {
        status: "APPROVED",
        finalLatitude: adjustedLat,
        finalLongitude: adjustedLng,
        reviewedBy: callerUid,
        reviewedAt: now,
        reviewNotes: reviewNotes || null,
      });

      console.log(`[reviewStopCoordinateSubmission] APPROVED submission ${submissionId} for route ${routeId}, stop ${stopId} (${adjustedLat}, ${adjustedLng})`);
      return { success: true, action: "APPROVED", submissionId, routeId, stopId };
    } else {
      // REJECT action: never touch route coordinates
      transaction.update(submissionRef, {
        status: "REJECTED",
        reviewedBy: callerUid,
        reviewedAt: now,
        reviewNotes: reviewNotes || null,
      });

      console.log(`[reviewStopCoordinateSubmission] REJECTED submission ${submissionId} for route ${routeId}, stop ${stopId}`);
      return { success: true, action: "REJECTED", submissionId, routeId, stopId };
    }
  });
}

// HTTPS Callable exports
exports.submitStopCoordinates = regionalFunctions.https.onCall(async (data, context) => {
  return processSubmitStopCoordinates(data, context);
});

exports.reviewStopCoordinateSubmission = regionalFunctions.https.onCall(async (data, context) => {
  return processReviewStopCoordinateSubmission(data, context);
});

exports.processSubmitStopCoordinates = processSubmitStopCoordinates;
exports.processReviewStopCoordinateSubmission = processReviewStopCoordinateSubmission;



