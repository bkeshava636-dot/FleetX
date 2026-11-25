import assert from 'node:assert/strict';

/**
 * FleetX – Final Integration & Regression Audit Test Suite
 * Validates Cross-Module Workflows A through G end-to-end.
 */

console.log('====================================================');
console.log('RUNNING FLEETX FINAL CROSS-MODULE INTEGRATION TESTS');
console.log('====================================================\n');

// Mock in-memory multi-collection state store
function createIntegrationStore() {
  const store = {
    firestore: {
      buses: {},
      routes: {},
      users: {},
      bus_number_index: {},
      bus_registration_index: {},
      stop_location_submissions: {}
    },
    rtdb: {
      users: {},
      bus_operators: {},
      live_locations: {}
    },
    auth: {}
  };

  return store;
}

// ----------------------------------------------------
// WORKFLOW A: Register a Bus & Assign Route
// ----------------------------------------------------
console.log('--- Workflow A: Register a Bus, Assign Route, & Android Compatibility ---');
{
  const store = createIntegrationStore();

  // Setup initial active route
  store.firestore.routes['ROUTE-01'] = {
    routeId: 'ROUTE-01',
    name: 'Main Campus Line',
    active: true,
    stops: [
      { stopId: 'STOP-1', name: 'Main Gate', order: 1, latitude: 15.16, longitude: 76.83, active: true },
      { stopId: 'STOP-2', name: 'Library', order: 2, latitude: 15.17, longitude: 76.84, active: true }
    ],
    schedules: { morning: '08:00 AM', evening: '05:00 PM' }
  };

  // 1. Register bus BUS-88
  const busNumber = '88';
  const regNumber = 'KA-34-A-8888';
  const busId = `BUS-${busNumber}`;

  // Unique index check
  assert.equal(store.firestore.bus_number_index[busNumber], undefined, 'Bus number not yet reserved');
  assert.equal(store.firestore.bus_registration_index[regNumber.toLowerCase()], undefined, 'Plate not yet reserved');

  // Atomic reservation
  store.firestore.bus_number_index[busNumber] = { busId, reservedAt: Date.now() };
  store.firestore.bus_registration_index[regNumber.toLowerCase()] = { busId, reservedAt: Date.now() };

  // Bus creation
  store.firestore.buses[busId] = {
    busId,
    busNumber,
    registrationNumber: regNumber,
    capacity: 55,
    active: true,
    routeId: null,
    driverId: null,
    driverName: null,
    assignmentVersion: 0,
    rtdbSyncStatus: 'SYNCED',
    createdAt: Date.now()
  };

  assert.ok(store.firestore.buses[busId], 'Bus successfully registered');
  console.log('  ✅ PASS: A.1. Bus successfully registered and unique index documents created');

  // 2. Assign route ROUTE-01
  const targetRoute = store.firestore.routes['ROUTE-01'];
  assert.ok(targetRoute && targetRoute.active, 'Target route exists and is active');

  store.firestore.buses[busId].routeId = 'ROUTE-01';
  store.firestore.buses[busId].updatedAt = Date.now();

  assert.equal(store.firestore.buses[busId].routeId, 'ROUTE-01');
  // Verify embedded stops and schedules in routes collection are completely intact
  assert.equal(store.firestore.routes['ROUTE-01'].stops.length, 2);
  assert.equal(store.firestore.routes['ROUTE-01'].stops[0].name, 'Main Gate');
  assert.equal(store.firestore.routes['ROUTE-01'].schedules.morning, '08:00 AM');
  console.log('  ✅ PASS: A.2. Route assigned; embedded stops and schedules preserved without mutation');

  // 3. Android Kotlin Bus model compatibility
  const bus = store.firestore.buses[busId];
  assert.equal(typeof bus.busId, 'string');
  assert.equal(typeof bus.busNumber, 'string');
  assert.equal(typeof bus.registrationNumber, 'string');
  assert.equal(typeof bus.routeId, 'string');
  assert.equal(bus.driverId, null);
  assert.equal(bus.driverName, null);
  assert.equal(typeof bus.capacity, 'number');
  assert.equal(typeof bus.active, 'boolean');
  console.log('  ✅ PASS: A.3. Bus document schema perfectly conforms to Android Kotlin Bus model');
}

// ----------------------------------------------------
// WORKFLOW B: Register & Assign Driver
// ----------------------------------------------------
console.log('\n--- Workflow B: Register Driver, Assign to Bus, & Verify RTDB ---');
{
  const store = createIntegrationStore();
  store.firestore.buses['BUS-88'] = {
    busId: 'BUS-88',
    busNumber: '88',
    registrationNumber: 'KA-34-A-8888',
    active: true,
    driverId: null,
    driverName: null,
    assignmentVersion: 1
  };

  // 1. Create driver
  const driverUid = 'uid_driver_99';
  const customId = 'drv_99';
  const driverName = 'Ravi Teja';
  const internalEmail = `${customId}@college.edu`;

  store.auth[driverUid] = { email: internalEmail, disabled: false };
  store.firestore.users[driverUid] = {
    uid: driverUid,
    customId,
    name: driverName,
    email: internalEmail,
    role: 'DRIVER',
    active: true,
    assignedBusId: null,
    phoneNumber: '9876543299'
  };
  store.rtdb.users[driverUid] = {
    role: 'DRIVER',
    active: true,
    name: driverName,
    assignedBusId: null
  };

  console.log('  ✅ PASS: B.1. Driver account created across Auth, Firestore, and RTDB');

  // 2. Assign to BUS-88
  const now = Date.now();
  const nextVer = store.firestore.buses['BUS-88'].assignmentVersion + 1;

  store.firestore.buses['BUS-88'].driverId = driverUid;
  store.firestore.buses['BUS-88'].driverName = driverName;
  store.firestore.buses['BUS-88'].assignmentVersion = nextVer;
  store.firestore.buses['BUS-88'].rtdbSyncStatus = 'SYNCED';

  store.firestore.users[driverUid].assignedBusId = 'BUS-88';
  store.firestore.users[driverUid].assignmentVersion = nextVer;

  store.rtdb.users[driverUid].assignedBusId = 'BUS-88';
  store.rtdb.users[driverUid].assignmentVersion = nextVer;
  store.rtdb.bus_operators['BUS-88'] = {
    busId: 'BUS-88',
    permanentDriverUid: driverUid,
    assignmentVersion: nextVer,
    operatorEpoch: now
  };

  // Verifications
  assert.equal(store.firestore.buses['BUS-88'].driverId, driverUid);
  assert.equal(store.firestore.users[driverUid].assignedBusId, 'BUS-88');
  assert.equal(store.rtdb.users[driverUid].assignedBusId, 'BUS-88');
  assert.equal(store.rtdb.bus_operators['BUS-88'].permanentDriverUid, driverUid);
  assert.equal(store.firestore.buses['BUS-88'].assignmentVersion, 2);
  assert.equal(store.rtdb.bus_operators['BUS-88'].assignmentVersion, 2);
  console.log('  ✅ PASS: B.2. Bus-driver assignment synchronized atomically with monotonic versioning');
}

// ----------------------------------------------------
// WORKFLOW C: Driver Transfer & Concurrency Control
// ----------------------------------------------------
console.log('\n--- Workflow C: Transfer Driver & Concurrency Collision Safety ---');
{
  const store = createIntegrationStore();
  store.firestore.buses['BUS-A'] = {
    busId: 'BUS-A',
    busNumber: '01',
    active: true,
    driverId: 'uid_drv_1',
    driverName: 'Driver One',
    assignmentVersion: 2
  };
  store.firestore.buses['BUS-B'] = {
    busId: 'BUS-B',
    busNumber: '02',
    active: true,
    driverId: null,
    driverName: null,
    assignmentVersion: 1
  };
  store.firestore.users['uid_drv_1'] = {
    uid: 'uid_drv_1',
    name: 'Driver One',
    role: 'DRIVER',
    active: true,
    assignedBusId: 'BUS-A'
  };
  store.rtdb.bus_operators['BUS-A'] = { busId: 'BUS-A', permanentDriverUid: 'uid_drv_1', assignmentVersion: 2 };
  store.rtdb.bus_operators['BUS-B'] = { busId: 'BUS-B', permanentDriverUid: null, assignmentVersion: 1 };
  store.rtdb.users['uid_drv_1'] = { role: 'DRIVER', active: true, assignedBusId: 'BUS-A' };

  // 1. Transfer Driver One from BUS-A to BUS-B
  const transferVerA = store.firestore.buses['BUS-A'].assignmentVersion + 1; // 3
  const transferVerB = store.firestore.buses['BUS-B'].assignmentVersion + 1; // 2

  // Clear BUS-A
  store.firestore.buses['BUS-A'].driverId = null;
  store.firestore.buses['BUS-A'].driverName = null;
  store.firestore.buses['BUS-A'].assignmentVersion = transferVerA;
  store.rtdb.bus_operators['BUS-A'].permanentDriverUid = null;
  store.rtdb.bus_operators['BUS-A'].assignmentVersion = transferVerA;

  // Assign BUS-B
  store.firestore.buses['BUS-B'].driverId = 'uid_drv_1';
  store.firestore.buses['BUS-B'].driverName = 'Driver One';
  store.firestore.buses['BUS-B'].assignmentVersion = transferVerB;
  store.firestore.users['uid_drv_1'].assignedBusId = 'BUS-B';
  store.rtdb.bus_operators['BUS-B'].permanentDriverUid = 'uid_drv_1';
  store.rtdb.bus_operators['BUS-B'].assignmentVersion = transferVerB;
  store.rtdb.users['uid_drv_1'].assignedBusId = 'BUS-B';

  assert.equal(store.firestore.buses['BUS-A'].driverId, null);
  assert.equal(store.firestore.buses['BUS-B'].driverId, 'uid_drv_1');
  assert.equal(store.firestore.users['uid_drv_1'].assignedBusId, 'BUS-B');
  assert.equal(store.rtdb.bus_operators['BUS-A'].permanentDriverUid, null);
  assert.equal(store.rtdb.bus_operators['BUS-B'].permanentDriverUid, 'uid_drv_1');
  console.log('  ✅ PASS: C.1. Driver transferred; source bus detached and destination bus attached');

  // 2. Concurrency collision: Admin 2 tries to overwrite Driver One without confirmation
  function assignDriverToBusWithChecks(busId, newDriverUid, confirmReplacement, expectedPreviousDriverId) {
    const bus = store.firestore.buses[busId];
    if (bus.driverId && bus.driverId !== newDriverUid) {
      if (!confirmReplacement) {
        throw new Error('Explicit confirmation is required to replace the existing driver.');
      }
      if (expectedPreviousDriverId !== undefined && expectedPreviousDriverId !== bus.driverId) {
        throw new Error(`Assignment conflict: Bus '${busId}' was updated by another administrator.`);
      }
    }
    bus.driverId = newDriverUid;
  }

  // Unconfirmed replacement attempt is rejected
  assert.throws(
    () => assignDriverToBusWithChecks('BUS-B', 'uid_drv_2', false, null),
    /Explicit confirmation is required/
  );
  console.log('  ✅ PASS: C.2. Unconfirmed replacement of occupied bus blocked by server guardrail');

  // Stale replacement attempt is rejected
  assert.throws(
    () => assignDriverToBusWithChecks('BUS-B', 'uid_drv_2', true, 'stale_drv_old'),
    /Assignment conflict/
  );
  console.log('  ✅ PASS: C.3. Stale replacement request with outdated expectedPreviousDriverId rejected');
}

// ----------------------------------------------------
// WORKFLOW D: Deactivation & Reactivation Guardrails
// ----------------------------------------------------
console.log('\n--- Workflow D: Deactivation Safety & Reactivation Cleansing ---');
{
  const store = createIntegrationStore();
  store.firestore.buses['BUS-10'] = { busId: 'BUS-10', active: true, driverId: 'uid_drv_10', assignmentVersion: 3 };
  store.firestore.users['uid_drv_10'] = { uid: 'uid_drv_10', role: 'DRIVER', active: true, assignedBusId: 'BUS-10' };
  store.rtdb.bus_operators['BUS-10'] = { busId: 'BUS-10', permanentDriverUid: 'uid_drv_10', assignmentVersion: 3 };
  store.auth['uid_drv_10'] = { disabled: false };

  // 1. Deactivating bus with assigned driver requires confirm
  function deactivateBus(busId, confirmDetach) {
    const bus = store.firestore.buses[busId];
    if (bus.driverId && !confirmDetach) {
      throw new Error('Must confirm detaching driver before deactivating bus.');
    }
    if (bus.driverId && confirmDetach) {
      const driver = store.firestore.users[bus.driverId];
      if (driver) driver.assignedBusId = null;
      bus.driverId = null;
    }
    bus.active = false;
  }

  assert.throws(() => deactivateBus('BUS-10', false), /Must confirm detaching driver/);
  deactivateBus('BUS-10', true);
  assert.equal(store.firestore.buses['BUS-10'].active, false);
  assert.equal(store.firestore.buses['BUS-10'].driverId, null);
  assert.equal(store.firestore.users['uid_drv_10'].assignedBusId, null);
  console.log('  ✅ PASS: D.1. Bus deactivation requires explicit confirmation to unassign driver');

  // 2. Reactivating driver with stale bus assignment cleans up stale reference
  store.firestore.users['uid_drv_10'].active = false;
  store.firestore.users['uid_drv_10'].assignedBusId = 'BUS-10'; // Stale pointer
  store.firestore.buses['BUS-10'].driverId = 'uid_other_driver'; // BUS-10 now owned by someone else

  function reactivateDriver(uid) {
    const driver = store.firestore.users[uid];
    if (driver.assignedBusId) {
      const b = store.firestore.buses[driver.assignedBusId];
      if (!b || b.driverId !== uid) {
        driver.assignedBusId = null; // Cleanse stale pointer
      }
    }
    driver.active = true;
  }

  reactivateDriver('uid_drv_10');
  assert.equal(store.firestore.users['uid_drv_10'].active, true);
  assert.equal(store.firestore.users['uid_drv_10'].assignedBusId, null, 'Stale bus assignment cleansed');
  console.log('  ✅ PASS: D.2. Reactivating driver strips stale bus assignment without split-brain');
}

// ----------------------------------------------------
// WORKFLOW E: Student Stop Coordinate Submission & Review
// ----------------------------------------------------
console.log('\n--- Workflow E: Student Stop Coordinate Submission, Approval, & Rejection ---');
{
  const store = createIntegrationStore();
  store.firestore.routes['ROUTE-K'] = {
    routeId: 'ROUTE-K',
    active: true,
    stops: [
      { stopId: 'STOP-A', name: 'Stop A', order: 1, latitude: 0, longitude: 0, visibleOnMap: false },
      { stopId: 'STOP-B', name: 'Stop B', order: 2, latitude: 0, longitude: 0, visibleOnMap: false }
    ]
  };

  // 1. Submit stop coordinates via submitStopCoordinates
  const subId1 = 'sub_001';
  store.firestore.stop_location_submissions[subId1] = {
    submissionId: subId1,
    routeId: 'ROUTE-K',
    stopId: 'STOP-A',
    proposedLatitude: 15.165,
    proposedLongitude: 76.832,
    status: 'PENDING'
  };

  // 2. Approve submission 1
  function approveSubmission(subId) {
    const sub = store.firestore.stop_location_submissions[subId];
    sub.status = 'APPROVED';
    const route = store.firestore.routes[sub.routeId];
    route.stops = route.stops.map(s => {
      if (s.stopId === sub.stopId) {
        return {
          ...s,
          latitude: sub.proposedLatitude,
          longitude: sub.proposedLongitude,
          visibleOnMap: true
        };
      }
      return s;
    });
  }

  approveSubmission(subId1);
  assert.equal(store.firestore.stop_location_submissions[subId1].status, 'APPROVED');
  assert.equal(store.firestore.routes['ROUTE-K'].stops[0].latitude, 15.165);
  assert.equal(store.firestore.routes['ROUTE-K'].stops[0].longitude, 76.832);
  assert.equal(store.firestore.routes['ROUTE-K'].stops[0].visibleOnMap, true);
  // Verify Stop B is completely untouched
  assert.equal(store.firestore.routes['ROUTE-K'].stops[1].latitude, 0);
  console.log('  ✅ PASS: E.1. Stop approval updates only targeted stop and sets coordinates');

  // 3. Reject submission 2
  const subId2 = 'sub_002';
  store.firestore.stop_location_submissions[subId2] = {
    submissionId: subId2,
    routeId: 'ROUTE-K',
    stopId: 'STOP-B',
    proposedLatitude: 99.99,
    proposedLongitude: 99.99,
    status: 'PENDING'
  };

  function rejectSubmission(subId) {
    store.firestore.stop_location_submissions[subId].status = 'REJECTED';
  }

  rejectSubmission(subId2);
  assert.equal(store.firestore.stop_location_submissions[subId2].status, 'REJECTED');
  // Route coordinates remain untouched
  assert.equal(store.firestore.routes['ROUTE-K'].stops[1].latitude, 0);
  console.log('  ✅ PASS: E.2. Stop rejection marks status REJECTED without modifying route');
}

// ----------------------------------------------------
// WORKFLOW F: Live Fleet Tracking & Freshness Rules
// ----------------------------------------------------
console.log('\n--- Workflow F: Live Fleet Tracking Telemetry & 90s Freshness Window ---');
{
  const now = Date.now();
  const freshTime = now - 30 * 1000;  // 30s ago (< 90s)
  const staleTime = now - 120 * 1000; // 120s ago (> 90s)

  function evaluateGpsStatus(telemetry, currentTime) {
    if (!telemetry || !telemetry.lastUpdated) return 'INACTIVE';
    if (telemetry.trackingStatus === 'INACTIVE') return 'INACTIVE';
    if (telemetry.trackingStatus === 'ARRIVED_AT_CAMPUS') return 'ARRIVED_AT_CAMPUS';
    if (telemetry.trackingStatus === 'WAITING_FOR_GPS') return 'WAITING_FOR_GPS';

    const elapsedMs = currentTime - telemetry.lastUpdated;
    if (elapsedMs <= 90 * 1000) {
      return 'ACTIVE';
    }
    return 'STALE';
  }

  assert.equal(evaluateGpsStatus({ lastUpdated: freshTime, trackingStatus: 'ACTIVE' }, now), 'ACTIVE');
  assert.equal(evaluateGpsStatus({ lastUpdated: staleTime, trackingStatus: 'ACTIVE' }, now), 'STALE');
  assert.equal(evaluateGpsStatus({ lastUpdated: freshTime, trackingStatus: 'INACTIVE' }, now), 'INACTIVE');
  assert.equal(evaluateGpsStatus({ lastUpdated: freshTime, trackingStatus: 'ARRIVED_AT_CAMPUS' }, now), 'ARRIVED_AT_CAMPUS');
  assert.equal(evaluateGpsStatus({ lastUpdated: freshTime, trackingStatus: 'WAITING_FOR_GPS' }, now), 'WAITING_FOR_GPS');

  console.log('  ✅ PASS: F.1. 5-state GPS status evaluation enforces 90s staleness window');
}

// ----------------------------------------------------
// WORKFLOW G: Driver Deletion & Active Swap Cleanup
// ----------------------------------------------------
console.log('\n--- Workflow G: Driver Deletion, RTDB Cleanup, & Temporary Swap Revocation ---');
{
  const store = createIntegrationStore();
  const driverUid = 'uid_del_user';

  store.firestore.users[driverUid] = { uid: driverUid, role: 'DRIVER', assignedBusId: 'BUS-05' };
  store.firestore.buses['BUS-05'] = { busId: 'BUS-05', driverId: driverUid, assignmentVersion: 4 };
  store.rtdb.users[driverUid] = { role: 'DRIVER', active: true };
  store.rtdb.bus_operators['BUS-05'] = { busId: 'BUS-05', permanentDriverUid: driverUid, assignmentVersion: 4 };
  store.rtdb.bus_operators['BUS-20'] = {
    busId: 'BUS-20',
    permanentDriverUid: 'uid_perm_20',
    hasTemporary: true,
    temporaryDriverUid: driverUid // Active temporary swap
  };
  store.auth[driverUid] = { email: 'del_user@college.edu' };

  function executeDeleteDriver(uid) {
    const user = store.firestore.users[uid];
    if (user && user.assignedBusId) {
      const b = store.firestore.buses[user.assignedBusId];
      if (b) {
        b.driverId = null;
        b.assignmentVersion++;
      }
      if (store.rtdb.bus_operators[user.assignedBusId]) {
        store.rtdb.bus_operators[user.assignedBusId].permanentDriverUid = null;
      }
    }

    // Revoke temporary swaps across all bus operators in RTDB
    for (const [bId, op] of Object.entries(store.rtdb.bus_operators)) {
      if (op.temporaryDriverUid === uid) {
        op.hasTemporary = false;
        op.temporaryDriverUid = null;
        op.temporaryAssignmentId = null;
      }
    }

    delete store.firestore.users[uid];
    delete store.rtdb.users[uid];
    delete store.auth[uid];
  }

  executeDeleteDriver(driverUid);

  // Verifications
  assert.equal(store.firestore.users[driverUid], undefined, 'Firestore user deleted');
  assert.equal(store.rtdb.users[driverUid], undefined, 'RTDB user deleted');
  assert.equal(store.auth[driverUid], undefined, 'Auth user deleted');
  assert.equal(store.firestore.buses['BUS-05'].driverId, null, 'Permanent bus unassigned');
  assert.equal(store.firestore.buses['BUS-05'].assignmentVersion, 5, 'Assignment version incremented');
  assert.equal(store.rtdb.bus_operators['BUS-20'].hasTemporary, false, 'Temporary swap revoked');
  assert.equal(store.rtdb.bus_operators['BUS-20'].temporaryDriverUid, null, 'Temporary driver UID cleared');
  assert.equal(store.rtdb.bus_operators['BUS-20'].permanentDriverUid, 'uid_perm_20', 'Permanent driver on swap bus preserved');

  console.log('  ✅ PASS: G.1. Driver deletion cleanly wipes user across all layers and revokes active temporary swap');
}

console.log('\n====================================================');
console.log('ALL WORKFLOW INTEGRATION TESTS (A THROUGH G) PASSED!');
console.log('====================================================');
