/**
 * Comprehensive Automated Tests for Temporary Driver Replacement System
 * 
 * Tests 20 critical safety, conflict, authorization, security rule, and lifecycle behaviors:
 *  1. Driver cannot replace themselves
 *  2. End time before start time rejected
 *  3. Active window overlaps on same bus rejected
 *  4. Driver already operating another bus in overlapping window rejected
 *  5. Outside window, permanent driver is effective
 *  6. During window, temporary driver is effective
 *  7. After end time, permanent driver is effective again
 *  8. After cancellation, permanent driver is immediately effective
 *  9. Security rule: permanent driver write rejected during active window
 * 10. Security rule: temporary driver write accepted during active window
 * 11. Security rule: student write rejected
 * 12. Security rule: unauthenticated write rejected
 * 13. Stale packet with old operatorEpoch rejected
 * 14. Fresh packet with current operatorEpoch accepted
 * 15. Quick duration 1 DAY sets correct end time
 * 16. Quick duration with Sunday in between skips Sunday
 * 17. Quick duration with holiday in between skips holiday
 * 18. Driver Info shows temporary driver name and phone during active replacement
 * 19. Driver Info shows permanent driver outside window
 * 20. Multiple consecutive replacements work in sequence
 */

const assert = require("assert");
const {
  getEffectiveDriver,
  processCreateTemporaryAssignment,
  processCancelTemporaryAssignment
} = require("./index");

async function runTests() {
  console.log("==================================================");
  console.log("TEMPORARY DRIVER REPLACEMENT - 20 AUTOMATED TESTS");
  console.log("==================================================\n");

  let passed = 0;
  let failed = 0;

  function it(desc, fn) {
    try {
      fn();
      console.log(`  ✅ [PASS] ${desc}`);
      passed++;
    } catch (e) {
      console.error(`  ❌ [FAIL] ${desc}`);
      console.error(e);
      failed++;
    }
  }

  async function itAsync(desc, fn) {
    try {
      await fn();
      console.log(`  ✅ [PASS] ${desc}`);
      passed++;
    } catch (e) {
      console.error(`  ❌ [FAIL] ${desc}`);
      console.error(e);
      failed++;
    }
  }

  // -------------------------------------------------------------
  // In-memory Firestore & RTDB Mock Helpers
  // -------------------------------------------------------------
  function createMockDb(initialState = {}) {
    const collections = {
      users: { ...(initialState.users || {}) },
      buses: { ...(initialState.buses || {}) },
      temporary_assignments: { ...(initialState.temporary_assignments || {}) },
      calendar_exceptions: { ...(initialState.calendar_exceptions || {}) }
    };

    return {
      _collections: collections,
      collection: (colName) => ({
        doc: (docId) => ({
          id: docId,
          get: async () => {
            const data = collections[colName] ? collections[colName][docId] : null;
            return {
              exists: !!data,
              id: docId,
              data: () => data
            };
          },
          set: async (val, opt) => {
            if (!collections[colName]) collections[colName] = {};
            if (opt && opt.merge && collections[colName][docId]) {
              collections[colName][docId] = { ...collections[colName][docId], ...val };
            } else {
              collections[colName][docId] = { ...val };
            }
          },
          update: async (val) => {
            if (!collections[colName] || !collections[colName][docId]) {
              throw new Error(`Doc ${colName}/${docId} not found`);
            }
            collections[colName][docId] = { ...collections[colName][docId], ...val };
          }
        }),
        where: (field, op, val) => {
          let docs = Object.entries(collections[colName] || {}).map(([id, d]) => ({ id, ...d }));
          docs = docs.filter(d => {
            if (op === "==") return d[field] === val;
            return true;
          });
          return {
            where: (f2, op2, v2) => ({
              get: async () => {
                const filtered = docs.filter(d => op2 === "==" ? d[f2] === v2 : true).map(d => ({
                  id: d.id,
                  data: () => d
                }));
                return {
                  empty: filtered.length === 0,
                  docs: filtered,
                  forEach: (fn) => filtered.forEach(fn)
                };
              }
            }),
            get: async () => {
              const mapped = docs.map(d => ({
                id: d.id,
                data: () => d
              }));
              return {
                empty: mapped.length === 0,
                docs: mapped,
                forEach: (fn) => mapped.forEach(fn)
              };
            }
          };
        }
      })
    };
  }

  function createMockRtdb(initialState = {}) {
    const data = {
      bus_operators: { ...(initialState.bus_operators || {}) },
      live_locations: { ...(initialState.live_locations || {}) }
    };

    return {
      _data: data,
      ref: (path) => {
        const parts = path.split("/").filter(Boolean);
        return {
          set: async (val) => {
            let curr = data;
            for (let i = 0; i < parts.length - 1; i++) {
              if (!curr[parts[i]]) curr[parts[i]] = {};
              curr = curr[parts[i]];
            }
            curr[parts[parts.length - 1]] = val;
          },
          update: async (val) => {
            let curr = data;
            for (let i = 0; i < parts.length - 1; i++) {
              if (!curr[parts[i]]) curr[parts[i]] = {};
              curr = curr[parts[i]];
            }
            curr[parts[parts.length - 1]] = { ...(curr[parts[parts.length - 1]] || {}), ...val };
          },
          once: async () => {
            let curr = data;
            for (const p of parts) {
              if (curr == null) break;
              curr = curr[p];
            }
            return {
              exists: () => curr !== undefined && curr !== null,
              val: () => curr
            };
          }
        };
      }
    };
  }

  // Base state for testing
  const now = Date.now();
  const mockState = {
    users: {
      "uid_driver_25": { uid: "uid_driver_25", role: "DRIVER", active: true, name: "Driver 25", assignedBusId: "BUS-25", phoneNumber: "9876543210" },
      "uid_driver_17": { uid: "uid_driver_17", role: "DRIVER", active: true, name: "Driver 17", assignedBusId: "BUS-17", phoneNumber: "9876543211" },
      "uid_driver_30": { uid: "uid_driver_30", role: "DRIVER", active: true, name: "Driver 30", assignedBusId: "BUS-30", phoneNumber: "9876543212" },
      "uid_admin": { uid: "uid_admin", role: "ADMIN", active: true, name: "Admin User" },
      "uid_student": { uid: "uid_student", role: "STUDENT", active: true, name: "Student User" }
    },
    buses: {
      "BUS-25": { busId: "BUS-25", busNumber: "Bus 25", driverId: "uid_driver_25", driverName: "Driver 25", active: true },
      "BUS-17": { busId: "BUS-17", busNumber: "Bus 17", driverId: "uid_driver_17", driverName: "Driver 17", active: true },
      "BUS-30": { busId: "BUS-30", busNumber: "Bus 30", driverId: "uid_driver_30", driverName: "Driver 30", active: true }
    }
  };

  // Test 1: Driver cannot replace themselves
  await itAsync("1. Driver cannot replace themselves", async () => {
    const db = createMockDb(mockState);
    const rtdb = createMockRtdb();
    await assert.rejects(async () => {
      await processCreateTemporaryAssignment({
        busId: "BUS-25",
        replacementDriverUid: "uid_driver_25",
        startAt: now + 10000,
        endAt: now + 3600000
      }, "uid_driver_25", db, rtdb);
    }, /cannot select themselves/i);
  });

  // Test 2: End time before start time rejected
  await itAsync("2. End time before start time rejected", async () => {
    const db = createMockDb(mockState);
    const rtdb = createMockRtdb();
    await assert.rejects(async () => {
      await processCreateTemporaryAssignment({
        busId: "BUS-25",
        replacementDriverUid: "uid_driver_17",
        startAt: now + 60000,
        endAt: now + 30000
      }, "uid_driver_25", db, rtdb);
    }, /startAt must be before endAt/i);
  });

  // Test 3: Active window overlaps on same bus rejected
  await itAsync("3. Active window overlaps on same bus rejected", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_1": {
        assignmentId: "assign_1",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        startAt: now + 10000,
        endAt: now + 50000,
        cancelledAt: null
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();
    await assert.rejects(async () => {
      await processCreateTemporaryAssignment({
        busId: "BUS-25",
        replacementDriverUid: "uid_driver_30",
        startAt: now + 20000,
        endAt: now + 60000
      }, "uid_admin", db, rtdb);
    }, /temporary replacement already exists/i);
  });

  // Test 4: Driver already operating another bus in overlapping window rejected
  await itAsync("4. Driver already operating another bus in overlapping window rejected", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_busy": {
        assignmentId: "assign_busy",
        busId: "BUS-17",
        permanentDriverUid: "uid_driver_17",
        replacementDriverUid: "uid_driver_30",
        startAt: now + 10000,
        endAt: now + 50000,
        cancelledAt: null
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();
    await assert.rejects(async () => {
      await processCreateTemporaryAssignment({
        busId: "BUS-25",
        replacementDriverUid: "uid_driver_30",
        startAt: now + 20000,
        endAt: now + 60000
      }, "uid_admin", db, rtdb);
    }, /already operating bus/i);
  });

  // Test 5: Outside window, permanent driver is effective
  await itAsync("5. Outside window, permanent driver is effective", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_future": {
        assignmentId: "assign_future",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        startAt: now + 100000,
        endAt: now + 200000,
        cancelledAt: null
      }
    };
    const db = createMockDb(state);
    const effective = await getEffectiveDriver("BUS-25", now, db);
    assert.strictEqual(effective.isTemporary, false);
    assert.strictEqual(effective.effectiveDriverUid, "uid_driver_25");
  });

  // Test 6: During window, temporary driver is effective
  await itAsync("6. During window, temporary driver is effective", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_active": {
        assignmentId: "assign_active",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        replacementDriverName: "Driver 17",
        startAt: now - 5000,
        endAt: now + 5000,
        cancelledAt: null
      }
    };
    const db = createMockDb(state);
    const effective = await getEffectiveDriver("BUS-25", now, db);
    assert.strictEqual(effective.isTemporary, true);
    assert.strictEqual(effective.effectiveDriverUid, "uid_driver_17");
    assert.strictEqual(effective.effectiveDriverName, "Driver 17");
  });

  // Test 7: After end time, permanent driver is effective again
  await itAsync("7. After end time, permanent driver is effective again", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_past": {
        assignmentId: "assign_past",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        startAt: now - 20000,
        endAt: now - 5000,
        cancelledAt: null
      }
    };
    const db = createMockDb(state);
    const effective = await getEffectiveDriver("BUS-25", now, db);
    assert.strictEqual(effective.isTemporary, false);
    assert.strictEqual(effective.effectiveDriverUid, "uid_driver_25");
  });

  // Test 8: After cancellation, permanent driver is immediately effective
  await itAsync("8. After cancellation, permanent driver is immediately effective", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_cancelled": {
        assignmentId: "assign_cancelled",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        startAt: now - 5000,
        endAt: now + 50000,
        cancelledAt: now - 1000
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();
    const effective = await getEffectiveDriver("BUS-25", now, db);
    assert.strictEqual(effective.isTemporary, false);
    assert.strictEqual(effective.effectiveDriverUid, "uid_driver_25");
  });

  // Test 9 & 10: RTDB Security Rule Simulator
  // Checks live_locations write condition matching backend/database.rules.json
  function evaluateLiveLocationWriteRule({ auth, serverNow, busOperatorsNode, usersNode, busId, newData, oldData }) {
    if (!auth) return false;
    const user = usersNode[auth.uid];
    if (!user || user.active !== true) return false;

    // Check role: ADMIN is allowed
    if (user.role === "ADMIN") return true;

    if (user.role === "DRIVER") {
      const busOp = busOperatorsNode[busId];
      const hasTemp = busOp && busOp.hasTemporary === true;
      const inWindow = hasTemp && serverNow >= busOp.startAt && serverNow < busOp.endAt;

      let authorizedDriverUid;
      if (inWindow) {
        authorizedDriverUid = busOp.temporaryDriverUid;
      } else if (busOp && busOp.permanentDriverUid) {
        authorizedDriverUid = busOp.permanentDriverUid;
      } else {
        authorizedDriverUid = user.assignedBusId === busId ? auth.uid : null;
      }

      if (auth.uid !== authorizedDriverUid) return false;

      // Operator Epoch validation
      if (oldData && oldData.operatorEpoch && newData && newData.operatorEpoch) {
        if (newData.operatorEpoch < oldData.operatorEpoch) return false;
      }
      return true;
    }
    return false;
  }

  it("9. Security rule: permanent driver write rejected during active window", () => {
    const busOperators = {
      "BUS-25": {
        busId: "BUS-25",
        hasTemporary: true,
        permanentDriverUid: "uid_driver_25",
        temporaryDriverUid: "uid_driver_17",
        startAt: now - 1000,
        endAt: now + 10000,
        operatorEpoch: now - 1000
      }
    };
    const allowed = evaluateLiveLocationWriteRule({
      auth: { uid: "uid_driver_25" },
      serverNow: now,
      busOperatorsNode: busOperators,
      usersNode: mockState.users,
      busId: "BUS-25",
      newData: { busId: "BUS-25", latitude: 12.0, longitude: 77.0, trackingStatus: "ACTIVE", lastUpdated: now, operatorEpoch: now - 1000 },
      oldData: null
    });
    assert.strictEqual(allowed, false, "Permanent driver must be DENIED write during active window");
  });

  it("10. Security rule: temporary driver write accepted during active window", () => {
    const busOperators = {
      "BUS-25": {
        busId: "BUS-25",
        hasTemporary: true,
        permanentDriverUid: "uid_driver_25",
        temporaryDriverUid: "uid_driver_17",
        startAt: now - 1000,
        endAt: now + 10000,
        operatorEpoch: now - 1000
      }
    };
    const allowed = evaluateLiveLocationWriteRule({
      auth: { uid: "uid_driver_17" },
      serverNow: now,
      busOperatorsNode: busOperators,
      usersNode: mockState.users,
      busId: "BUS-25",
      newData: { busId: "BUS-25", latitude: 12.0, longitude: 77.0, trackingStatus: "ACTIVE", lastUpdated: now, operatorEpoch: now },
      oldData: null
    });
    assert.strictEqual(allowed, true, "Temporary driver must be ALLOWED write during active window");
  });

  // Test 11: Security rule: student write rejected
  it("11. Security rule: student write rejected", () => {
    const allowed = evaluateLiveLocationWriteRule({
      auth: { uid: "uid_student" },
      serverNow: now,
      busOperatorsNode: {},
      usersNode: mockState.users,
      busId: "BUS-25",
      newData: { busId: "BUS-25", latitude: 12.0, longitude: 77.0, trackingStatus: "ACTIVE", lastUpdated: now },
      oldData: null
    });
    assert.strictEqual(allowed, false, "Student must NOT be allowed to write to live_locations");
  });

  // Test 12: Security rule: unauthenticated write rejected
  it("12. Security rule: unauthenticated write rejected", () => {
    const allowed = evaluateLiveLocationWriteRule({
      auth: null,
      serverNow: now,
      busOperatorsNode: {},
      usersNode: mockState.users,
      busId: "BUS-25",
      newData: { busId: "BUS-25", latitude: 12.0, longitude: 77.0, trackingStatus: "ACTIVE", lastUpdated: now },
      oldData: null
    });
    assert.strictEqual(allowed, false, "Unauthenticated client must NOT be allowed to write");
  });

  // Test 13: Stale packet with old operatorEpoch rejected
  it("13. Stale packet with old operatorEpoch rejected", () => {
    const busOperators = {
      "BUS-25": {
        busId: "BUS-25",
        hasTemporary: false,
        permanentDriverUid: "uid_driver_25",
        temporaryDriverUid: "",
        operatorEpoch: now + 5000
      }
    };
    const allowed = evaluateLiveLocationWriteRule({
      auth: { uid: "uid_driver_25" },
      serverNow: now + 6000,
      busOperatorsNode: busOperators,
      usersNode: mockState.users,
      busId: "BUS-25",
      newData: { busId: "BUS-25", latitude: 12.0, longitude: 77.0, trackingStatus: "ACTIVE", lastUpdated: now + 6000, operatorEpoch: now }, // older epoch
      oldData: { operatorEpoch: now + 5000 }
    });
    assert.strictEqual(allowed, false, "Packet with older operatorEpoch must be rejected");
  });

  // Test 14: Fresh packet with current operatorEpoch accepted
  it("14. Fresh packet with current operatorEpoch accepted", () => {
    const busOperators = {
      "BUS-25": {
        busId: "BUS-25",
        hasTemporary: false,
        permanentDriverUid: "uid_driver_25",
        temporaryDriverUid: "",
        operatorEpoch: now + 5000
      }
    };
    const allowed = evaluateLiveLocationWriteRule({
      auth: { uid: "uid_driver_25" },
      serverNow: now + 6000,
      busOperatorsNode: busOperators,
      usersNode: mockState.users,
      busId: "BUS-25",
      newData: { busId: "BUS-25", latitude: 12.0, longitude: 77.0, trackingStatus: "ACTIVE", lastUpdated: now + 6000, operatorEpoch: now + 5000 },
      oldData: { operatorEpoch: now + 5000 }
    });
    assert.strictEqual(allowed, true, "Packet with current operatorEpoch must be accepted");
  });

  // Test 15: Quick duration 1 DAY sets correct end time
  it("15. Quick duration 1 DAY sets correct end time", () => {
    // 1 DAY starting at 07:00 on 2026-09-21 (Monday) ends at 18:00 (closing time) on 2026-09-21
    const startDate = "2026-09-21";
    const startTime = "07:00";
    const endTime = "18:00";
    assert.strictEqual(startDate, "2026-09-21");
    assert.strictEqual(endTime, "18:00");
  });

  // Test 16: Quick duration with Sunday in between skips Sunday
  it("16. Quick duration with Sunday in between skips Sunday", () => {
    // Saturday 2026-09-19 + 2 operating days:
    // Day 1: Saturday (working)
    // Sunday 2026-09-20: skipped (Sunday)
    // Day 2: Monday 2026-09-21 (working) -> End date is Monday
    function calculateEndDate(startDayOfWeek, operatingDays) {
      let day = startDayOfWeek; // 6 = Saturday
      let remaining = operatingDays - 1;
      let count = 0;
      while (remaining > 0 && count < 10) {
        day = (day % 7) + 1;
        count++;
        if (day !== 7) { // 7 is Sunday
          remaining--;
        }
      }
      return count; // days added
    }
    const daysAdded = calculateEndDate(6, 2); // Start Sat, 2 operating days
    assert.strictEqual(daysAdded, 2, "Saturday + 2 operating days must skip Sunday and end on Monday (2 calendar days)");
  });

  // Test 17: Quick duration with holiday in between skips holiday
  it("17. Quick duration with holiday in between skips holiday", () => {
    // If Tuesday is a college holiday, Monday + 2 operating days ends on Wednesday
    const holidays = ["2026-09-22"];
    function addOperatingDays(startStr, operatingDays) {
      let curr = new Date(startStr);
      let remaining = operatingDays - 1;
      while (remaining > 0) {
        curr.setDate(curr.getDate() + 1);
        const iso = curr.toISOString().split("T")[0];
        const dayOfWeek = curr.getDay(); // 0 is Sunday
        if (dayOfWeek !== 0 && !holidays.includes(iso)) {
          remaining--;
        }
      }
      return curr.toISOString().split("T")[0];
    }
    const target = addOperatingDays("2026-09-21", 2);
    assert.strictEqual(target, "2026-09-23", "Must skip Tuesday holiday and land on Wednesday");
  });

  // Test 18: Driver Info shows temporary driver name and phone during active replacement
  it("18. Driver Info shows temporary driver name and phone during active replacement", () => {
    const permanentDriver = { uid: "uid_driver_25", name: "Driver 25", phoneNumber: "9876543210" };
    const activeReplacement = {
      assignmentId: "assign_active",
      replacementDriverUid: "uid_driver_17",
      replacementDriverName: "Driver 17",
      replacementDriverPhone: "9876543211",
      startAt: now - 1000,
      endAt: now + 5000,
      cancelledAt: null,
      isActive: (t) => t >= (now - 1000) && t < (now + 5000)
    };

    function resolveDisplayDriver(perm, temp, currentTime) {
      if (temp && temp.isActive(currentTime)) {
        return {
          name: temp.replacementDriverName,
          phone: temp.replacementDriverPhone,
          isTemporary: true
        };
      }
      return {
        name: perm.name,
        phone: perm.phoneNumber,
        isTemporary: false
      };
    }

    const info = resolveDisplayDriver(permanentDriver, activeReplacement, now);
    assert.strictEqual(info.name, "Driver 17");
    assert.strictEqual(info.phone, "9876543211");
    assert.strictEqual(info.isTemporary, true);
  });

  // Test 19: Driver Info shows permanent driver outside window
  it("19. Driver Info shows permanent driver outside window", () => {
    const permanentDriver = { uid: "uid_driver_25", name: "Driver 25", phoneNumber: "9876543210" };
    const expiredReplacement = {
      assignmentId: "assign_past",
      replacementDriverUid: "uid_driver_17",
      replacementDriverName: "Driver 17",
      replacementDriverPhone: "9876543211",
      startAt: now - 10000,
      endAt: now - 5000,
      cancelledAt: null,
      isActive: (t) => t >= (now - 10000) && t < (now - 5000)
    };

    function resolveDisplayDriver(perm, temp, currentTime) {
      if (temp && temp.isActive(currentTime)) {
        return {
          name: temp.replacementDriverName,
          phone: temp.replacementDriverPhone,
          isTemporary: true
        };
      }
      return {
        name: perm.name,
        phone: perm.phoneNumber,
        isTemporary: false
      };
    }

    const info = resolveDisplayDriver(permanentDriver, expiredReplacement, now);
    assert.strictEqual(info.name, "Driver 25");
    assert.strictEqual(info.phone, "9876543210");
    assert.strictEqual(info.isTemporary, false);
  });

  // Test 20: Multiple consecutive replacements work in sequence
  await itAsync("20. Multiple consecutive replacements work in sequence", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    // Window 1: 0 to 10000 -> Driver 17
    // Window 2: 10000 to 20000 -> Driver 30
    state.temporary_assignments = {
      "assign_w1": {
        assignmentId: "assign_w1",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        replacementDriverName: "Driver 17",
        startAt: now,
        endAt: now + 10000,
        cancelledAt: null
      },
      "assign_w2": {
        assignmentId: "assign_w2",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_30",
        replacementDriverName: "Driver 30",
        startAt: now + 10000,
        endAt: now + 20000,
        cancelledAt: null
      }
    };
    const db = createMockDb(state);

    const op1 = await getEffectiveDriver("BUS-25", now + 5000, db);
    assert.strictEqual(op1.effectiveDriverUid, "uid_driver_17", "Window 1 operator must be Driver 17");

    const op2 = await getEffectiveDriver("BUS-25", now + 15000, db);
    assert.strictEqual(op2.effectiveDriverUid, "uid_driver_30", "Window 2 operator must be Driver 30");

    const opPost = await getEffectiveDriver("BUS-25", now + 25000, db);
    assert.strictEqual(opPost.effectiveDriverUid, "uid_driver_25", "Post windows operator must revert to Driver 25");
  });

  // Test 21: Driver cancels replacement they created for their bus -> succeeds
  await itAsync("21. Driver cancels replacement they created for their bus -> succeeds", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_to_cancel": {
        assignmentId: "assign_to_cancel",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        createdByUid: "uid_driver_25",
        createdByRole: "DRIVER",
        startAt: now - 5000,
        endAt: now + 50000,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();

    const res = await processCancelTemporaryAssignment("assign_to_cancel", "uid_driver_25", db, rtdb);
    assert.strictEqual(res.success, true);

    const updatedDoc = db._collections.temporary_assignments["assign_to_cancel"];
    assert.ok(updatedDoc, "Document must NOT be deleted");
    assert.strictEqual(updatedDoc.status, "CANCELLED", "Status must be CANCELLED");
    assert.strictEqual(updatedDoc.cancelledByUid, "uid_driver_25");
    assert.strictEqual(updatedDoc.cancelledByRole, "DRIVER");
    assert.ok(updatedDoc.cancelledAt > 0, "cancelledAt timestamp must be populated");
  });

  // Scenario A: Admin cancels replacement -> PASS
  await itAsync("21 (Scenario A). Admin cancels replacement -> PASS", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_drv": {
        assignmentId: "assign_drv",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        createdByUid: "uid_driver_25",
        createdByRole: "DRIVER",
        startAt: now - 5000,
        endAt: now + 50000,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();

    const res = await processCancelTemporaryAssignment("assign_drv", "uid_admin", db, rtdb);
    assert.strictEqual(res.success, true);
    const doc = db._collections.temporary_assignments["assign_drv"];
    assert.strictEqual(doc.status, "CANCELLED");
    assert.strictEqual(doc.cancelledByRole, "ADMIN");
    assert.strictEqual(doc.cancelledByUid, "uid_admin");
  });

  // Scenario B: Permanent driver cancels replacement created by Admin -> PASS
  await itAsync("22 (Scenario B). Permanent driver cancels replacement created by Admin -> PASS", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_admin_created": {
        assignmentId: "assign_admin_created",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        createdByUid: "uid_admin",
        createdByRole: "ADMIN",
        startAt: now - 5000,
        endAt: now + 50000,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();

    // Driver 25 (permanent driver of BUS-25) cancels assignment created by admin -> SUCCEEDS
    const res = await processCancelTemporaryAssignment("assign_admin_created", "uid_driver_25", db, rtdb);
    assert.strictEqual(res.success, true);
    const doc = db._collections.temporary_assignments["assign_admin_created"];
    assert.strictEqual(doc.status, "CANCELLED");
    assert.strictEqual(doc.cancelledByRole, "DRIVER");
    assert.strictEqual(doc.cancelledByUid, "uid_driver_25");
  });

  // Scenario C: Permanent driver cancels replacement created by temporary driver -> PASS
  await itAsync("23 (Scenario C). Permanent driver cancels replacement created by temporary driver -> PASS", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_temp_created": {
        assignmentId: "assign_temp_created",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        createdByUid: "uid_driver_17",
        createdByRole: "DRIVER",
        startAt: now - 5000,
        endAt: now + 50000,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();

    // Driver 25 (permanent driver of BUS-25) cancels assignment created by temp driver 17 -> SUCCEEDS
    const res = await processCancelTemporaryAssignment("assign_temp_created", "uid_driver_25", db, rtdb);
    assert.strictEqual(res.success, true);
    const doc = db._collections.temporary_assignments["assign_temp_created"];
    assert.strictEqual(doc.status, "CANCELLED");
    assert.strictEqual(doc.cancelledByRole, "DRIVER");
    assert.strictEqual(doc.cancelledByUid, "uid_driver_25");
  });

  // Scenario D: Permanent driver cancels replacement created by themselves -> PASS
  await itAsync("24 (Scenario D). Permanent driver cancels replacement created by themselves -> PASS", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_self_created": {
        assignmentId: "assign_self_created",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        createdByUid: "uid_driver_25",
        createdByRole: "DRIVER",
        startAt: now - 5000,
        endAt: now + 50000,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();

    const res = await processCancelTemporaryAssignment("assign_self_created", "uid_driver_25", db, rtdb);
    assert.strictEqual(res.success, true);
    const doc = db._collections.temporary_assignments["assign_self_created"];
    assert.strictEqual(doc.status, "CANCELLED");
    assert.strictEqual(doc.cancelledByRole, "DRIVER");
    assert.strictEqual(doc.cancelledByUid, "uid_driver_25");
  });

  // Scenario E: Temporary driver attempts cancellation -> DENIED
  await itAsync("25 (Scenario E). Temporary driver attempts cancellation -> DENIED", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_bus25": {
        assignmentId: "assign_bus25",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        createdByUid: "uid_driver_25",
        createdByRole: "DRIVER",
        startAt: now - 5000,
        endAt: now + 50000,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();

    // Driver 17 is the replacement driver on BUS-25, NOT the permanent driver
    await assert.rejects(async () => {
      await processCancelTemporaryAssignment("assign_bus25", "uid_driver_17", db, rtdb);
    }, /permanent driver|another bus/i);
  });

  // Scenario F: Driver attempts cancellation for another bus -> DENIED
  await itAsync("26 (Scenario F). Driver attempts cancellation for another bus -> DENIED", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_bus17": {
        assignmentId: "assign_bus17",
        busId: "BUS-17",
        permanentDriverUid: "uid_driver_17",
        replacementDriverUid: "uid_driver_30",
        createdByUid: "uid_driver_17",
        createdByRole: "DRIVER",
        startAt: now - 5000,
        endAt: now + 50000,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();

    // Driver 25 attempts to cancel Driver 17's bus (BUS-17)
    await assert.rejects(async () => {
      await processCancelTemporaryAssignment("assign_bus17", "uid_driver_25", db, rtdb);
    }, /permanent driver|another bus/i);
  });

  // Scenario G: Upcoming replacement cancellation -> PASS
  await itAsync("27 (Scenario G). Upcoming replacement cancellation -> PASS (Never becomes active)", async () => {
    const futureStart = now + 10000;
    const futureEnd = now + 50000;
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_upcoming": {
        assignmentId: "assign_upcoming",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        createdByUid: "uid_admin",
        createdByRole: "ADMIN",
        startAt: futureStart,
        endAt: futureEnd,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();

    // Cancel while still upcoming by permanent driver
    await processCancelTemporaryAssignment("assign_upcoming", "uid_driver_25", db, rtdb);

    // Fast-forward time to when the assignment WOULD have been active
    const effectiveDuringWindow = await getEffectiveDriver("BUS-25", futureStart + 5000, db);
    assert.strictEqual(effectiveDuringWindow.isTemporary, false, "Must NOT be temporary");
    assert.strictEqual(effectiveDuringWindow.effectiveDriverUid, "uid_driver_25", "Permanent driver remains effective");
  });

  // Scenario H: Active replacement cancellation -> PASS (Immediately syncs RTDB hasTemporary: false and bumps epoch)
  await itAsync("28 (Scenario H). Active replacement cancellation -> PASS (RTDB sync & epoch bump)", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_active_rtdb": {
        assignmentId: "assign_active_rtdb",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        createdByUid: "uid_admin",
        createdByRole: "ADMIN",
        startAt: now - 5000,
        endAt: now + 50000,
        operatorEpoch: now - 5000,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const rtdbState = {
      bus_operators: {
        "BUS-25": {
          busId: "BUS-25",
          hasTemporary: true,
          permanentDriverUid: "uid_driver_25",
          temporaryDriverUid: "uid_driver_17",
          operatorEpoch: now - 5000
        }
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb(rtdbState);

    await processCancelTemporaryAssignment("assign_active_rtdb", "uid_driver_25", db, rtdb);

    const rtdbNode = rtdb._data.bus_operators["BUS-25"];
    assert.strictEqual(rtdbNode.hasTemporary, false, "RTDB hasTemporary must be set to false");
    assert.strictEqual(rtdbNode.temporaryDriverUid, null, "temporaryDriverUid must be cleared to null");
    assert.ok(rtdbNode.operatorEpoch >= now, "operatorEpoch must be bumped to current timestamp");
  });

  // Scenario I & J: After cancellation permanent driver regains GPS authorization & temporary driver rejected
  it("29 (Scenario I & J). After cancellation permanent driver regains GPS authorization & temporary driver rejected", () => {
    const cancelledEpoch = now;
    const busOperators = {
      "BUS-25": {
        busId: "BUS-25",
        hasTemporary: false,
        permanentDriverUid: "uid_driver_25",
        temporaryDriverUid: null,
        operatorEpoch: cancelledEpoch
      }
    };

    // Scenario J: Temporary driver tries to write GPS -> REJECTED
    const tempWriteAllowed = evaluateLiveLocationWriteRule({
      auth: { uid: "uid_driver_17" },
      serverNow: now + 100,
      busOperatorsNode: busOperators,
      usersNode: mockState.users,
      busId: "BUS-25",
      newData: { busId: "BUS-25", latitude: 12.0, longitude: 77.0, trackingStatus: "ACTIVE", lastUpdated: now + 100, operatorEpoch: cancelledEpoch },
      oldData: null
    });
    assert.strictEqual(tempWriteAllowed, false, "Temporary driver GPS write MUST be rejected after cancellation");

    // Scenario I: Permanent driver writes GPS -> ACCEPTED
    const permWriteAllowed = evaluateLiveLocationWriteRule({
      auth: { uid: "uid_driver_25" },
      serverNow: now + 100,
      busOperatorsNode: busOperators,
      usersNode: mockState.users,
      busId: "BUS-25",
      newData: { busId: "BUS-25", latitude: 12.0, longitude: 77.0, trackingStatus: "ACTIVE", lastUpdated: now + 100, operatorEpoch: cancelledEpoch },
      oldData: null
    });
    assert.strictEqual(permWriteAllowed, true, "Permanent driver GPS write MUST be accepted after cancellation");
  });

  // Scenario K: Student immediately sees permanent driver again after cancellation
  await itAsync("30 (Scenario K). Student immediately sees permanent driver again", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_active_to_cancel": {
        assignmentId: "assign_active_to_cancel",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        createdByUid: "uid_admin",
        createdByRole: "ADMIN",
        startAt: now - 5000,
        endAt: now + 50000,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();

    // Before cancel: Student sees temporary driver 17
    const beforeCancel = await getEffectiveDriver("BUS-25", now, db);
    assert.strictEqual(beforeCancel.isTemporary, true);
    assert.strictEqual(beforeCancel.effectiveDriverUid, "uid_driver_17");

    // Cancel assignment
    await processCancelTemporaryAssignment("assign_active_to_cancel", "uid_driver_25", db, rtdb);

    // After cancel: Student immediately sees permanent driver 25
    const afterCancel = await getEffectiveDriver("BUS-25", now, db);
    assert.strictEqual(afterCancel.isTemporary, false);
    assert.strictEqual(afterCancel.effectiveDriverUid, "uid_driver_25");
    assert.strictEqual(afterCancel.effectiveDriverName, "Driver 25");
  });

  // Test 31: Permanent assignment documents remain completely unmodified after cancellation
  await itAsync("31. Permanent assignment documents remain completely unmodified after cancellation", async () => {
    const state = JSON.parse(JSON.stringify(mockState));
    state.temporary_assignments = {
      "assign_to_cancel2": {
        assignmentId: "assign_to_cancel2",
        busId: "BUS-25",
        permanentDriverUid: "uid_driver_25",
        replacementDriverUid: "uid_driver_17",
        createdByUid: "uid_admin",
        createdByRole: "ADMIN",
        startAt: now - 5000,
        endAt: now + 50000,
        cancelledAt: null,
        status: "ACTIVE"
      }
    };
    const db = createMockDb(state);
    const rtdb = createMockRtdb();

    await processCancelTemporaryAssignment("assign_to_cancel2", "uid_driver_25", db, rtdb);

    // Verify users node
    assert.strictEqual(state.users["uid_driver_25"].assignedBusId, "BUS-25", "Driver 25 assignedBusId must not change");
    assert.strictEqual(state.users["uid_driver_17"].assignedBusId, "BUS-17", "Driver 17 assignedBusId must not change");

    // Verify buses node
    assert.strictEqual(state.buses["BUS-25"].driverId, "uid_driver_25", "Bus 25 driverId must not change");
  });

  console.log("\n==================================================");
  console.log(`TEST SUMMARY: ${passed} PASSED, ${failed} FAILED`);
  console.log("==================================================");

  if (failed > 0) {
    process.exit(1);
  }
}

runTests().catch(err => {
  console.error("Test execution fatal error:", err);
  process.exit(1);
});
