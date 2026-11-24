import assert from 'node:assert/strict';

// Test implementation of checkStopHasCoordinates
function checkStopHasCoordinates(stop) {
  if (!stop) return false;
  if (stop.visibleOnMap === false) return false;
  const lat = stop.latitude;
  const lng = stop.longitude;
  if (typeof lat !== 'number' || typeof lng !== 'number') return false;
  if (isNaN(lat) || isNaN(lng)) return false;
  if (lat === 0 && lng === 0) return false;
  return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
}

// Test implementation of naturalRouteSort
function naturalRouteSort(a, b) {
  const numA = parseInt(a.routeId.replace(/\D/g, ''), 10) || 0;
  const numB = parseInt(b.routeId.replace(/\D/g, ''), 10) || 0;
  if (numA !== numB) return numA - numB;
  return a.name.localeCompare(b.name);
}

// Test implementation of computeRouteStopStats
function computeRouteStopStats(routes) {
  let totalStops = 0;
  let mappedStops = 0;
  let missingStops = 0;
  let activeRoutes = 0;

  for (const r of routes) {
    if (r.active) {
      activeRoutes++;
      for (const s of r.stops) {
        if (s.active) {
          totalStops++;
          if (checkStopHasCoordinates(s)) {
            mappedStops++;
          } else {
            missingStops++;
          }
        }
      }
    }
  }

  const coveragePercent = totalStops > 0 ? Math.round((mappedStops / totalStops) * 100) : 0;

  return {
    totalStops,
    mappedStops,
    missingStops,
    activeRoutes,
    coveragePercent,
  };
}

// GPS freshness evaluation test (synchronized with 90,000ms threshold)
const GPS_STALE_THRESHOLD_MS = 90_000;
function isGpsTransmitting(loc, nowMs) {
  if (!loc || loc.trackingStatus !== 'ACTIVE') return false;
  const lastUpdated = typeof loc.lastUpdated === 'number' ? loc.lastUpdated : 0;
  const hasCoords = (loc.latitude !== 0 || loc.longitude !== 0) &&
    typeof loc.latitude === 'number' && typeof loc.longitude === 'number';
  const isFresh = (nowMs - lastUpdated) <= GPS_STALE_THRESHOLD_MS;
  return hasCoords && isFresh;
}

console.log('==================================================');
console.log('RUNNING FLEETX DASHBOARD & ROUTE METRIC TESTS');
console.log('==================================================\n');

// 1. checkStopHasCoordinates
assert.equal(checkStopHasCoordinates({ latitude: 15.3435, longitude: 76.1554, visibleOnMap: true }), true, 'Valid coordinates must return true');
assert.equal(checkStopHasCoordinates({ latitude: 0.0, longitude: 0.0, visibleOnMap: true }), false, '0,0 coordinates must return false');
assert.equal(checkStopHasCoordinates({ latitude: null, longitude: null }), false, 'Null coordinates must return false');
assert.equal(checkStopHasCoordinates({ latitude: 15.3435, longitude: 76.1554, visibleOnMap: false }), false, 'visibleOnMap=false must return false');
assert.equal(checkStopHasCoordinates({ latitude: 100.0, longitude: 76.1554, visibleOnMap: true }), false, 'Out of bounds lat (>90) must return false');
console.log('  ✅ PASS: checkStopHasCoordinates accurately validates coordinate presence and bounds');

// 2. naturalRouteSort
const sampleRoutes = [
  { routeId: 'ROUTE-10', name: 'Route 10' },
  { routeId: 'ROUTE-02', name: 'Route 2' },
  { routeId: 'ROUTE-01', name: 'Route 1' },
  { routeId: 'ROUTE-25', name: 'Route 25' },
];
sampleRoutes.sort(naturalRouteSort);
assert.equal(sampleRoutes[0].routeId, 'ROUTE-01');
assert.equal(sampleRoutes[1].routeId, 'ROUTE-02');
assert.equal(sampleRoutes[2].routeId, 'ROUTE-10');
assert.equal(sampleRoutes[3].routeId, 'ROUTE-25');
console.log('  ✅ PASS: naturalRouteSort correctly sorts route IDs sequentially (01, 02, 10, 25)');

// 3. computeRouteStopStats
const testRoutes = [
  {
    routeId: 'ROUTE-01',
    active: true,
    stops: [
      { stopId: 'S1', active: true, latitude: 15.1, longitude: 76.1, visibleOnMap: true },
      { stopId: 'S2', active: true, latitude: 0.0, longitude: 0.0, visibleOnMap: true },
      { stopId: 'S3', active: true, latitude: 15.2, longitude: 76.2, visibleOnMap: true },
      { stopId: 'S4', active: false, latitude: 15.3, longitude: 76.3, visibleOnMap: true }, // inactive, should not count
    ]
  },
  {
    routeId: 'ROUTE-02',
    active: true,
    stops: [
      { stopId: 'S5', active: true, latitude: 0.0, longitude: 0.0, visibleOnMap: true },
    ]
  },
  {
    routeId: 'ROUTE-03',
    active: false, // inactive route, should not contribute
    stops: [
      { stopId: 'S6', active: true, latitude: 15.5, longitude: 76.5, visibleOnMap: true }
    ]
  }
];

const stats = computeRouteStopStats(testRoutes);
assert.equal(stats.activeRoutes, 2, 'Active routes count must match active: true routes');
assert.equal(stats.totalStops, 4, 'Total active stops across active routes must be 4');
assert.equal(stats.mappedStops, 2, 'Mapped stops with valid coords must be 2');
assert.equal(stats.missingStops, 2, 'Missing stops with 0.0 coords must be 2');
assert.equal(stats.coveragePercent, 50, 'Coverage percentage must be 50%');
console.log('  ✅ PASS: computeRouteStopStats accurately aggregates stops and coverage percentages');

// 4. GPS Freshness Threshold
const now = Date.now();
assert.equal(
  isGpsTransmitting({ trackingStatus: 'ACTIVE', latitude: 15.34, longitude: 76.15, lastUpdated: now - 30_000 }, now),
  true,
  'GPS updated 30s ago must be active'
);
assert.equal(
  isGpsTransmitting({ trackingStatus: 'ACTIVE', latitude: 15.34, longitude: 76.15, lastUpdated: now - 95_000 }, now),
  false,
  'GPS updated 95s ago (>90s) must be considered stale'
);
assert.equal(
  isGpsTransmitting({ trackingStatus: 'INACTIVE', latitude: 15.34, longitude: 76.15, lastUpdated: now - 5_000 }, now),
  false,
  'Non-ACTIVE trackingStatus must be excluded'
);
assert.equal(
  isGpsTransmitting({ trackingStatus: 'ACTIVE', latitude: 0.0, longitude: 0.0, lastUpdated: now - 5_000 }, now),
  false,
  '0,0 coordinates must be excluded from active transmitting GPS'
);
console.log('  ✅ PASS: GPS freshness logic correctly enforces the 90-second staleness window');

console.log('\n==================================================');
console.log('TEST SUMMARY: All dashboard calculation tests passed!');
console.log('==================================================');
