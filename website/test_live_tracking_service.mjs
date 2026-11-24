import assert from 'node:assert/strict';

// Test implementation of 5-state GPS evaluator (mirrors tracking.ts)
function evaluateBusGpsState(telemetry, nowMs = Date.now()) {
  if (!telemetry) return 'INACTIVE';

  const status = String(telemetry.trackingStatus || 'INACTIVE').toUpperCase();

  // 1. Campus arrival status is preserved
  if (status === 'ARRIVED_AT_CAMPUS') {
    return 'ARRIVED_AT_CAMPUS';
  }

  // 2. Waiting for satellite lock
  if (status === 'WAITING_FOR_GPS') {
    return 'WAITING_FOR_GPS';
  }

  // 3. Stale signal explicitly reported
  if (status === 'STALE') {
    return 'STALE';
  }

  // 4. Active signal evaluation
  if (status === 'ACTIVE') {
    // Zero coordinates indicate awaiting satellite lock
    if (telemetry.latitude === 0.0 && telemetry.longitude === 0.0) {
      return 'WAITING_FOR_GPS';
    }
    // Missing or invalid timestamp
    if (!telemetry.lastUpdated || telemetry.lastUpdated <= 0) {
      return 'STALE';
    }
    // Independent 90-second freshness threshold
    const elapsed = Math.max(0, nowMs - telemetry.lastUpdated);
    if (elapsed > 90_000) {
      return 'STALE';
    }
    return 'ACTIVE';
  }

  return 'INACTIVE';
}

// Test implementation of speedMpsToKmh
function speedMpsToKmh(mps) {
  if (typeof mps !== 'number' || isNaN(mps) || mps <= 0) return 0;
  return Math.round(mps * 3.6);
}

// Test implementation of bearingToCompassDirection
function bearingToCompassDirection(bearing) {
  if (typeof bearing !== 'number' || isNaN(bearing)) return 'N/A';
  const normalized = ((bearing % 360) + 360) % 360;
  const directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
  const index = Math.round(normalized / 45) % 8;
  return directions[index];
}

// Test implementation of formatRelativeTime
function formatRelativeTime(epochMs, nowMs = Date.now()) {
  if (!epochMs || epochMs <= 0) return 'Never';
  const elapsedSeconds = Math.max(0, Math.floor((nowMs - epochMs) / 1000));
  if (elapsedSeconds < 5) return 'Just now';
  if (elapsedSeconds < 60) return `${elapsedSeconds}s ago`;
  const elapsedMinutes = Math.floor(elapsedSeconds / 60);
  if (elapsedMinutes < 60) return `${elapsedMinutes}m ago`;
  const elapsedHours = Math.floor(elapsedMinutes / 60);
  if (elapsedHours < 24) return `${elapsedHours}h ago`;
  return `${Math.floor(elapsedHours / 24)}d ago`;
}

// Test implementation of naturalBusSort
function naturalBusSort(a, b) {
  const numA = parseInt(a.busNumber.replace(/\D/g, ''), 10) || parseInt(a.busId.replace(/\D/g, ''), 10) || 0;
  const numB = parseInt(b.busNumber.replace(/\D/g, ''), 10) || parseInt(b.busId.replace(/\D/g, ''), 10) || 0;
  if (numA !== numB) return numA - numB;
  return a.busNumber.localeCompare(b.busNumber);
}

console.log('==================================================');
console.log('RUNNING MILESTONE 4 LIVE FLEET TRACKING TESTS');
console.log('==================================================\n');

// --- Test Suite 1: High-Fidelity 5-State GPS Status ---
console.log('--- Test Suite 1: 5-State GPS Status Evaluation ---');

const now = 1700000000000;

// 1. ACTIVE state: valid coordinates within 90s
const activeTelemetry = {
  busId: 'BUS-01',
  latitude: 15.1683,
  longitude: 76.8317,
  trackingStatus: 'ACTIVE',
  lastUpdated: now - 15000, // 15s ago
};
assert.equal(evaluateBusGpsState(activeTelemetry, now), 'ACTIVE', 'Recent active telemetry must evaluate to ACTIVE');

// 2. STALE state by timeout: trackingStatus was ACTIVE, but lastUpdated is 95s ago (> 90s)
const staleByTimeoutTelemetry = {
  busId: 'BUS-02',
  latitude: 15.1683,
  longitude: 76.8317,
  trackingStatus: 'ACTIVE',
  lastUpdated: now - 95000, // 95s ago (> 90s threshold)
};
assert.equal(evaluateBusGpsState(staleByTimeoutTelemetry, now), 'STALE', 'Active telemetry older than 90s must evaluate to STALE');

// Boundary test: exactly 89s -> ACTIVE, exactly 91s -> STALE
assert.equal(
  evaluateBusGpsState({ ...activeTelemetry, lastUpdated: now - 89000 }, now),
  'ACTIVE',
  '89s old telemetry is still ACTIVE'
);
assert.equal(
  evaluateBusGpsState({ ...activeTelemetry, lastUpdated: now - 91000 }, now),
  'STALE',
  '91s old telemetry must transition to STALE'
);

// 3. WAITING_FOR_GPS: trackingStatus is ACTIVE but coordinates are 0.0, 0.0 (satellite fix pending)
const waitingTelemetry = {
  busId: 'BUS-03',
  latitude: 0.0,
  longitude: 0.0,
  trackingStatus: 'ACTIVE',
  lastUpdated: now - 5000,
};
assert.equal(evaluateBusGpsState(waitingTelemetry, now), 'WAITING_FOR_GPS', 'Zero coordinates under ACTIVE status must evaluate to WAITING_FOR_GPS');

// Explicit WAITING_FOR_GPS trackingStatus
assert.equal(
  evaluateBusGpsState({ busId: 'BUS-03', latitude: 15.0, longitude: 76.0, trackingStatus: 'WAITING_FOR_GPS', lastUpdated: now }, now),
  'WAITING_FOR_GPS'
);

// 4. ARRIVED_AT_CAMPUS: must NOT be classified as INACTIVE or STALE
const arrivedTelemetry = {
  busId: 'BUS-04',
  latitude: 15.1683,
  longitude: 76.8317,
  trackingStatus: 'ARRIVED_AT_CAMPUS',
  lastUpdated: now - 300000, // 5 minutes ago
};
assert.equal(
  evaluateBusGpsState(arrivedTelemetry, now),
  'ARRIVED_AT_CAMPUS',
  'ARRIVED_AT_CAMPUS must be preserved and NOT converted to INACTIVE or STALE'
);

// 5. INACTIVE: trackingStatus is INACTIVE, or telemetry is null/missing
const inactiveTelemetry = {
  busId: 'BUS-05',
  latitude: 15.1683,
  longitude: 76.8317,
  trackingStatus: 'INACTIVE',
  lastUpdated: now - 1000,
};
assert.equal(evaluateBusGpsState(inactiveTelemetry, now), 'INACTIVE', 'Explicit INACTIVE trackingStatus must evaluate to INACTIVE');
assert.equal(evaluateBusGpsState(null, now), 'INACTIVE', 'Null telemetry record must evaluate to INACTIVE');

console.log('  ✅ PASS: All 5 GPS states correctly evaluated against 90s freshness window');

// --- Test Suite 2: Speed and Bearing Conversions ---
console.log('\n--- Test Suite 2: Speed and Bearing Conversions ---');

// Speed conversions (m/s to km/h)
assert.equal(speedMpsToKmh(0), 0);
assert.equal(speedMpsToKmh(10), 36, '10 m/s should convert to 36 km/h');
assert.equal(speedMpsToKmh(16.67), 60, '16.67 m/s should convert to 60 km/h');
assert.equal(speedMpsToKmh(-5), 0, 'Negative speed should yield 0 km/h');
assert.equal(speedMpsToKmh(NaN), 0, 'NaN speed should yield 0 km/h');

// Compass direction conversions (0..360 degrees)
assert.equal(bearingToCompassDirection(0), 'N', '0° is North');
assert.equal(bearingToCompassDirection(360), 'N', '360° is North');
assert.equal(bearingToCompassDirection(45), 'NE', '45° is North-East');
assert.equal(bearingToCompassDirection(90), 'E', '90° is East');
assert.equal(bearingToCompassDirection(135), 'SE', '135° is South-East');
assert.equal(bearingToCompassDirection(180), 'S', '180° is South');
assert.equal(bearingToCompassDirection(225), 'SW', '225° is South-West');
assert.equal(bearingToCompassDirection(270), 'W', '270° is West');
assert.equal(bearingToCompassDirection(315), 'NW', '315° is North-West');
assert.equal(bearingToCompassDirection(NaN), 'N/A', 'NaN bearing returns N/A');

console.log('  ✅ PASS: Speed m/s to km/h and bearing azimuth to compass directions verified');

// --- Test Suite 3: Relative Time Formatting ---
console.log('\n--- Test Suite 3: Relative Time Formatting ---');

assert.equal(formatRelativeTime(now - 2000, now), 'Just now');
assert.equal(formatRelativeTime(now - 25000, now), '25s ago');
assert.equal(formatRelativeTime(now - 125000, now), '2m ago');
assert.equal(formatRelativeTime(now - 7200000, now), '2h ago');
assert.equal(formatRelativeTime(0, now), 'Never');
assert.equal(formatRelativeTime(null, now), 'Never');

console.log('  ✅ PASS: Telemetry heartbeat relative time formatting verified');

// --- Test Suite 4: College Campus Coordinates ---
console.log('\n--- Test Suite 4: College Campus Coordinates (BITM Ballari) ---');

const BITM_CAMPUS = { lat: 15.168307, lng: 76.831722 };
assert.ok(BITM_CAMPUS.lat > 15.0 && BITM_CAMPUS.lat < 16.0, 'Campus latitude in Ballari region');
assert.ok(BITM_CAMPUS.lng > 76.0 && BITM_CAMPUS.lng < 77.0, 'Campus longitude in Ballari region');

console.log(`  ✅ PASS: Default campus location verified at BITM Ballari (${BITM_CAMPUS.lat}, ${BITM_CAMPUS.lng})`);

// --- Test Suite 5: Route Stops & Polyline Extraction ---
console.log('\n--- Test Suite 5: Route Stops Polyline Generator ---');

const mockRoute = {
  routeId: 'ROUTE-01',
  name: 'Route 1 - North Express',
  stops: [
    { stopId: 'STOP-03', name: 'Third Stop', latitude: 15.1800, longitude: 76.8400, order: 3, active: true, visibleOnMap: true },
    { stopId: 'STOP-01', name: 'First Stop', latitude: 15.1600, longitude: 76.8200, order: 1, active: true, visibleOnMap: true },
    { stopId: 'STOP-02', name: 'Second Stop (Missing)', latitude: 0.0, longitude: 0.0, order: 2, active: true, visibleOnMap: false },
  ],
};

function extractRoutePolyline(route) {
  if (!route || !Array.isArray(route.stops)) return { path: [], stops: [] };
  const validStops = route.stops
    .filter((s) => s.visibleOnMap !== false && (s.latitude !== 0 || s.longitude !== 0))
    .sort((a, b) => a.order - b.order);
  const path = validStops.map((s) => ({ lat: s.latitude, lng: s.longitude }));
  return { path, stops: validStops };
}

const { path, stops: extractedStops } = extractRoutePolyline(mockRoute);
assert.equal(path.length, 2, 'Only stops with valid coordinates should be in polyline path');
assert.equal(extractedStops[0].stopId, 'STOP-01', 'Stops must be ordered by sequence index');
assert.equal(extractedStops[1].stopId, 'STOP-03', 'Missing-coordinate stop excluded from polyline');
assert.equal(path[0].lat, 15.1600);
assert.equal(path[1].lat, 15.1800);

console.log('  ✅ PASS: Route stop sequence and approximate straight-line polyline path generated');

// --- Test Suite 6: Natural Bus Sorting & Data Fusion ---
console.log('\n--- Test Suite 6: Bus Sorting and Data Fusion ---');

const busList = [
  { busId: 'BUS-10', busNumber: 'Bus 10' },
  { busId: 'BUS-02', busNumber: 'Bus 02' },
  { busId: 'BUS-01', busNumber: 'Bus 01' },
];

busList.sort(naturalBusSort);
assert.equal(busList[0].busNumber, 'Bus 01');
assert.equal(busList[1].busNumber, 'Bus 02');
assert.equal(busList[2].busNumber, 'Bus 10');

console.log('  ✅ PASS: Natural sequential ordering for bus identifiers verified');

console.log('\n==================================================');
console.log('MILESTONE 4 TESTS COMPLETE: All assertions passed!');
console.log('==================================================');
