const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { FieldValue } = require("firebase-admin/firestore");
const crypto = require("crypto");

if (!admin.apps.length) {
  admin.initializeApp();
}

const regionalFunctions = functions.region("asia-south1");
const ENFORCE_APP_CHECK = process.env.ENFORCE_APP_CHECK !== "false";

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
        cancellationNotifiedAt: FieldValue.serverTimestamp()
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
      notifiedStopIds: isReplacementChanged ? successfullySentStops : FieldValue.arrayUnion(...successfullySentStops),
      notificationSentAt: FieldValue.serverTimestamp()
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
 * Hardened with App Check and authoritative Firestore / RTDB unassignment cleanup.
 */
exports.deleteDriverAccount = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processDeleteDriverAccount(data, context);
  });

/**
 * updateDriverEmail
 * Secure server-side HTTPS Callable Cloud Function for Driver ID / Login Email Update.
 * Allows an authenticated active Administrator to update a driver's Firebase Auth login email
 * while strictly preserving their UID, password, and account history.
 */
exports.updateDriverEmail = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
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
 * Evaluates whether an IP address belongs to loopback, private RFC1918, link-local, or documentation ranges.
 */
function isPrivateOrInternalIp(ip) {
  if (!ip || typeof ip !== "string") return true;
  const clean = ip.replace(/^::ffff:/, "").trim().toLowerCase();
  if (clean === "127.0.0.1" || clean === "::1" || clean === "localhost" || clean === "0.0.0.0") return true;
  // RFC 1918 (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16) and RFC 3927 (169.254.0.0/16 link-local)
  if (/^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[0-1])\.|169\.254\.)/.test(clean)) return true;
  // Unique local IPv6 (fc00::/7) or link-local (fe80::/10)
  if (/^(fc|fd|fe80)/i.test(clean)) return true;
  return false;
}

/**
 * Resolves a trusted client IP address from the HTTPS Callable context.
 * 
 * Security Invariants:
 * - Never blindly trusts client-supplied leftmost X-Forwarded-For values.
 * - Prioritizes Google Cloud infrastructure-injected headers ('x-appengine-user-ip', 'fastly-client-ip').
 * - In local emulator / test environments, allows loopback.
 * - If multiple hops exist in X-Forwarded-For without trusted upstream, extracts the rightmost non-internal hop.
 * - If no trusted IP can be resolved, returns null so rate limiting can fallback
 *   to an isolated per-identifier bucket rather than a single shared bucket.
 */
function getClientIp(context) {
  if (!context || !context.rawRequest) {
    return null;
  }
  const headers = context.rawRequest.headers || {};

  // 1. Google Cloud App Engine / Cloud Functions edge-authenticated client IP (GFE verified)
  const appEngineIp = headers["x-appengine-user-ip"];
  if (appEngineIp && typeof appEngineIp === "string" && appEngineIp.trim().length > 0) {
    const clean = appEngineIp.replace(/^::ffff:/, "").trim();
    if (!isPrivateOrInternalIp(clean)) {
      return clean;
    }
  }

  // 2. Inspect X-Forwarded-For conservatively
  // Google Front End and standard reverse proxies append the connecting client IP at the end of the chain.
  // We extract the rightmost non-internal hop, discarding any client-injected leftmost prefixes.
  const xForwardedFor = headers["x-forwarded-for"] || headers["X-Forwarded-For"];
  if (xForwardedFor && typeof xForwardedFor === "string") {
    const hops = xForwardedFor.split(",").map(h => h.trim().replace(/^::ffff:/, "")).filter(Boolean);
    const nonInternal = hops.filter(h => !isPrivateOrInternalIp(h));
    if (nonInternal.length > 0) {
      return nonInternal[nonInternal.length - 1];
    }
  }

  // 3. Fastly CDN edge header (Firebase Hosting rewrites)
  const fastlyIp = headers["fastly-client-ip"];
  if (fastlyIp && typeof fastlyIp === "string" && fastlyIp.trim().length > 0) {
    const clean = fastlyIp.replace(/^::ffff:/, "").trim();
    if (!isPrivateOrInternalIp(clean)) {
      return clean;
    }
  }

  // 4. Cloudflare connecting IP (if proxied via Cloudflare)
  const cfIp = headers["cf-connecting-ip"];
  if (cfIp && typeof cfIp === "string" && cfIp.trim().length > 0) {
    const clean = cfIp.replace(/^::ffff:/, "").trim();
    if (!isPrivateOrInternalIp(clean)) {
      return clean;
    }
  }

  // 5. Direct socket remote address (only return if non-internal public IP)
  const reqIp = context.rawRequest.ip || (context.rawRequest.socket && context.rawRequest.socket.remoteAddress);
  if (reqIp && typeof reqIp === "string" && reqIp.trim().length > 0) {
    const cleanReqIp = reqIp.replace(/^::ffff:/, "").trim();
    if (!isPrivateOrInternalIp(cleanReqIp)) {
      return cleanReqIp;
    }
  }

  return null;
}

/**
 * Atomic server-side rate limiter using Firestore transactions.
 * Uses the internal 'security_rate_limits' collection.
 */
async function checkRateLimit(key, maxAttempts, windowMs, db = admin.firestore()) {
  const safeDocId = key.replace(/[^a-zA-Z0-9_-]/g, "_").substring(0, 100);
  const rateLimitRef = db.collection("security_rate_limits").doc(safeDocId);
  const now = Date.now();

  return await db.runTransaction(async (transaction) => {
    const doc = await transaction.get(rateLimitRef);
    if (!doc.exists) {
      transaction.set(rateLimitRef, {
        attempts: 1,
        firstAttempt: now,
        lastAttempt: now,
        expiresAt: new Date(now + windowMs)
      });
      return { allowed: true, remaining: maxAttempts - 1 };
    }

    const data = doc.data() || {};
    const firstAttempt = typeof data.firstAttempt === "number" ? data.firstAttempt : now;
    const elapsed = now - firstAttempt;

    if (elapsed > windowMs) {
      // Window expired, reset counter
      transaction.set(rateLimitRef, {
        attempts: 1,
        firstAttempt: now,
        lastAttempt: now,
        expiresAt: new Date(now + windowMs)
      });
      return { allowed: true, remaining: maxAttempts - 1 };
    }

    if (data.attempts >= maxAttempts) {
      return {
        allowed: false,
        remaining: 0,
        retryAfterMs: Math.max(0, windowMs - elapsed)
      };
    }

    const nextAttempts = (typeof data.attempts === "number" ? data.attempts : 1) + 1;
    transaction.update(rateLimitRef, {
      attempts: nextAttempts,
      lastAttempt: now
    });
    return { allowed: true, remaining: maxAttempts - nextAttempts };
  });
}

/**
 * resolveAdminLogin
 * Secure server-side HTTPS Callable Cloud Function for Admin ID -> Firebase Auth Email Resolution.
 * Allows an unauthenticated client to resolve an Admin ID (e.g. 'admin26') strictly to the real
 * Firebase Auth email without exposing Firestore profile records or enabling account enumeration.
 * 
 * Security Protections:
 * - Server-side atomic rate limiting per IP (or isolated per-id fallback when IP is unresolvable).
 * - Never returns personal external email addresses (e.g. @gmail.com) to unauthenticated callers.
 * - Uniform fallback email returned for non-existent, inactive, non-admin, or external-email accounts.
 * - Prevents username/staff ID enumeration: identical response pattern across all states.
 */
exports.resolveAdminLogin = regionalFunctions.https.onCall(async (data, context) => {
  const rawAdminId = data && data.adminId ? String(data.adminId).trim() : "";
  if (!rawAdminId || rawAdminId.length > 100) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Administrator ID must be provided."
    );
  }
  const adminId = rawAdminId.toLowerCase();

  // 1. Server-side Rate Limiting (10 requests per 5 minutes per IP or isolated per-ID fallback)
  const clientIp = getClientIp(context);
  let rateLimitKey;
  if (clientIp) {
    const ipHash = crypto.createHash("sha256").update(clientIp).digest("hex").substring(0, 16);
    rateLimitKey = `login_res_ip_${ipHash}`;
  } else {
    // Isolated fallback: missing IP uses an identifier-specific bucket to prevent DoS for other users
    const idHash = crypto.createHash("sha256").update(adminId).digest("hex").substring(0, 16);
    rateLimitKey = `login_res_id_${idHash}`;
  }

  try {
    const rateCheck = await checkRateLimit(rateLimitKey, 10, 5 * 60 * 1000);
    if (!rateCheck.allowed) {
      console.warn(`[resolveAdminLogin] Rate limit exceeded (key=${rateLimitKey})`);
      throw new functions.https.HttpsError(
        "resource-exhausted",
        "Too many login resolution requests. Please try again later."
      );
    }
  } catch (err) {
    if (err instanceof functions.https.HttpsError) throw err;
    console.error("[resolveAdminLogin] Rate limit check error:", err);
  }

  // 2. Uniform fallback email to prevent account enumeration
  const uniformFallbackEmail = adminId.includes("@") ? adminId : `${adminId}@collegebus.internal`;

  // 3. Look up admin profile in Firestore using Firebase Admin SDK
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

  // If user does not exist, return uniform email to prevent account enumeration
  if (querySnapshot.empty) {
    return { email: uniformFallbackEmail };
  }

  const userDoc = querySnapshot.docs[0];
  const userData = userDoc.data() || {};

  // If user is NOT an active ADMIN, return uniform fallback email to prevent enumeration
  // and status leakage (does NOT reveal role or deactivation status)
  if (userData.role !== "ADMIN" || userData.active !== true) {
    return { email: uniformFallbackEmail };
  }

  const registeredEmail = userData.email ? String(userData.email).trim() : "";

  // Never disclose personal external email addresses (e.g. @gmail.com) to unauthenticated callers.
  // Only internal organization emails (@collegebus.internal) are returned.
  if (registeredEmail && registeredEmail.toLowerCase().endsWith("@collegebus.internal")) {
    return { email: registeredEmail };
  }

  // If the admin account uses an external personal email, return the uniform fallback internal email
  // to prevent personal email extraction while maintaining anti-enumeration.
  return { email: uniformFallbackEmail };
});

/**
 * requestAdminPasswordReset
 * Secure server-side HTTPS Callable Cloud Function for Admin Password Recovery.
 * Resolves Admin ID to registered external email and triggers Firebase password reset email.
 * Always returns a generic confirmation response to prevent account enumeration.
 * 
 * Security Protections:
 * - IP rate limiting (max 5 requests / 15 minutes, with isolated per-id fallback) prevents service abuse.
 * - Account rate limiting (max 3 emails / 1 hour) prevents email flooding attacks against administrators.
 * - Generic confirmation response regardless of account existence or status.
 */
exports.requestAdminPasswordReset = regionalFunctions.https.onCall(async (data, context) => {
  const genericResponse = {
    success: true,
    message: "If an active administrator account exists, a password reset email has been sent."
  };

  const rawAdminId = data && data.adminId ? String(data.adminId).trim() : "";
  if (!rawAdminId || rawAdminId.length > 100) {
    return genericResponse;
  }
  const adminId = rawAdminId.toLowerCase();

  // 1. IP-level Rate Limiting: max 5 requests per 15 minutes
  const clientIp = getClientIp(context);
  let ipRateLimitKey;
  if (clientIp) {
    const ipHash = crypto.createHash("sha256").update(clientIp).digest("hex").substring(0, 16);
    ipRateLimitKey = `pwd_reset_ip_${ipHash}`;
  } else {
    const idHash = crypto.createHash("sha256").update(adminId).digest("hex").substring(0, 16);
    ipRateLimitKey = `pwd_reset_id_${idHash}`;
  }

  try {
    const ipCheck = await checkRateLimit(ipRateLimitKey, 5, 15 * 60 * 1000);
    if (!ipCheck.allowed) {
      console.warn(`[requestAdminPasswordReset] IP rate limit exceeded (key=${ipRateLimitKey})`);
      throw new functions.https.HttpsError(
        "resource-exhausted",
        "Too many password reset requests. Please try again later."
      );
    }
  } catch (err) {
    if (err instanceof functions.https.HttpsError) throw err;
    console.error("[requestAdminPasswordReset] Rate limit check error:", err);
  }

  // 2. Account-level Rate Limiting: max 3 reset emails per 1 hour per adminId
  // Protects against flooding an administrator's inbox
  const idHash = crypto.createHash("sha256").update(adminId).digest("hex").substring(0, 16);
  const idRateLimitKey = `pwd_reset_acc_${idHash}`;

  try {
    const idCheck = await checkRateLimit(idRateLimitKey, 3, 60 * 60 * 1000);
    if (!idCheck.allowed) {
      console.warn(`[requestAdminPasswordReset] Account rate limit exceeded for ${adminId}. Suppressing email.`);
      // Return generic response without sending email to prevent email flooding
      return genericResponse;
    }
  } catch (err) {
    console.error("[requestAdminPasswordReset] Account rate limit check error:", err);
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

    // Support verified external recoveryEmail field:
    // If recoveryEmail is present and valid, use it for dispatching the password reset link.
    // Otherwise fallback to external registered email if available.
    const rawTarget = userData.recoveryEmail || userData.email || (adminId.includes("@") ? adminId : null);
    const targetEmail = rawTarget ? String(rawTarget).trim() : null;
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
 * Hardened with App Check and role verification.
 */
exports.resetDriverPassword = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processResetDriverPassword(data, context);
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
 * Verifies that the request context has a cryptographically validated App Check token.
 */
function verifyAppCheck(context) {
  const isAppCheckValid = Boolean(context && context.app && typeof context.app.appId === "string" && context.app.appId.trim().length > 0);
  if (!isAppCheckValid) {
    if (!ENFORCE_APP_CHECK) {
      console.warn("[AppCheck Monitoring] Allowing unverified callable while ENFORCE_APP_CHECK=false.");
      return;
    }
    throw new functions.https.HttpsError(
      "unauthenticated",
      "App Check verification failed. This request must originate from an App Check verified client."
    );
  }
}

/**
 * HTTPS Callable Cloud Function for creating a temporary driver replacement.
 * Hardened with App Check and authentication verification.
 */
exports.createTemporaryAssignment = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    verifyAppCheck(context);
    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
    }
    try {
      return await processCreateTemporaryAssignment(data, context.auth.uid, admin.firestore(), admin.database());
    } catch (err) {
      if (err instanceof functions.https.HttpsError) throw err;
      console.error("[createTemporaryAssignment] Error:", err.message);
      throw new functions.https.HttpsError("invalid-argument", err.message);
    }
  });

/**
 * HTTPS Callable Cloud Function for cancelling a temporary driver replacement.
 * Hardened with App Check and authentication verification.
 */
exports.cancelTemporaryAssignment = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    verifyAppCheck(context);
    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
    }
    const assignmentId = data && data.assignmentId ? String(data.assignmentId).trim() : null;
    try {
      return await processCancelTemporaryAssignment(assignmentId, context.auth.uid, admin.firestore(), admin.database());
    } catch (err) {
      if (err instanceof functions.https.HttpsError) throw err;
      console.error("[cancelTemporaryAssignment] Error:", err.message);
      throw new functions.https.HttpsError("invalid-argument", err.message);
    }
  });

/**
 * Pure testable logic for getEffectiveDriver callable.
 * Enforces authentication, active staff role (ADMIN or DRIVER), and strips phone PII.
 */
async function processGetEffectiveDriver(data, context, db = admin.firestore()) {
  verifyAppCheck(context);
  if (!context || !context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication is required.");
  }

  const callerDoc = await db.collection("users").doc(context.auth.uid).get();
  if (!callerDoc.exists) {
    throw new functions.https.HttpsError("permission-denied", "Caller authorization profile not found.");
  }
  const caller = callerDoc.data() || {};
  if (caller.active !== true || (caller.role !== "ADMIN" && caller.role !== "DRIVER")) {
    throw new functions.https.HttpsError("permission-denied", "Only active staff members may resolve effective operators.");
  }

  const busId = data && data.busId ? String(data.busId).trim() : null;
  const currentTime = data && data.currentTime ? Number(data.currentTime) : Date.now();
  if (!busId) {
    throw new functions.https.HttpsError("invalid-argument", "Bus ID is required.");
  }

  const resolved = await getEffectiveDriver(busId, currentTime, db);
  if (!resolved) return null;

  return {
    busId: resolved.busId,
    isTemporary: resolved.isTemporary,
    driverId: resolved.driverId,
    driverName: resolved.driverName,
    effectiveDriverUid: resolved.effectiveDriverUid,
    effectiveDriverName: resolved.effectiveDriverName,
    startAt: resolved.startAt,
    endAt: resolved.endAt,
    temporaryAssignmentId: resolved.temporaryAssignmentId,
    operatorEpoch: resolved.operatorEpoch
  };
}

/**
 * HTTPS Callable Cloud Function for resolving the authoritative effective driver.
 * Hardened with App Check and authenticated staff verification.
 */
exports.getEffectiveDriver = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return await processGetEffectiveDriver(data, context, admin.firestore());
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
      await rtdb.ref(`bus_operators/${busId}`).update({
        hasTemporary: false,
        temporaryDriverUid: null,
        cancelledAt: Date.now(),
        operatorEpoch: Date.now()
      });
    } else {
      await rtdb.ref(`bus_operators/${busId}`).set({
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
    await syncBusDriverContact(busId, admin.firestore()).catch(() => {});
    return null;
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
exports.resolveEffectiveDriver = getEffectiveDriver;
exports.processGetEffectiveDriver = processGetEffectiveDriver;
exports.syncPublicDriverDirectory = syncPublicDriverDirectory;
exports.syncBusDriverContact = syncBusDriverContact;
exports.processCreateTemporaryAssignment = processCreateTemporaryAssignment;
exports.processCancelTemporaryAssignment = processCancelTemporaryAssignment;

// =========================================================================
// STUDENT STOP-COORDINATE CROWDSOURCING & ADMINISTRATOR REVIEW (Milestone 3)
// =========================================================================

/**
 * Core business logic for submitting student stop coordinates.
 * Enforces:
 * 1. App Check verification (rejects unverified clients).
 * 2. Independent input validation of all fields.
 * 3. Route and Stop existence and active checks in Firestore.
 * 4. Status strictly forced to PENDING, stripping all review fields.
 * 5. Spam / rate-limit protection (10-minute duplicate window).
 */
async function processSubmitStopCoordinates(data, context, db = admin.firestore()) {
  // 1. App Check Enforcement
  // Enforces that the request originates from an authentic App Check verified client.
  // Context MUST contain a valid decoded App Check token with a non-empty appId.
  // Merely supplying an X-Firebase-AppCheck header is strictly rejected.
  const isAppCheckValid = Boolean(context && context.app && typeof context.app.appId === "string" && context.app.appId.trim().length > 0);

  if (!isAppCheckValid) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "App Check verification failed. All student submissions must originate from an App Check verified client."
    );
  }

  // 2. Independent Input Validation
  const routeId = data && typeof data.routeId === "string" ? data.routeId.trim() : "";
  const stopId = data && typeof data.stopId === "string" ? data.stopId.trim() : "";
  const proposedLat = Number(data && data.proposedLatitude);
  const proposedLng = Number(data && data.proposedLongitude);
  const rawSource = data && typeof data.source === "string" ? data.source.trim().toUpperCase() : "";
  const studentNotes = data && typeof data.studentNotes === "string" ? data.studentNotes.trim().slice(0, 500) : null;

  if (!routeId || routeId.length > 64) {
    throw new functions.https.HttpsError("invalid-argument", "A valid routeId (1-64 characters) is required.");
  }

  if (!stopId || stopId.length > 64) {
    throw new functions.https.HttpsError("invalid-argument", "A valid stopId (1-64 characters) is required.");
  }

  if (isNaN(proposedLat) || isNaN(proposedLng) || proposedLat < -90 || proposedLat > 90 || proposedLng < -180 || proposedLng > 180) {
    throw new functions.https.HttpsError("invalid-argument", "Valid geographic coordinates are required (lat [-90, 90], lng [-180, 180]).");
  }

  if (proposedLat === 0 && proposedLng === 0) {
    throw new functions.https.HttpsError("invalid-argument", "Coordinates (0, 0) cannot be submitted as a public bus stop location.");
  }

  if (rawSource !== "GPS" && rawSource !== "MANUAL") {
    throw new functions.https.HttpsError("invalid-argument", "Location source must be either 'GPS' or 'MANUAL'.");
  }

  let validatedAccuracy = null;
  if (rawSource === "GPS") {
    const acc = Number(data && data.accuracyMeters);
    if (isNaN(acc) || acc <= 0 || acc > 5000) {
      throw new functions.https.HttpsError("invalid-argument", "A valid positive GPS accuracy in metres (<= 5000m) is required for GPS submissions.");
    }
    validatedAccuracy = Math.round(acc);
  }

  // 3. Verify route and stop exist and are active in Firestore
  const routeRef = db.collection("routes").doc(routeId);
  const routeDoc = await routeRef.get();
  if (!routeDoc.exists) {
    throw new functions.https.HttpsError("not-found", `Route ${routeId} does not exist.`);
  }

  const routeData = routeDoc.data() || {};
  if (routeData.active === false) {
    throw new functions.https.HttpsError("failed-precondition", `Route ${routeId} is inactive.`);
  }

  const stops = Array.isArray(routeData.stops) ? routeData.stops : [];
  const targetStop = stops.find((s) => s && s.stopId === stopId);
  if (!targetStop) {
    throw new functions.https.HttpsError("not-found", `Stop ${stopId} not found in route ${routeId}.`);
  }
  if (targetStop.active === false) {
    throw new functions.https.HttpsError("failed-precondition", `Stop ${stopId} is inactive.`);
  }

  // 4. Duplicate / Spam Protection: Check for recent pending submission for the exact same stop within last 10 minutes
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
        // If proposed point is within ~50 metres of another pending proposal, prevent duplicate flood
        if (latDiff < 0.00045 && lngDiff < 0.00045) {
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

  // 5. Enforce status strictly to PENDING and sanitize all administrative review fields
  const submissionData = {
    routeId,
    routeName: String(routeData.name || routeId),
    stopId,
    stopName: String(targetStop.name || stopId),
    stopOrder: typeof targetStop.order === "number" ? targetStop.order : 0,
    proposedLatitude: proposedLat,
    proposedLongitude: proposedLng,
    accuracyMeters: validatedAccuracy,
    source: rawSource,
    status: "PENDING", // STRICTLY FORCED
    submittedAt: FieldValue.serverTimestamp(),
    existingLatitude: typeof targetStop.latitude === "number" ? targetStop.latitude : null,
    existingLongitude: typeof targetStop.longitude === "number" ? targetStop.longitude : null,
    studentNotes: studentNotes || null,
    reviewedBy: null, // STRIPPED
    reviewedAt: null, // STRIPPED
    reviewNotes: null, // STRIPPED
    finalLatitude: null,
    finalLongitude: null,
  };

  const docRef = await db.collection("stop_location_submissions").add(submissionData);
  console.log(`[submitStopCoordinates] Submitted proposal ${docRef.id} for route ${routeId}, stop ${stopId}`);
  return { success: true, submissionId: docRef.id };
}

/**
 * Core business logic for reviewing (approving or rejecting) student stop coordinates.
 * Enforces:
 * 1. Active Administrator authorization (users/{callerUid}.role === 'ADMIN' && active === true).
 * 2. Concurrency control via Firestore runTransaction.
 * 3. Atomic status transition from PENDING to APPROVED or REJECTED.
 * 4. Full preservation of route stop array ordering, types, and custom attributes.
 * 5. Rejections leave official route coordinates 100% untouched.
 */
async function processReviewStopCoordinateSubmission(data, context, db = admin.firestore()) {
  // 1. Verify caller authentication
  if (!context || !context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication is required to review submissions.");
  }

  const callerUid = context.auth.uid;

  // 2. Verify caller is an active Administrator in Firestore
  const callerDoc = await db.collection("users").doc(callerUid).get();
  if (!callerDoc.exists) {
    throw new functions.https.HttpsError("permission-denied", "Caller profile not found.");
  }
  const callerData = callerDoc.data() || {};
  if (callerData.role !== "ADMIN" || callerData.active !== true) {
    throw new functions.https.HttpsError("permission-denied", "Only active administrators are authorized to review submissions.");
  }

  // 3. Validate input parameters
  const submissionId = data && typeof data.submissionId === "string" ? data.submissionId.trim() : "";
  const action = data && (data.action === "APPROVE" || data.action === "REJECT") ? data.action : null;
  const reviewNotes = data && typeof data.reviewNotes === "string" ? data.reviewNotes.trim().slice(0, 1000) : null;

  if (!submissionId || !action) {
    throw new functions.https.HttpsError("invalid-argument", "Valid submissionId and action ('APPROVE' or 'REJECT') are required.");
  }

  const submissionRef = db.collection("stop_location_submissions").doc(submissionId);

  // 4. Run Transaction for Concurrency and Atomic Updates
  return await db.runTransaction(async (transaction) => {
    const subDoc = await transaction.get(submissionRef);
    if (!subDoc.exists) {
      throw new functions.https.HttpsError("not-found", `Submission ${submissionId} not found.`);
    }

    const subData = subDoc.data() || {};
    // Ensure submission is currently PENDING (prevents concurrent double-reviews or overwrite races)
    if (subData.status !== "PENDING") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        `Submission ${submissionId} has already been reviewed (${subData.status}) by ${subData.reviewedBy || "another administrator"}.`
      );
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

    const now = FieldValue.serverTimestamp();

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

// HTTPS Callable exports with App Check configuration
exports.submitStopCoordinates = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processSubmitStopCoordinates(data, context);
  });

exports.reviewStopCoordinateSubmission = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processReviewStopCoordinateSubmission(data, context);
  });

exports.processSubmitStopCoordinates = processSubmitStopCoordinates;
exports.processReviewStopCoordinateSubmission = processReviewStopCoordinateSubmission;

// =========================================================================
// MILESTONE 5: FLEET & DRIVER MANAGEMENT BACKEND ENGINE
// =========================================================================

/**
 * Validates driver profile inputs strictly conforming to Android DriverProfileValidator.kt:
 * - Name >= 2 chars, non-blank
 * - Staff ID (customId) >= 3 chars, lowercase alphanumeric, hyphens, underscores, not containing 'admin'
 * - Phone number: optional, normalized to 10 digits starting with 6, 7, 8, or 9
 * - Password: >= 6 chars (when isCreate is true)
 */
function validateDriverProfileInputs({ name, customId, phoneNumber, password }, isCreate = true) {
  const cleanName = String(name || "").trim();
  if (!cleanName || cleanName.length < 2) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Driver display name must be at least 2 characters long."
    );
  }

  const cleanId = String(customId || "").trim().toLowerCase();
  if (!cleanId || cleanId.length < 3) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Staff ID must be at least 3 characters long."
    );
  }
  if (cleanId.includes("admin")) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Staff ID cannot contain 'admin'."
    );
  }
  if (!/^[a-z0-9_\-]+$/.test(cleanId)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Staff ID may only contain lowercase letters, numbers, hyphens, and underscores."
    );
  }

  let cleanPhone = null;
  if (phoneNumber != null && String(phoneNumber).trim().length > 0) {
    const raw = String(phoneNumber).trim();
    const digitsOnly = raw.replace(/\D/g, "");
    let normalized = digitsOnly;
    if (digitsOnly.length === 12 && digitsOnly.startsWith("91")) {
      normalized = digitsOnly.substring(2);
    } else if (digitsOnly.length === 11 && digitsOnly.startsWith("0")) {
      normalized = digitsOnly.substring(1);
    }
    if (normalized.length !== 10) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Phone number must be a valid 10-digit mobile number."
      );
    }
    if (!/^[6-9]/.test(normalized)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Indian mobile numbers must begin with 6, 7, 8, or 9."
      );
    }
    cleanPhone = normalized;
  }

  let cleanPassword = null;
  if (isCreate) {
    cleanPassword = String(password || "");
    if (cleanPassword.length < 6) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Password must be at least 6 characters long."
      );
    }
  }

  return { cleanName, cleanId, cleanPhone, cleanPassword };
}

/**
 * Maps staff ID to internal email: `${cleanCustomId}@collegebus.internal`
 * Strictly matches Android DriverProfileValidator.mapStaffEmail(customId).
 */
function mapStaffEmail(customId) {
  return `${String(customId).trim().toLowerCase()}@collegebus.internal`;
}

/**
 * Verifies caller is authenticated and is an active Administrator in Firestore.
 */
async function verifyActiveAdmin(context, db = admin.firestore()) {
  if (!context || !context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication is required to perform this action."
    );
  }

  const callerUid = context.auth.uid;
  const callerDoc = await db.collection("users").doc(callerUid).get();
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
      "Only active administrators are authorized to perform this operation."
    );
  }

  return { callerUid, callerData };
}

/**
 * Idempotent synchronization helper for Realtime Database /users and /bus_operators.
 * Preserves temporary driver replacements and monotonic version sequences.
 */
async function syncAssignmentToRtdb({ busId, driverUid, driverName, previousDriverUid, epoch, version }, rtdb = admin.database()) {
  if (!rtdb || !busId) return;

  const now = epoch || Date.now();
  const ver = Number(version) || 1;

  // 1. If previous driver was unassigned, clear assignedBusId in RTDB /users
  if (previousDriverUid && previousDriverUid !== driverUid) {
    try {
      await rtdb.ref(`users/${previousDriverUid}`).update({
        assignedBusId: null,
        assignmentVersion: ver
      });
    } catch (e) {
      console.warn(`[syncAssignmentToRtdb] Warning clearing RTDB users/${previousDriverUid}:`, e.message);
    }
  }

  // 2. If new driver assigned, update assignedBusId in RTDB /users
  if (driverUid) {
    try {
      await rtdb.ref(`users/${driverUid}`).update({
        role: "DRIVER",
        active: true,
        name: driverName || "",
        assignedBusId: busId,
        assignmentVersion: ver
      });
    } catch (e) {
      console.warn(`[syncAssignmentToRtdb] Warning updating RTDB users/${driverUid}:`, e.message);
    }
  }

  // 3. Update RTDB /bus_operators/{busId} preserving temporary replacement fields atomically
  try {
    const opRef = rtdb.ref(`bus_operators/${busId}`);
    let committedVersion = ver;

    if (typeof opRef.transaction === "function") {
      const txResult = await opRef.transaction((existing) => {
        const current = existing || {};
        const currentVersion = Number(current.assignmentVersion) || 0;

        // Discard stale out-of-order write
        if (ver > 0 && ver < currentVersion) {
          return; // Abort transaction without updating
        }

        // Monotonic progression
        committedVersion = Math.max(ver, currentVersion + 1);

        const updatePayload = {
          busId: busId,
          permanentDriverUid: driverUid || null,
          assignmentVersion: committedVersion,
          operatorEpoch: now
        };

        if (current.hasTemporary === true) {
          updatePayload.hasTemporary = true;
          updatePayload.temporaryDriverUid = current.temporaryDriverUid || null;
          updatePayload.startAt = current.startAt || 0;
          updatePayload.endAt = current.endAt || 0;
          if (current.temporaryAssignmentId) {
            updatePayload.temporaryAssignmentId = current.temporaryAssignmentId;
          }
        }

        return updatePayload;
      });

      console.log(`[syncAssignmentToRtdb] Atomically synced RTDB bus_operators/${busId} (permDriver=${driverUid}, ver=${committedVersion})`);
      return { success: txResult ? txResult.committed : true, version: committedVersion };
    } else {
      const snap = await opRef.get();
      const existing = snap.val() || {};
      const currentVersion = Number(existing.assignmentVersion) || 0;

      if (ver > 0 && ver < currentVersion) {
        console.warn(`[syncAssignmentToRtdb] Skipping stale write for ${busId} (ver ${ver} < current ${currentVersion})`);
        return { success: false, version: currentVersion };
      }

      committedVersion = Math.max(ver, currentVersion + 1);

      const updatePayload = {
        busId: busId,
        permanentDriverUid: driverUid || null,
        assignmentVersion: committedVersion,
        operatorEpoch: now
      };

      if (existing.hasTemporary === true) {
        updatePayload.hasTemporary = true;
        updatePayload.temporaryDriverUid = existing.temporaryDriverUid || null;
        updatePayload.startAt = existing.startAt || 0;
        updatePayload.endAt = existing.endAt || 0;
        if (existing.temporaryAssignmentId) {
          updatePayload.temporaryAssignmentId = existing.temporaryAssignmentId;
        }
      }

      await opRef.update(updatePayload);
      console.log(`[syncAssignmentToRtdb] Synced RTDB bus_operators/${busId} (permDriver=${driverUid}, ver=${committedVersion})`);
      return { success: true, version: committedVersion };
    }
  } catch (e) {
    console.error(`[syncAssignmentToRtdb] Error updating RTDB bus_operators/${busId}:`, e.message);
    throw e;
  }
}

/**
 * Idempotently synchronizes a driver's public presence in 'public_driver_directory'.
 * 
 * Fields published (strictly minimal, privacy-conscious):
 * - driverId: string (custom staff ID or UID)
 * - driverName: string (display name)
 * - assignedBusId: string | null (only if the assigned bus is active)
 * - active: boolean (true)
 * - updatedAt: number (timestamp)
 * 
 * Privacy invariants:
 * - Personal emails, phone numbers, Auth UIDs, credentials, and metadata are NEVER written.
 * - Inactive or deleted drivers are removed from the collection.
 */
async function syncPublicDriverDirectory(driverUid, db = admin.firestore()) {
  if (!driverUid) return;
  const safeDriverUid = String(driverUid).trim();
  const dirRef = db.collection("public_driver_directory").doc(safeDriverUid);

  try {
    const userDoc = await db.collection("users").doc(safeDriverUid).get();
    if (!userDoc.exists) {
      // Driver was deleted: remove public directory record
      await dirRef.delete().catch(() => {});
      return;
    }

    const userData = userDoc.data() || {};
    if (userData.role !== "DRIVER" || userData.active !== true) {
      // Inactive or not a driver: remove public directory record
      await dirRef.delete().catch(() => {});
      return;
    }

    const assignedBusId = userData.assignedBusId ? String(userData.assignedBusId).trim() : null;
    let isBusActive = false;

    if (assignedBusId) {
      const busDoc = await db.collection("buses").doc(assignedBusId).get();
      if (busDoc.exists && busDoc.data().active !== false) {
        isBusActive = true;
      }
    }

    const publicEntry = {
      driverId: userData.customId || safeDriverUid,
      driverName: userData.name || "Driver",
      assignedBusId: isBusActive ? assignedBusId : null,
      active: true,
      updatedAt: Date.now()
    };

    await dirRef.set(publicEntry);
    console.log(`[syncPublicDriverDirectory] Synced public entry for driver ${safeDriverUid} (bus=${publicEntry.assignedBusId})`);
  } catch (err) {
    console.error(`[syncPublicDriverDirectory] Failed to sync driver ${safeDriverUid}:`, err.message);
  }
}

/**
 * Synchronizes a single bus's public driver contact document (`public_bus_contacts/{busId}`).
 * Stores ONLY `{ busId, driverName, phoneNumber, isTemporary, active, updatedAt }` for the
 * currently effective active driver (never email, uid, or customId).
 * Deletes the document whenever the bus is inactive, unassigned, or the driver is inactive/has no phone.
 */
async function syncBusDriverContact(busId, db = admin.firestore(), nowMs = Date.now()) {
  const cleanBusId = String(busId || "").trim();
  if (!cleanBusId) return { busId: "", status: "SKIPPED" };

  const contactRef = db.collection("public_bus_contacts").doc(cleanBusId);
  try {
    const busDoc = await db.collection("buses").doc(cleanBusId).get();
    const busData = busDoc.exists ? (busDoc.data() || {}) : null;
    if (!busDoc.exists || busData.active === false) {
      await contactRef.delete().catch(() => {});
      return { busId: cleanBusId, status: "DELETED" };
    }

    // 1. Check active non-cancelled temporary assignment first
    let activeTemp = null;
    const tempSnap = await db.collection("temporary_assignments")
      .where("busId", "==", cleanBusId)
      .where("cancelledAt", "==", null)
      .get();
    tempSnap.forEach((doc) => {
      const data = doc.data() || {};
      if (data.status === "CANCELLED" || data.cancelledAt != null) return;
      const start = Number(data.startAt) || 0;
      const end = Number(data.endAt) || 0;
      if (nowMs >= start && nowMs < end) {
        activeTemp = data;
      }
    });

    const targetUid = activeTemp
      ? String(activeTemp.replacementDriverUid || "").trim()
      : String(busData.driverId || "").trim();

    if (!targetUid) {
      await contactRef.delete().catch(() => {});
      return { busId: cleanBusId, status: "DELETED" };
    }

    const userDoc = await db.collection("users").doc(targetUid).get();
    const userData = userDoc.exists ? (userDoc.data() || {}) : null;
    if (!userDoc.exists || userData.role !== "DRIVER" || userData.active !== true) {
      await contactRef.delete().catch(() => {});
      return { busId: cleanBusId, status: "DELETED" };
    }

    const phone = userData.phoneNumber ? String(userData.phoneNumber).trim() : "";
    if (!phone) {
      await contactRef.delete().catch(() => {});
      return { busId: cleanBusId, status: "DELETED" };
    }

    const fallbackName = activeTemp ? activeTemp.replacementDriverName : busData.driverName;
    const entry = {
      busId: cleanBusId,
      driverName: String(userData.name || fallbackName || "Driver").trim(),
      phoneNumber: phone,
      isTemporary: Boolean(activeTemp),
      active: true,
      updatedAt: nowMs
    };

    await contactRef.set(entry);
    return { busId: cleanBusId, status: "SYNCED", isTemporary: entry.isTemporary };
  } catch (err) {
    console.error(`[syncBusDriverContact] Failed to sync ${cleanBusId}:`, err.message);
    return { busId: cleanBusId, status: "ERROR", error: err.message };
  }
}

/**
 * 1. createDriverAccount
 * Secure server-side driver onboarding using Firebase Admin SDK.
 * Protects active administrator session from displacement and enforces rollback on DB failure.
 */
async function processCreateDriverAccount(data, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();
  const auth = dependencies.auth || admin.auth();
  const rtdb = dependencies.rtdb || admin.database();

  await verifyActiveAdmin(context, db);

  const { cleanName, cleanId, cleanPhone, cleanPassword } = validateDriverProfileInputs(data, true);
  const assignedBusId = data && data.assignedBusId ? String(data.assignedBusId).trim() : null;

  // Verify Staff ID uniqueness in Firestore users
  const existingDocs = await db.collection("users").where("customId", "==", cleanId).get();
  if (!existingDocs.empty) {
    throw new functions.https.HttpsError(
      "already-exists",
      `Staff ID '${cleanId}' is already registered to another driver or staff member.`
    );
  }

  const internalEmail = mapStaffEmail(cleanId);
  let newUid = null;

  try {
    // Create Firebase Auth user
    const authRecord = await auth.createUser({
      email: internalEmail,
      password: cleanPassword,
      displayName: cleanName
    });
    newUid = authRecord.uid;

    const now = Date.now();

    // Create Firestore user profile
    const profilePayload = {
      uid: newUid,
      customId: cleanId,
      name: cleanName,
      email: internalEmail,
      role: "DRIVER",
      active: true,
      assignedBusId: assignedBusId || null,
      phoneNumber: cleanPhone,
      createdAt: now
    };
    await db.collection("users").doc(newUid).set(profilePayload);

    // Create RTDB authorization record
    if (rtdb) {
      await rtdb.ref(`users/${newUid}`).set({
        role: "DRIVER",
        active: true,
        name: cleanName,
        customId: cleanId,
        assignedBusId: assignedBusId || null
      });
    }

    // If initial bus assignment was requested, link bus atomically
    if (assignedBusId) {
      const busRef = db.collection("buses").doc(assignedBusId);
      await db.runTransaction(async (transaction) => {
        const busDoc = await transaction.get(busRef);
        if (!busDoc.exists) {
          throw new functions.https.HttpsError("not-found", `Bus '${assignedBusId}' not found.`);
        }
        if (busDoc.data().active === false) {
          throw new functions.https.HttpsError("failed-precondition", `Cannot assign driver to deactivated bus '${assignedBusId}'.`);
        }
        const currentDriverId = busDoc.data().driverId;
        if (currentDriverId && currentDriverId !== newUid) {
          transaction.update(db.collection("users").doc(currentDriverId), { assignedBusId: null });
        }
        const newVersion = (busDoc.data().assignmentVersion || 0) + 1;
        transaction.update(busRef, {
          driverId: newUid,
          driverName: cleanName,
          assignmentVersion: newVersion,
          assignmentEpoch: now,
          rtdbSyncStatus: "SYNCED"
        });
      });

      if (rtdb) {
        await syncAssignmentToRtdb({
          busId: assignedBusId,
          driverUid: newUid,
          driverName: cleanName,
          epoch: now,
          version: 1
        }, rtdb).catch(() => {});
      }
    }

    // Synchronize to public driver directory
    await syncPublicDriverDirectory(newUid, db);

    console.log(`[createDriverAccount] Successfully created driver ${newUid} (${cleanId})`);
    return {
      success: true,
      uid: newUid,
      customId: cleanId,
      name: cleanName,
      email: internalEmail,
      phoneNumber: cleanPhone,
      assignedBusId: assignedBusId || null
    };

  } catch (err) {
    console.error(`[createDriverAccount] Failure creating driver:`, err.message);
    // Rollback Auth user if created
    if (newUid) {
      try {
        await auth.deleteUser(newUid);
        console.log(`[createDriverAccount] Rolled back Auth user ${newUid} after database creation failure.`);
      } catch (rollbackErr) {
        console.warn(`[createDriverAccount] Failed to rollback Auth user ${newUid}:`, rollbackErr.message);
      }
    }
    if (err instanceof functions.https.HttpsError) throw err;
    throw new functions.https.HttpsError("internal", err.message || "Failed to create driver account.");
  }
}

/**
 * 2. setBusDriverAssignment
 * Authoritative bus-driver assignment, replacement, move, and unassignment.
 * Uses atomic Firestore transactions and deterministic monotonic versioning for RTDB.
 */
async function processSetBusDriverAssignment(data, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();
  const rtdb = dependencies.rtdb || admin.database();

  await verifyActiveAdmin(context, db);

  const busId = data && data.busId ? String(data.busId).trim() : null;
  const driverUid = data && data.driverUid ? String(data.driverUid).trim() : null;

  if (!busId) {
    throw new functions.https.HttpsError("invalid-argument", "Bus ID is required.");
  }

  const busRef = db.collection("buses").doc(busId);
  const now = Date.now();
  let assignedDriverName = null;
  let previousDriverId = null;
  let newBusVersion = 1;
  let transferredFromBusId = null;
  let transferredFromBusVersion = 0;

  // Atomic Firestore Transaction
  await db.runTransaction(async (transaction) => {
    const busDoc = await transaction.get(busRef);
    if (!busDoc.exists) {
      throw new functions.https.HttpsError("not-found", `Bus '${busId}' does not exist.`);
    }
    const busData = busDoc.data() || {};
    if (busData.active === false) {
      throw new functions.https.HttpsError("failed-precondition", `Cannot assign driver to deactivated bus '${busId}'.`);
    }

    previousDriverId = busData.driverId || null;
    newBusVersion = (busData.assignmentVersion || 0) + 1;

    // Case A: Assigning a driver
    if (driverUid) {
      const driverRef = db.collection("users").doc(driverUid);
      const driverDoc = await transaction.get(driverRef);
      if (!driverDoc.exists) {
        throw new functions.https.HttpsError("not-found", `Driver '${driverUid}' does not exist.`);
      }
      const driverData = driverDoc.data() || {};
      if (driverData.role !== "DRIVER") {
        throw new functions.https.HttpsError("invalid-argument", `User '${driverUid}' is not a driver.`);
      }
      if (driverData.active === false) {
        throw new functions.https.HttpsError("failed-precondition", `Cannot assign deactivated driver '${driverData.name}' to a bus.`);
      }

      assignedDriverName = driverData.name || "";

      // Move scenario: If driver is currently assigned to a different bus, clear old bus
      if (driverData.assignedBusId && driverData.assignedBusId !== busId) {
        transferredFromBusId = driverData.assignedBusId;
        const oldBusRef = db.collection("buses").doc(driverData.assignedBusId);
        const oldBusDoc = await transaction.get(oldBusRef);
        if (oldBusDoc.exists) {
          transferredFromBusVersion = (oldBusDoc.data().assignmentVersion || 0) + 1;
          transaction.update(oldBusRef, {
            driverId: null,
            driverName: null,
            assignmentVersion: transferredFromBusVersion,
            assignmentEpoch: now,
            rtdbSyncStatus: "PENDING"
          });
        }
      }

      // Replace scenario: If this bus already had a different driver, enforce explicit confirmation & conflict checks
      if (previousDriverId && previousDriverId !== driverUid) {
        const confirmReplacement = Boolean(data && (data.confirmReplacement || data.confirmReplaceDriver));
        if (!confirmReplacement) {
          throw new functions.https.HttpsError(
            "failed-precondition",
            `Bus '${busId}' already has an assigned driver ('${busData.driverName || previousDriverId}'). Explicit confirmation is required to replace the existing driver.`
          );
        }

        // Stale Client Detection: verify expected previous driver matches authoritative Firestore state
        if (data && data.expectedPreviousDriverId !== undefined && data.expectedPreviousDriverId !== null) {
          const expectedPrev = String(data.expectedPreviousDriverId).trim();
          if (expectedPrev !== previousDriverId) {
            throw new functions.https.HttpsError(
              "failed-precondition",
              `Assignment conflict: Bus '${busId}' is currently assigned to '${busData.driverName || previousDriverId}', not '${expectedPrev}'. Please refresh before confirming replacement.`
            );
          }
        }

        const prevDriverRef = db.collection("users").doc(previousDriverId);
        transaction.update(prevDriverRef, {
          assignedBusId: null,
          assignmentVersion: newBusVersion
        });
      }

      // Assign to target bus
      transaction.update(busRef, {
        driverId: driverUid,
        driverName: assignedDriverName,
        assignmentVersion: newBusVersion,
        assignmentEpoch: now,
        rtdbSyncStatus: "PENDING"
      });

      transaction.update(driverRef, {
        assignedBusId: busId,
        assignmentVersion: newBusVersion
      });

    } else {
      // Case B: Unassigning driver from bus
      if (previousDriverId) {
        const prevDriverRef = db.collection("users").doc(previousDriverId);
        transaction.update(prevDriverRef, {
          assignedBusId: null,
          assignmentVersion: newBusVersion
        });
      }

      transaction.update(busRef, {
        driverId: null,
        driverName: null,
        assignmentVersion: newBusVersion,
        assignmentEpoch: now,
        rtdbSyncStatus: "PENDING"
      });
    }
  });

  // Post-Transaction RTDB Sync with retry
  if (rtdb) {
    // If a driver was transferred from oldBusId, sync oldBusId to RTDB as well
    if (transferredFromBusId) {
      try {
        await syncAssignmentToRtdb({
          busId: transferredFromBusId,
          driverUid: null,
          driverName: "",
          epoch: now,
          version: transferredFromBusVersion
        }, rtdb);
        await db.collection("buses").doc(transferredFromBusId).update({
          rtdbSyncStatus: "SYNCED",
          rtdbSyncError: null,
          rtdbSyncLastAttempt: now
        }).catch(() => {});
      } catch (oldBusErr) {
        console.warn(`[setBusDriverAssignment] RTDB sync delayed for transferred-from bus ${transferredFromBusId}:`, oldBusErr.message);
      }
    }

    try {
      await syncAssignmentToRtdb({
        busId,
        driverUid,
        driverName: assignedDriverName,
        previousDriverUid: previousDriverId,
        epoch: now,
        version: newBusVersion
      }, rtdb);

      await busRef.update({
        rtdbSyncStatus: "SYNCED",
        rtdbSyncError: null,
        rtdbSyncLastAttempt: now
      });
    } catch (rtdbErr) {
      console.warn(`[setBusDriverAssignment] RTDB sync delayed for ${busId}:`, rtdbErr.message);
      await busRef.update({
        rtdbSyncStatus: "FAILED",
        rtdbSyncError: rtdbErr.message,
        rtdbSyncLastAttempt: now
      }).catch(() => {});
    }
  }

  // Synchronize affected driver(s) to public directory
  if (driverUid) {
    await syncPublicDriverDirectory(driverUid, db);
  }
  if (previousDriverId && previousDriverId !== driverUid) {
    await syncPublicDriverDirectory(previousDriverId, db);
  }

  return {
    success: true,
    busId,
    driverUid: driverUid || null,
    driverName: assignedDriverName,
    assignmentVersion: newBusVersion
  };
}

/**
 * 3. toggleDriverActiveStatus
 * Reversible driver deactivation and reactivation.
 * Protects against deactivating assigned drivers unless explicitly confirmed.
 */
async function processToggleDriverActiveStatus(data, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();
  const auth = dependencies.auth || admin.auth();
  const rtdb = dependencies.rtdb || admin.database();

  await verifyActiveAdmin(context, db);

  const driverUid = data && data.driverUid ? String(data.driverUid).trim() : null;
  const active = Boolean(data && data.active);
  const unassignBusIfAssigned = Boolean(data && data.unassignBusIfAssigned);

  if (!driverUid) {
    throw new functions.https.HttpsError("invalid-argument", "driverUid is required.");
  }

  const driverRef = db.collection("users").doc(driverUid);
  const driverDoc = await driverRef.get();
  if (!driverDoc.exists) {
    throw new functions.https.HttpsError("not-found", `Driver '${driverUid}' not found.`);
  }
  const driverData = driverDoc.data() || {};
  if (driverData.role !== "DRIVER") {
    throw new functions.https.HttpsError("invalid-argument", `User '${driverUid}' is not a driver.`);
  }

  const now = Date.now();
  let unassignedBusId = null;

  // Deactivation safety check
  if (!active && driverData.assignedBusId) {
    if (!unassignBusIfAssigned) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        `Driver '${driverData.name}' cannot be deactivated while assigned to bus '${driverData.assignedBusId}'. Please unassign the bus first.`
      );
    }
    // Admin confirmed unassignment on deactivation
    unassignedBusId = driverData.assignedBusId;
    const busRef = db.collection("buses").doc(unassignedBusId);
    let newBusVersion = 1;
    await db.runTransaction(async (transaction) => {
      const bDoc = await transaction.get(busRef);
      newBusVersion = bDoc.exists ? (bDoc.data().assignmentVersion || 0) + 1 : 1;
      transaction.update(busRef, {
        driverId: null,
        driverName: null,
        assignmentVersion: newBusVersion,
        assignmentEpoch: now,
        rtdbSyncStatus: "SYNCED"
      });
      transaction.update(driverRef, {
        active: false,
        assignedBusId: null
      });
    });

    if (rtdb) {
      await syncAssignmentToRtdb({
        busId: unassignedBusId,
        driverUid: null,
        previousDriverUid: driverUid,
        epoch: now,
        version: newBusVersion
      }, rtdb).catch(() => {});
    }
  } else {
    // Normal active toggle without bus conflict
    // If activating, verify driverData.assignedBusId is not stale or pointing to another driver's bus
    if (active && driverData.assignedBusId) {
      const busSnap = await db.collection("buses").doc(driverData.assignedBusId).get();
      if (!busSnap.exists || busSnap.data().driverId !== driverUid) {
        await driverRef.update({ active: true, assignedBusId: null });
      } else {
        await driverRef.update({ active: true });
      }
    } else {
      await driverRef.update({ active: active });
    }
  }

  // Synchronize RTDB user authorization
  if (rtdb) {
    await rtdb.ref(`users/${driverUid}`).update({
      active: active,
      assignedBusId: active ? (driverData.assignedBusId || null) : null
    }).catch(e => console.warn(`[toggleDriverActiveStatus] RTDB users update warning:`, e.message));
  }

  // Synchronize Firebase Auth disabled state
  try {
    await auth.updateUser(driverUid, { disabled: !active });
  } catch (authErr) {
    console.warn(`[toggleDriverActiveStatus] Auth updateUser warning for ${driverUid}:`, authErr.message);
  }

  // Synchronize to public directory (removes or disables public record)
  await syncPublicDriverDirectory(driverUid, db);

  console.log(`[toggleDriverActiveStatus] Driver ${driverUid} active status set to ${active}`);
  return {
    success: true,
    driverUid,
    active,
    unassignedBusId
  };
}

/**
 * 4. toggleBusActiveStatus
 * Reversible bus deactivation and reactivation.
 * Warns and detaches assigned driver if deactivating an active bus.
 */
async function processToggleBusActiveStatus(data, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();
  const rtdb = dependencies.rtdb || admin.database();

  await verifyActiveAdmin(context, db);

  const busId = data && data.busId ? String(data.busId).trim() : null;
  const active = Boolean(data && data.active);
  const unassignDriverIfAssigned = Boolean(data && data.unassignDriverIfAssigned);

  if (!busId) {
    throw new functions.https.HttpsError("invalid-argument", "busId is required.");
  }

  const busRef = db.collection("buses").doc(busId);
  const busDoc = await busRef.get();
  if (!busDoc.exists) {
    throw new functions.https.HttpsError("not-found", `Bus '${busId}' not found.`);
  }
  const busData = busDoc.data() || {};
  const now = Date.now();
  let detachedDriverId = null;

  // Deactivation safety check
  if (!active && busData.driverId) {
    if (!unassignDriverIfAssigned) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        `Bus '${busData.busNumber || busId}' has an active assigned driver. Unassign the driver or confirm detachment before deactivating.`
      );
    }

    detachedDriverId = busData.driverId;
    const driverRef = db.collection("users").doc(detachedDriverId);
    const newVersion = (busData.assignmentVersion || 0) + 1;

    await db.runTransaction(async (transaction) => {
      transaction.update(driverRef, { assignedBusId: null });
      transaction.update(busRef, {
        active: false,
        driverId: null,
        driverName: null,
        assignmentVersion: newVersion,
        assignmentEpoch: now,
        rtdbSyncStatus: "SYNCED"
      });
    });

    if (rtdb) {
      await syncAssignmentToRtdb({
        busId,
        driverUid: null,
        previousDriverUid: detachedDriverId,
        epoch: now,
        version: newVersion
      }, rtdb).catch(() => {});
    }
  } else {
    // Normal active toggle without driver conflict
    await busRef.update({ active: active });
  }

  // If a driver was detached due to bus deactivation, sync their public directory status
  if (detachedDriverId) {
    await syncPublicDriverDirectory(detachedDriverId, db);
  }

  console.log(`[toggleBusActiveStatus] Bus ${busId} active status set to ${active}`);
  return {
    success: true,
    busId,
    active,
    detachedDriverId
  };
}

/**
 * 5. updateDriverStaffProfile
 * Updates driver name, Staff ID (customId), and contact phone number.
 * If Staff ID changes, updates Auth login email with rollback if DB update fails.
 */
async function processUpdateDriverStaffProfile(data, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();
  const auth = dependencies.auth || admin.auth();
  const rtdb = dependencies.rtdb || admin.database();

  await verifyActiveAdmin(context, db);

  const driverUid = data && data.driverUid ? String(data.driverUid).trim() : null;
  if (!driverUid) {
    throw new functions.https.HttpsError("invalid-argument", "driverUid is required.");
  }

  const { cleanName, cleanId, cleanPhone } = validateDriverProfileInputs(data, false);

  const driverRef = db.collection("users").doc(driverUid);
  const driverDoc = await driverRef.get();
  if (!driverDoc.exists) {
    throw new functions.https.HttpsError("not-found", `Driver '${driverUid}' not found.`);
  }
  const driverData = driverDoc.data() || {};
  if (driverData.role !== "DRIVER") {
    throw new functions.https.HttpsError("invalid-argument", `User '${driverUid}' is not a driver.`);
  }

  const originalCustomId = driverData.customId || "";
  const originalName = driverData.name || "";
  const originalEmail = driverData.email || mapStaffEmail(originalCustomId);
  const customIdChanged = cleanId !== originalCustomId.toLowerCase();
  const newEmail = mapStaffEmail(cleanId);

  // If Staff ID changed, verify uniqueness across other users
  if (customIdChanged) {
    const existing = await db.collection("users").where("customId", "==", cleanId).get();
    if (!existing.empty && existing.docs.some(d => d.id !== driverUid)) {
      throw new functions.https.HttpsError("already-exists", `Staff ID '${cleanId}' is already in use by another user.`);
    }

    // Update Auth email first
    await auth.updateUser(driverUid, {
      email: newEmail,
      displayName: cleanName
    });

    try {
      // Update Firestore and RTDB
      await driverRef.update({
        customId: cleanId,
        email: newEmail,
        name: cleanName,
        phoneNumber: cleanPhone
      });

      if (rtdb) {
        await rtdb.ref(`users/${driverUid}`).update({
          customId: cleanId,
          name: cleanName
        });
      }

      // If assigned to a bus, update cached driverName in buses
      if (driverData.assignedBusId) {
        await db.collection("buses").doc(driverData.assignedBusId).update({
          driverName: cleanName
        }).catch(() => {});
      }
    } catch (dbErr) {
      console.error(`[updateDriverStaffProfile] Database update failed. Rolling back Auth email to ${originalEmail}`);
      await auth.updateUser(driverUid, {
        email: originalEmail,
        displayName: originalName
      }).catch(rbErr => console.warn(`[updateDriverStaffProfile] Rollback Auth error:`, rbErr.message));
      throw new functions.https.HttpsError("internal", dbErr.message || "Failed to update driver profile in database.");
    }
  } else {
    // Only name/phone changed
    await auth.updateUser(driverUid, { displayName: cleanName });
    await driverRef.update({
      name: cleanName,
      phoneNumber: cleanPhone
    });

    if (rtdb) {
      await rtdb.ref(`users/${driverUid}`).update({ name: cleanName });
    }

    if (driverData.assignedBusId) {
      await db.collection("buses").doc(driverData.assignedBusId).update({
        driverName: cleanName
      }).catch(() => {});
    }
  }

  // Synchronize to public directory (updates name/phone in public directory)
  await syncPublicDriverDirectory(driverUid, db);

  console.log(`[updateDriverStaffProfile] Driver ${driverUid} profile updated (Staff ID: ${cleanId})`);
  return {
    success: true,
    driverUid,
    customId: cleanId,
    name: cleanName,
    email: customIdChanged ? newEmail : originalEmail,
    phoneNumber: cleanPhone
  };
}

/**
 * 6. reconcileBusRtdb
 * Idempotent one-click or scheduled reconciliation for a bus's RTDB operational nodes.
 */
async function processReconcileBusRtdb(data, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();
  const rtdb = dependencies.rtdb || admin.database();

  await verifyActiveAdmin(context, db);

  const busId = data && data.busId ? String(data.busId).trim() : null;
  if (!busId) {
    throw new functions.https.HttpsError("invalid-argument", "busId is required.");
  }

  const busDoc = await db.collection("buses").doc(busId).get();
  if (!busDoc.exists) {
    throw new functions.https.HttpsError("not-found", `Bus '${busId}' not found.`);
  }
  const busData = busDoc.data() || {};
  const effectiveDriverId = busData.active === false ? null : (busData.driverId || null);
  const now = Date.now();
  const nextVer = (busData.assignmentVersion || 0) + 1;

  if (rtdb) {
    await syncAssignmentToRtdb({
      busId,
      driverUid: effectiveDriverId,
      driverName: busData.driverName || "",
      epoch: now,
      version: nextVer
    }, rtdb);
  }

  await db.collection("buses").doc(busId).update({
    assignmentVersion: nextVer,
    rtdbSyncStatus: "SYNCED",
    rtdbSyncError: null,
    rtdbSyncLastAttempt: now
  });

  return { success: true, busId, rtdbSyncStatus: "SYNCED", assignmentVersion: nextVer };
}

/**
 * 7. onBusAssignmentWritten
 * Reactive Firestore Trigger for buses/{busId}.
 * Provides anti-entropy reconciliation for legacy Android direct writes and out-of-order rejection.
 */
async function processOnBusAssignmentWritten(change, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();
  const rtdb = dependencies.rtdb || admin.database();

  const busId = context && context.params ? context.params.busId : null;
  if (!busId) return null;

  const before = change.before && change.before.exists ? change.before.data() : null;
  const after = change.after && change.after.exists ? change.after.data() : null;

  // 1. Bus document deletion
  if (!after) {
    if (rtdb) {
      try {
        const opSnap = await rtdb.ref(`bus_operators/${busId}`).get();
        const opVal = opSnap.val() || {};
        if (opVal.hasTemporary !== true) {
          await rtdb.ref(`bus_operators/${busId}`).remove();
        } else {
          await rtdb.ref(`bus_operators/${busId}`).update({ permanentDriverUid: null });
        }
      } catch (e) {
        console.warn(`[onBusAssignmentWritten] Warning on bus delete for ${busId}:`, e.message);
      }
    }
    await syncBusDriverContact(busId, db).catch(() => {});
    return null;
  }

  // 2. Check if assignment-relevant fields changed
  const driverChanged = !before || before.driverId !== after.driverId;
  const activeChanged = !before || before.active !== after.active;
  const versionChanged = !before || before.assignmentVersion !== after.assignmentVersion;

  if (!driverChanged && !activeChanged && !versionChanged) {
    return null;
  }

  const incomingVersion = Number(after.assignmentVersion) || 0;
  const now = Date.now();
  const targetDriver = after.active === false ? null : (after.driverId || null);

  // 3. Authoritative Firestore check: verify whether this trigger event has already been superseded
  if (db) {
    try {
      const currentSnap = await db.collection("buses").doc(busId).get();
      if (currentSnap && currentSnap.exists) {
        const currentData = currentSnap.data() || {};
        const currentFirestoreVersion = Number(currentData.assignmentVersion) || 0;
        const currentFirestoreDriver = currentData.active === false ? null : (currentData.driverId || null);

        // A. If Firestore document has already advanced past this event's version:
        if (incomingVersion > 0 && incomingVersion < currentFirestoreVersion) {
          console.warn(`[onBusAssignmentWritten] Discarding stale event for ${busId}: incoming v${incomingVersion} < current Firestore v${currentFirestoreVersion}`);
          return null;
        }

        // B. For legacy Android writes (incomingVersion <= 0) or unversioned writes:
        // If current Firestore document has a different driver than this event's after.driverId,
        // it means a newer write has already modified this bus in Firestore! Discard this stale event.
        if (incomingVersion <= 0 && currentFirestoreDriver !== targetDriver) {
          console.warn(`[onBusAssignmentWritten] Discarding stale legacy event for ${busId}: event driver (${targetDriver}) != current Firestore driver (${currentFirestoreDriver})`);
          return null;
        }
      }
    } catch (e) {
      console.warn(`[onBusAssignmentWritten] Warning checking current Firestore state for ${busId}:`, e.message);
    }
  }

  // 4. Out-of-order & duplicate event check against RTDB
  if (rtdb) {
    try {
      const opSnap = await rtdb.ref(`bus_operators/${busId}`).get();
      const opVal = opSnap.val() || {};
      const currentRtdbVersion = Number(opVal.assignmentVersion) || 0;

      // Discard stale out-of-order event
      if (incomingVersion > 0 && incomingVersion < currentRtdbVersion) {
        console.warn(`[onBusAssignmentWritten] Discarding stale out-of-order event for ${busId}: incoming v${incomingVersion} < current RTDB v${currentRtdbVersion}`);
        return null;
      }

      // Skip duplicate idempotent write
      const currentPerm = opVal.permanentDriverUid || null;
      if (incomingVersion > 0 && incomingVersion === currentRtdbVersion && currentPerm === targetDriver) {
        console.log(`[onBusAssignmentWritten] Duplicate event for ${busId} (v${incomingVersion}). Skipping.`);
        return null;
      }

      // Concurrency check against RTDB & Firestore:
      // If RTDB currently holds a driver different from targetDriver, re-verify with Firestore
      // in case a concurrent write (e.g. Trigger B) committed while this trigger was in flight
      if (currentPerm && currentPerm !== targetDriver) {
        const freshSnap = await db.collection("buses").doc(busId).get();
        if (freshSnap.exists) {
          const freshData = freshSnap.data() || {};
          const freshDriver = freshData.active === false ? null : (freshData.driverId || null);
          if (freshDriver !== targetDriver) {
            console.warn(`[onBusAssignmentWritten] Aborting superseded write for ${busId}: current Firestore driver (${freshDriver}) != event driver (${targetDriver})`);
            return null;
          }
        }
      }

      // 5. Legacy Android write detection: if after.assignmentVersion was not set by Android
      let effectiveVersion = incomingVersion;
      if (incomingVersion <= 0 || incomingVersion <= currentRtdbVersion) {
        effectiveVersion = currentRtdbVersion + 1;
      }

      // 5. Execute authoritative RTDB sync FIRST before updating Firestore version
      // This prevents a re-entrant trigger invocation from finding stale RTDB state
      await syncAssignmentToRtdb({
        busId,
        driverUid: targetDriver,
        driverName: after.driverName || "",
        previousDriverUid: before ? before.driverId : null,
        epoch: now,
        version: effectiveVersion
      }, rtdb);

      // 6. Post-sync Authoritative Convergence Check:
      // Verify whether Firestore was updated while syncAssignmentToRtdb was in flight
      const postSnap = await db.collection("buses").doc(busId).get();
      if (postSnap.exists) {
        const postData = postSnap.data() || {};
        const authoritativeDriver = postData.active === false ? null : (postData.driverId || null);
        if (authoritativeDriver !== targetDriver) {
          console.warn(`[onBusAssignmentWritten] Concurrency race detected for ${busId}: in-flight driver (${targetDriver}) superseded by authoritative Firestore driver (${authoritativeDriver}). Re-converging RTDB.`);
          const reconvergeVersion = Math.max(effectiveVersion, Number(postData.assignmentVersion) || 0) + 1;

          // Re-sync RTDB with the authoritative driver, explicitly clearing targetDriver
          await syncAssignmentToRtdb({
            busId,
            driverUid: authoritativeDriver,
            driverName: postData.driverName || "",
            previousDriverUid: targetDriver,
            epoch: Date.now(),
            version: reconvergeVersion
          }, rtdb);

          // Clear targetDriver's erroneous authorization in both Firestore and RTDB
          if (targetDriver) {
            await db.collection("users").doc(targetDriver).update({ assignedBusId: null }).catch(() => {});
            await rtdb.ref(`users/${targetDriver}`).update({ assignedBusId: null }).catch(() => {});
          }

          // Ensure authoritative driver is correctly authorized in both Firestore and RTDB
          if (authoritativeDriver) {
            await db.collection("users").doc(authoritativeDriver).update({ assignedBusId: busId }).catch(() => {});
            await rtdb.ref(`users/${authoritativeDriver}`).update({ assignedBusId: busId, active: true, role: "DRIVER" }).catch(() => {});
          }

          await db.collection("buses").doc(busId).update({
            assignmentVersion: reconvergeVersion,
            rtdbSyncStatus: "SYNCED",
            rtdbSyncError: null,
            rtdbSyncLastAttempt: Date.now()
          }).catch(() => {});

          return { success: true, busId, version: reconvergeVersion, convergedTo: authoritativeDriver };
        }
      }

      // 7. Cross-Bus Collision Safeguard:
      // If legacy Android assigned this driver to busId while they were still assigned to another bus,
      // unassign the driver from the other bus in both Firestore and RTDB
      if (targetDriver) {
        const conflictBuses = await db.collection("buses")
          .where("driverId", "==", targetDriver)
          .get()
          .catch(() => ({ docs: [] }));

        for (const cDoc of conflictBuses.docs) {
          if (cDoc.id !== busId) {
            const cBusId = cDoc.id;
            const cVer = (cDoc.data().assignmentVersion || 0) + 1;
            console.log(`[onBusAssignmentWritten] Resolving cross-bus collision: Unassigning ${targetDriver} from ${cBusId}`);
            await cDoc.ref.update({
              driverId: null,
              driverName: null,
              assignmentVersion: cVer,
              rtdbSyncStatus: "SYNCED"
            }).catch(() => {});
            await syncAssignmentToRtdb({
              busId: cBusId,
              driverUid: null,
              previousDriverUid: targetDriver,
              epoch: now,
              version: cVer
            }, rtdb).catch(() => {});
          }
        }
      }

      // 8. Reconcile driver profile in Firestore
      if (targetDriver) {
        await db.collection("users").doc(targetDriver).update({
          assignedBusId: busId
        }).catch(() => {});
      }
      if (before && before.driverId && before.driverId !== targetDriver) {
        await db.collection("users").doc(before.driverId).update({
          assignedBusId: null
        }).catch(() => {});
      }

      // 9. Update Firestore bus document AFTER RTDB has successfully committed
      // Any subsequent re-entrant trigger event will find RTDB matching and exit as duplicate
      await db.collection("buses").doc(busId).update({
        assignmentVersion: effectiveVersion,
        rtdbSyncStatus: "SYNCED",
        rtdbSyncError: null,
        rtdbSyncLastAttempt: now
      }).catch(err => console.warn(`[onBusAssignmentWritten] Warning updating bus version for ${busId}:`, err.message));

      await syncBusDriverContact(busId, db).catch(() => {});
    } catch (e) {
      console.error(`[onBusAssignmentWritten] Error reconciling ${busId}:`, e.message);
      await db.collection("buses").doc(busId).update({
        rtdbSyncStatus: "FAILED",
        rtdbSyncError: e.message,
        rtdbSyncLastAttempt: now
      }).catch(() => {});
    }
  }

  return null;
}

/**
 * 8. resetDriverPassword
 * Hardened driver password reset with active-admin verification.
 */
async function processResetDriverPassword(data, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();
  const auth = dependencies.auth || admin.auth();

  await verifyActiveAdmin(context, db);

  const driverUid = data && data.driverUid ? String(data.driverUid).trim() : null;
  const newPassword = data && data.newPassword ? String(data.newPassword) : null;

  if (!driverUid) {
    throw new functions.https.HttpsError("invalid-argument", "Target driver UID must be provided.");
  }
  if (!newPassword || newPassword.length < 6) {
    throw new functions.https.HttpsError("invalid-argument", "Password must be at least 6 characters long.");
  }

  const targetDoc = await db.collection("users").doc(driverUid).get();
  if (!targetDoc.exists) {
    throw new functions.https.HttpsError("not-found", "Target driver profile not found.");
  }
  const targetData = targetDoc.data() || {};
  if (targetData.role !== "DRIVER") {
    throw new functions.https.HttpsError("permission-denied", "Unauthorized: Target user is not a driver.");
  }

  try {
    await auth.updateUser(driverUid, { password: newPassword });
    console.log(`[resetDriverPassword] Successfully reset password for driver ${driverUid}`);
    return { success: true, message: "Driver password reset successfully." };
  } catch (error) {
    console.error(`[resetDriverPassword] Error resetting password for driver ${driverUid}:`, error.message);
    throw new functions.https.HttpsError("internal", error.message || "Failed to reset driver password.");
  }
}

/**
 * 9. deleteDriverAccount
 * Hardened permanent driver account deletion with full Firestore, RTDB, and Auth cleanup.
 */
async function processDeleteDriverAccount(data, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();
  const auth = dependencies.auth || admin.auth();
  const rtdb = dependencies.rtdb || admin.database();

  const { callerUid } = await verifyActiveAdmin(context, db);

  const driverUid = data && data.driverUid ? String(data.driverUid).trim() : null;
  if (!driverUid) {
    throw new functions.https.HttpsError("invalid-argument", "A valid driverUid must be provided.");
  }
  if (driverUid === callerUid) {
    throw new functions.https.HttpsError("invalid-argument", "Administrators cannot delete their own active account.");
  }

  const targetDoc = await db.collection("users").doc(driverUid).get();
  if (targetDoc.exists) {
    const targetData = targetDoc.data() || {};
    if (targetData.role === "ADMIN") {
      throw new functions.https.HttpsError("permission-denied", "Administrator accounts cannot be deleted via the driver management endpoint.");
    }

    // 1. Unassign from assigned bus with monotonic versioning
    if (targetData.assignedBusId) {
      const busId = targetData.assignedBusId;
      const busRef = db.collection("buses").doc(busId);
      let busVer = 1;
      await db.runTransaction(async (transaction) => {
        const bDoc = await transaction.get(busRef);
        if (bDoc.exists) {
          busVer = (bDoc.data().assignmentVersion || 0) + 1;
          transaction.update(busRef, {
            driverId: null,
            driverName: null,
            assignmentVersion: busVer,
            assignmentEpoch: Date.now(),
            rtdbSyncStatus: "SYNCED"
          });
        }
      }).catch((err) => {
        console.warn(`[deleteDriverAccount] Transaction warning unassigning bus ${busId}:`, err.message);
      });

      if (rtdb) {
        await syncAssignmentToRtdb({
          busId,
          driverUid: null,
          previousDriverUid: driverUid,
          epoch: Date.now(),
          version: busVer
        }, rtdb).catch(() => {});
      }
    }

    // Unassign from any other bus if driverId matches
    const busesSnap = await db.collection("buses").where("driverId", "==", driverUid).get();
    for (const bDoc of busesSnap.docs) {
      if (bDoc.id !== targetData.assignedBusId) {
        let otherVer = (bDoc.data().assignmentVersion || 0) + 1;
        await bDoc.ref.update({
          driverId: null,
          driverName: null,
          assignmentVersion: otherVer,
          assignmentEpoch: Date.now(),
          rtdbSyncStatus: "SYNCED"
        }).catch(() => {});
        if (rtdb) {
          await syncAssignmentToRtdb({
            busId: bDoc.id,
            driverUid: null,
            previousDriverUid: driverUid,
            version: otherVer
          }, rtdb).catch(() => {});
        }
      }
    }

    // Clean up temporary bus swap authorization if driver was active in a temporary swap
    if (rtdb) {
      try {
        const busOpsSnap = await rtdb.ref("bus_operators").get();
        const hasData = busOpsSnap && (typeof busOpsSnap.exists === "function" ? busOpsSnap.exists() : Boolean(busOpsSnap.val()));
        if (hasData) {
          const ops = busOpsSnap.val() || {};
          for (const [bId, opData] of Object.entries(ops)) {
            if (opData && opData.temporaryDriverUid === driverUid) {
              console.log(`[deleteDriverAccount] Revoking temporary driver authorization for deleted driver ${driverUid} on ${bId}`);
              await rtdb.ref(`bus_operators/${bId}`).update({
                hasTemporary: false,
                temporaryDriverUid: null,
                temporaryAssignmentId: null
              }).catch(() => {});
            }
          }
        }
      } catch (swapErr) {
        console.warn(`[deleteDriverAccount] RTDB temporary swap check warning:`, swapErr.message);
      }
    }

    // 2. Delete Firestore users document and public directory entry
    await db.collection("users").doc(driverUid).delete().catch(() => {});
    await db.collection("public_driver_directory").doc(driverUid).delete().catch(() => {});
  }

  // 3. Remove RTDB user node
  if (rtdb) {
    await rtdb.ref(`users/${driverUid}`).remove().catch(() => {});
  }

  // 4. Delete Firebase Auth user
  try {
    await auth.deleteUser(driverUid);
    console.log(`[deleteDriverAccount] Successfully deleted user ${driverUid} from Firebase Auth`);
    return { success: true, driverUid, message: `Driver user ${driverUid} deleted from Firebase Authentication.` };
  } catch (error) {
    if (error.code === "auth/user-not-found") {
      return { success: true, driverUid, message: "User was already absent from Firebase Authentication." };
    }
    console.error(`[deleteDriverAccount] Error deleting user ${driverUid}:`, error.message);
    throw new functions.https.HttpsError("internal", error.message || "Failed to delete user from Firebase Authentication.");
  }
}

// =========================================================================
// HTTPS CALLABLE & TRIGGER EXPORTS WITH APP CHECK CONFIGURATION
// =========================================================================

exports.createDriverAccount = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processCreateDriverAccount(data, context);
  });

exports.setBusDriverAssignment = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processSetBusDriverAssignment(data, context);
  });

exports.toggleDriverActiveStatus = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processToggleDriverActiveStatus(data, context);
  });

exports.toggleBusActiveStatus = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processToggleBusActiveStatus(data, context);
  });

exports.updateDriverStaffProfile = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processUpdateDriverStaffProfile(data, context);
  });

exports.reconcileBusRtdb = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processReconcileBusRtdb(data, context);
  });

exports.onBusAssignmentWritten = regionalFunctions.firestore
  .document("buses/{busId}")
  .onWrite(async (change, context) => {
    return processOnBusAssignmentWritten(change, context);
  });

/**
 * 10. reconcileOperationalRtdb
 * Automated scheduled worker running every 5 minutes.
 * Scans for buses with FAILED or stale PENDING RTDB synchronization,
 * verifies authoritative Firestore state, applies idempotent sync,
 * preserves active temporary replacements, and records recovery status.
 */
async function processReconcileOperationalRtdb(dependencies = {}) {
  const db = dependencies.db || admin.firestore();
  const rtdb = dependencies.rtdb || admin.database();

  const now = Date.now();
  const staleThresholdMs = 60 * 1000; // 60 seconds

  // Query buses with FAILED, stale PENDING, or absent/null sync status
  const failedBusesSnap = await db.collection("buses").where("rtdbSyncStatus", "==", "FAILED").get();
  const pendingBusesSnap = await db.collection("buses").where("rtdbSyncStatus", "==", "PENDING").get();

  // In Cloud Firestore, where("field", "==", null) DOES NOT match documents where the field is missing.
  // To discover older/legacy bus records with assigned drivers where rtdbSyncStatus is absent or null,
  // we query all buses with assigned drivers (driverId != null) and evaluate !rtdbSyncStatus in memory.
  const assignedBusesSnap = await db.collection("buses").where("driverId", "!=", null).get().catch(() => ({ docs: [] }));

  const candidatesMap = new Map();
  failedBusesSnap.docs.forEach(doc => candidatesMap.set(doc.id, doc));
  pendingBusesSnap.docs.forEach(doc => {
    const data = doc.data() || {};
    const lastAttempt = Number(data.rtdbSyncLastAttempt) || Number(data.assignmentEpoch) || 0;
    if (now - lastAttempt > staleThresholdMs) {
      candidatesMap.set(doc.id, doc);
    }
  });
  if (assignedBusesSnap && assignedBusesSnap.docs) {
    assignedBusesSnap.docs.forEach(doc => {
      const data = doc.data() || {};
      // Matches both completely missing field (undefined) and explicitly null field
      if (!data.rtdbSyncStatus) {
        candidatesMap.set(doc.id, doc);
      }
    });
  }

  const results = {
    totalEvaluated: candidatesMap.size,
    repaired: 0,
    errors: 0,
    details: []
  };

  for (const [busId, busDoc] of candidatesMap) {
    try {
      // 1. Fresh document read prevents overwriting newer assignments committed while waiting
      const freshSnap = await db.collection("buses").doc(busId).get();
      if (!freshSnap.exists) continue;
      const busData = freshSnap.data() || {};

      // If document was already synced or assignment epoch is newer than run start, skip
      if (busData.rtdbSyncStatus === "SYNCED" || Number(busData.assignmentEpoch) > now) {
        console.log(`[reconcileOperationalRtdb] Skipping ${busId}: already synced or updated by newer assignment.`);
        continue;
      }

      // Check max retry count to prevent endless thrashing on permanently invalid records
      const retryCount = (Number(busData.rtdbSyncRetryCount) || 0) + 1;
      if (retryCount > 5) {
        await busDoc.ref.update({
          rtdbSyncStatus: "PERMANENTLY_FAILED",
          rtdbSyncError: `Exceeded max recovery attempts (5). Requires manual admin intervention.`,
          rtdbSyncLastAttempt: now,
          rtdbSyncRetryCount: retryCount
        });
        results.errors++;
        results.details.push({ busId, status: "PERMANENTLY_FAILED", error: "Exceeded max attempts" });
        continue;
      }

      const targetDriver = busData.active === false ? null : (busData.driverId || null);

      // Read current RTDB state
      const opSnap = await rtdb.ref(`bus_operators/${busId}`).get();
      const opVal = opSnap.val() || {};
      const currentRtdbVersion = Number(opVal.assignmentVersion) || 0;
      const busVersion = Number(busData.assignmentVersion) || 0;

      // Ensure monotonic version progression
      const nextVersion = Math.max(busVersion, currentRtdbVersion) + 1;

      // Authoritative sync preserving temporary swap
      const syncRes = await syncAssignmentToRtdb({
        busId,
        driverUid: targetDriver,
        driverName: busData.driverName || "",
        epoch: now,
        version: nextVersion
      }, rtdb);

      const effectiveVersion = (syncRes && syncRes.version) ? syncRes.version : nextVersion;

      // Reconcile driver profile in RTDB and Firestore if assigned
      if (targetDriver) {
        await db.collection("users").doc(targetDriver).update({
          assignedBusId: busId
        }).catch(() => {});
        await rtdb.ref(`users/${targetDriver}`).update({
          assignedBusId: busId,
          active: true,
          role: "DRIVER"
        }).catch(() => {});
      }

      // Mark bus document SYNCED
      await busDoc.ref.update({
        assignmentVersion: effectiveVersion,
        rtdbSyncStatus: "SYNCED",
        rtdbSyncError: null,
        rtdbSyncLastAttempt: now,
        rtdbSyncRetryCount: 0
      });

      results.repaired++;
      results.details.push({ busId, status: "REPAIRED", version: effectiveVersion });
      console.log(`[reconcileOperationalRtdb] Repaired RTDB sync for ${busId} to version ${effectiveVersion}`);
      await syncBusDriverContact(busId, db).catch(() => {});
    } catch (e) {
      results.errors++;
      results.details.push({ busId, status: "FAILED", error: e.message });
      console.error(`[reconcileOperationalRtdb] Failed repairing ${busId}:`, e.message);
      const busData = busDoc.data() || {};
      const retryCount = (Number(busData.rtdbSyncRetryCount) || 0) + 1;
      await busDoc.ref.update({
        rtdbSyncError: e.message,
        rtdbSyncLastAttempt: now,
        rtdbSyncRetryCount: retryCount
      }).catch(() => {});
    }
  }

  // Also keep public_bus_contacts synchronized for temporary assignments starting/expiring by wall-clock time
  try {
    const contactBuses = new Set();
    const activeTempSnap = await db.collection("temporary_assignments")
      .where("cancelledAt", "==", null)
      .get()
      .catch(() => ({ docs: [] }));
    activeTempSnap.docs.forEach((d) => {
      const bId = d.data() && d.data().busId;
      if (bId) contactBuses.add(String(bId).trim());
    });
    const tempContactsSnap = await db.collection("public_bus_contacts")
      .where("isTemporary", "==", true)
      .get()
      .catch(() => ({ docs: [] }));
    tempContactsSnap.docs.forEach((d) => contactBuses.add(d.id));
    for (const bId of contactBuses) {
      await syncBusDriverContact(bId, db, now).catch(() => {});
    }
  } catch (contactErr) {
    console.warn("[reconcileOperationalRtdb] Contact sync warning:", contactErr.message);
  }

  return results;
}

/**
 * 11. registerBusAccount
 * Atomic bus registration enforcing uniqueness of busNumber and registrationNumber
 * across Firestore collections and index documents within an atomic transaction.
 */
async function processRegisterBusAccount(data, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();

  await verifyActiveAdmin(context, db);

  const rawBusNum = data && data.busNumber ? String(data.busNumber).trim() : "";
  const rawReg = data && data.registrationNumber ? String(data.registrationNumber).trim().toUpperCase() : "";
  const rawCap = data && data.capacity !== undefined ? Number(data.capacity) : 50;
  const rawRouteId = data && data.routeId ? String(data.routeId).trim() : null;

  if (!rawBusNum) {
    throw new functions.https.HttpsError("invalid-argument", "Bus number is required.");
  }
  if (!/^[a-zA-Z0-9_-]{1,10}$/.test(rawBusNum)) {
    throw new functions.https.HttpsError("invalid-argument", "Bus number must be 1-10 alphanumeric characters (e.g. 01, 25, B-01).");
  }

  if (!rawReg) {
    throw new functions.https.HttpsError("invalid-argument", "Registration number is required.");
  }
  if (rawReg.length < 5 || rawReg.length > 20) {
    throw new functions.https.HttpsError("invalid-argument", "Registration number must be between 5 and 20 characters (e.g. KA-34-A-1234).");
  }

  if (isNaN(rawCap) || rawCap < 1 || rawCap > 120 || !Number.isInteger(rawCap)) {
    throw new functions.https.HttpsError("invalid-argument", "Bus seating capacity must be a whole number between 1 and 120.");
  }

  const paddedNum = /^\d+$/.test(rawBusNum) ? rawBusNum.padStart(2, "0") : rawBusNum;
  const busId = rawBusNum.toUpperCase().startsWith("BUS-") ? rawBusNum.toUpperCase() : `BUS-${paddedNum}`;
  const now = Date.now();

  const busRef = db.collection("buses").doc(busId);
  const busNumIndexRef = db.collection("bus_number_index").doc(rawBusNum.toUpperCase());
  const regIndexRef = db.collection("bus_registration_index").doc(rawReg);

  // Pre-transaction check for legacy documents without index entries
  const existingNumSnap = await db.collection("buses").where("busNumber", "==", rawBusNum).limit(1).get().catch(() => ({ empty: true }));
  if (!existingNumSnap.empty) {
    const existingDoc = existingNumSnap.docs ? existingNumSnap.docs[0] : null;
    if (existingDoc && existingDoc.id !== busId) {
      throw new functions.https.HttpsError("already-exists", `A bus with bus number '${rawBusNum}' already exists.`);
    }
  }

  const existingRegSnap = await db.collection("buses").where("registrationNumber", "==", rawReg).limit(1).get().catch(() => ({ empty: true }));
  if (!existingRegSnap.empty) {
    const existingDoc = existingRegSnap.docs ? existingRegSnap.docs[0] : null;
    if (existingDoc && existingDoc.id !== busId) {
      throw new functions.https.HttpsError("already-exists", `A bus with registration number '${rawReg}' already exists.`);
    }
  }

  // Validate routeId existence and active status if initial route provided
  if (rawRouteId) {
    const routeSnap = await db.collection("routes").doc(rawRouteId).get();
    if (!routeSnap.exists) {
      throw new functions.https.HttpsError("not-found", `Route '${rawRouteId}' does not exist.`);
    }
    const routeData = routeSnap.data() || {};
    if (routeData.active === false) {
      throw new functions.https.HttpsError("failed-precondition", `Cannot assign deactivated route '${rawRouteId}'.`);
    }
  }

  await db.runTransaction(async (transaction) => {
    // 1. Check primary bus document
    const busDoc = await transaction.get(busRef);
    if (busDoc.exists) {
      throw new functions.https.HttpsError("already-exists", `Bus with ID '${busId}' already exists.`);
    }

    // 2. Check bus number index document
    const busNumDoc = await transaction.get(busNumIndexRef);
    if (busNumDoc.exists) {
      throw new functions.https.HttpsError("already-exists", `A bus with bus number '${rawBusNum}' already exists.`);
    }

    // 3. Check registration number index document
    const regDoc = await transaction.get(regIndexRef);
    if (regDoc.exists) {
      throw new functions.https.HttpsError("already-exists", `A bus with registration number '${rawReg}' already exists.`);
    }

    const busRecord = {
      busNumber: rawBusNum,
      registrationNumber: rawReg,
      capacity: rawCap,
      active: true,
      routeId: rawRouteId,
      driverId: null,
      driverName: null,
      assignmentVersion: 0,
      assignmentEpoch: now,
      rtdbSyncStatus: "SYNCED",
      rtdbSyncError: null,
      rtdbSyncLastAttempt: now,
      rtdbSyncRetryCount: 0,
      createdAt: now,
      updatedAt: now
    };

    // Atomically set bus document and both index entries
    transaction.set(busRef, busRecord);
    transaction.set(busNumIndexRef, { busId, busNumber: rawBusNum, createdAt: now });
    transaction.set(regIndexRef, { busId, registrationNumber: rawReg, createdAt: now });
  });

  console.log(`[registerBusAccount] Successfully registered bus ${busId} (${rawReg})`);
  return {
    success: true,
    busId,
    busNumber: rawBusNum,
    registrationNumber: rawReg,
    capacity: rawCap,
    active: true,
    routeId: rawRouteId
  };
}

/**
 * 12. updateBusDetails
 * Updates bus registration number, capacity, and route assignment atomically.
 * Maintains registration number unique index entries and safely handles legacy buses.
 */
async function processUpdateBusDetails(data, context, dependencies = {}) {
  const db = dependencies.db || admin.firestore();

  await verifyActiveAdmin(context, db);

  const busId = data && data.busId ? String(data.busId).trim() : null;
  if (!busId) {
    throw new functions.https.HttpsError("invalid-argument", "Bus ID is required.");
  }

  const busRef = db.collection("buses").doc(busId);
  const now = Date.now();

  const rawReg = data.registrationNumber !== undefined ? String(data.registrationNumber).trim().toUpperCase() : undefined;
  const rawCap = data.capacity !== undefined ? Number(data.capacity) : undefined;
  const rawRouteId = data.routeId !== undefined ? (data.routeId ? String(data.routeId).trim() : null) : undefined;

  if (rawReg !== undefined) {
    if (!rawReg || rawReg.length < 5 || rawReg.length > 20) {
      throw new functions.https.HttpsError("invalid-argument", "Registration number must be between 5 and 20 characters (e.g. KA-34-A-1234).");
    }
  }

  if (rawCap !== undefined) {
    if (isNaN(rawCap) || rawCap < 1 || rawCap > 120 || !Number.isInteger(rawCap)) {
      throw new functions.https.HttpsError("invalid-argument", "Bus seating capacity must be a whole number between 1 and 120.");
    }
  }

  // Pre-transaction check for legacy buses if registration number is changing
  if (rawReg) {
    const existingRegSnap = await db.collection("buses").where("registrationNumber", "==", rawReg).limit(1).get().catch(() => ({ empty: true }));
    if (!existingRegSnap.empty) {
      const existingDoc = existingRegSnap.docs ? existingRegSnap.docs[0] : null;
      if (existingDoc && existingDoc.id !== busId) {
        throw new functions.https.HttpsError("already-exists", `Registration number '${rawReg}' is already used by another bus.`);
      }
    }
  }

  // Validate routeId existence and active status if route is being updated
  if (rawRouteId) {
    const routeSnap = await db.collection("routes").doc(rawRouteId).get();
    if (!routeSnap.exists) {
      throw new functions.https.HttpsError("not-found", `Route '${rawRouteId}' does not exist.`);
    }
    const routeData = routeSnap.data() || {};
    if (routeData.active === false) {
      throw new functions.https.HttpsError("failed-precondition", `Cannot assign deactivated route '${rawRouteId}'.`);
    }
  }

  await db.runTransaction(async (transaction) => {
    const busDoc = await transaction.get(busRef);
    if (!busDoc.exists) {
      throw new functions.https.HttpsError("not-found", `Bus '${busId}' does not exist.`);
    }

    const currentData = busDoc.data() || {};
    const updates = { updatedAt: now };

    if (rawReg !== undefined && rawReg !== currentData.registrationNumber) {
      const newRegIndexRef = db.collection("bus_registration_index").doc(rawReg);
      const newRegDoc = await transaction.get(newRegIndexRef);

      if (newRegDoc.exists) {
        const ownerBusId = newRegDoc.data() ? newRegDoc.data().busId : null;
        if (ownerBusId && ownerBusId !== busId) {
          throw new functions.https.HttpsError("already-exists", `Registration number '${rawReg}' is already used by another bus.`);
        }
      }

      // If bus had an old registration number, release the old index if owned by this bus
      const oldReg = currentData.registrationNumber ? String(currentData.registrationNumber).trim().toUpperCase() : null;
      if (oldReg && oldReg !== rawReg) {
        const oldRegIndexRef = db.collection("bus_registration_index").doc(oldReg);
        const oldRegDoc = await transaction.get(oldRegIndexRef);
        if (oldRegDoc.exists && oldRegDoc.data() && oldRegDoc.data().busId === busId) {
          transaction.delete(oldRegIndexRef);
        }
      }

      // Reserve new registration index
      transaction.set(newRegIndexRef, { busId, registrationNumber: rawReg, createdAt: now });
      updates.registrationNumber = rawReg;
    }

    if (rawCap !== undefined) {
      updates.capacity = rawCap;
    }

    if (rawRouteId !== undefined) {
      updates.routeId = rawRouteId;
    }

    transaction.update(busRef, updates);
  });

  console.log(`[updateBusDetails] Successfully updated bus ${busId}`);
  return { success: true, busId };
}

exports.registerBusAccount = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processRegisterBusAccount(data, context);
  });

exports.updateBusDetails = regionalFunctions
  .runWith({ enforceAppCheck: ENFORCE_APP_CHECK })
  .https.onCall(async (data, context) => {
    return processUpdateBusDetails(data, context);
  });

exports.reconcileOperationalRtdb = regionalFunctions.pubsub
  .schedule("every 5 minutes")
  .onRun(async (context) => {
    return processReconcileOperationalRtdb();
  });

/**
 * Firestore background trigger on /users/{userId}.
 * Automatically synchronizes driver state to 'public_driver_directory' whenever
 * a user document is created, updated, or deleted.
 */
exports.onUserWritten = regionalFunctions.firestore
  .document("users/{userId}")
  .onWrite(async (change, context) => {
    const userId = context.params.userId;
    const afterData = change.after.exists ? change.after.data() : null;
    const beforeData = change.before.exists ? change.before.data() : null;
    const isDriverAfter = afterData && afterData.role === "DRIVER";
    const isDriverBefore = beforeData && beforeData.role === "DRIVER";

    if (isDriverAfter || isDriverBefore) {
      await syncPublicDriverDirectory(userId);
      const affectedBuses = new Set();
      if (afterData && afterData.assignedBusId) affectedBuses.add(String(afterData.assignedBusId).trim());
      if (beforeData && beforeData.assignedBusId) affectedBuses.add(String(beforeData.assignedBusId).trim());
      const tempSnap = await admin.firestore().collection("temporary_assignments")
        .where("replacementDriverUid", "==", userId)
        .where("cancelledAt", "==", null)
        .get()
        .catch(() => ({ docs: [] }));
      tempSnap.docs.forEach((d) => {
        const bId = d.data() && d.data().busId;
        if (bId) affectedBuses.add(String(bId).trim());
      });
      for (const bId of affectedBuses) {
        await syncBusDriverContact(bId).catch(() => {});
      }
    }
    return null;
  });

// Export business logic handlers and helpers for automated test harness
exports.processCreateDriverAccount = processCreateDriverAccount;
exports.processSetBusDriverAssignment = processSetBusDriverAssignment;
exports.processToggleDriverActiveStatus = processToggleDriverActiveStatus;
exports.processToggleBusActiveStatus = processToggleBusActiveStatus;
exports.processUpdateDriverStaffProfile = processUpdateDriverStaffProfile;
exports.processReconcileBusRtdb = processReconcileBusRtdb;
exports.processOnBusAssignmentWritten = processOnBusAssignmentWritten;
exports.processReconcileOperationalRtdb = processReconcileOperationalRtdb;
exports.processResetDriverPassword = processResetDriverPassword;
exports.processDeleteDriverAccount = processDeleteDriverAccount;
exports.processRegisterBusAccount = processRegisterBusAccount;
exports.processUpdateBusDetails = processUpdateBusDetails;
exports.validateDriverProfileInputs = validateDriverProfileInputs;
exports.mapStaffEmail = mapStaffEmail;
exports.syncAssignmentToRtdb = syncAssignmentToRtdb;
exports.syncPublicDriverDirectory = syncPublicDriverDirectory;
exports.syncBusDriverContact = syncBusDriverContact;
exports.checkRateLimit = checkRateLimit;
exports.getClientIp = getClientIp;
exports.isPrivateOrInternalIp = isPrivateOrInternalIp;

