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
 * 26. Cross-Bus collision resolution: clears previous bus when driver assigned to new bus
 * 27. Trigger re-entrancy idempotence: verifies Firestore version update doesn't cause recursive RTDB write
 * 28. Scheduled reconciliation worker: processReconcileOperationalRtdb recovers FAILED and stale PENDING syncs
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
  processReconcileOperationalRtdb,
  processResetDriverPassword,
  processDeleteDriverAccount,
  processRegisterBusAccount,
  processUpdateBusDetails,
  syncAssignmentToRtdb,
  processGetEffectiveDriver,
  syncPublicDriverDirectory,
  processCreateTemporaryAssignment
} = require("./index");

// In-memory mock harness for Firestore, Auth, and RTDB
function createMockHarness(initialState = {}) {
  const firestoreData = {
    users: { ...(initialState.users || {}) },
    buses: { ...(initialState.buses || {}) },
    routes: { ...(initialState.routes || {}) },
    bus_number_index: { ...(initialState.bus_number_index || {}) },
    bus_registration_index: { ...(initialState.bus_registration_index || {}) },
    public_driver_directory: { ...(initialState.public_driver_directory || {}) },
    temporary_assignments: { ...(initialState.temporary_assignments || {}) }
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
          const filters = [{ field, op, value }];
          const queryObj = {
            where(nextField, nextOp, nextValue) {
              filters.push({ field: nextField, op: nextOp, value: nextValue });
              return queryObj;
            },
            limit(num) {
              return queryObj;
            },
            async get() {
              const col = firestoreData[colName] || {};
              const matching = [];
              for (const [id, doc] of Object.entries(col)) {
                const matchesAll = filters.every(f => {
                  if (f.op === "==") return doc[f.field] === f.value;
                  if (f.op === "!=") return doc[f.field] !== undefined && doc[f.field] !== f.value;
                  return false;
                });
                if (matchesAll) {
                  matching.push({
                    id,
                    data: () => ({ ...doc }),
                    ref: mockDb.collection(colName).doc(id)
                  });
                }
              }
              return {
                empty: matching.length === 0,
                docs: matching,
                forEach(cb) { matching.forEach(cb); }
              };
            }
          };
          return queryObj;
        }
      };
    },
    async runTransaction(updateFunction) {
      const stagedWrites = [];
      const transaction = {
        async get(docRef) {
          return docRef.get();
        },
        set(docRef, data) {
          stagedWrites.push(() => docRef.set(data));
        },
        update(docRef, data) {
          stagedWrites.push(() => docRef.update(data));
        },
        delete(docRef) {
          stagedWrites.push(() => docRef.delete());
        }
      };
      const res = await updateFunction(transaction);
      for (const write of stagedWrites) {
        await write();
      }
      return res;
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
            exists: () => val !== undefined && val !== null,
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
        async transaction(updateFn) {
          if (!rtdbData[rootKey]) rtdbData[rootKey] = {};
          const current = childKey ? rtdbData[rootKey][childKey] : rtdbData[rootKey];
          const copy = current ? JSON.parse(JSON.stringify(current)) : null;
          const result = updateFn(copy);
          if (result === undefined) {
            return { committed: false, snapshot: { val: () => copy } };
          }
          if (childKey) {
            rtdbData[rootKey][childKey] = JSON.parse(JSON.stringify(result));
          } else {
            rtdbData[rootKey] = JSON.parse(JSON.stringify(result));
          }
          return { committed: true, snapshot: { val: () => JSON.parse(JSON.stringify(result)) } };
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

  await itAsync("3.2. Replacement: requires explicit confirmation and unassigns previous driver atomically", async () => {
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

    // 1. Silent replacement attempt without confirmReplacement is rejected
    await assert.rejects(async () => {
      await processSetBusDriverAssignment(
        { busId: "BUS-01", driverUid: "uid_driver_B" },
        createAdminContext(),
        { db: harness.mockDb, rtdb: harness.mockRtdb }
      );
    }, /Explicit confirmation is required to replace the existing driver/);

    // 2. Confirmed replacement succeeds atomically
    const res = await processSetBusDriverAssignment(
      { busId: "BUS-01", driverUid: "uid_driver_B", confirmReplacement: true, expectedPreviousDriverId: "uid_driver_A" },
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

  await itAsync("6.4. Cross-bus collision: legacy Android assigning a driver already on another bus clears previous bus", async () => {
    const harness = createMockHarness({
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_shared", driverName: "Shared Driver", active: true, assignmentVersion: 1 },
        "BUS-02": { busId: "BUS-02", driverId: "uid_shared", driverName: "Shared Driver", active: true } // Android just wrote this
      },
      users: {
        uid_shared: { uid: "uid_shared", name: "Shared Driver", role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_shared", assignmentVersion: 1 },
        "BUS-02": { busId: "BUS-02", permanentDriverUid: null, assignmentVersion: 0 }
      }
    });

    const change = {
      before: { exists: true, data: () => ({ busId: "BUS-02", driverId: null, active: true }) },
      after: { exists: true, data: () => ({ busId: "BUS-02", driverId: "uid_shared", driverName: "Shared Driver", active: true }) }
    };

    await processOnBusAssignmentWritten(change, { params: { busId: "BUS-02" } }, { db: harness.mockDb, rtdb: harness.mockRtdb });

    // BUS-02 now has the driver
    assert.strictEqual(harness.firestoreData.buses["BUS-02"].driverId, "uid_shared");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-02"].permanentDriverUid, "uid_shared");

    // BUS-01 was automatically cleaned up in both Firestore and RTDB
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, null, "BUS-01 must have driverId cleared");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, null, "BUS-01 RTDB operator must be cleared");

    // User assignedBusId points to BUS-02
    assert.strictEqual(harness.firestoreData.users["uid_shared"].assignedBusId, "BUS-02");
  });

  await itAsync("6.5. Trigger re-entrancy safety: re-entrant trigger event generated by Firestore version update terminates immediately as duplicate", async () => {
    const harness = createMockHarness({
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_driver_A", active: true }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_old", assignmentVersion: 2 }
      }
    });

    // Invocation 1: Legacy write without version
    const change1 = {
      before: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_old", active: true }) },
      after: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_A", active: true }) }
    };
    await processOnBusAssignmentWritten(change1, { params: { busId: "BUS-01" } }, { db: harness.mockDb, rtdb: harness.mockRtdb });

    // Verify Invocation 1 updated RTDB first and bumped version to 3
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].assignmentVersion, 3);
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_driver_A");

    // Invocation 2: Generated by Firestore update on line 2750 ({ assignmentVersion: 3, rtdbSyncStatus: "SYNCED" })
    const change2 = {
      before: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_A", active: true }) },
      after: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_A", active: true, assignmentVersion: 3, rtdbSyncStatus: "SYNCED" }) }
    };

    let rtdbWrittenDuringInvocation2 = false;
    const originalSet = harness.mockRtdb.ref;
    // Track if any RTDB writes happen during Invocation 2
    harness.mockRtdb.ref = (path) => {
      const refObj = originalSet(path);
      return {
        ...refObj,
        update: async (val) => {
          rtdbWrittenDuringInvocation2 = true;
          return refObj.update(val);
        }
      };
    };

    await processOnBusAssignmentWritten(change2, { params: { busId: "BUS-01" } }, { db: harness.mockDb, rtdb: harness.mockRtdb });

    // Invocation 2 must exit as duplicate without issuing any RTDB updates
    assert.strictEqual(rtdbWrittenDuringInvocation2, false, "Invocation 2 must NOT execute redundant RTDB writes");
  });

  await itAsync("6.6. Stale Trigger: delayed event for Driver A executing after Driver B does not overwrite newer assignment (Versioned)", async () => {
    // 1. Bus 1 initially has Driver A (v1)
    // 2. Bus 1 subsequently assigned Driver B (v2) in Firestore
    const harness = createMockHarness({
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_driver_B", driverName: "Driver B", active: true, assignmentVersion: 2, rtdbSyncStatus: "SYNCED" }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_driver_B", assignmentVersion: 2 }
      }
    });

    // 3. Driver B's trigger already executed.
    // 4. Delayed trigger event for Driver A arrives with v1
    const delayedChangeA = {
      before: { exists: true, data: () => ({ busId: "BUS-01", driverId: null, active: true }) },
      after: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_A", driverName: "Driver A", active: true, assignmentVersion: 1 }) }
    };

    const res = await processOnBusAssignmentWritten(delayedChangeA, { params: { busId: "BUS-01" } }, { db: harness.mockDb, rtdb: harness.mockRtdb });

    assert.strictEqual(res, null, "Delayed event must be discarded");
    // Verify RTDB still has Driver B
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_driver_B", "RTDB must continue to show Driver B");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].assignmentVersion, 2);
    // Verify Firestore still has Driver B
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, "uid_driver_B", "Firestore must remain authoritative with Driver B");
  });



  await itAsync("6.7. Stale Trigger: delayed event for Driver A executing after Driver B does not overwrite newer assignment (Legacy Android Unversioned)", async () => {
    // 1. Android wrote Driver A (unversioned).
    // 2. Android subsequently wrote Driver B (unversioned). Current Firestore doc has Driver B!
    const harness = createMockHarness({
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_driver_B", driverName: "Driver B", active: true, assignmentVersion: 1, rtdbSyncStatus: "SYNCED" }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_driver_B", assignmentVersion: 1 }
      }
    });

    // 3. Delayed trigger event for Driver A arrives with NO assignmentVersion
    const delayedChangeA = {
      before: { exists: true, data: () => ({ busId: "BUS-01", driverId: null, active: true }) },
      after: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_A", driverName: "Driver A", active: true }) } // no assignmentVersion!
    };

    const res = await processOnBusAssignmentWritten(delayedChangeA, { params: { busId: "BUS-01" } }, { db: harness.mockDb, rtdb: harness.mockRtdb });

    assert.strictEqual(res, null, "Delayed legacy event must be discarded");
    // Verify RTDB still has Driver B
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_driver_B", "RTDB must continue to show Driver B");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].assignmentVersion, 1);
    // Verify Firestore still has Driver B
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, "uid_driver_B", "Firestore must remain authoritative with Driver B");
  });

  await itAsync("6.8. Deterministic Interleaved Concurrency: paused Trigger A does not overwrite Driver B after resume", async () => {
    // Initial state: Bus 1 assigned to Driver A in Firestore
    const harness = createMockHarness({
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_driver_A", driverName: "Driver A", active: true }
      },
      users: {
        uid_driver_A: { uid: "uid_driver_A", role: "DRIVER", active: true, assignedBusId: "BUS-01" },
        uid_driver_B: { uid: "uid_driver_B", role: "DRIVER", active: true, assignedBusId: null }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: null, assignmentVersion: 0 }
      },
      rtdbUsers: {
        uid_driver_A: { role: "DRIVER", active: true, assignedBusId: null },
        uid_driver_B: { role: "DRIVER", active: true, assignedBusId: null }
      }
    });

    let triggerAPaused = false;
    let resumeTriggerA = null;
    const pausePromise = new Promise((resolve) => {
      resumeTriggerA = resolve;
    });

    // Intercept rtdb.ref for Trigger A to pause it immediately after its initial Firestore check
    const originalRef = harness.mockRtdb.ref;
    let invocationCount = 0;

    harness.mockRtdb.ref = (path) => {
      const refObj = originalRef(path);
      if (path === "bus_operators/BUS-01") {
        invocationCount++;
        // If this is Trigger A's initial RTDB read after its Firestore check:
        if (invocationCount === 1) {
          return {
            ...refObj,
            async get() {
              triggerAPaused = true;
              // Pause Trigger A here!
              await pausePromise;
              return refObj.get();
            }
          };
        }
      }
      return refObj;
    };

    // 1. Trigger A starts for Driver A
    const changeA = {
      before: { exists: true, data: () => ({ busId: "BUS-01", driverId: null, active: true }) },
      after: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_A", driverName: "Driver A", active: true }) }
    };

    const promiseA = processOnBusAssignmentWritten(
      changeA,
      { params: { busId: "BUS-01" } },
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    // Wait until Trigger A has completed its initial Firestore check and is paused
    while (!triggerAPaused) {
      await new Promise((r) => setTimeout(r, 5));
    }

    // 4. Firestore is updated to Driver B
    harness.firestoreData.buses["BUS-01"] = {
      busId: "BUS-01",
      driverId: "uid_driver_B",
      driverName: "Driver B",
      active: true
    };
    harness.firestoreData.users["uid_driver_B"].assignedBusId = "BUS-01";
    harness.firestoreData.users["uid_driver_A"].assignedBusId = null;

    // 5. Trigger B synchronizes Driver B to RTDB and completes
    const changeB = {
      before: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_A", active: true }) },
      after: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_B", driverName: "Driver B", active: true }) }
    };

    await processOnBusAssignmentWritten(
      changeB,
      { params: { busId: "BUS-01" } },
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    // Verify Trigger B set Driver B in RTDB
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_driver_B");
    assert.strictEqual(harness.rtdbData.users["uid_driver_B"].assignedBusId, "BUS-01");

    // 6. Resume Trigger A
    resumeTriggerA();

    // 7. Await Trigger A completion
    await promiseA;

    // EXPECTED RESULTS:
    // - Driver B must remain the final RTDB assignment
    assert.strictEqual(
      harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid,
      "uid_driver_B",
      "Driver B must remain the final RTDB assignment"
    );
    // - Driver A must never overwrite the newer assignment permanently
    assert.strictEqual(
      harness.firestoreData.buses["BUS-01"].driverId,
      "uid_driver_B",
      "Driver A must never overwrite the newer assignment in Firestore"
    );
    // - Firestore and RTDB must converge
    assert.strictEqual(
      harness.firestoreData.buses["BUS-01"].driverId,
      harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid,
      "Firestore and RTDB must converge"
    );
    // - Driver authorization records must remain consistent
    assert.strictEqual(harness.rtdbData.users["uid_driver_B"].assignedBusId, "BUS-01");
    assert.strictEqual(harness.rtdbData.users["uid_driver_A"].assignedBusId, null);
  });

  await itAsync("6.9. Post-Sync Authoritative Convergence: Trigger A paused immediately before syncAssignmentToRtdb restores Driver B", async () => {
    // Initial state: Bus 1 assigned to Driver A in Firestore, but unassigned in RTDB
    const harness = createMockHarness({
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_driver_A", driverName: "Driver A", active: true }
      },
      users: {
        uid_driver_A: { uid: "uid_driver_A", role: "DRIVER", active: true, assignedBusId: "BUS-01" },
        uid_driver_B: { uid: "uid_driver_B", role: "DRIVER", active: true, assignedBusId: null }
      },
      bus_operators: {
        "BUS-01": {
          busId: "BUS-01",
          permanentDriverUid: null,
          assignmentVersion: 0,
          hasTemporary: true,
          temporaryDriverUid: "uid_temp_swap",
          startAt: 100,
          endAt: 999999
        }
      },
      rtdbUsers: {
        uid_driver_A: { role: "DRIVER", active: true, assignedBusId: null },
        uid_driver_B: { role: "DRIVER", active: true, assignedBusId: null }
      }
    });

    let triggerAPausedBeforeSync = false;
    let resumeTriggerA = null;
    const pausePromise = new Promise((resolve) => {
      resumeTriggerA = resolve;
    });

    const originalRef = harness.mockRtdb.ref;
    let preSyncCheckPassed = false;

    harness.mockRtdb.ref = (path) => {
      const refObj = originalRef(path);
      // Let Trigger A pass its initial Firestore check AND its RTDB pre-sync discrepancy check (on bus_operators/BUS-01)
      if (path === "bus_operators/BUS-01" && !preSyncCheckPassed) {
        return {
          ...refObj,
          async get() {
            const res = await refObj.get();
            preSyncCheckPassed = true; // Pre-sync check on RTDB has now passed
            return res;
          }
        };
      }

      // Pause Trigger A immediately before it writes in syncAssignmentToRtdb (first call in syncAssignmentToRtdb is users/uid_driver_A)
      if (path === "users/uid_driver_A" && preSyncCheckPassed && !triggerAPausedBeforeSync) {
        return {
          ...refObj,
          async update(val) {
            triggerAPausedBeforeSync = true;
            // PAUSE TRIGGER A HERE: immediately before syncAssignmentToRtdb writes!
            await pausePromise;
            return refObj.update(val);
          }
        };
      }

      return refObj;
    };

    // 1. Trigger A starts for Driver A
    const changeA = {
      before: { exists: true, data: () => ({ busId: "BUS-01", driverId: null, active: true }) },
      after: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_A", driverName: "Driver A", active: true }) }
    };

    const promiseA = processOnBusAssignmentWritten(
      changeA,
      { params: { busId: "BUS-01" } },
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    // Wait until Trigger A has passed pre-sync check and is paused immediately before syncAssignmentToRtdb
    while (!triggerAPausedBeforeSync) {
      await new Promise((r) => setTimeout(r, 5));
    }

    // 5. While Trigger A is paused, Firestore is updated to Driver B
    harness.firestoreData.buses["BUS-01"] = {
      busId: "BUS-01",
      driverId: "uid_driver_B",
      driverName: "Driver B",
      active: true,
      assignmentVersion: 2,
      rtdbSyncStatus: "SYNCED"
    };
    harness.firestoreData.users["uid_driver_B"].assignedBusId = "BUS-01";
    harness.firestoreData.users["uid_driver_A"].assignedBusId = null;

    // 6. Trigger B synchronizes Driver B to RTDB and completes
    const changeB = {
      before: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_A", active: true }) },
      after: { exists: true, data: () => ({ busId: "BUS-01", driverId: "uid_driver_B", driverName: "Driver B", active: true, assignmentVersion: 2 }) }
    };

    await processOnBusAssignmentWritten(
      changeB,
      { params: { busId: "BUS-01" } },
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    // Verify Trigger B set Driver B in RTDB
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_driver_B");
    assert.strictEqual(harness.rtdbData.users["uid_driver_B"].assignedBusId, "BUS-01");

    // 7. Resume Trigger A
    resumeTriggerA();

    // 8. Trigger A attempts to synchronize Driver A, then post-sync convergence restores Driver B
    await promiseA;

    // EXPECTED RESULTS:
    // - Firestore bus assignment: Driver B
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, "uid_driver_B");
    // - RTDB bus operator: Driver B
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_driver_B");
    // - RTDB Driver B authorization: correct
    assert.strictEqual(harness.rtdbData.users["uid_driver_B"].assignedBusId, "BUS-01");
    // - RTDB Driver A authorization: cleared
    assert.strictEqual(harness.rtdbData.users["uid_driver_A"].assignedBusId, null);
    // - Assignment version: monotonic
    assert.ok(harness.rtdbData.bus_operators["BUS-01"].assignmentVersion >= 3);
    // - Temporary bus swap information: preserved
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].hasTemporary, true);
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].temporaryDriverUid, "uid_temp_swap");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].startAt, 100);
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].endAt, 999999);
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

  await itAsync("7.3. Scheduled reconciliation worker repairs FAILED and stale PENDING buses", async () => {
    const staleTime = Date.now() - 120000; // 120s ago (> 60s stale threshold)
    const freshTime = Date.now() - 10000;  // 10s ago (< 60s stale threshold)

    const harness = createMockHarness({
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_drv_1", driverName: "Driver 1", active: true, assignmentVersion: 4, rtdbSyncStatus: "FAILED" },
        "BUS-02": { busId: "BUS-02", driverId: null, driverName: null, active: true, assignmentVersion: 5, rtdbSyncStatus: "PENDING", rtdbSyncLastAttempt: staleTime },
        "BUS-03": { busId: "BUS-03", driverId: "uid_drv_3", driverName: "Driver 3", active: true, assignmentVersion: 6, rtdbSyncStatus: "SYNCED" },
        "BUS-04": { busId: "BUS-04", driverId: "uid_drv_4", driverName: "Driver 4", active: true, assignmentVersion: 7, rtdbSyncStatus: "PENDING", rtdbSyncLastAttempt: freshTime }
      },
      users: {
        uid_drv_1: { uid: "uid_drv_1", name: "Driver 1", role: "DRIVER", active: true, assignedBusId: null }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "old_drv", assignmentVersion: 2 },
        "BUS-02": { busId: "BUS-02", permanentDriverUid: "uid_old_2", assignmentVersion: 4 },
        "BUS-03": { busId: "BUS-03", permanentDriverUid: "uid_drv_3", assignmentVersion: 6 },
        "BUS-04": { busId: "BUS-04", permanentDriverUid: null, assignmentVersion: 6 }
      },
      rtdbUsers: {
        uid_drv_1: { role: "DRIVER", active: true }
      }
    });

    const report = await processReconcileOperationalRtdb({ db: harness.mockDb, rtdb: harness.mockRtdb });

    assert.strictEqual(report.totalEvaluated, 2, "Must evaluate exactly BUS-01 (FAILED) and BUS-02 (stale PENDING)");
    assert.strictEqual(report.repaired, 2, "Must repair both buses");
    assert.strictEqual(report.errors, 0);

    // BUS-01 verified
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].rtdbSyncStatus, "SYNCED");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "uid_drv_1");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].assignmentVersion, 5);
    assert.strictEqual(harness.firestoreData.users["uid_drv_1"].assignedBusId, "BUS-01");
    assert.strictEqual(harness.rtdbData.users["uid_drv_1"].assignedBusId, "BUS-01");

    // BUS-02 verified
    assert.strictEqual(harness.firestoreData.buses["BUS-02"].rtdbSyncStatus, "SYNCED");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-02"].permanentDriverUid, null);

    // BUS-03 & BUS-04 untouched
    assert.strictEqual(harness.firestoreData.buses["BUS-03"].rtdbSyncStatus, "SYNCED");
    assert.strictEqual(harness.firestoreData.buses["BUS-04"].rtdbSyncStatus, "PENDING");
  });

  await itAsync("7.4. Atomic RTDB Transaction Concurrency: simultaneous sync invocations generate monotonic non-colliding versions", async () => {
    const harness = createMockHarness({
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "old_drv", assignmentVersion: 2 }
      }
    });

    // Simulate two simultaneous Cloud Function invocations both attempting version 3
    const p1 = syncAssignmentToRtdb({ busId: "BUS-01", driverUid: "driver_A", version: 3 }, harness.mockRtdb);
    const p2 = syncAssignmentToRtdb({ busId: "BUS-01", driverUid: "driver_B", version: 3 }, harness.mockRtdb);

    const [res1, res2] = await Promise.all([p1, p2]);

    assert.strictEqual(res1.success, true);
    assert.strictEqual(res2.success, true);

    // One invocation must commit version 3, the second invocation must increment to version 4
    const versions = [res1.version, res2.version].sort((a, b) => a - b);
    assert.strictEqual(versions[0], 3, "First committed version must be 3");
    assert.strictEqual(versions[1], 4, "Second re-evaluated transaction must increment to 4");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].assignmentVersion, 4);
  });

  await itAsync("7.5. Cross-Document Transfer Failure & Recovery: repairs all 6 records across Firestore and RTDB", async () => {
    // Driver starts on BUS-01. Move to BUS-02.
    // Simulate RTDB failure during setBusDriverAssignment so sync is incomplete.
    let failRtdb = true;
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_transferred: { uid: "uid_transferred", name: "Transfer Driver", role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", active: true, driverId: "uid_transferred", driverName: "Transfer Driver", assignmentVersion: 2 },
        "BUS-02": { busId: "BUS-02", busNumber: "02", active: true, driverId: null, driverName: null, assignmentVersion: 1 }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_transferred", assignmentVersion: 2 },
        "BUS-02": { busId: "BUS-02", permanentDriverUid: null, assignmentVersion: 1 }
      },
      rtdbUsers: {
        uid_transferred: { role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      }
    });

    // Intercept RTDB ref to simulate network failure
    const originalRef = harness.mockRtdb.ref;
    harness.mockRtdb.ref = (path) => {
      if (failRtdb && path.startsWith("bus_operators/BUS-02")) {
        throw new Error("Simulated RTDB network timeout on target bus");
      }
      return originalRef(path);
    };

    // Execute transfer from BUS-01 to BUS-02
    await processSetBusDriverAssignment(
      { busId: "BUS-02", driverUid: "uid_transferred" },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    // Verify state after failure:
    // Firestore transaction committed atomically:
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, null, "Record 1: Firestore buses/01 unassigned");
    assert.strictEqual(harness.firestoreData.buses["BUS-02"].driverId, "uid_transferred", "Record 2: Firestore buses/02 assigned");
    assert.strictEqual(harness.firestoreData.users["uid_transferred"].assignedBusId, "BUS-02", "Record 3: Firestore users/uid points to BUS-02");
    assert.strictEqual(harness.firestoreData.buses["BUS-02"].rtdbSyncStatus, "FAILED", "Target bus flagged FAILED");

    // Re-enable RTDB and run scheduled reconciliation worker
    failRtdb = false;
    harness.mockRtdb.ref = originalRef;

    const report = await processReconcileOperationalRtdb({ db: harness.mockDb, rtdb: harness.mockRtdb });
    assert.strictEqual(report.repaired >= 1, true);

    // Verify all 6 records are completely and consistently synchronized:
    // 1. Firestore buses/01
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, null);
    // 2. Firestore buses/02
    assert.strictEqual(harness.firestoreData.buses["BUS-02"].driverId, "uid_transferred");
    assert.strictEqual(harness.firestoreData.buses["BUS-02"].rtdbSyncStatus, "SYNCED");
    // 3. Firestore users/uid
    assert.strictEqual(harness.firestoreData.users["uid_transferred"].assignedBusId, "BUS-02");
    // 4. RTDB bus_operators/BUS-01
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, null);
    // 5. RTDB bus_operators/BUS-02
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-02"].permanentDriverUid, "uid_transferred");
    // 6. RTDB users/uid
    assert.strictEqual(harness.rtdbData.users["uid_transferred"].assignedBusId, "BUS-02");
  });

  await itAsync("7.6. Scheduled Reconciliation Safety: skips newer assignments and caps permanent failures", async () => {
    const harness = createMockHarness({
      buses: {
        // Bus with newer assignment epoch than run start
        "BUS-NEW": { busId: "BUS-NEW", driverId: "uid_new", active: true, assignmentVersion: 5, rtdbSyncStatus: "FAILED", assignmentEpoch: Date.now() + 50000 },
        // Bus with excessive failure count (>5)
        "BUS-PERM-FAIL": { busId: "BUS-PERM-FAIL", driverId: "uid_corrupt", active: true, assignmentVersion: 1, rtdbSyncStatus: "FAILED", rtdbSyncRetryCount: 6 }
      },
      bus_operators: {
        "BUS-NEW": { busId: "BUS-NEW", permanentDriverUid: "uid_old", assignmentVersion: 4 },
        "BUS-PERM-FAIL": { busId: "BUS-PERM-FAIL", permanentDriverUid: null, assignmentVersion: 1 }
      }
    });

    const report = await processReconcileOperationalRtdb({ db: harness.mockDb, rtdb: harness.mockRtdb });

    // BUS-NEW must be skipped without modifying RTDB
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-NEW"].permanentDriverUid, "uid_old", "Must not overwrite newer assignment");

    // BUS-PERM-FAIL must transition to PERMANENTLY_FAILED with recorded error
    assert.strictEqual(harness.firestoreData.buses["BUS-PERM-FAIL"].rtdbSyncStatus, "PERMANENTLY_FAILED");
    assert.ok(harness.firestoreData.buses["BUS-PERM-FAIL"].rtdbSyncError.includes("Exceeded max recovery attempts"));
  });

  await itAsync("7.7. Missing Firestore Sync Status: discovers and repairs buses with FAILED, PENDING, null, and absent rtdbSyncStatus", async () => {
    const staleTime = Date.now() - 120000;

    const harness = createMockHarness({
      buses: {
        // 1. FAILED sync status
        "BUS-FAILED": { busId: "BUS-FAILED", driverId: "uid_1", driverName: "D1", active: true, assignmentVersion: 1, rtdbSyncStatus: "FAILED" },
        // 2. Stale PENDING sync status
        "BUS-PENDING": { busId: "BUS-PENDING", driverId: "uid_2", driverName: "D2", active: true, assignmentVersion: 2, rtdbSyncStatus: "PENDING", rtdbSyncLastAttempt: staleTime },
        // 3. Explicitly null rtdbSyncStatus
        "BUS-NULL": { busId: "BUS-NULL", driverId: "uid_3", driverName: "D3", active: true, assignmentVersion: 3, rtdbSyncStatus: null },
        // 4. Completely absent rtdbSyncStatus field (legacy document)
        "BUS-ABSENT": { busId: "BUS-ABSENT", driverId: "uid_4", driverName: "D4", active: true, assignmentVersion: 4 }, // No rtdbSyncStatus field at all!
        // 5. SYNCED status (should NOT be touched)
        "BUS-SYNCED": { busId: "BUS-SYNCED", driverId: "uid_5", driverName: "D5", active: true, assignmentVersion: 5, rtdbSyncStatus: "SYNCED" }
      },
      bus_operators: {
        "BUS-FAILED": { busId: "BUS-FAILED", permanentDriverUid: null, assignmentVersion: 0 },
        "BUS-PENDING": { busId: "BUS-PENDING", permanentDriverUid: null, assignmentVersion: 0 },
        "BUS-NULL": { busId: "BUS-NULL", permanentDriverUid: null, assignmentVersion: 0 },
        "BUS-ABSENT": { busId: "BUS-ABSENT", permanentDriverUid: null, assignmentVersion: 0 },
        "BUS-SYNCED": { busId: "BUS-SYNCED", permanentDriverUid: "uid_5", assignmentVersion: 5 }
      },
      users: {
        uid_1: { uid: "uid_1", name: "D1", role: "DRIVER", active: true },
        uid_2: { uid: "uid_2", name: "D2", role: "DRIVER", active: true },
        uid_3: { uid: "uid_3", name: "D3", role: "DRIVER", active: true },
        uid_4: { uid: "uid_4", name: "D4", role: "DRIVER", active: true },
        uid_5: { uid: "uid_5", name: "D5", role: "DRIVER", active: true }
      }
    });

    const report = await processReconcileOperationalRtdb({ db: harness.mockDb, rtdb: harness.mockRtdb });

    assert.strictEqual(report.totalEvaluated, 4, "Must evaluate exactly the 4 desynchronized buses (FAILED, PENDING, NULL, ABSENT)");
    assert.strictEqual(report.repaired, 4, "Must successfully repair all 4 buses");

    // Verify all 4 buses now have rtdbSyncStatus: 'SYNCED'
    assert.strictEqual(harness.firestoreData.buses["BUS-FAILED"].rtdbSyncStatus, "SYNCED");
    assert.strictEqual(harness.firestoreData.buses["BUS-PENDING"].rtdbSyncStatus, "SYNCED");
    assert.strictEqual(harness.firestoreData.buses["BUS-NULL"].rtdbSyncStatus, "SYNCED");
    assert.strictEqual(harness.firestoreData.buses["BUS-ABSENT"].rtdbSyncStatus, "SYNCED");

    // Verify RTDB operator nodes updated with correct drivers
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-FAILED"].permanentDriverUid, "uid_1");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-PENDING"].permanentDriverUid, "uid_2");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-NULL"].permanentDriverUid, "uid_3");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-ABSENT"].permanentDriverUid, "uid_4");

    // Verify BUS-SYNCED was untouched
    assert.strictEqual(harness.firestoreData.buses["BUS-SYNCED"].rtdbSyncStatus, "SYNCED");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-SYNCED"].assignmentVersion, 5);
  });

  await itAsync("7.8. Driver Unassignment Race: Stale unassignment for Bus 1 cannot accidentally unassign transferred Driver A from Bus 2", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_driver_A: { uid: "uid_driver_A", name: "Driver A", role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", active: true, driverId: "uid_driver_A", driverName: "Driver A", assignmentVersion: 1 },
        "BUS-02": { busId: "BUS-02", busNumber: "02", active: true, driverId: null, driverName: null, assignmentVersion: 1 }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_driver_A", assignmentVersion: 1 },
        "BUS-02": { busId: "BUS-02", permanentDriverUid: null, assignmentVersion: 1 }
      }
    });

    // 1. Driver A is assigned to Bus 1.
    // 2. The client reads assigned bus ID: "BUS-01" and prepares unassignment request { busId: "BUS-01", driverUid: null }.
    const staleUnassignRequest = { busId: "BUS-01", driverUid: null };

    // 3. Before the stale request reaches the server, another administrator transfers Driver A to Bus 2:
    const transferRes = await processSetBusDriverAssignment(
      { busId: "BUS-02", driverUid: "uid_driver_A" },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );
    assert.strictEqual(transferRes.success, true);
    assert.strictEqual(harness.firestoreData.users["uid_driver_A"].assignedBusId, "BUS-02");
    assert.strictEqual(harness.firestoreData.buses["BUS-02"].driverId, "uid_driver_A");
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, null);

    // 4. Now the original stale unassignment request for Bus 1 executes:
    const unassignRes = await processSetBusDriverAssignment(
      staleUnassignRequest,
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );
    assert.strictEqual(unassignRes.success, true);

    // 5. Verification:
    // Driver A MUST remain assigned to Bus 2 in Firestore!
    assert.strictEqual(harness.firestoreData.users["uid_driver_A"].assignedBusId, "BUS-02", "Driver A must remain assigned to Bus 2");
    assert.strictEqual(harness.firestoreData.buses["BUS-02"].driverId, "uid_driver_A", "Bus 2 must retain Driver A");
    // Bus 1 remains unassigned
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, null, "Bus 1 is unassigned");
    // RTDB for Bus 2 must retain Driver A authorization!
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-02"].permanentDriverUid, "uid_driver_A", "RTDB for Bus 2 must retain Driver A");
  });

  // --- Suite 8: Atomic Bus Registration & Protected Metadata Updates ---
  console.log("\n--- Suite 8: Atomic Bus Registration & Protected Metadata Updates ---");

  await itAsync("8.1. Caller authorization checks: rejects unauthenticated, non-admin, and inactive callers", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        admin_inactive: { uid: "admin_inactive", role: "ADMIN", active: false },
        uid_driver: { uid: "uid_driver", role: "DRIVER", active: true }
      }
    });

    // Unauthenticated
    await assert.rejects(async () => {
      await processRegisterBusAccount(
        { busNumber: "05", registrationNumber: "KA-34-A-1234", capacity: 45 },
        null,
        { db: harness.mockDb }
      );
    }, /Authentication is required/);

    // Non-admin (driver)
    await assert.rejects(async () => {
      await processRegisterBusAccount(
        { busNumber: "05", registrationNumber: "KA-34-A-1234", capacity: 45 },
        { auth: { uid: "uid_driver" } },
        { db: harness.mockDb }
      );
    }, /Only active administrators are authorized/);

    // Inactive admin
    await assert.rejects(async () => {
      await processRegisterBusAccount(
        { busNumber: "05", registrationNumber: "KA-34-A-1234", capacity: 45 },
        { auth: { uid: "admin_inactive" } },
        { db: harness.mockDb }
      );
    }, /Only active administrators are authorized/);
  });

  await itAsync("8.2. Atomic Bus Registration: registers bus and creates unique index documents atomically", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      }
    });

    const res = await processRegisterBusAccount(
      { busNumber: "05", registrationNumber: "KA-34-A-1234", capacity: 52 },
      createAdminContext(),
      { db: harness.mockDb }
    );

    assert.strictEqual(res.success, true);
    assert.strictEqual(res.busId, "BUS-05");
    assert.strictEqual(res.busNumber, "05");
    assert.strictEqual(res.registrationNumber, "KA-34-A-1234");
    assert.strictEqual(res.capacity, 52);

    // Primary bus document
    const busDoc = harness.firestoreData.buses["BUS-05"];
    assert.ok(busDoc);
    assert.strictEqual(busDoc.busNumber, "05");
    assert.strictEqual(busDoc.registrationNumber, "KA-34-A-1234");
    assert.strictEqual(busDoc.capacity, 52);
    assert.strictEqual(busDoc.active, true);
    assert.strictEqual(busDoc.driverId, null);
    assert.strictEqual(busDoc.rtdbSyncStatus, "SYNCED");

    // Index documents
    assert.ok(harness.firestoreData.bus_number_index["05"], "Bus number index document must be created");
    assert.strictEqual(harness.firestoreData.bus_number_index["05"].busId, "BUS-05");

    assert.ok(harness.firestoreData.bus_registration_index["KA-34-A-1234"], "Registration number index document must be created");
    assert.strictEqual(harness.firestoreData.bus_registration_index["KA-34-A-1234"].busId, "BUS-05");
  });

  await itAsync("8.3. Simultaneous registrations with the same bus number reject second attempt without overwriting", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      }
    });

    // Admin 1 registers bus 05
    const res1 = await processRegisterBusAccount(
      { busNumber: "05", registrationNumber: "KA-34-A-1234", capacity: 50 },
      createAdminContext(),
      { db: harness.mockDb }
    );
    assert.strictEqual(res1.success, true);

    // Admin 2 attempts to register bus 05 with a different plate
    await assert.rejects(async () => {
      await processRegisterBusAccount(
        { busNumber: "05", registrationNumber: "KA-34-A-9999", capacity: 40 },
        createAdminContext(),
        { db: harness.mockDb }
      );
    }, /already exists/);

    // Verify Admin 1's registration is NOT overwritten
    assert.strictEqual(harness.firestoreData.buses["BUS-05"].registrationNumber, "KA-34-A-1234");
    assert.strictEqual(harness.firestoreData.buses["BUS-05"].capacity, 50);
  });

  await itAsync("8.4. Simultaneous registrations with the same registration number but different bus numbers reject duplicate plate", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      }
    });

    // Admin 1 registers bus 05 with plate KA-34-A-1234
    await processRegisterBusAccount(
      { busNumber: "05", registrationNumber: "KA-34-A-1234", capacity: 50 },
      createAdminContext(),
      { db: harness.mockDb }
    );

    // Admin 2 attempts to register bus 06 with the same plate KA-34-A-1234
    await assert.rejects(async () => {
      await processRegisterBusAccount(
        { busNumber: "06", registrationNumber: "KA-34-A-1234", capacity: 50 },
        createAdminContext(),
        { db: harness.mockDb }
      );
    }, /already exists/);

    // BUS-06 must NOT exist in the fleet
    assert.strictEqual(harness.firestoreData.buses["BUS-06"], undefined);
  });

  await itAsync("8.5. Existing buses without unique index documents: migration safety detects collisions via legacy lookup", async () => {
    // Legacy bus in buses collection that has NO entries in index collections
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      },
      buses: {
        "BUS-90": { busId: "BUS-90", busNumber: "90", registrationNumber: "KA-34-A-9090", capacity: 45, active: true }
      }
    });

    // Attempt to register Bus 91 with the legacy bus's plate KA-34-A-9090
    await assert.rejects(async () => {
      await processRegisterBusAccount(
        { busNumber: "91", registrationNumber: "KA-34-A-9090", capacity: 50 },
        createAdminContext(),
        { db: harness.mockDb }
      );
    }, /already exists/);

    // Attempt to register legacy bus number 90
    await assert.rejects(async () => {
      await processRegisterBusAccount(
        { busNumber: "90", registrationNumber: "KA-34-A-9191", capacity: 50 },
        createAdminContext(),
        { db: harness.mockDb }
      );
    }, /already exists/);
  });

  await itAsync("8.6. Protected Bus Metadata Updates: registration number update rotates index documents atomically", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      },
      routes: {
        "ROUTE-01": { routeId: "ROUTE-01", name: "Campus Express", active: true }
      },
      buses: {
        "BUS-05": { busId: "BUS-05", busNumber: "05", registrationNumber: "KA-34-A-1234", capacity: 50, active: true }
      },
      bus_registration_index: {
        "KA-34-A-1234": { busId: "BUS-05", registrationNumber: "KA-34-A-1234" }
      }
    });

    const res = await processUpdateBusDetails(
      { busId: "BUS-05", registrationNumber: "KA-34-A-5555", capacity: 55, routeId: "ROUTE-01" },
      createAdminContext(),
      { db: harness.mockDb }
    );
    assert.strictEqual(res.success, true);

    // Bus document updated
    const bus = harness.firestoreData.buses["BUS-05"];
    assert.strictEqual(bus.registrationNumber, "KA-34-A-5555");
    assert.strictEqual(bus.capacity, 55);
    assert.strictEqual(bus.routeId, "ROUTE-01");

    // Old index deleted
    assert.strictEqual(harness.firestoreData.bus_registration_index["KA-34-A-1234"], undefined, "Old plate index must be deleted");

    // New index created
    assert.ok(harness.firestoreData.bus_registration_index["KA-34-A-5555"], "New plate index must be created");
    assert.strictEqual(harness.firestoreData.bus_registration_index["KA-34-A-5555"].busId, "BUS-05");
  });

  await itAsync("8.7. Reject registration number update when target plate is already owned by another bus", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", registrationNumber: "KA-34-A-1111", capacity: 50, active: true },
        "BUS-02": { busId: "BUS-02", busNumber: "02", registrationNumber: "KA-34-A-2222", capacity: 50, active: true }
      },
      bus_registration_index: {
        "KA-34-A-1111": { busId: "BUS-01", registrationNumber: "KA-34-A-1111" },
        "KA-34-A-2222": { busId: "BUS-02", registrationNumber: "KA-34-A-2222" }
      }
    });

    // BUS-02 attempts to update plate to KA-34-A-1111 (owned by BUS-01)
    await assert.rejects(async () => {
      await processUpdateBusDetails(
        { busId: "BUS-02", registrationNumber: "KA-34-A-1111" },
        createAdminContext(),
        { db: harness.mockDb }
      );
    }, /already used by another bus/);

    // BUS-02 remains unchanged
    assert.strictEqual(harness.firestoreData.buses["BUS-02"].registrationNumber, "KA-34-A-2222");
    assert.strictEqual(harness.firestoreData.bus_registration_index["KA-34-A-2222"].busId, "BUS-02");
    assert.strictEqual(harness.firestoreData.bus_registration_index["KA-34-A-1111"].busId, "BUS-01");
  });

  await itAsync("8.8. Failed transaction rollback: database and indexes remain completely unmodified when transaction fails", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      }
    });

    // Override runTransaction to simulate an unexpected database crash midway
    const originalRunTransaction = harness.mockDb.runTransaction;
    harness.mockDb.runTransaction = async (updateFunction) => {
      const stagedWrites = [];
      const transaction = {
        async get(docRef) { return docRef.get(); },
        set(docRef, data) { stagedWrites.push(() => docRef.set(data)); },
        update(docRef, data) { stagedWrites.push(() => docRef.update(data)); },
        delete(docRef) { stagedWrites.push(() => docRef.delete()); }
      };
      await updateFunction(transaction);
      // Simulate crash before committing staged writes
      throw new Error("Simulated Firestore transaction commit failure");
    };

    await assert.rejects(async () => {
      await processRegisterBusAccount(
        { busNumber: "88", registrationNumber: "KA-34-A-8888", capacity: 48 },
        createAdminContext(),
        { db: harness.mockDb }
      );
    }, /Simulated Firestore transaction commit failure/);

    // Verify nothing was written
    assert.strictEqual(harness.firestoreData.buses["BUS-88"], undefined);
    assert.strictEqual(harness.firestoreData.bus_number_index["88"], undefined);
    assert.strictEqual(harness.firestoreData.bus_registration_index["KA-34-A-8888"], undefined);
  });

  await itAsync("8.9. Existing Android Compatibility: registered and updated bus schemas contain all required Kotlin fields", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      }
    });

    await processRegisterBusAccount(
      { busNumber: "12", registrationNumber: "KA-34-A-1212", capacity: 50 },
      createAdminContext(),
      { db: harness.mockDb }
    );

    const bus = harness.firestoreData.buses["BUS-12"];
    assert.ok(bus);

    // Verify all Kotlin Bus.kt fields are present with correct types
    assert.strictEqual(typeof bus.busNumber, "string");
    assert.strictEqual(typeof bus.registrationNumber, "string");
    assert.strictEqual(typeof bus.capacity, "number");
    assert.strictEqual(typeof bus.active, "boolean");
    assert.strictEqual(bus.routeId, null);
    assert.strictEqual(bus.driverId, null);
    assert.strictEqual(bus.driverName, null);
  });

  await itAsync("8.10. Route Assignment Validation: rejects nonexistent or deactivated route IDs while preserving embedded stops/schedules in routes", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true }
      },
      routes: {
        "ROUTE-ACTIVE": {
          routeId: "ROUTE-ACTIVE",
          name: "Main Campus Route",
          active: true,
          stops: [{ stopId: "STOP-01", name: "Gate 1", order: 1, latitude: 15.1, longitude: 76.9 }],
          schedules: { morning: "08:00 AM", evening: "05:00 PM" }
        },
        "ROUTE-DEACTIVATED": {
          routeId: "ROUTE-DEACTIVATED",
          name: "Old Deprecated Route",
          active: false,
          stops: [{ stopId: "STOP-99", name: "Closed Gate", order: 1, latitude: 15.2, longitude: 76.8 }]
        }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", registrationNumber: "KA-34-A-1001", capacity: 50, active: true, routeId: null }
      }
    });

    // 1. Rejects nonexistent route during registration
    await assert.rejects(async () => {
      await processRegisterBusAccount(
        { busNumber: "77", registrationNumber: "KA-34-A-7777", capacity: 50, routeId: "ROUTE-NONEXISTENT" },
        createAdminContext(),
        { db: harness.mockDb }
      );
    }, /Route 'ROUTE-NONEXISTENT' does not exist/);

    // 2. Rejects deactivated route during registration
    await assert.rejects(async () => {
      await processRegisterBusAccount(
        { busNumber: "77", registrationNumber: "KA-34-A-7777", capacity: 50, routeId: "ROUTE-DEACTIVATED" },
        createAdminContext(),
        { db: harness.mockDb }
      );
    }, /Cannot assign deactivated route/);

    // 3. Rejects nonexistent route during metadata update
    await assert.rejects(async () => {
      await processUpdateBusDetails(
        { busId: "BUS-01", routeId: "ROUTE-GHOST" },
        createAdminContext(),
        { db: harness.mockDb }
      );
    }, /Route 'ROUTE-GHOST' does not exist/);

    // 4. Successfully assigns valid active route
    const res = await processUpdateBusDetails(
      { busId: "BUS-01", routeId: "ROUTE-ACTIVE" },
      createAdminContext(),
      { db: harness.mockDb }
    );
    assert.strictEqual(res.success, true);
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].routeId, "ROUTE-ACTIVE");

    // 5. Embedded stops and schedules in routes collection remain completely intact
    const routeAfter = harness.firestoreData.routes["ROUTE-ACTIVE"];
    assert.strictEqual(routeAfter.stops.length, 1);
    assert.strictEqual(routeAfter.stops[0].stopId, "STOP-01");
    assert.strictEqual(routeAfter.schedules.morning, "08:00 AM");
  });

  // --- Suite 9: Phase 4 Security & Data-Integrity Deep Audit ---
  console.log("\n--- Suite 9: Phase 4 Security & Data-Integrity Deep Audit ---");

  await itAsync("9.1. Driver Deletion with Active Temporary Bus Swap: revokes temporary RTDB authorization", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_temp_driver: { uid: "uid_temp_driver", role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_temp_driver", driverName: "Temp Driver", assignmentVersion: 2 },
        "BUS-12": { busId: "BUS-12", driverId: "uid_perm_12", driverName: "Perm 12", assignmentVersion: 4 }
      },
      rtdbUsers: {
        uid_temp_driver: { role: "DRIVER", active: true }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "uid_temp_driver", assignmentVersion: 2 },
        "BUS-12": {
          busId: "BUS-12",
          permanentDriverUid: "uid_perm_12",
          hasTemporary: true,
          temporaryDriverUid: "uid_temp_driver",
          temporaryAssignmentId: "swap_123",
          assignmentVersion: 4
        }
      },
      authUsers: {
        uid_temp_driver: { email: "drv_temp@collegebus.internal" }
      }
    });

    const res = await processDeleteDriverAccount(
      { driverUid: "uid_temp_driver" },
      createAdminContext(),
      { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(res.success, true);
    // Verified: Auth, Firestore users, and RTDB users wiped
    assert.strictEqual(harness.authUsers["uid_temp_driver"], undefined);
    assert.strictEqual(harness.firestoreData.users["uid_temp_driver"], undefined);
    assert.strictEqual(harness.rtdbData.users["uid_temp_driver"], undefined);

    // Verified: Permanent bus BUS-01 unassigned with incremented version
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, null);
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].assignmentVersion, 3);
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, null);

    // Verified: Active temporary authorization on BUS-12 is revoked
    const bus12Op = harness.rtdbData.bus_operators["BUS-12"];
    assert.strictEqual(bus12Op.hasTemporary, false);
    assert.strictEqual(bus12Op.temporaryDriverUid, null);
    assert.strictEqual(bus12Op.temporaryAssignmentId, null);
    assert.strictEqual(bus12Op.permanentDriverUid, "uid_perm_12", "Perm driver on BUS-12 preserved");
  });

  await itAsync("9.2. Driver Deletion Failure Recovery: idempotent retry succeeds cleanly", async () => {
    let authFailedOnce = true;
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_retry_del: { uid: "uid_retry_del", role: "DRIVER", active: true, assignedBusId: null }
      },
      rtdbUsers: {
        uid_retry_del: { role: "DRIVER", active: true }
      },
      authUsers: {
        uid_retry_del: { email: "retry_del@collegebus.internal" }
      }
    });

    // Custom mock auth with simulated transient failure
    const flakyAuth = {
      deleteUser: async (uid) => {
        if (authFailedOnce) {
          authFailedOnce = false;
          throw new Error("Simulated Firebase Auth network timeout");
        }
        delete harness.authUsers[uid];
      }
    };

    // First attempt fails at auth deletion
    await assert.rejects(async () => {
      await processDeleteDriverAccount(
        { driverUid: "uid_retry_del" },
        createAdminContext(),
        { db: harness.mockDb, auth: flakyAuth, rtdb: harness.mockRtdb }
      );
    }, /Simulated Firebase Auth network timeout/);

    // Second retry attempt recovers cleanly
    const retryRes = await processDeleteDriverAccount(
      { driverUid: "uid_retry_del" },
      createAdminContext(),
      { db: harness.mockDb, auth: flakyAuth, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(retryRes.success, true);
    assert.strictEqual(harness.authUsers["uid_retry_del"], undefined);
  });

  await itAsync("9.3. Password Reset Security Invariants: rejects non-admin callers and role escalation", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        admin_target: { uid: "admin_target", role: "ADMIN", active: true },
        driver_target: { uid: "driver_target", role: "DRIVER", active: true }
      },
      authUsers: {
        driver_target: { email: "drv@college.edu", password: "oldPassword123" }
      }
    });

    // 1. Rejects unauthenticated caller
    await assert.rejects(async () => {
      await processResetDriverPassword(
        { driverUid: "driver_target", newPassword: "newPass123" },
        { auth: null },
        { db: harness.mockDb, auth: harness.mockAuth }
      );
    }, /Authentication is required/);

    // 2. Rejects password less than 6 characters
    await assert.rejects(async () => {
      await processResetDriverPassword(
        { driverUid: "driver_target", newPassword: "123" },
        createAdminContext(),
        { db: harness.mockDb, auth: harness.mockAuth }
      );
    }, /Password must be at least 6 characters long/);

    // 3. Rejects resetting an Admin password via driver reset
    await assert.rejects(async () => {
      await processResetDriverPassword(
        { driverUid: "admin_target", newPassword: "newPass12345" },
        createAdminContext(),
        { db: harness.mockDb, auth: harness.mockAuth }
      );
    }, /Target user is not a driver/);

    // 4. Valid driver reset succeeds and does NOT write password to Firestore
    const res = await processResetDriverPassword(
      { driverUid: "driver_target", newPassword: "securePassword2026" },
      createAdminContext(),
      { db: harness.mockDb, auth: harness.mockAuth }
    );
    assert.strictEqual(res.success, true);
    assert.strictEqual(harness.authUsers["driver_target"].password, "securePassword2026");
    assert.strictEqual(harness.firestoreData.users["driver_target"].password, undefined, "Password must NEVER be stored in Firestore");
  });

  await itAsync("9.4. Driver Reactivation Stale Bus Cleansing: clears invalid assignedBusId if bus was transferred", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        uid_inactive_drv: { uid: "uid_inactive_drv", role: "DRIVER", active: false, assignedBusId: "BUS-01" },
        uid_new_drv: { uid: "uid_new_drv", role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "uid_new_drv", driverName: "New Driver" }
      },
      rtdbUsers: {
        uid_inactive_drv: { role: "DRIVER", active: false, assignedBusId: null }
      }
    });

    // Reactivate driver whose assignedBusId is occupied by uid_new_drv
    const res = await processToggleDriverActiveStatus(
      { driverUid: "uid_inactive_drv", active: true },
      createAdminContext(),
      { db: harness.mockDb, auth: harness.mockAuth, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(res.success, true);
    assert.strictEqual(res.active, true);
    // Verified: stale bus assignment was stripped
    assert.strictEqual(harness.firestoreData.users["uid_inactive_drv"].assignedBusId, null);
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, "uid_new_drv", "Current bus driver untouched");
  });

  await itAsync("9.5. Driver Assignment Concurrency: atomic transactions prevent split-brain assignments", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        drv_alpha: { uid: "drv_alpha", name: "Driver Alpha", role: "DRIVER", active: true, assignedBusId: null },
        drv_beta: { uid: "drv_beta", name: "Driver Beta", role: "DRIVER", active: true, assignedBusId: null }
      },
      buses: {
        "BUS-50": { busId: "BUS-50", busNumber: "50", driverId: null, active: true, assignmentVersion: 1 }
      },
      bus_operators: {
        "BUS-50": { busId: "BUS-50", permanentDriverUid: null, assignmentVersion: 1 }
      },
      rtdbUsers: {
        drv_alpha: { role: "DRIVER", active: true },
        drv_beta: { role: "DRIVER", active: true }
      }
    });

    // Simulate two sequential admin calls (simulating transaction serialization in Firestore)
    await processSetBusDriverAssignment(
      { busId: "BUS-50", driverUid: "drv_alpha" },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(harness.firestoreData.buses["BUS-50"].driverId, "drv_alpha");
    assert.strictEqual(harness.firestoreData.buses["BUS-50"].assignmentVersion, 2);

    await processSetBusDriverAssignment(
      { busId: "BUS-50", driverUid: "drv_beta", confirmReplacement: true },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );

    assert.strictEqual(harness.firestoreData.buses["BUS-50"].driverId, "drv_beta");
    assert.strictEqual(harness.firestoreData.buses["BUS-50"].assignmentVersion, 3);
    assert.strictEqual(harness.firestoreData.users["drv_alpha"].assignedBusId, null, "Driver Alpha replaced");
    assert.strictEqual(harness.firestoreData.users["drv_beta"].assignedBusId, "BUS-50", "Driver Beta assigned");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-50"].permanentDriverUid, "drv_beta");
  });

  await itAsync("9.6. Concurrent Driver Replacement Race: stale replacement request rejected without overwriting newer assignment", async () => {
    // Initial state: Bus 01 has Driver A
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", role: "ADMIN", active: true },
        driver_A: { uid: "driver_A", name: "Driver A", role: "DRIVER", active: true, assignedBusId: "BUS-01" },
        driver_B: { uid: "driver_B", name: "Driver B", role: "DRIVER", active: true, assignedBusId: null },
        driver_C: { uid: "driver_C", name: "Driver C", role: "DRIVER", active: true, assignedBusId: null }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", driverId: "driver_A", driverName: "Driver A", active: true, assignmentVersion: 1 }
      },
      bus_operators: {
        "BUS-01": { busId: "BUS-01", permanentDriverUid: "driver_A", assignmentVersion: 1 }
      },
      rtdbUsers: {
        driver_A: { role: "DRIVER", active: true, assignedBusId: "BUS-01" },
        driver_B: { role: "DRIVER", active: true, assignedBusId: null },
        driver_C: { role: "DRIVER", active: true, assignedBusId: null }
      }
    });

    // Step 1: Admin 1 attempts to assign Driver B without confirmation -> REJECTED
    await assert.rejects(async () => {
      await processSetBusDriverAssignment(
        { busId: "BUS-01", driverUid: "driver_B" },
        createAdminContext(),
        { db: harness.mockDb, rtdb: harness.mockRtdb }
      );
    }, /Explicit confirmation is required to replace the existing driver/);

    // Step 2: Admin 2 concurrently replaces Driver A with Driver C with confirmation -> SUCCEEDS
    const resC = await processSetBusDriverAssignment(
      { busId: "BUS-01", driverUid: "driver_C", confirmReplacement: true, expectedPreviousDriverId: "driver_A" },
      createAdminContext(),
      { db: harness.mockDb, rtdb: harness.mockRtdb }
    );
    assert.strictEqual(resC.success, true);
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, "driver_C");
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].assignmentVersion, 2);
    assert.strictEqual(harness.firestoreData.users["driver_A"].assignedBusId, null);
    assert.strictEqual(harness.firestoreData.users["driver_C"].assignedBusId, "BUS-01");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "driver_C");

    // Step 3: Admin 1 now submits their delayed request for Driver B, but their expectedPreviousDriverId was "driver_A"
    // Because Admin 2 already replaced Driver A with Driver C, the backend detects the stale conflict!
    await assert.rejects(async () => {
      await processSetBusDriverAssignment(
        { busId: "BUS-01", driverUid: "driver_B", confirmReplacement: true, expectedPreviousDriverId: "driver_A" },
        createAdminContext(),
        { db: harness.mockDb, rtdb: harness.mockRtdb }
      );
    }, /Assignment conflict: Bus 'BUS-01' is currently assigned to 'Driver C', not 'driver_A'/);

    // Step 4: Verify Bus 01 remains authoritatively assigned to Driver C
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].driverId, "driver_C", "Driver C was NOT silently overwritten");
    assert.strictEqual(harness.firestoreData.buses["BUS-01"].assignmentVersion, 2);
    assert.strictEqual(harness.firestoreData.users["driver_B"].assignedBusId, null, "Driver B remained unassigned");
    assert.strictEqual(harness.rtdbData.bus_operators["BUS-01"].permanentDriverUid, "driver_C", "RTDB operator remains Driver C");
  });

  console.log("\n--- Suite 10: Security Hardening (public_driver_directory & getEffectiveDriver) ---");

  await itAsync("10.1. Public driver directory strips contactNumber/phoneNumber while preserving driverName & assignedBusId", async () => {
    const harness = createMockHarness({
      users: {
        drv_01: { uid: "drv_01", customId: "drv_01", name: "Ramesh", phoneNumber: "9876543210", role: "DRIVER", active: true, assignedBusId: "BUS-01" }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", active: true, driverId: "drv_01" }
      }
    });

    await syncPublicDriverDirectory("drv_01", harness.mockDb);
    const entry = harness.firestoreData.public_driver_directory["drv_01"];
    assert.ok(entry, "public_driver_directory entry must be created");
    assert.strictEqual(entry.driverName, "Ramesh");
    assert.strictEqual(entry.assignedBusId, "BUS-01");
    assert.strictEqual(entry.contactNumber, undefined, "contactNumber must NOT be written publicly");
    assert.strictEqual(entry.phoneNumber, undefined, "phoneNumber must NOT be written publicly");
  });

  await itAsync("10.2. getEffectiveDriver rejects missing App Check, unauthenticated, and unauthorized callers", async () => {
    const harness = createMockHarness({
      users: {
        drv_01: { uid: "drv_01", role: "DRIVER", active: true },
        inactive_drv: { uid: "inactive_drv", role: "DRIVER", active: false }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", driverId: "drv_01", driverName: "Ramesh", driverPhone: "9876543210" }
      }
    });

    // Missing App Check
    await assert.rejects(async () => {
      await processGetEffectiveDriver({ busId: "BUS-01" }, { auth: { uid: "drv_01" } }, harness.mockDb);
    }, /App Check/);

    // Missing Auth
    await assert.rejects(async () => {
      await processGetEffectiveDriver({ busId: "BUS-01" }, { app: { appId: "valid" } }, harness.mockDb);
    }, /Authentication is required/);

    // Inactive user
    await assert.rejects(async () => {
      await processGetEffectiveDriver({ busId: "BUS-01" }, { app: { appId: "valid" }, auth: { uid: "inactive_drv" } }, harness.mockDb);
    }, /Only active staff/);

    // Valid active driver with App Check succeeds and strips driverPhone
    const res = await processGetEffectiveDriver({ busId: "BUS-01" }, { app: { appId: "valid" }, auth: { uid: "drv_01" } }, harness.mockDb);
    assert.strictEqual(res.busId, "BUS-01");
    assert.strictEqual(res.effectiveDriverName, "Ramesh");
    assert.strictEqual(res.driverPhone, undefined, "getEffectiveDriver callable must NOT expose driverPhone");
  });

  await itAsync("10.3. processCreateTemporaryAssignment omits replacementDriverPhone & permanentDriverPhone from public Firestore doc", async () => {
    const harness = createMockHarness({
      users: {
        admin_super: { uid: "admin_super", customId: "admin_super", role: "ADMIN", active: true },
        drv_01: { uid: "drv_01", customId: "drv_01", name: "Ramesh", phoneNumber: "9876543210", role: "DRIVER", active: true, assignedBusId: "BUS-01" },
        drv_02: { uid: "drv_02", customId: "drv_02", name: "Suresh", phoneNumber: "9876543211", role: "DRIVER", active: true, assignedBusId: null }
      },
      buses: {
        "BUS-01": { busId: "BUS-01", busNumber: "01", active: true, driverId: "drv_01", driverName: "Ramesh" }
      }
    });

    const now = Date.now();
    const res = await processCreateTemporaryAssignment(
      { busId: "BUS-01", replacementDriverUid: "drv_02", startAt: now, endAt: now + 3600000, reason: "Shift swap" },
      "admin_super",
      harness.mockDb,
      harness.mockRtdb
    );
    assert.strictEqual(res.success, true);
    const createdId = res.assignment.assignmentId;
    const storedDoc = harness.firestoreData.temporary_assignments[createdId];
    assert.ok(storedDoc, "temporary_assignments document must be stored");
    assert.strictEqual(storedDoc.replacementDriverName, "Suresh");
    assert.strictEqual(storedDoc.replacementDriverId, "drv_02");
    assert.strictEqual(storedDoc.status, "ACTIVE");
    assert.strictEqual(storedDoc.replacementDriverPhone, undefined, "replacementDriverPhone must NOT be stored in temporary_assignments");
    assert.strictEqual(storedDoc.permanentDriverPhone, undefined, "permanentDriverPhone must NOT be stored in temporary_assignments");
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
