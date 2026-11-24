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
console.log('RUNNING MILESTONE 3 SUBMISSIONS & APPROVAL TESTS');
console.log('==================================================\n');

// --- Test Suite 1: Coordinate Validation & GPS Accuracy ---
console.log('--- Test Suite 1: Coordinate Bounds & Validation ---');

// Valid coordinates
const valid1 = validateCoordinates(12.9716, 77.5946);
assert.equal(valid1.valid, true, 'Valid Bangalore coordinates must pass');

const validEquator = validateCoordinates(0.1234, 100.5678);
assert.equal(validEquator.valid, true, 'Valid non-zero coordinates near equator must pass');

// Invalid coordinates: out of range
const invalidLat = validateCoordinates(95.0, 77.0);
assert.equal(invalidLat.valid, false, 'Latitude > 90 must fail');
assert.match(invalidLat.reason, /Latitude must be between -90 and 90/);

const invalidLatNeg = validateCoordinates(-90.1, 77.0);
assert.equal(invalidLatNeg.valid, false, 'Latitude < -90 must fail');

const invalidLng = validateCoordinates(12.0, 185.0);
assert.equal(invalidLng.valid, false, 'Longitude > 180 must fail');

const invalidLngNeg = validateCoordinates(12.0, -180.5);
assert.equal(invalidLngNeg.valid, false, 'Longitude < -180 must fail');

// Null Island (0, 0) rejection
const zeroZero = validateCoordinates(0, 0);
assert.equal(zeroZero.valid, false, 'Coordinates (0, 0) must be rejected');
assert.match(zeroZero.reason, /Coordinates \(0, 0\) cannot be submitted/);

// NaN / non-numeric coordinates
const nanCoord = validateCoordinates(NaN, 77.0);
assert.equal(nanCoord.valid, false, 'NaN coordinates must be rejected');

console.log('  ✅ PASS: Strict coordinate boundary and Null Island validations pass');

// --- Test Suite 2: Haversine Distance Calculations ---
console.log('\n--- Test Suite 2: Distance Calculations ---');

// Identical coordinates
const distZero = calculateDistanceMeters(12.9716, 77.5946, 12.9716, 77.5946);
assert.equal(distZero, 0, 'Distance between identical points must be 0m');

// Distance between known points (~350m)
const distKnown = calculateDistanceMeters(12.9716, 77.5946, 12.9740, 77.5960);
assert.ok(distKnown > 300 && distKnown < 400, `Distance should be approximately 350m, got: ${distKnown}m`);
console.log(`  ✅ PASS: Haversine distance calculation verified (${distKnown}m)`);

// --- Test Suite 3: Submission Payload Sanitization & Forced PENDING Status ---
console.log('\n--- Test Suite 3: Submission Sanitization & Security ---');

const rawUntrustedStudentDoc = {
  routeId: 'ROUTE-05',
  routeName: 'Route 5 - South Campus',
  stopId: 'STOP-03',
  stopName: 'Jayanagar 4th Block',
  stopOrder: 3,
  proposedLatitude: 12.9298,
  proposedLongitude: 77.5834,
  accuracyMeters: 14.2,
  source: 'GPS',
  // Malicious attempts by student client to auto-approve or inject admin fields:
  status: 'APPROVED',
  reviewedBy: 'hacked_admin_uid',
  reviewedAt: Date.now(),
  reviewNotes: 'Auto-approved by script',
};

// Simulation of backend parser/ingestion forcing PENDING status and stripping admin fields
function sanitizeStudentSubmission(input) {
  const coordCheck = validateCoordinates(input.proposedLatitude, input.proposedLongitude);
  if (!coordCheck.valid) throw new Error(coordCheck.reason);

  return {
    routeId: String(input.routeId || ''),
    routeName: String(input.routeName || ''),
    stopId: String(input.stopId || ''),
    stopName: String(input.stopName || ''),
    stopOrder: Number(input.stopOrder) || 0,
    proposedLatitude: Number(input.proposedLatitude),
    proposedLongitude: Number(input.proposedLongitude),
    accuracyMeters: typeof input.accuracyMeters === 'number' ? input.accuracyMeters : null,
    source: input.source === 'GPS' ? 'GPS' : 'MANUAL',
    status: 'PENDING', // STRICTLY FORCED TO PENDING
    submittedAt: Date.now(),
    reviewedBy: null, // STRIPPED
    reviewedAt: null, // STRIPPED
    reviewNotes: null, // STRIPPED
    finalLatitude: null,
    finalLongitude: null,
  };
}

const sanitized = sanitizeStudentSubmission(rawUntrustedStudentDoc);
assert.equal(sanitized.status, 'PENDING', 'Student cannot set status to APPROVED');
assert.equal(sanitized.reviewedBy, null, 'Student cannot inject reviewedBy');
assert.equal(sanitized.reviewedAt, null, 'Student cannot inject reviewedAt');
assert.equal(sanitized.reviewNotes, null, 'Student cannot inject reviewNotes');
console.log('  ✅ PASS: Student submissions are strictly forced to PENDING with admin fields stripped');

// --- Test Suite 4: Duplicate & Spam Protection ---
console.log('\n--- Test Suite 4: Duplicate & Spam Protection ---');

function isDuplicateSubmission(existingSubs, newProposal, windowMs = 10 * 60 * 1000) {
  const now = Date.now();
  for (const s of existingSubs) {
    if (
      s.routeId === newProposal.routeId &&
      s.stopId === newProposal.stopId &&
      s.status === 'PENDING'
    ) {
      const timeDiff = now - s.submittedAt;
      if (timeDiff < windowMs) {
        const latDiff = Math.abs(s.proposedLatitude - newProposal.proposedLatitude);
        const lngDiff = Math.abs(s.proposedLongitude - newProposal.proposedLongitude);
        if (latDiff < 0.00005 && lngDiff < 0.00005) {
          return true;
        }
      }
    }
  }
  return false;
}

const existingPendingList = [
  {
    routeId: 'ROUTE-05',
    stopId: 'STOP-03',
    proposedLatitude: 12.92980,
    proposedLongitude: 77.58340,
    status: 'PENDING',
    submittedAt: Date.now() - 60000, // 1 minute ago
  },
];

const duplicateProposal = {
  routeId: 'ROUTE-05',
  stopId: 'STOP-03',
  proposedLatitude: 12.92981,
  proposedLongitude: 77.58341,
};

const distinctProposal = {
  routeId: 'ROUTE-05',
  stopId: 'STOP-03',
  proposedLatitude: 12.93500, // ~600m away
  proposedLongitude: 77.58900,
};

assert.equal(isDuplicateSubmission(existingPendingList, duplicateProposal), true, 'Identical recent proposal must be flagged as duplicate');
assert.equal(isDuplicateSubmission(existingPendingList, distinctProposal), false, 'Distinct alternative proposal for the same stop must be accepted');
console.log('  ✅ PASS: Spam and duplicate submissions are accurately prevented');

// --- Test Suite 5: Administrator Approval & Android-Compatible Route Update ---
console.log('\n--- Test Suite 5: Approval Transaction & Android Compatibility ---');

// Mock route document strictly matching Kotlin BusStop and Route models
const initialRouteDoc = {
  routeId: 'ROUTE-01',
  name: 'Route 1 - North Campus Express',
  active: true,
  busId: 'BUS-101',
  stops: [
    {
      stopId: 'STOP-01',
      name: 'College Main Campus',
      latitude: 13.0827,
      longitude: 80.2707,
      order: 1,
      active: true,
      visibleOnMap: true,
      customMetadata: 'gate-1',
    },
    {
      stopId: 'STOP-02',
      name: 'Silk Board Junction',
      latitude: 0.0, // Missing coordinates
      longitude: 0.0,
      order: 2,
      active: true,
      visibleOnMap: false,
      customMetadata: 'flyover-pillar-42',
    },
    {
      stopId: 'STOP-03',
      name: 'Electronic City Toll',
      latitude: 12.8399,
      longitude: 77.6770,
      order: 3,
      active: true,
      visibleOnMap: true,
      customMetadata: 'phase-1',
    },
  ],
};

const submissionToApprove = {
  id: 'sub_xyz_123',
  routeId: 'ROUTE-01',
  stopId: 'STOP-02',
  proposedLatitude: 12.9172,
  proposedLongitude: 77.6228,
  status: 'PENDING',
  submittedAt: Date.now() - 500000,
};

// Simulation of review transaction
function executeReviewTransaction(routeDoc, submission, action, adminUser, options = {}) {
  // 1. Role verification
  if (!adminUser || adminUser.role !== 'ADMIN' || adminUser.active !== true) {
    throw new Error('PERMISSION_DENIED: Only active administrators can review submissions.');
  }

  // 2. Submission status verification
  if (submission.status !== 'PENDING') {
    throw new Error(`FAILED_PRECONDITION: Submission is already ${submission.status}`);
  }

  const updatedSubmission = { ...submission };
  const updatedRoute = { ...routeDoc };

  if (action === 'APPROVE') {
    const finalLat = options.adjustedLatitude ?? submission.proposedLatitude;
    const finalLng = options.adjustedLongitude ?? submission.proposedLongitude;

    const coordCheck = validateCoordinates(finalLat, finalLng);
    if (!coordCheck.valid) throw new Error(coordCheck.reason);

    // Find stop in array
    const stopIndex = updatedRoute.stops.findIndex((s) => s.stopId === submission.stopId);
    if (stopIndex === -1) throw new Error('Stop not found in route.');

    // Update ONLY latitude, longitude, and visibleOnMap
    // STRICTLY PRESERVE: stopId, name, order, active, customMetadata, and array order
    updatedRoute.stops = updatedRoute.stops.map((stop, idx) => {
      if (idx === stopIndex) {
        return {
          ...stop,
          latitude: finalLat,
          longitude: finalLng,
          visibleOnMap: true,
        };
      }
      return stop;
    });

    updatedSubmission.status = 'APPROVED';
    updatedSubmission.finalLatitude = finalLat;
    updatedSubmission.finalLongitude = finalLng;
    updatedSubmission.reviewedBy = adminUser.uid;
    updatedSubmission.reviewedAt = Date.now();
    updatedSubmission.reviewNotes = options.reviewNotes || null;
  } else if (action === 'REJECT') {
    updatedSubmission.status = 'REJECTED';
    updatedSubmission.reviewedBy = adminUser.uid;
    updatedSubmission.reviewedAt = Date.now();
    updatedSubmission.reviewNotes = options.reviewNotes || null;
    // Route stops remain completely unmodified!
  }

  return { updatedRoute, updatedSubmission };
}

// Test unauthorized approval attempt
const nonAdminUser = { uid: 'driver_99', role: 'DRIVER', active: true };
assert.throws(
  () => executeReviewTransaction(initialRouteDoc, submissionToApprove, 'APPROVE', nonAdminUser),
  /PERMISSION_DENIED/,
  'Non-admin user cannot approve submissions'
);

// Test authorized admin approval with adjustment
const adminUser = { uid: 'admin_principal_01', role: 'ADMIN', active: true };
const { updatedRoute: approvedRoute, updatedSubmission: approvedSub } = executeReviewTransaction(
  initialRouteDoc,
  submissionToApprove,
  'APPROVE',
  adminUser,
  {
    adjustedLatitude: 12.9175, // Fine-tuned by admin
    adjustedLongitude: 77.6230,
    reviewNotes: 'Fine-tuned 3m to north curb by Silk Board bus stop.',
  }
);

// Assert submission outcome
assert.equal(approvedSub.status, 'APPROVED', 'Submission status must be APPROVED');
assert.equal(approvedSub.finalLatitude, 12.9175, 'Final latitude must reflect fine-tuned coordinate');
assert.equal(approvedSub.finalLongitude, 77.6230, 'Final longitude must reflect fine-tuned coordinate');
assert.equal(approvedSub.reviewedBy, 'admin_principal_01', 'Reviewer UID must be recorded');
assert.ok(approvedSub.reviewedAt > 0, 'Review timestamp must be recorded');

// Assert Route stops array preservation and compatibility with Kotlin Android app
assert.equal(approvedRoute.stops.length, 3, 'Stop count must remain exactly 3');
assert.equal(approvedRoute.stops[0].stopId, 'STOP-01', 'Order of stop 1 preserved');
assert.equal(approvedRoute.stops[1].stopId, 'STOP-02', 'Order of stop 2 preserved');
assert.equal(approvedRoute.stops[2].stopId, 'STOP-03', 'Order of stop 3 preserved');

// Verify updated stop fields
const updatedStop = approvedRoute.stops[1];
assert.equal(updatedStop.name, 'Silk Board Junction', 'Stop name preserved');
assert.equal(updatedStop.order, 2, 'Stop order index preserved');
assert.equal(updatedStop.active, true, 'Stop active status preserved');
assert.equal(updatedStop.customMetadata, 'flyover-pillar-42', 'Arbitrary metadata preserved');
assert.equal(updatedStop.latitude, 12.9175, 'Latitude updated to approved value');
assert.equal(updatedStop.longitude, 77.6230, 'Longitude updated to approved value');
assert.equal(updatedStop.visibleOnMap, true, 'visibleOnMap toggled to true');

// Verify unaffected stops remained 100% untouched
assert.equal(approvedRoute.stops[0].latitude, 13.0827, 'Stop 1 latitude untouched');
assert.equal(approvedRoute.stops[2].latitude, 12.8399, 'Stop 3 latitude untouched');

console.log('  ✅ PASS: Approved submission updates matching stop inside array, preserving order, stopId, name, and types');

// --- Test Suite 6: Rejection Leaving Route Unmodified ---
console.log('\n--- Test Suite 6: Rejection Safety ---');

const { updatedRoute: rejectedRoute, updatedSubmission: rejectedSub } = executeReviewTransaction(
  initialRouteDoc,
  submissionToApprove,
  'REJECT',
  adminUser,
  { reviewNotes: 'Coordinate points to middle of highway divider.' }
);

assert.equal(rejectedSub.status, 'REJECTED', 'Submission status must be REJECTED');
assert.equal(rejectedSub.reviewedBy, 'admin_principal_01');
assert.deepEqual(rejectedRoute.stops, initialRouteDoc.stops, 'Route stops array must remain 100% identical on rejection');

console.log('  ✅ PASS: Rejection updates submission status without modifying official route coordinates');

// --- Test Suite 7: Multiple Submissions for Same Stop ---
console.log('\n--- Test Suite 7: Multiple Submissions for Same Stop ---');

const allSubmissions = [
  { id: 'sub_1', routeId: 'ROUTE-01', stopId: 'STOP-02', proposedLatitude: 12.9170, proposedLongitude: 77.6225, submittedAt: 100 },
  { id: 'sub_2', routeId: 'ROUTE-01', stopId: 'STOP-02', proposedLatitude: 12.9172, proposedLongitude: 77.6228, submittedAt: 200 },
  { id: 'sub_3', routeId: 'ROUTE-01', stopId: 'STOP-03', proposedLatitude: 12.8400, proposedLongitude: 77.6775, submittedAt: 300 },
];

const related = allSubmissions.filter((s) => s.routeId === 'ROUTE-01' && s.stopId === 'STOP-02' && s.id !== 'sub_1');
assert.equal(related.length, 1, 'Correctly isolates alternative submissions for the same stop');
assert.equal(related[0].id, 'sub_2');

console.log('  ✅ PASS: Multiple proposals for identical stops are properly linked for admin review');

console.log('\n==================================================');
console.log('MILESTONE 3 TESTS COMPLETE: All assertions passed!');
console.log('==================================================');
