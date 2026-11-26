const assert = require("assert");
const { processBusSwapWrite, syncBusSwapMappingToRtdb, reconcileExpiredBusSwaps } = require("./index");

async function runTests() {
  console.log("==================================================");
  console.log("RUNNING M1.2 BUS SWAP RTDB SYNCHRONIZATION TESTS");
  console.log("==================================================\n");

  let passed = 0;
  let total = 0;

  async function itAsync(desc, fn) {
    total++;
    try {
      await fn();
      console.log(`  ✅ PASS: ${desc}`);
      passed++;
    } catch (err) {
      console.error(`  ❌ FAIL: ${desc}`);
      console.error(err);
      process.exitCode = 1;
    }
  }

  function createMockRtdb(initial = {}) {
    const store = { ...initial };
    const counters = { setCalls: 0, removeCalls: 0 };
    return {
      store,
      counters,
      ref: (path) => ({
        once: async () => ({
          val: () => (Object.prototype.hasOwnProperty.call(store, path) ? store[path] : null)
        }),
        set: async (val) => {
          counters.setCalls++;
          store[path] = val;
        },
        remove: async () => {
          counters.removeCalls++;
          delete store[path];
        }
      })
    };
  }

  function createMockDb({ activeSwapsByBus = {}, routeStops = [{ stopId: "STOP-01", name: "Main Gate" }] } = {}) {
    return {
      collection: (col) => {
        if (col === "bus_swaps") {
          let targetBus = null;
          let targetStatus = null;
          const chain = {
            where: (field, _op, val) => {
              if (field === "originalBusId") targetBus = val;
              if (field === "status") targetStatus = val;
              return chain;
            },
            get: async () => {
              const list = targetBus
                ? (activeSwapsByBus[targetBus] || [])
                : Object.values(activeSwapsByBus).flat();
              const filtered = list.filter(
                (d) => !targetStatus || String(d.status || "ACTIVE").toUpperCase() === targetStatus
              );
              return {
                forEach: (cb) => filtered.forEach((d) => cb({ id: d.swapId, data: () => d }))
              };
            }
          };
          return chain;
        }
        if (col === "calendar_exceptions") {
          return { doc: () => ({ get: async () => ({ exists: false }) }) };
        }
        if (col === "routes") {
          return { doc: () => ({ get: async () => ({ exists: true, data: () => ({ stops: routeStops }) }) }) };
        }
        return {};
      }
    };
  }

  function createMockMessaging() {
    const sent = [];
    return { sent, send: async (msg) => { sent.push(msg); return "msg_1"; } };
  }

  await itAsync("Website-created ACTIVE swap creates RTDB bus_swap_mappings/{originalBusId}", async () => {
    const rtdb = createMockRtdb();
    const db = createMockDb();
    const messaging = createMockMessaging();
    const change = {
      before: { exists: false },
      after: {
        exists: true,
        data: () => ({
          swapId: "swap_web_1",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-23",
          routeId: "R-25",
          scopeType: "ALL_STOPS",
          status: "ACTIVE",
          createdAt: 1700000000000
        }),
        ref: { update: async () => {} }
      }
    };

    const res = await processBusSwapWrite(change, { params: { swapId: "swap_web_1" } }, db, messaging, rtdb);
    assert.strictEqual(res.success, true);
    assert.strictEqual(messaging.sent.length, 1);
    const mapping = rtdb.store["bus_swap_mappings/BUS-25"];
    assert.ok(mapping, "Expected RTDB mapping to be created");
    assert.strictEqual(mapping.originalBusId, "BUS-25");
    assert.strictEqual(mapping.replacementBusId, "BUS-23");
    assert.strictEqual(mapping.active, true);
    assert.strictEqual(mapping.swapId, "swap_web_1");
  });

  await itAsync("Duplicate invocation / Android already-synced mapping is idempotent (no redundant RTDB write)", async () => {
    const rtdb = createMockRtdb({
      "bus_swap_mappings/BUS-25": {
        originalBusId: "BUS-25",
        replacementBusId: "BUS-23",
        active: true,
        swapId: "swap_android_1",
        updatedAt: 1700000000000
      }
    });
    const db = createMockDb();
    await syncBusSwapMappingToRtdb(db, rtdb, "swap_android_1", null, {
      swapId: "swap_android_1",
      originalBusId: "BUS-25",
      replacementBusId: "BUS-23",
      status: "ACTIVE"
    });
    assert.strictEqual(rtdb.counters.setCalls, 0);
    assert.strictEqual(rtdb.counters.removeCalls, 0);
  });

  await itAsync("Resolving a swap removes RTDB bus_swap_mappings/{originalBusId}", async () => {
    const rtdb = createMockRtdb({
      "bus_swap_mappings/BUS-25": {
        originalBusId: "BUS-25",
        replacementBusId: "BUS-23",
        active: true,
        swapId: "swap_web_1"
      }
    });
    const db = createMockDb();
    const messaging = createMockMessaging();
    const change = {
      before: { exists: true, data: () => ({ swapId: "swap_web_1", originalBusId: "BUS-25", replacementBusId: "BUS-23", status: "ACTIVE" }) },
      after: { exists: true, data: () => ({ swapId: "swap_web_1", originalBusId: "BUS-25", replacementBusId: "BUS-23", status: "RESOLVED" }) }
    };

    const res = await processBusSwapWrite(change, { params: { swapId: "swap_web_1" } }, db, messaging, rtdb);
    assert.strictEqual(res.skipped, true);
    assert.strictEqual(rtdb.store["bus_swap_mappings/BUS-25"], undefined);
    assert.strictEqual(rtdb.counters.removeCalls, 1);
  });

  await itAsync("Cancelling a swap removes RTDB mapping and still sends cancellation FCM", async () => {
    const rtdb = createMockRtdb({
      "bus_swap_mappings/BUS-25": {
        originalBusId: "BUS-25",
        replacementBusId: "BUS-23",
        active: true,
        swapId: "swap_web_1"
      }
    });
    const db = createMockDb();
    const messaging = createMockMessaging();
    const change = {
      before: { exists: true, data: () => ({ swapId: "swap_web_1", originalBusId: "BUS-25", replacementBusId: "BUS-23", status: "ACTIVE" }) },
      after: {
        exists: true,
        data: () => ({
          swapId: "swap_web_1",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-23",
          routeId: "R-25",
          status: "CANCELLED",
          notifiedStopIds: ["STOP-01"]
        }),
        ref: { update: async () => {} }
      }
    };

    const res = await processBusSwapWrite(change, { params: { swapId: "swap_web_1" } }, db, messaging, rtdb);
    assert.strictEqual(res.type, "CANCELLED");
    assert.strictEqual(messaging.sent.length, 1);
    assert.strictEqual(rtdb.store["bus_swap_mappings/BUS-25"], undefined);
  });

  await itAsync("Expired swap does not create or leave stale RTDB mapping", async () => {
    const rtdb = createMockRtdb({
      "bus_swap_mappings/BUS-25": {
        originalBusId: "BUS-25",
        replacementBusId: "BUS-23",
        active: true,
        swapId: "swap_exp"
      }
    });
    const db = createMockDb();
    await syncBusSwapMappingToRtdb(
      db,
      rtdb,
      "swap_exp",
      null,
      {
        swapId: "swap_exp",
        originalBusId: "BUS-25",
        replacementBusId: "BUS-23",
        status: "ACTIVE",
        expiresAt: 1000
      },
      2000
    );
    assert.strictEqual(rtdb.store["bus_swap_mappings/BUS-25"], undefined);
  });

  await itAsync("Deleting swap doc removes RTDB mapping when no other active swap exists", async () => {
    const rtdb = createMockRtdb({
      "bus_swap_mappings/BUS-25": {
        originalBusId: "BUS-25",
        replacementBusId: "BUS-23",
        active: true,
        swapId: "swap_del"
      }
    });
    const db = createMockDb();
    const messaging = createMockMessaging();
    const change = {
      before: { exists: true, data: () => ({ swapId: "swap_del", originalBusId: "BUS-25", replacementBusId: "BUS-23", status: "ACTIVE" }) },
      after: { exists: false }
    };

    await processBusSwapWrite(change, { params: { swapId: "swap_del" } }, db, messaging, rtdb);
    assert.strictEqual(rtdb.store["bus_swap_mappings/BUS-25"], undefined);
  });

  await itAsync("Cancelling one swap preserves another active swap on the same originalBusId", async () => {
    const rtdb = createMockRtdb({
      "bus_swap_mappings/BUS-25": {
        originalBusId: "BUS-25",
        replacementBusId: "BUS-23",
        active: true,
        swapId: "swap_1"
      }
    });
    const db = createMockDb({
      activeSwapsByBus: {
        "BUS-25": [
          { swapId: "swap_2", originalBusId: "BUS-25", replacementBusId: "BUS-19", status: "ACTIVE", createdAt: 1700000005000 }
        ]
      }
    });
    await syncBusSwapMappingToRtdb(db, rtdb, "swap_1", null, {
      swapId: "swap_1",
      originalBusId: "BUS-25",
      replacementBusId: "BUS-23",
      status: "CANCELLED"
    });
    const mapping = rtdb.store["bus_swap_mappings/BUS-25"];
    assert.ok(mapping, "Expected fallback to remaining active swap");
    assert.strictEqual(mapping.swapId, "swap_2");
    assert.strictEqual(mapping.replacementBusId, "BUS-19");
  });

  // -------------------------------------------------------------------------
  // Scheduled Bus Swap Expiry Reconciliation Tests
  // -------------------------------------------------------------------------
  function createParentAwareMockRtdb(initialMappings = {}) {
    const mappings = { ...initialMappings };
    const counters = { setCalls: 0, removeCalls: 0 };
    return {
      mappings,
      counters,
      ref: (path) => {
        if (path === "bus_swap_mappings") {
          return {
            once: async () => ({ val: () => ({ ...mappings }) }),
            get: async () => ({ val: () => ({ ...mappings }) })
          };
        }
        const busId = path.replace("bus_swap_mappings/", "");
        return {
          set: async (val) => {
            counters.setCalls++;
            mappings[busId] = val;
          },
          remove: async () => {
            counters.removeCalls++;
            delete mappings[busId];
          }
        };
      }
    };
  }

  await itAsync("Reconcile: active unexpired swap remains mapped with 0 redundant writes", async () => {
    const rtdb = createParentAwareMockRtdb({
      "BUS-25": { originalBusId: "BUS-25", replacementBusId: "BUS-23", active: true, swapId: "swap_ok" }
    });
    const db = createMockDb({
      activeSwapsByBus: {
        "BUS-25": [{ swapId: "swap_ok", originalBusId: "BUS-25", replacementBusId: "BUS-23", status: "ACTIVE", expiresAt: 5000 }]
      }
    });
    const res = await reconcileExpiredBusSwaps({ db, rtdb, now: 2000 });
    assert.strictEqual(res.unchanged, 1);
    assert.strictEqual(rtdb.counters.setCalls, 0);
    assert.strictEqual(rtdb.counters.removeCalls, 0);
    assert.strictEqual(rtdb.mappings["BUS-25"].swapId, "swap_ok");
  });

  await itAsync("Reconcile: expired swap mapping is removed", async () => {
    const rtdb = createParentAwareMockRtdb({
      "BUS-25": { originalBusId: "BUS-25", replacementBusId: "BUS-23", active: true, swapId: "swap_old" }
    });
    const db = createMockDb({
      activeSwapsByBus: {
        "BUS-25": [{ swapId: "swap_old", originalBusId: "BUS-25", replacementBusId: "BUS-23", status: "ACTIVE", expiresAt: 1000 }]
      }
    });
    const res = await reconcileExpiredBusSwaps({ db, rtdb, now: 2000 });
    assert.strictEqual(res.removed, 1);
    assert.strictEqual(rtdb.mappings["BUS-25"], undefined);
  });

  await itAsync("Reconcile: expired swap promotes another valid active swap on the same bus", async () => {
    const rtdb = createParentAwareMockRtdb({
      "BUS-25": { originalBusId: "BUS-25", replacementBusId: "BUS-23", active: true, swapId: "swap_old" }
    });
    const db = createMockDb({
      activeSwapsByBus: {
        "BUS-25": [
          { swapId: "swap_old", originalBusId: "BUS-25", replacementBusId: "BUS-23", status: "ACTIVE", expiresAt: 1000, createdAt: 100 },
          { swapId: "swap_valid", originalBusId: "BUS-25", replacementBusId: "BUS-19", status: "ACTIVE", expiresAt: 9000, createdAt: 200 }
        ]
      }
    });
    const res = await reconcileExpiredBusSwaps({ db, rtdb, now: 2000 });
    assert.strictEqual(res.replaced, 1);
    assert.strictEqual(rtdb.mappings["BUS-25"].swapId, "swap_valid");
    assert.strictEqual(rtdb.mappings["BUS-25"].replacementBusId, "BUS-19");
  });

  await itAsync("Reconcile: handles multiple expired swaps and repeated runs are idempotent", async () => {
    const rtdb = createParentAwareMockRtdb({
      "BUS-10": { originalBusId: "BUS-10", replacementBusId: "BUS-11", active: true, swapId: "exp_1" },
      "BUS-12": { originalBusId: "BUS-12", replacementBusId: "BUS-13", active: true, swapId: "exp_2" },
      "BUS-14": { originalBusId: "BUS-14", replacementBusId: "BUS-15", active: true, swapId: "keep_1" }
    });
    const db = createMockDb({
      activeSwapsByBus: {
        "BUS-10": [{ swapId: "exp_1", originalBusId: "BUS-10", replacementBusId: "BUS-11", status: "ACTIVE", expiresAt: 500 }],
        "BUS-12": [{ swapId: "exp_2", originalBusId: "BUS-12", replacementBusId: "BUS-13", status: "ACTIVE", expiresAt: 600 }],
        "BUS-14": [{ swapId: "keep_1", originalBusId: "BUS-14", replacementBusId: "BUS-15", status: "ACTIVE", expiresAt: 9000 }]
      }
    });

    const first = await reconcileExpiredBusSwaps({ db, rtdb, now: 2000 });
    assert.strictEqual(first.removed, 2);
    assert.strictEqual(first.unchanged, 1);
    assert.strictEqual(rtdb.mappings["BUS-10"], undefined);
    assert.strictEqual(rtdb.mappings["BUS-12"], undefined);
    assert.strictEqual(rtdb.mappings["BUS-14"].swapId, "keep_1");

    const second = await reconcileExpiredBusSwaps({ db, rtdb, now: 2000 });
    assert.strictEqual(second.removed, 0);
    assert.strictEqual(second.replaced, 0);
    assert.strictEqual(second.unchanged, 1);
  });

  await itAsync("Reconcile: repairs missing RTDB mapping for a valid active Firestore swap", async () => {
    const rtdb = createParentAwareMockRtdb({});
    const db = createMockDb({
      activeSwapsByBus: {
        "BUS-30": [
          {
            swapId: "swap_missing_rtdb",
            originalBusId: "BUS-30",
            replacementBusId: "BUS-31",
            status: "ACTIVE",
            expiresAt: 9000,
            createdAt: 500
          }
        ]
      }
    });

    const res = await reconcileExpiredBusSwaps({ db, rtdb, now: 2000 });
    assert.strictEqual(res.created, 1);
    assert.ok(rtdb.mappings["BUS-30"], "Expected missing RTDB mapping to be repaired");
    assert.strictEqual(rtdb.mappings["BUS-30"].swapId, "swap_missing_rtdb");
    assert.strictEqual(rtdb.mappings["BUS-30"].replacementBusId, "BUS-31");
    assert.strictEqual(rtdb.mappings["BUS-30"].active, true);
  });

  console.log(`\nM1.2 TEST SUMMARY: ${passed}/${total} PASSED`);
}

runTests();
