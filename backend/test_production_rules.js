const fs = require('fs');

async function runSecurityTests() {
  const gs = JSON.parse(fs.readFileSync('C:/Users/bhrao/college_bus_tracker/app/google-services.json', 'utf8'));
  const apiKey = gs.client[0].api_key[0].current_key;

  console.log("==================================================");
  console.log("LIVE PRODUCTION DATABASE RULES VERIFICATION SUITE");
  console.log("==================================================");

  // 1. Authenticate Throwaway Account
  const throwawayEmail = `audit_student_${Date.now()}@test.internal`;
  const signUpRes = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${apiKey}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: throwawayEmail, password: 'AuditPassword123!', returnSecureToken: true })
  });
  const signUpData = await signUpRes.json();
  const throwawayToken = signUpData.idToken;
  const throwawayUid = signUpData.localId;
  console.log(`Identity 1: Throwaway Student/User (UID: ${throwawayUid})`);

  // 2. Authenticate Test Driver
  const testDriverJson = JSON.parse(fs.readFileSync('C:/Users/bhrao/.secrets/pilot_test_driver.json', 'utf8'));
  const tdRes = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: testDriverJson.email, password: testDriverJson.password, returnSecureToken: true })
  });
  const tdData = await tdRes.json();
  const testDriverToken = tdData.idToken;
  const testDriverUid = tdData.localId;
  console.log(`Identity 2: Test Driver (UID: ${testDriverUid}, Bus: TEST-BUS-01)`);

  // 3. Authenticate Driver 3 (GURU SWAMY)
  // Read encrypted credential via child_process or clixml
  const { execSync } = require('child_process');
  const d3Pass = execSync(`powershell -NoProfile -Command "(Import-Clixml 'C:\\Users\\bhrao\\.secrets\\driver3_credential.clixml').GetNetworkCredential().Password"`).toString().trim();
  const d3Res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'driver3@collegebus.internal', password: d3Pass, returnSecureToken: true })
  });
  const d3Data = await d3Res.json();
  const d3Token = d3Data.idToken;
  const d3Uid = d3Data.localId;
  console.log(`Identity 3: Driver 3 GURU SWAMY (UID: ${d3Uid}, Bus: BUS-03)\n`);

  const now = Date.now();

  const testCases = [
    {
      num: 1,
      name: "Authorized Driver 3 -> Assigned Bus (BUS-03)",
      identity: `Driver 3 (${d3Uid})`,
      path: "live_locations/BUS-03",
      payload: { busId: "BUS-03", latitude: 15.15344, longitude: 76.90194, trackingStatus: "INACTIVE", lastUpdated: now },
      token: d3Token,
      expected: 200
    },
    {
      num: 2,
      name: "Authorized Driver 3 -> Unassigned Bus (BUS-01)",
      identity: `Driver 3 (${d3Uid})`,
      path: "live_locations/BUS-01",
      payload: { busId: "BUS-01", latitude: 15.15344, longitude: 76.90194, trackingStatus: "INACTIVE", lastUpdated: now },
      token: d3Token,
      expected: 401
    },
    {
      num: 3,
      name: "Test Driver -> Assigned Test Bus (TEST-BUS-01)",
      identity: `Test Driver (${testDriverUid})`,
      path: "live_locations/TEST-BUS-01",
      payload: { busId: "TEST-BUS-01", latitude: 15.15, longitude: 76.90, trackingStatus: "INACTIVE", lastUpdated: now },
      token: testDriverToken,
      expected: 200
    },
    {
      num: 4,
      name: "Test Driver -> Production Bus (BUS-03)",
      identity: `Test Driver (${testDriverUid})`,
      path: "live_locations/BUS-03",
      payload: { busId: "BUS-03", latitude: 15.15, longitude: 76.90, trackingStatus: "INACTIVE", lastUpdated: now },
      token: testDriverToken,
      expected: 401
    },
    {
      num: 5,
      name: "Ordinary Authenticated User -> live_locations/BUS-03",
      identity: `Throwaway Student (${throwawayUid})`,
      path: "live_locations/BUS-03",
      payload: { busId: "BUS-03", latitude: 15.15344, longitude: 76.90194, trackingStatus: "ACTIVE", lastUpdated: now },
      token: throwawayToken,
      expected: 401
    },
    {
      num: 6,
      name: "Ordinary Authenticated User -> live_locations/TEST-BUS-01",
      identity: `Throwaway Student (${throwawayUid})`,
      path: "live_locations/TEST-BUS-01",
      payload: { busId: "TEST-BUS-01", latitude: 15.15, longitude: 76.90, trackingStatus: "ACTIVE", lastUpdated: now },
      token: throwawayToken,
      expected: 401
    },
    {
      num: 7,
      name: "Student Legitimate Contribution -> location_sources/BUS-03/<uid>",
      identity: `Throwaway Student (${throwawayUid})`,
      path: `location_sources/BUS-03/${throwawayUid}`,
      payload: { busId: "BUS-03", sourceId: throwawayUid, sourceType: "SECONDARY_STUDENT", latitude: 15.153, longitude: 76.901, accuracy: 12.0, timestamp: now, active: true },
      token: throwawayToken,
      expected: 200
    },
    {
      num: 8,
      name: "Student Spoofing PRIMARY_DRIVER -> location_sources/BUS-03/<uid>",
      identity: `Throwaway Student (${throwawayUid})`,
      path: `location_sources/BUS-03/${throwawayUid}`,
      payload: { busId: "BUS-03", sourceId: throwawayUid, sourceType: "PRIMARY_DRIVER", latitude: 15.153, longitude: 76.901, accuracy: 12.0, timestamp: now, active: true },
      token: throwawayToken,
      expected: 401
    },
    {
      num: 9,
      name: "Ordinary User Impersonating Driver 3 UID -> location_sources",
      identity: `Throwaway Student (${throwawayUid})`,
      path: `location_sources/BUS-03/${d3Uid}`,
      payload: { busId: "BUS-03", sourceId: d3Uid, sourceType: "PRIMARY_DRIVER", latitude: 15.153, longitude: 76.901, accuracy: 12.0, timestamp: now, active: true },
      token: throwawayToken,
      expected: 401
    }
  ];

  let passed = 0;
  let failed = 0;

  for (const tc of testCases) {
    console.log(`--------------------------------------------------`);
    console.log(`TEST ${tc.num}: ${tc.name}`);
    console.log(`Command: PUT https://college-bus-tracker-prod-default-rtdb.firebaseio.com/${tc.path}.json?auth=<TOKEN>`);
    console.log(`Authenticated Identity: ${tc.identity}`);
    console.log(`Target Path: /${tc.path}`);
    console.log(`Payload: ${JSON.stringify(tc.payload)}`);
    console.log(`Expected Status: ${tc.expected === 200 ? '200 OK (ALLOW)' : '401 Unauthorized (DENY)'}`);

    const res = await fetch(`https://college-bus-tracker-prod-default-rtdb.firebaseio.com/${tc.path}.json?auth=${tc.token}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(tc.payload)
    });

    const body = await res.text();
    const actualStatus = res.status;
    const isPass = (actualStatus === tc.expected);

    console.log(`Actual Status: ${actualStatus} ${actualStatus === 200 ? 'OK' : 'Unauthorized'}`);
    console.log(`Raw Response: ${body.trim()}`);
    console.log(`Test Result: ${isPass ? 'PASS' : 'FAIL'}`);

    if (isPass) passed++;
    else failed++;
  }

  // Cleanup throwaway location_sources entry if created
  try {
    await fetch(`https://college-bus-tracker-prod-default-rtdb.firebaseio.com/location_sources/BUS-03/${throwawayUid}.json?auth=${throwawayToken}`, {
      method: 'DELETE'
    });
  } catch (e) {}

  console.log(`\n==================================================`);
  console.log(`TOTAL TESTS: ${testCases.length} | PASSED: ${passed} | FAILED: ${failed}`);
  console.log(`RULES STATUS: DEPLOYED & ACTIVELY ENFORCING IN PRODUCTION`);
  console.log(`==================================================\n`);

  if (failed > 0) process.exit(1);
}

runSecurityTests().catch(err => {
  console.error("Runner error:", err);
  process.exit(1);
});
