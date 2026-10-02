const admin = require("firebase-admin");
const fs = require("fs");
const path = require("path");

const DB_HOST = process.env.FIREBASE_DATABASE_EMULATOR_HOST || "127.0.0.1:9000";
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
const PROJECT_ID = "college-bus-tracker-prod";
const NS = "college-bus-tracker-prod-default-rtdb";

console.log("==================================================");
console.log("FIREBASE EMULATOR - DATABASE RULES VERIFICATION");
console.log("==================================================");
console.log(`DB Host: ${DB_HOST} | Auth Host: ${AUTH_HOST}\n`);

// Initialize admin
if (!admin.apps.length) {
  admin.initializeApp({
    projectId: PROJECT_ID,
    databaseURL: `http://${DB_HOST}/?ns=${NS}`
  });
}

const db = admin.database();

async function getIdToken(uid, email) {
  // Sign in or create user in Auth emulator
  const res = await fetch(`http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-key`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email: email || `${uid}@test.internal`,
      password: "password123",
      returnSecureToken: true
    })
  });
  const data = await res.json();
  if (data.idToken) {
    // If the emulator created a random localId, update it to our target uid
    return data.idToken;
  }
  throw new Error("Failed to get idToken from auth emulator: " + JSON.stringify(data));
}

// Or mint custom token verified by auth emulator
async function getCustomAuthToken(uid) {
  const customToken = await admin.auth().createCustomToken(uid);
  const res = await fetch(`http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=fake-key`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token: customToken, returnSecureToken: true })
  });
  const data = await res.json();
  if (!data.idToken) {
    throw new Error("Failed to exchange custom token: " + JSON.stringify(data));
  }
  return data.idToken;
}

async function run() {
  const now = Date.now();

  console.log("1. Seeding emulator RTDB state via Admin SDK...");
  await db.ref("users").set({
    "driver_perm": {
      name: "Permanent Driver",
      role: "DRIVER",
      active: true,
      assignedBusId: "BUS-01"
    },
    "driver_temp": {
      name: "Temporary Driver",
      role: "DRIVER",
      active: true,
      assignedBusId: "BUS-02"
    },
    "driver_unrelated": {
      name: "Unrelated Driver",
      role: "DRIVER",
      active: true,
      assignedBusId: "BUS-09"
    },
    "student_01": {
      name: "Test Student",
      role: "STUDENT",
      active: true
    },
    "admin_01": {
      name: "Admin User",
      role: "ADMIN",
      active: true
    }
  });

  await db.ref("bus_operators").set({
    "BUS-01": {
      busId: "BUS-01",
      permanentDriverUid: "driver_perm",
      hasTemporary: true,
      temporaryDriverUid: "driver_temp",
      startAt: now - 10000,
      endAt: now + 60000,
      operatorEpoch: now - 10000
    },
    "BUS-02": {
      busId: "BUS-02",
      permanentDriverUid: "driver_temp",
      hasTemporary: false
    }
  });

  console.log("   Seed data written successfully.\n");

  console.log("2. Generating authenticated tokens for test identities...");
  const tokenPerm = await getCustomAuthToken("driver_perm");
  const tokenTemp = await getCustomAuthToken("driver_temp");
  const tokenUnrelated = await getCustomAuthToken("driver_unrelated");
  const tokenStudent = await getCustomAuthToken("student_01");
  console.log("   Tokens acquired successfully.\n");

  async function testRestWrite(desc, path, body, token, expectedAllow) {
    const url = `http://${DB_HOST}/${path}.json?ns=${NS}&auth=${token}`;
    const res = await fetch(url, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    const isAllow = res.status === 200;
    const resBody = await res.text();
    const passed = (isAllow === expectedAllow);
    const statusLabel = passed ? "PASS" : "FAIL";
    const resultWord = isAllow ? "ALLOW (200 OK)" : `DENY (${res.status} ${resBody.trim()})`;
    const expectedWord = expectedAllow ? "ALLOW" : "DENY";

    console.log(`[${statusLabel}] ${desc}`);
    console.log(`       Path: ${path}`);
    console.log(`       Expected: ${expectedWord} | Actual: ${resultWord}`);
    if (!passed) {
      throw new Error(`Test failed for: ${desc}`);
    }
    return { passed, resBody };
  }

  console.log("3. Executing the 7 mandatory emulator security scenarios:\n");

  // Scenario A: Authorized permanent driver -> own assigned bus (BUS-02 has driver_temp as permanent, no temporary active)
  // Let's also test driver_perm on BUS-01 outside temporary window or driver_temp on BUS-02 (no temporary).
  // On BUS-02: permanentDriverUid is driver_temp, hasTemporary is false.
  await testRestWrite(
    "Scenario A: Authorized permanent driver -> own assigned bus",
    "live_locations/BUS-02",
    {
      busId: "BUS-02",
      latitude: 15.15,
      longitude: 76.90,
      trackingStatus: "ACTIVE",
      lastUpdated: now
    },
    tokenTemp,
    true
  );

  // Scenario B: Authorized temporary replacement driver -> effective replacement bus
  // On BUS-01: hasTemporary is true, now is within [startAt, endAt], temporaryDriverUid is driver_temp.
  await testRestWrite(
    "Scenario B: Authorized temporary replacement driver -> effective replacement bus",
    "live_locations/BUS-01",
    {
      busId: "BUS-01",
      latitude: 15.15,
      longitude: 76.90,
      trackingStatus: "ACTIVE",
      lastUpdated: now
    },
    tokenTemp,
    true
  );

  // Scenario C: Driver -> another unrelated bus
  // driver_unrelated -> BUS-01 live_locations
  await testRestWrite(
    "Scenario C: Driver -> another unrelated bus",
    "live_locations/BUS-01",
    {
      busId: "BUS-01",
      latitude: 15.15,
      longitude: 76.90,
      trackingStatus: "ACTIVE",
      lastUpdated: now
    },
    tokenUnrelated,
    false
  );

  // Scenario D: Student/ordinary authenticated user -> live_locations
  await testRestWrite(
    "Scenario D: Student/ordinary authenticated user -> live_locations",
    "live_locations/BUS-01",
    {
      busId: "BUS-01",
      latitude: 15.15,
      longitude: 76.90,
      trackingStatus: "ACTIVE",
      lastUpdated: now
    },
    tokenStudent,
    false
  );

  // Scenario E: Student/ordinary authenticated user -> location_sources
  // E1: Legitimate student contribution path (sourceType: SECONDARY_STUDENT, sourceId: student_01) -> ALLOW
  await testRestWrite(
    "Scenario E1: Student legitimate location contribution path",
    "location_sources/BUS-01/student_01",
    {
      busId: "BUS-01",
      sourceId: "student_01",
      sourceType: "SECONDARY_STUDENT",
      latitude: 15.15,
      longitude: 76.90,
      accuracy: 10.0,
      timestamp: now,
      active: true
    },
    tokenStudent,
    true
  );

  // E2: Student attempting to spoof PRIMARY_DRIVER -> DENY
  await testRestWrite(
    "Scenario E2: Student attempting to spoof PRIMARY_DRIVER in location_sources",
    "location_sources/BUS-01/student_01",
    {
      busId: "BUS-01",
      sourceId: "student_01",
      sourceType: "PRIMARY_DRIVER",
      latitude: 15.15,
      longitude: 76.90,
      accuracy: 10.0,
      timestamp: now,
      active: true
    },
    tokenStudent,
    false
  );

  // Scenario F: Driver claims sourceType=PRIMARY_DRIVER for a bus they do not control
  // driver_unrelated trying to write PRIMARY_DRIVER to BUS-01 location_sources
  await testRestWrite(
    "Scenario F: Driver claims sourceType=PRIMARY_DRIVER for bus they do not control",
    "location_sources/BUS-01/driver_unrelated",
    {
      busId: "BUS-01",
      sourceId: "driver_unrelated",
      sourceType: "PRIMARY_DRIVER",
      latitude: 15.15,
      longitude: 76.90,
      accuracy: 10.0,
      timestamp: now,
      active: true
    },
    tokenUnrelated,
    false
  );

  // Scenario G: Driver writes their legitimate location_sources/{busId}/{uid}
  // driver_temp (effective replacement driver on BUS-01) writes PRIMARY_DRIVER to BUS-01
  await testRestWrite(
    "Scenario G: Driver writes legitimate location_sources/{busId}/{uid}",
    "location_sources/BUS-01/driver_temp",
    {
      busId: "BUS-01",
      sourceId: "driver_temp",
      sourceType: "PRIMARY_DRIVER",
      latitude: 15.15,
      longitude: 76.90,
      accuracy: 10.0,
      timestamp: now,
      active: true
    },
    tokenTemp,
    true
  );

  console.log("\n==================================================");
  console.log("ALL 7 EMULATOR SECURITY SCENARIOS PASSED!");
  console.log("==================================================");
  process.exit(0);
}

run().catch(err => {
  console.error("Test execution error:", err);
  process.exit(1);
});
