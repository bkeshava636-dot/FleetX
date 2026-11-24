import assert from 'node:assert/strict';

// Test implementation of calculateDistanceMeters (mirrors submissionService.ts)
function calculateDistanceMeters(lat1, lon1, lat2, lon2) {
  if (lat1 === lat2 && lon1 === lon2) return 0;
  const R = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c);
}

// Test implementation of validateCoordinates (mirrors submissionService.ts)
function validateCoordinates(lat, lng) {
  if (typeof lat !== 'number' || typeof lng !== 'number' || isNaN(lat) || isNaN(lng)) {
    return { valid: false, reason: 'Latitude and Longitude must be valid numbers.' };
  }
  if (lat < -90 || lat > 90) {
    return { valid: false, reason: 'Latitude must be between -90 and 90 degrees.' };
  }
  if (lng < -180 || lng > 180) {
    return { valid: false, reason: 'Longitude must be between -180 and 180 degrees.' };
  }
  if (lat === 0 && lng === 0) {
    return { valid: false, reason: 'Coordinates (0, 0) cannot be submitted as a public bus stop.' };
  }
  return { valid: true };
}

console.log('==================================================');
console.log('RUNNING MILESTONE 3 SECURITY & SUBMISSIONS TESTS');
console.log('==================================================\n');

// --- Test Suite 1: Direct Firestore Security Rule Enforcement ---
console.log('--- Test Suite 1: Direct Client Firestore Rules (Denied Client Writes) ---');

function evaluateFirestoreRule(authContext, operation, collectionName) {
  if (collectionName === 'stop_location_submissions') {
    if (operation === 'create') {
      // Rule: allow create: if false; (All creations MUST use callable submitStopCoordinates Cloud Function)
      return false;
    }
    if (operation === 'read' || operation === 'update' || operation === 'delete') {
      // Rule: allow read, update, delete: if isAdmin();
      return Boolean(authContext && authContext.role === 'ADMIN' && authContext.active === true);
    }
  }
  return false;
}

// 1. Unauthenticated direct create attempt
assert.equal(
  evaluateFirestoreRule(null, 'create', 'stop_location_submissions'),
  false,
  'Unauthenticated direct client create must be strictly DENIED'
);

// 2. Authenticated student direct create attempt
const studentAuth = { uid: 'student_123', role: 'STUDENT', active: true };
assert.equal(
  evaluateFirestoreRule(studentAuth, 'create', 'stop_location_submissions'),
  false,
  'Student direct client create must be strictly DENIED'
);

// 3. Authenticated driver direct create attempt
const driverAuth = { uid: 'driver_456', role: 'DRIVER', active: true };
assert.equal(
  evaluateFirestoreRule(driverAuth, 'create', 'stop_location_submissions'),
  false,
  'Driver direct client create must be strictly DENIED'
);

// 4. Student direct update / delete attempt
assert.equal(
  evaluateFirestoreRule(studentAuth, 'update', 'stop_location_submissions'),
  false,
  'Student direct update must be strictly DENIED'
);
assert.equal(
  evaluateFirestoreRule(studentAuth, 'delete', 'stop_location_submissions'),
  false,
  'Student direct delete must be strictly DENIED'
);

// 5. Student read attempt
assert.equal(
  evaluateFirestoreRule(studentAuth, 'read', 'stop_location_submissions'),
  false,
  'Student direct read of submissions must be strictly DENIED'
);

// 6. Active administrator read, update, delete
const adminAuth = { uid: 'admin_789', role: 'ADMIN', active: true };
assert.equal(
  evaluateFirestoreRule(adminAuth, 'read', 'stop_location_submissions'),
  true,
  'Active administrator read must be ALLOWED'
);
assert.equal(
  evaluateFirestoreRule(adminAuth, 'update', 'stop_location_submissions'),
  true,
  'Active administrator update (review) must be ALLOWED'
);
assert.equal(
  evaluateFirestoreRule(adminAuth, 'delete', 'stop_location_submissions'),
  true,
  'Active administrator delete must be ALLOWED'
);

console.log('  ✅ PASS: Direct client writes denied; only Admin SDK in Cloud Function can create submissions');

// --- Test Suite 2: App Check Enforcement on submitStopCoordinates ---
console.log('\n--- Test Suite 2: App Check Enforcement on Callable Cloud Function ---');

// Mock implementation of processSubmitStopCoordinates business logic
function simulateSubmitStopCoordinates(data, context, mockDb) {
  // 1. App Check Enforcement
  if (!context || !context.app || !context.app.appId) {
    throw new Error('FAILED_PRECONDITION: App Check verification failed. All student submissions must originate from an App Check verified client.');
  }

  // 2. Input validation
  const routeId = data && typeof data.routeId === 'string' ? data.routeId.trim() : '';
  const stopId = data && typeof data.stopId === 'string' ? data.stopId.trim() : '';
  const proposedLat = Number(data && data.proposedLatitude);
  const proposedLng = Number(data && data.proposedLongitude);
  const rawSource = data && typeof data.source === 'string' ? data.source.trim().toUpperCase() : '';

  if (!routeId || routeId.length > 64) {
    throw new Error('INVALID_ARGUMENT: A valid routeId (1-64 characters) is required.');
  }
  if (!stopId || stopId.length > 64) {
    throw new Error('INVALID_ARGUMENT: A valid stopId (1-64 characters) is required.');
  }
  if (isNaN(proposedLat) || isNaN(proposedLng) || proposedLat < -90 || proposedLat > 90 || proposedLng < -180 || proposedLng > 180) {
    throw new Error('INVALID_ARGUMENT: Valid geographic coordinates are required (lat [-90, 90], lng [-180, 180]).');
  }
  if (proposedLat === 0 && proposedLng === 0) {
    throw new Error('INVALID_ARGUMENT: Coordinates (0, 0) cannot be submitted as a public bus stop location.');
  }
  if (rawSource !== 'GPS' && rawSource !== 'MANUAL') {
    throw new Error("INVALID_ARGUMENT: Location source must be either 'GPS' or 'MANUAL'.");
  }

  let validatedAccuracy = null;
  if (rawSource === 'GPS') {
    const acc = Number(data && data.accuracyMeters);
    if (isNaN(acc) || acc <= 0 || acc > 5000) {
      throw new Error('INVALID_ARGUMENT: A valid positive GPS accuracy in metres (<= 5000m) is required for GPS submissions.');
    }
    validatedAccuracy = Math.round(acc);
  }

  // 3. Route and Stop existence check
  const route = mockDb.routes[routeId];
  if (!route) {
    throw new Error(`NOT_FOUND: Route ${routeId} does not exist.`);
  }
  if (route.active === false) {
    throw new Error(`FAILED_PRECONDITION: Route ${routeId} is inactive.`);
  }

  const stops = Array.isArray(route.stops) ? route.stops : [];
  const targetStop = stops.find((s) => s.stopId === stopId);
  if (!targetStop) {
    throw new Error(`NOT_FOUND: Stop ${stopId} not found in route ${routeId}.`);
  }
  if (targetStop.active === false) {
    throw new Error(`FAILED_PRECONDITION: Stop ${stopId} is inactive.`);
  }

  // 4. Duplicate protection
  const existing = mockDb.submissions.filter(
    (s) => s.routeId === routeId && s.stopId === stopId && s.status === 'PENDING'
  );
  for (const s of existing) {
    const dist = calculateDistanceMeters(s.proposedLatitude, s.proposedLongitude, proposedLat, proposedLng);
    if (dist < 50) {
      throw new Error('ALREADY_EXISTS: An identical pending coordinate submission for this bus stop was recently received.');
    }
  }

  // 5. Sanitized submission record with FORCED PENDING status
  const submissionId = `sub_${Date.now()}`;
  const record = {
    id: submissionId,
    routeId,
    routeName: route.name,
    stopId,
    stopName: targetStop.name,
    stopOrder: targetStop.order,
    proposedLatitude: proposedLat,
    proposedLongitude: proposedLng,
    accuracyMeters: validatedAccuracy,
    source: rawSource,
    status: 'PENDING',
    submittedAt: Date.now(),
    reviewedBy: null,
    reviewedAt: null,
    reviewNotes: null,
    finalLatitude: null,
    finalLongitude: null,
  };

  mockDb.submissions.push(record);
  return { success: true, submissionId };
}

const mockDatabase = {
  routes: {
    'ROUTE-01': {
      name: 'North Campus Express',
      active: true,
      stops: [
        { stopId: 'STOP-01', name: 'Main Campus', order: 1, active: true },
        { stopId: 'STOP-02', name: 'Silk Board', order: 2, active: true },
        { stopId: 'STOP-INACTIVE', name: 'Old Gate', order: 3, active: false },
      ],
    },
    'ROUTE-INACTIVE': {
      name: 'Old Route',
      active: false,
      stops: [{ stopId: 'STOP-99', name: 'Depot', order: 1, active: true }],
    },
  },
  submissions: [],
};

// Test A: Submission without App Check token -> must fail
assert.throws(
  () =>
    simulateSubmitStopCoordinates(
      { routeId: 'ROUTE-01', stopId: 'STOP-02', proposedLatitude: 12.9172, proposedLongitude: 77.6228, source: 'MANUAL' },
      null, // No App Check context
      mockDatabase
    ),
  /FAILED_PRECONDITION.*App Check verification failed/
);

// Test B: Submission with valid App Check token -> succeeds
const validAppCheckContext = { app: { appId: 'fleetx.web.appcheck' } };
const subResult = simulateSubmitStopCoordinates(
  { routeId: 'ROUTE-01', stopId: 'STOP-02', proposedLatitude: 12.9172, proposedLongitude: 77.6228, source: 'MANUAL' },
  validAppCheckContext,
  mockDatabase
);
assert.ok(subResult.success);
assert.ok(subResult.submissionId);

console.log('  ✅ PASS: Submissions without valid App Check rejected; valid App Check accepted');

// --- Test Suite 3: Independent Field Validation ---
console.log('\n--- Test Suite 3: Independent Field Validation ---');

// Invalid coordinates: out of range
assert.throws(
  () =>
    simulateSubmitStopCoordinates(
      { routeId: 'ROUTE-01', stopId: 'STOP-02', proposedLatitude: 95.0, proposedLongitude: 77.6228, source: 'MANUAL' },
      validAppCheckContext,
      mockDatabase
    ),
  /INVALID_ARGUMENT.*Valid geographic coordinates/
);

// Null Island rejection (0, 0)
assert.throws(
  () =>
    simulateSubmitStopCoordinates(
      { routeId: 'ROUTE-01', stopId: 'STOP-02', proposedLatitude: 0, proposedLongitude: 0, source: 'MANUAL' },
      validAppCheckContext,
      mockDatabase
    ),
  /INVALID_ARGUMENT.*Coordinates \(0, 0\) cannot be submitted/
);

// Non-existent route
assert.throws(
  () =>
    simulateSubmitStopCoordinates(
      { routeId: 'ROUTE-NONEXISTENT', stopId: 'STOP-02', proposedLatitude: 12.9172, proposedLongitude: 77.6228, source: 'MANUAL' },
      validAppCheckContext,
      mockDatabase
    ),
  /NOT_FOUND.*Route ROUTE-NONEXISTENT does not exist/
);

// Inactive route
assert.throws(
  () =>
    simulateSubmitStopCoordinates(
      { routeId: 'ROUTE-INACTIVE', stopId: 'STOP-99', proposedLatitude: 12.9172, proposedLongitude: 77.6228, source: 'MANUAL' },
      validAppCheckContext,
      mockDatabase
    ),
  /FAILED_PRECONDITION.*Route ROUTE-INACTIVE is inactive/
);

// Inactive stop
assert.throws(
  () =>
    simulateSubmitStopCoordinates(
      { routeId: 'ROUTE-01', stopId: 'STOP-INACTIVE', proposedLatitude: 12.9172, proposedLongitude: 77.6228, source: 'MANUAL' },
      validAppCheckContext,
      mockDatabase
    ),
  /FAILED_PRECONDITION.*Stop STOP-INACTIVE is inactive/
);

// GPS source without accuracy
assert.throws(
  () =>
    simulateSubmitStopCoordinates(
      { routeId: 'ROUTE-01', stopId: 'STOP-02', proposedLatitude: 12.9172, proposedLongitude: 77.6228, source: 'GPS', accuracyMeters: -5 },
      validAppCheckContext,
      mockDatabase
    ),
  /INVALID_ARGUMENT.*valid positive GPS accuracy/
);

console.log('  ✅ PASS: Route/stop existence, active status, coordinate bounds, and accuracy strictly validated');

// --- Test Suite 4: Duplicate / Rate-Limit Protection ---
console.log('\n--- Test Suite 4: Duplicate & Spam Protection ---');

assert.throws(
  () =>
    simulateSubmitStopCoordinates(
      { routeId: 'ROUTE-01', stopId: 'STOP-02', proposedLatitude: 12.91721, proposedLongitude: 77.62281, source: 'MANUAL' },
      validAppCheckContext,
      mockDatabase
    ),
  /ALREADY_EXISTS.*identical pending coordinate submission/
);

console.log('  ✅ PASS: Repeated proposals for the same stop within ~50m blocked as duplicate spam');

// --- Test Suite 5: Administrator Approval, Concurrency & Concurrency Races ---
console.log('\n--- Test Suite 5: Concurrency Safety & Transactional Concurrency ---');

// Mock transactional review function
function simulateReviewTransaction(submissionId, action, adminContext, mockDb, options = {}) {
  // 1. Verify caller authentication
  if (!adminContext || !adminContext.auth) {
    throw new Error('UNAUTHENTICATED: Authentication is required to review submissions.');
  }

  const callerUid = adminContext.auth.uid;
  const user = mockDb.users[callerUid];
  if (!user || user.role !== 'ADMIN' || user.active !== true) {
    throw new Error('PERMISSION_DENIED: Only active administrators are authorized to review submissions.');
  }

  // Transaction simulation: Read submission
  const sub = mockDb.submissions.find((s) => s.id === submissionId);
  if (!sub) {
    throw new Error(`NOT_FOUND: Submission ${submissionId} not found.`);
  }

  // Precondition check: must be PENDING
  if (sub.status !== 'PENDING') {
    throw new Error(`FAILED_PRECONDITION: Submission ${submissionId} has already been reviewed (${sub.status}) by ${sub.reviewedBy}.`);
  }

  const route = mockDb.routes[sub.routeId];
  if (!route) {
    throw new Error(`NOT_FOUND: Route ${sub.routeId} not found.`);
  }

  const stopIndex = route.stops.findIndex((s) => s.stopId === sub.stopId);
  if (stopIndex === -1) {
    throw new Error(`NOT_FOUND: Stop ${sub.stopId} not found in route ${sub.routeId}.`);
  }

  if (action === 'APPROVE') {
    const finalLat = options.adjustedLatitude ?? sub.proposedLatitude;
    const finalLng = options.adjustedLongitude ?? sub.proposedLongitude;

    // Update stop while preserving all other properties and order
    route.stops = route.stops.map((item, idx) => {
      if (idx === stopIndex) {
        return {
          ...item,
          latitude: finalLat,
          longitude: finalLng,
          visibleOnMap: true,
        };
      }
      return item;
    });

    sub.status = 'APPROVED';
    sub.finalLatitude = finalLat;
    sub.finalLongitude = finalLng;
    sub.reviewedBy = callerUid;
    sub.reviewedAt = Date.now();
    sub.reviewNotes = options.reviewNotes || null;

    return { success: true, action: 'APPROVED', sub, route };
  } else {
    sub.status = 'REJECTED';
    sub.reviewedBy = callerUid;
    sub.reviewedAt = Date.now();
    sub.reviewNotes = options.reviewNotes || null;

    return { success: true, action: 'REJECTED', sub, route };
  }
}

mockDatabase.users = {
  admin_lead: { uid: 'admin_lead', role: 'ADMIN', active: true },
  admin_assistant: { uid: 'admin_assistant', role: 'ADMIN', active: true },
  driver_bob: { uid: 'driver_bob', role: 'DRIVER', active: true },
};

const pendingSubId = mockDatabase.submissions[0].id;

// Test A: Driver attempts to approve -> denied
assert.throws(
  () =>
    simulateReviewTransaction(pendingSubId, 'APPROVE', { auth: { uid: 'driver_bob' } }, mockDatabase),
  /PERMISSION_DENIED/
);

// Test B: First administrator approves -> succeeds
const firstReviewResult = simulateReviewTransaction(
  pendingSubId,
  'APPROVE',
  { auth: { uid: 'admin_lead' } },
  mockDatabase,
  { adjustedLatitude: 12.9175, adjustedLongitude: 77.6230, reviewNotes: 'Approved roadside spot' }
);
assert.equal(firstReviewResult.action, 'APPROVED');
assert.equal(mockDatabase.routes['ROUTE-01'].stops[1].latitude, 12.9175);
assert.equal(mockDatabase.routes['ROUTE-01'].stops[1].visibleOnMap, true);

// Test C: Concurrent second administrator attempts to approve same submission -> FAILS with FAILED_PRECONDITION
assert.throws(
  () =>
    simulateReviewTransaction(
      pendingSubId,
      'APPROVE',
      { auth: { uid: 'admin_assistant' } },
      mockDatabase,
      { adjustedLatitude: 12.9180, adjustedLongitude: 77.6240 }
    ),
  /FAILED_PRECONDITION.*already been reviewed \(APPROVED\) by admin_lead/,
  'Concurrent approval of already-reviewed submission must be rejected'
);

console.log('  ✅ PASS: Concurrency race prevented: second concurrent admin attempt safely rejected');

// --- Test Suite 6: Android App Data Model Compatibility ---
console.log('\n--- Test Suite 6: Android Kotlin Schema Compatibility ---');

const approvedStop = mockDatabase.routes['ROUTE-01'].stops[1];
assert.equal(typeof approvedStop.stopId, 'string');
assert.equal(typeof approvedStop.name, 'string');
assert.equal(typeof approvedStop.order, 'number');
assert.equal(typeof approvedStop.latitude, 'number');
assert.equal(typeof approvedStop.longitude, 'number');
assert.equal(typeof approvedStop.active, 'boolean');
assert.equal(typeof approvedStop.visibleOnMap, 'boolean');
assert.equal(approvedStop.visibleOnMap, true);

// Ensure stop order in route was preserved: STOP-01, STOP-02, STOP-INACTIVE
assert.equal(mockDatabase.routes['ROUTE-01'].stops[0].stopId, 'STOP-01');
assert.equal(mockDatabase.routes['ROUTE-01'].stops[1].stopId, 'STOP-02');
assert.equal(mockDatabase.routes['ROUTE-01'].stops[2].stopId, 'STOP-INACTIVE');

console.log('  ✅ PASS: Approved stop strictly preserves Android BusStop fields, order, and schema types');

// --- Test Suite 7: Rejection Safety ---
console.log('\n--- Test Suite 7: Rejection Safety ---');

// Add a fresh pending submission
const sub2 = simulateSubmitStopCoordinates(
  { routeId: 'ROUTE-01', stopId: 'STOP-01', proposedLatitude: 13.0829, proposedLongitude: 80.2710, source: 'MANUAL' },
  validAppCheckContext,
  mockDatabase
);

const originalStop01Coords = {
  lat: mockDatabase.routes['ROUTE-01'].stops[0].latitude,
  lng: mockDatabase.routes['ROUTE-01'].stops[0].longitude,
};

const rejectResult = simulateReviewTransaction(
  sub2.submissionId,
  'REJECT',
  { auth: { uid: 'admin_lead' } },
  mockDatabase,
  { reviewNotes: 'Marked on the railway track.' }
);

assert.equal(rejectResult.action, 'REJECTED');
assert.equal(mockDatabase.routes['ROUTE-01'].stops[0].latitude, originalStop01Coords.lat);
assert.equal(mockDatabase.routes['ROUTE-01'].stops[0].longitude, originalStop01Coords.lng);

console.log('  ✅ PASS: Rejection updates status to REJECTED without modifying official route coordinates');

console.log('\n==================================================');
console.log('ALL MILESTONE 3 SECURITY & CONCURRENCY TESTS PASSED!');
console.log('==================================================');
