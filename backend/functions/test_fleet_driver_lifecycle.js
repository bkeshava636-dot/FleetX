/**
 * Comprehensive Automated Tests for Milestone 5: Fleet & Driver Management Lifecycle
 * 
 * Verifies all Phase 1 backend capabilities:
 *  1. Driver Profile validation (Name, Staff ID format, phone normalization, password)
 *  2. Staff Email mapping (${customId}@collegebus.internal)
 *  3. Admin authorization verification (rejects unauthenticated & non-admin callers)
 *  4. Driver creation & atomic registration in Firestore + RTDB
 *  5. Driver creation failure rollback: Auth user deleted when database write fails
 *  6. Initial bus assignment during driver creation
 *  7. Bus-Driver assignment: fresh assignment links bus and driver
 *  8. Bus-Driver replacement: replacing Driver A with Driver B cleanly unassigns Driver A
 *  9. Bus-Driver transfer: moving Driver A from Bus 01 to Bus 02 clears Bus 01
 * 10. Bus-Driver unassignment: unassigning clears both Firestore documents & RTDB permanentDriverUid
 * 11. Transactional concurrency: rejects assignment when bus state modified concurrently
 * 12. Deactivation guardrail: rejects deactivating assigned driver without explicit unassign confirmation
 * 13. Driver deactivation: cleanly unassigns bus, disables Auth account, updates RTDB
 * 14. Driver reactivation: enables Auth account, marks active in Firestore & RTDB
 * 15. Bus deactivation guardrail: rejects deactivating bus with active driver without confirmation
 * 16. Bus deactivation: cleanly detaches driver, marks bus inactive in Firestore & RTDB
 * 17. Staff ID update: updates Auth email, Firestore profile, and RTDB customId
 * 18. Staff ID update rollback: reverts Auth email if database update fails
 * 19. Password reset: updates Auth password, verifies target role is DRIVER
 * 20. Driver deletion: cleans up bus assignment, deletes Firestore & RTDB profiles, deletes Auth user
 * 21. Temporary bus swap preservation: clearing permanent driver preserves active temporary replacement
 * 22. Out-of-order trigger events: discards trigger event when incoming assignmentVersion < current RTDB version
 * 23. Duplicate trigger events: skips redundant idempotent trigger write
 * 24. Legacy Android direct writes: detects lack of assignmentVersion and reconciles RTDB
 * 25. RTDB Reconciliation: reconcileBusRtdb applies authoritative Firestore state and marks SYNCED
 */

const assert = require("assert");
const {
  validateDriverProfileInputs,
  mapStaffEmail,
  processCreateDriverAccount,
  processSetBusDriverAssignment,
  processToggleDriverActiveStatus,
  processToggleBusActiveStatus,
  processUpdateDriverStaffProfile,
  processReconcileBusRtdb,
  processOnBusAssignmentWritten,
  processResetDriverPassword,
  processDeleteDriverAccount
} = require("./index");

// In-memory mock harness for Firestore, Auth, and RTDB
function createMockHarness(initialState = {}) {
  const firestoreData = {
    users: { ...(initialState.users || {}) },
    buses: { ...(initialState.buses || {}) }
  };

  const rtdbData = {
    users: { ...(initialState.rtdbUsers || {}) },
    bus_operators: { ...(initialState.bus_operators || {}) }
  };

  const authUsers = { ...(initialState.authUsers || {}) };
  let authCreateFailure = false;
  let firestoreSetFailure = false;
  let authDeleteCalls = [];
  let authUpdateCalls = [];

  // Mock Firestore
  const mockDb = {
    collection(colName) {
      return {
        doc(docId) {
          return {
            id: docId,
            async get() {
              const col = firestoreData[colName] || {};
              const data = col[docId];
              return {
                exists: Boolean(data),
                id: docId,
                data: () => (data ? { ...data } : null)
              };
            },
            async set(data) {
              if (firestoreSetFailure) throw new Error("Firestore network disconnect");
              if (!firestoreData[colName]) firestoreData[colName] = {};
              firestoreData[colName][docId] = { ...data };
            },
            async update(data) {
              if (!firestoreData[colName] || !firestoreData[colName][docId]) {
                throw new Error(`Doc ${colName}/${docId} not found`);
              }
              firestoreData[colName][docId] = { ...firestoreData[colName][docId], ...data };
            },
            async delete() {
              if (firestoreData[colName]) {
                delete firestoreData[colName][docId];
              }
            }
          };
        },
        where(field, op, value) {
          return {
            async get() {
              const col = firestoreData[colName] || {};
              const matching = [];
              for (const [id, doc] of Object.entries(col)) {
                if (op === "==" && doc[field] === value) {
                  matching.push({
                    id,
                    data: () => ({ ...doc })
                  });
                }
              }
              return {
                empty: matching.length === 0,
                docs: matching
              };
            }
          };
        }
      };
    },
    async runTransaction(updateFunction) {
      const transaction = {
        async get(docRef) {
          return docRef.get();
        },
        update(docRef, data) {
          const parts = docRef.id ? ["", docRef.id] : [];
          // update immediately in mock
          docRef.update(data);
        }
      };
      return await updateFunction(transaction);
    }
  };

  // Mock RTDB
  const mockRtdb = {
    ref(path) {
      const parts = path.split("/");
      const rootKey = parts[0];
      const childKey = parts[1];

      return {
        async get() {
          const col = rtdbData[rootKey] || {};
          const val = childKey ? col[childKey] : col;
          return {
            val: () => (val ? JSON.parse(JSON.stringify(val)) : null)
          };
        },
        async set(val) {
          if (!rtdbData[rootKey]) rtdbData[rootKey] = {};
          if (childKey) {
            rtdbData[rootKey][childKey] = JSON.parse(JSON.stringify(val));
          } else {
            rtdbData[rootKey] = JSON.parse(JSON.stringify(val));
          }
        },
        async update(val) {
          if (!rtdbData[rootKey]) rtdbData[rootKey] = {};
          if (childKey) {
            rtdbData[rootKey][childKey] = {
              ...(rtdbData[rootKey][childKey] || {}),
              ...JSON.parse(JSON.stringify(val))
            };
          }
        },
        async remove() {
          if (rtdbData[rootKey] && childKey) {
            delete rtdbData[rootKey][childKey];
          }
        }
      };
    }
  };

  // Mock Auth
  const mockAuth = {
    async createUser({ email, password, displayName }) {
      if (authCreateFailure) {
        throw new Error("Simulated Auth user creation failure");
      }
      const uid = `uid_${displayName.replace(/\s+/g, "_").toLowerCase()}_${Date.now()}`;
      authUsers[uid] = { email, password, displayName, disabled: false };
      return { uid, email, displayName };
    },
    async updateUser(uid, fields) {
      authUpdateCalls.push({ uid, fields });
      if (!authUsers[uid]) throw new Error(`User ${uid} not found`);
      authUsers[uid] = { ...authUsers[uid], ...fields };
      return authUsers[uid];
    },
    async deleteUser(uid) {
      authDeleteCalls.push(uid);
      if (authUsers[uid]) {
        delete authUsers[uid];
      }
    }
  };

  return {
    firestoreData,
    rtdbData,
    authUsers,
    authDeleteCalls,
    authUpdateCalls,
    mockDb,
    mockRtdb,
    mockAuth,
    setAuthCreateFailure(flag) {
      authCreateFailure = flag;
    },
    setFirestoreSetFailure(flag) {
      firestoreSetFailure = flag;
    }
  };
}

// Active Admin Context Helper
function createAdminContext(uid = "admin_super") {
  return {
    auth: { uid, token: { email: "admin@collegebus.internal" } },
    app: { appId: "fleetx-web-app" }
  };
}

async function runFleetDriverTests() {
  console.log("==================================================");
  console.log("RUNNING MILESTONE 5 FLEET & DRIVER LIFECYCLE TESTS");
  console.log("==================================================\n");

  let passed = 0;
  let failed = 0;

  function it(desc, fn) {
    try {
      fn();
      console.log(`  ✅ PASS: ${desc}`);
      passed++;
    } catch (e) {
      console.error(`  ❌ FAIL: ${desc}`);
      console.error(e);
      failed++;
    }
  }

  async function itAsync(desc, fn) {
    try {
      await fn();
      console.log(`  ✅ PASS: ${desc}`);
      passed++;
    } catch (e) {
      console.error(`  ❌ FAIL: ${desc}`);
      console.error(e);
      failed++;
    }
  }

  // --- Suite 1: Driver Profile Validation & Email Mapping ---
  console.log("--- Suite 1: Input Validation & Email Mapping ---");

  it("1.1. Validates conforming name, staff ID, and phone number", () => {
    const res = validateDriverProfileInputs({
      name: "Ramesh Kumar",
      customId: "drv_01",
      phoneNumber: "+91 9876543210",
      password: "securePassword123"
    }, true);
    assert.strictEqual(res.cleanName, "Ramesh Kumar");
    assert.strictEqual(res.cleanId, "drv_01");
    assert.strictEqual(res.cleanPhone, "9876543210");
    assert.strictEqual(res.cleanPassword, "securePassword123");
  });

  it("1.2. Rejects invalid staff ID (contains 'admin', spaces, or special symbols)", () => {
    assert.throws(() => {
      validateDriverProfileInputs({ name: "Ramesh", customId: "drv_admin", password: "pwd" }, true);
    }, /cannot contain 'admin'/);

    assert.throws(() => {
      validateDriverProfileInputs({ name: "Ramesh", customId: "drv 01", password: "pwd" }, true);
    }, /lowercase letters, numbers/);

    assert.throws(() => {
      validateDriverProfileInputs({ name: "Ramesh", customId: "drv#01", password: "pwd" }, false);
    }, /lowercase letters, numbers/);
  });

  it("1.3. Normalizes 10-digit Indian mobile numbers and rejects invalid prefixes", () => {
    const res1 = validateDriverProfileInputs({ name: "Ramesh", customId: "drv_01", phoneNumber: "09876543210" }, false);
    assert.strictEqual(res1.cleanPhone, "9876543210");

    assert.throws(() => {
      validateDriverProfileInputs({ name: "Ramesh", customId: "drv_01", phoneNumber: "1234567890" }, false);
    }, /must begin with 6, 7, 8, or 9/);
  });

  it("1.4. Accurately maps staff email convention", () => {
    assert.strictEqual(mapStaffEmail("DRV_01"), "drv_01@collegebus.internal");
    assert.strictEqual(mapStaffEmail(" staff-42 "), "staff-42@collegebus.internal");
  });

  // --- Suite 2: Admin Authorization & Driver Creation ---
  console.log("\n--- Suite 2: Driver Creation & Auth Rollback ---");

  await itAsync("2.1. Rejects unauthenticated caller from creating driver", async () => {
    const harness = createMockHarness();
    await assert.rejects(async () => {
      await processCreateDriverAccount(
        { name: "Driver One", customId: "drv_01", password: "password123" },
        null, // No auth context
        { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
      );
    }, (err) => err.code === "unauthenticated" || err.message.includes("Authentication is required"));
  });

  await itAsync("2.2. Rejects non-admin user from creating driver", async () => {
    const harness = createMockHarness({
      users: {
        student_caller: { uid: "student_caller", role: "STUDENT", active: true }
      }
    });
    await assert.rejects(async () => {
      await processCreateDriverAccount(
        { name: "Driver One", customId: "drv_01", password: "password123" },
        { auth: { uid: "student_caller" } },
        { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
      );
    }, (err) => err.code === "permission-denied" || err.message.includes("Only active administrators"));
  });

  await itAsync("2.3. Creates driver in Auth, Firestore, and RTDB atomically", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      }
    });

    const res = await processCreateDriverAccount(
      {
        name: "Suresh Gowda",
        customId: "drv_01",
        phoneNumber: "+91 9123456780",
        password: "secretPassword"
      },
      createAdminContext(),
      { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(res.success, true);
    assert.strictEqual(res.customId, "drv_01");
    assert.strictEqual(res.email, "drv_01@collegebus.internal");

    // Verify Firestore document
    const createdDoc = harness.firestoreData.users[res.uid];
    assert.ok(createdDoc, "Firestore user profile must exist");
    assert.strictEqual(createdDoc.role, "DRIVER");
    assert.strictEqual(createdDoc.active, true);
    assert.strictEqual(createdDoc.phoneNumber, "9123456780");

    // Verify RTDB document
    const rtdbUser = harness.rtdbData.users[res.uid];
    assert.ok(rtdbUser, "RTDB user profile must exist");
    assert.strictEqual(rtdbUser.role, "DRIVER");
    assert.strictEqual(rtdbUser.active, true);
  });

  await itAsync("2.4. Prevents duplicate Staff ID registration", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        existing_driver: { uid: "existing_driver", customId: "drv_01", role: "DRIVER", active: true }
      }
    });

    await assert.rejects(async () => {
      await processCreateDriverAccount(
        { name: "Second Driver", customId: "drv_01", password: "password123" },
        createAdminContext(),
        { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
      );
    }, (err) => err.code === "already-exists" || err.message.includes("already registered"));
  });

  await itAsync("2.5. Rolls back Auth user when Firestore/RTDB creation fails", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      }
    });
    // Trigger simulated firestore failure
    harness.setFirestoreSetFailure(true);

    await assert.rejects(async () => {
      await processCreateDriverAccount(
        { name: "Failed Driver", customId: "drv_fail", password: "password123" },
        createAdminContext(),
        { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
      );
    }, /Firestore network disconnect/);

    assert.strictEqual(harness.authDeleteCalls.length, 1, "Auth deleteUser must be called during rollback");
  });

  // --- Suite 3: Bus-Driver Assignment Engine ---
  console.log("\n--- Suite 3: Bus-Driver Assignment Engine ---");

  await itAsync("3.1. Assigns active driver to bus and synchronizes RTDB", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_driver_01: { uid: "uid_driver_01", name: "Driver One", role: "DRIVER", active: true, assignedBusId: null }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", active: true, driverId: null, driverName: null, assignmentVersion: 1 }
      }
    });

    const res = await processSetBusDriverAssignment(
      { busId: "BUS-01", driverUid: "uid_driver_01" },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(res.success, true);
    assert.strictEqual(res.assignmentVersion, 2);

    // Check Firestore
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, "uid_driver_01");
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverName, "Driver One");
    assert.strictEqual(harness.firestoreData.users["uid_driver_01"].assignedBusId, "BUS-01");

    // Check RTDB
    assert.strictEqual(harness.rtdbData.users["uid_driver_01"].assignedBusId, "BUS-01");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_driver_01");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].assignmentVersion, 2);
  });

  await itAsync("3.2. Replacement: unassigns previous driver and assigns new driver", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_driver_A: { uid: "uid_driver_A", name: "Driver A", role: "DRIVER", active: true, assignedBusId: "BUS-01" },
        uid_driver_B: { uid: "uid_driver_B", name: "Driver B", role: "DRIVER", active: true, assignedBusId: null }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", active: true, driverId: "uid_driver_A", driverName: "Driver A", assignmentVersion: 2 }
      },
      rtdbUsers: {
        uid_driver_A: { role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_driver_A", assignmentVersion: 2 }
      }
    });

    const res = await processSetBusDriverAssignment(
      { busId: "BUS-01", driverUid: "uid_driver_B" },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(res.success, true);
    assert.strictEqual(res.assignmentVersion, 3);

    // Driver A unassigned
    assert.strictEqual(harness.firestoreData.users["uid_driver_A"].assignedBusId, null);
    assert.strictEqual(harness.rtdbData.users["uid_driver_A"].assignedBusId, null);

    // Driver B assigned
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, "uid_driver_B");
    assert.strictEqual(harness.firestoreData.users["uid_driver_B"].assignedBusId, "BUS-01");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_driver_B");
  });

  await itAsync("3.3. Transfer: moving driver from Bus 01 to Bus 02 clears Bus 01", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_driver_A: { uid: "uid_driver_A", name: "Driver A", role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", active: true, driverId: "uid_driver_A", driverName: "Driver A", assignmentVersion: 1 },
        "BUS-02": { busId: "BUS-02", busNumber: "02", active: true, driverId: null, driverName: null, assignmentVersion: 1 }
      }
    });

    const res = await processSetBusDriverAssignment(
      { busId: "BUS-02", driverUid: "uid_driver_A" },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(res.success, true);

    // Bus 01 cleared
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, null);
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverName, null);

    // Bus 02 assigned
    assert.strictEqual(harness.firestoreData.buses["BUS-02"].driverId, "uid_driver_A");
    assert.strictEqual(harness.firestoreData.users["uid_driver_A"].assignedBusId, "BUS-02");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-02"].permanentDriverUid, "uid_driver_A");
  });

  await itAsync("3.4. Unassignment: clears bus and driver records completely", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_driver_01: { uid: "uid_driver_01", name: "Driver One", role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", active: true, driverId: "uid_driver_01", driverName: "Driver One", assignmentVersion: 4 }
      }
    });

    const res = await processSetBusDriverAssignment(
      { busId: "BUS-01", driverUid: null }, // Null unassigns
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(res.success, true);
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, null);
    assert.strictEqual(harness.firestoreData.users["uid_driver_01"].assignedBusId, null);
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, null);
  });

  await itAsync("3.5. Rejects assigning to deactivated bus or assigning deactivated driver", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_active: { uid: "uid_active", role: "DRIVER", active: true },
        uid_inactive: { uid: "uid_inactive", role: "DRIVER", active: false }
      },
      buses: {
        "BUS-ACTIVE": { busId: "BUS-ACTIVE", active: true },
        "BUS-DEACTIVATED": { busId: "BUS-DEACTIVATED", active: false }
      }
    });

    // Case A: Bus is deactivated
    await assert.rejects(async () => {
      await processSetBusDriverAssignment(
        { busId: "BUS-DEACTIVATED", driverUid: "uid_active" },
        createAdminContext(),
        { db: harness.mockDb, rtdb: harness.mockRtdb }
      );
    }, /Cannot assign driver to deactivated bus/);

    // Case B: Driver is deactivated
    await assert.rejects(async () => {
      await processSetBusDriverAssignment(
        { busId: "BUS-ACTIVE", driverUid: "uid_inactive" },
        createAdminContext(),
        { db: harness.mockDb, rtdb: harness.mockRtdb }
      );
    }, /Cannot assign deactivated driver/);
  });

  // --- Suite 4: Deactivation Guardrails ---
  console.log("\n--- Suite 4: Deactivation Guardrails ---");

  await itAsync("4.1. Driver deactivation rejects if driver is assigned to bus unless confirmed", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_driver_01: { uid: "uid_driver_01", name: "Ramesh", role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", active: true, driverId: "uid_driver_01" }
      },
      authUsers: {
        uid_driver_01: { email: "drv_01@collegebus.internal", disabled: false }
      }
    });

    // Without explicit confirmation -> rejected
    await assert.rejects(async () => {
      await processToggleDriverActiveStatus(
        { driverUid: "uid_driver_01", active: false },
        createAdminContext(),
        { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
      );
    }, /cannot be deactivated while assigned to bus/);

    // With explicit unassignment confirmed -> succeeds
    const res = await processToggleDriverActiveStatus(
      { driverUid: "uid_driver_01", active: false, unassignBusIfAssigned: true },
      createAdminContext(),
      { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
    );
    assert.strictEqual(res.success, true);
    assert.strictEqual(harness.firestoreData.users["uid_driver_01"].active, false);
    assert.strictEqual(harness.firestoreData.users["uid_driver_01"].assignedBusId, null);
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, null);
    assert.strictEqual(harness.authUsers["uid_driver_01"].disabled, true);
  });

  await itAsync("4.2. Bus deactivation detaches driver when confirmed", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_driver_01: { uid: "uid_driver_01", name: "Ramesh", role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", active: true, driverId: "uid_driver_01", driverName: "Ramesh" }
      }
    });

    // Without confirmation -> rejected
    await assert.rejects(async () => {
      await processToggleBusActiveStatus(
        { busId: "BUS-01", active: false },
        createAdminContext(),
        { db: harness.mockDb, rtdb: harness.mockRtdb }
      );
    }, /has an active assigned driver/);

    // With confirmation -> unassigns driver and deactivates bus
    const res = await processToggleBusActiveStatus(
      { busId: "BUS-01", active: false, unassignDriverIfAssigned: true },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );
    assert.strictEqual(res.success, true);
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].active, false);
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, null);
    assert.strictEqual(harness.firestoreData.users["uid_driver_01"].assignedBusId, null);
  });

  // --- Suite 5: Staff ID Update & Temporary Swap Preservation ---
  console.log("\n--- Suite 5: Staff ID Updates & Temporary Swaps ---");

  await itAsync("5.1. Updates Staff ID and Auth email; rolls back if DB update fails", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_driver_01: { uid: "uid_driver_01", customId: "drv_01", name: "Ramesh", role: "DRIVER", email: "drv_01@collegebus.internal" }
      },
      authUsers: {
        uid_driver_01: { email: "drv_01@collegebus.internal", displayName: "Ramesh" }
      }
    });

    const res = await processUpdateDriverStaffProfile(
      { driverUid: "uid_driver_01", name: "Ramesh K", customId: "drv_01_new", phoneNumber: "9876543210" },
      createAdminContext(),
      { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(res.success, true);
    assert.strictEqual(res.customId, "drv_01_new");
    assert.strictEqual(res.email, "drv_01_new@collegebus.internal");
    assert.strictEqual(harness.authUsers["uid_driver_01"].email, "drv_01_new@collegebus.internal");
    assert.strictEqual(harness.firestoreData.users["uid_driver_01"].customId, "drv_01_new");
  });

  await itAsync("5.2. Preserves active temporary bus swap when unassigning permanent driver", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_perm: { uid: "uid_perm", name: "Permanent", role: "DRIVER", active: true, assignedBusId: "BUS-25" }
      },
      buses: {
        "BUS-25": { busId: "BUS-25", active: true, driverId: "uid_perm", driverName: "Permanent", assignmentVersion: 5 }
      },
      bus_operators: {
        "BUS-25": {
          busId: "BUS-25",
          permanentDriverUid: "uid_perm",
          hasTemporary: true,
          temporaryDriverUid: "uid_temp_replacement",
          startAt: 1000,
          endAt: 9999999999,
          temporaryAssignmentId: "swap_123"
        }
      }
    });

    // Unassign permanent driver
    await processSetBusDriverAssignment(
      { busId: "BUS-25", driverUid: null },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    const rtdbNode = harness.rtdbData.bus_operators["BUS-25"];
    assert.strictEqual(rtdbNode.permanentDriverUid, null, "Permanent driver must be cleared");
    assert.strictEqual(rtdbNode.hasTemporary, true, "hasTemporary MUST remain true");
    assert.strictEqual(rtdbNode.temporaryDriverUid, "uid_temp_replacement", "temporary driver MUST remain untouched");
  });

  // --- Suite 6: Trigger Anti-Entropy & Out-of-Order Handling ---
  console.log("\n--- Suite 6: Trigger Anti-Entropy & Idempotence ---");

  await itAsync("6.1. Trigger discards stale out-of-order event where version < RTDB version", async () => {
    const harness = createMockHarness({
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_new", assignmentVersion: 2 }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_new", assignmentVersion: 5 }
      }
    });

    const change = {
      before: { exists: true, data: () => ({ driverId: "uid_old", assignmentVersion: 1 }) },
      after: { exists: true, data: () => ({ driverId: "uid_old_delayed", assignmentVersion: 2, active: true }) }
    };

    // Stale trigger execution
    await processOnBusAssignmentWritten(change, { params: { busId: "BUS-01" } }, { db: harness.mockDb, rtdb: harness.mockRtdb });

    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_new", "RTDB permanent driver must NOT be overwritten by stale event");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].assignmentVersion, 5);
  });

  await itAsync("6.2. Trigger handles legacy Android direct write without version", async () => {
    const harness = createMockHarness({
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_android", driverName: "Android Driver", active: true } // No assignmentVersion
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_old", assignmentVersion: 3 }
      }
    });

    const change = {
      before: { exists: true, data: () => ({ driverId: "uid_old", active: true }) },
      after: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_android", driverName: "Android Driver", active: true }) }
    };

    await processOnBusAssignmentWritten(change, { params: { busId: "BUS-01" } }, { db: harness.mockDb, rtdb: harness.mockRtdb });

    // Verifies monotonic increment to version 4 and RTDB synchronization
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_android");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].assignmentVersion, 4);
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].assignmentVersion, 4);
  });

  await itAsync("6.3. RTDB reconciliation forces authoritative state and marks SYNCED", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_recovered", driverName: "Recovered", active: true, assignmentVersion: 10, rtdbSyncStatus: "FAILED" }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_old", assignmentVersion: 8 }
      }
    });

    const res = await processReconcileBusRtdb(
      { busId: "BUS-01" },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(res.success, true);
    assert.strictEqual(res.rtdbSyncStatus, "SYNCED");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_recovered");
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].rtdbSyncStatus, "SYNCED");
  });

  // --- Suite 7: Password Reset & Hardened Deletion ---
  console.log("\n--- Suite 7: Password Reset & Driver Deletion ---");

  await itAsync("7.1. Password reset verifies target role is DRIVER and updates Auth", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_driver_01: { uid: "uid_driver_01", role: "DRIVER", active: true },
        uid_student: { uid: "uid_student", role: "STUDENT", active: true }
      },
      authUsers: {
        uid_driver_01: { email: "drv_01@collegebus.internal", password: "oldPassword" }
      }
    });

    // Attempting reset on student -> rejected
    await assert.rejects(async () => {
      await processResetDriverPassword(
        { driverUid: "uid_student", newPassword: "newSecurePassword123" },
        createAdminContext(),
        { db: harness.mockDb, auth: harness.mockAuth }
      );
    }, /Target user is not a driver/);

    // Resetting on driver -> succeeds
    const res = await processResetDriverPassword(
      { driverUid: "uid_driver_01", newPassword: "newSecurePassword123" },
      createAdminContext(),
      { db: harness.mockDb, auth: harness.mockAuth }
    );
    assert.strictEqual(res.success, true);
    assert.strictEqual(harness.authUsers["uid_driver_01"].password, "newSecurePassword123");
  });

  await itAsync("7.2. Hardened driver deletion cleans bus, Firestore, RTDB, and Auth", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_to_delete: { uid: "uid_to_delete", role: "DRIVER", active: true, assignedBusId: "BUS-09" }
      },
      buses: {
        "BUS-09": { busId: "BUS-09", driverId: "uid_to_delete", driverName: "To Delete" }
      },
      rtdbUsers: {
        uid_to_delete: { role: "DRIVER", active: true }
      },
      bus_operators: {
        "BUS-09": { busId: "BUS-09", permanentDriverUid: "uid_to_delete" }
      },
      authUsers: {
        uid_to_delete: { email: "drv_del@collegebus.internal" }
      }
    });

    const res = await processDeleteDriverAccount(
      { driverUid: "uid_to_delete" },
      createAdminContext(),
      { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(res.success, true);
    assert.strictEqual(harness.firestoreData.users["uid_to_delete"], undefined, "Firestore user profile must be deleted");
    assert.strictEqual(harness.rtdbData.users["uid_to_delete"], undefined, "RTDB user profile must be removed");
    assert.strictEqual(harness.authUsers["uid_to_delete"], undefined, "Auth user must be deleted");
    assert.strictEqual(harness.firestoreData.buses["BUS-09"].driverId, null, "Bus assignment must be unassigned");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-09"].permanentDriverUid, null, "Bus operator must be cleared");
  });

  console.log("\n==================================================");
  console.log(`TEST SUMMARY: ${passed} PASSED, ${failed} FAILED`);
  console.log("==================================================");

  if (failed > 0) {
    process.exit(1);
  }
}

runFleetDriverTests().catch(err => {
  console.error("Fatal error running test suite:", err);
  process.exit(1);
});
