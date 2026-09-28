const assert = require("assert");
const { sanitizeTopic, buildBusStopTopic, processBusSwapWrite } = require("./index");

async function runTests() {
  console.log("==================================================");
  console.log("RUNNING CLOUD FUNCTION BUS SWAP NOTIFICATION TESTS");
  console.log("==================================================\n");

  let testsPassed = 0;
  let testsTotal = 0;

  function it(desc, fn) {
    testsTotal++;
    try {
      fn();
      console.log(`  ✅ PASS: ${desc}`);
      testsPassed++;
    } catch (err) {
      console.error(`  ❌ FAIL: ${desc}`);
      console.error(err);
    }
  }

  async function itAsync(desc, fn) {
    testsTotal++;
    try {
      await fn();
      console.log(`  ✅ PASS: ${desc}`);
      testsPassed++;
    } catch (err) {
      console.error(`  ❌ FAIL: ${desc}`);
      console.error(err);
    }
  }

  // 1. Topic generation & sanitization
  it("generates correct topic format: bus_{originalBusId}_stop_{stopId}", () => {
    assert.strictEqual(buildBusStopTopic("BUS-25", "STOP-01"), "bus_BUS-25_stop_STOP-01");
    assert.strictEqual(buildBusStopTopic("BUS-25", "STOP-02"), "bus_BUS-25_stop_STOP-02");
    assert.strictEqual(buildBusStopTopic("BUS-25", "STOP-03"), "bus_BUS-25_stop_STOP-03");
  });

  it("sanitizes spaces and special characters in topic names", () => {
    assert.strictEqual(buildBusStopTopic("BUS 25", "STOP 02"), "bus_BUS_25_stop_STOP_02");
    assert.strictEqual(buildBusStopTopic("BUS#25", "STOP@01"), "bus_BUS_25_stop_STOP_01");
  });

  // Mock DB and Messaging
  function createMockDb({ holiday = false, routeStops = null } = {}) {
    return {
      collection: (colName) => {
        if (colName === "calendar_exceptions") {
          return {
            doc: (dateStr) => ({
              get: async () => ({
                exists: holiday,
                data: () => (holiday ? { type: "FULL_DAY_HOLIDAY", name: "Festival Holiday" } : {})
              })
            })
          };
        }
        if (colName === "routes") {
          return {
            doc: (routeId) => ({
              get: async () => ({
                exists: routeStops !== false,
                data: () => ({
                  stops: (routeStops && routeStops !== false) ? routeStops : [
                    { stopId: "STOP-01", name: "Central Bus Stand" },
                    { stopId: "STOP-02", name: "Gavi Math Circle" },
                    { stopId: "STOP-03", name: "Hosalli Cross" }
                  ]
                })
              })
            })
          };
        }
        return {};
      }
    };
  }

  function createMockMessaging() {
    const sentMessages = [];
    return {
      sentMessages,
      send: async (msg) => {
        sentMessages.push(msg);
        return "projects/test/messages/msg_123";
      }
    };
  }

  // 2. Normal Swap Notification
  await itAsync("sends targeted notifications for affected stops on original bus", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();
    let updatedFields = null;

    const change = {
      before: { exists: false },
      after: {
        exists: true,
        data: () => ({
          status: "ACTIVE",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-30",
          routeId: "ROUTE-25",
          date: "2026-09-15",
          affectedStopIds: ["STOP-01", "STOP-02"]
        }),
        ref: {
          update: async (fields) => {
            updatedFields = fields;
          }
        }
      }
    };

    const result = await processBusSwapWrite(change, { params: { swapId: "SWAP-001" } }, mockDb, mockMessaging);

    assert.strictEqual(result.success, true);
    assert.strictEqual(result.notifiedCount, 2);
    assert.strictEqual(mockMessaging.sentMessages.length, 2);

    const topics = mockMessaging.sentMessages.map(m => m.topic);
    assert(topics.includes("bus_BUS-25_stop_STOP-01"));
    assert(topics.includes("bus_BUS-25_stop_STOP-02"));
    assert(!topics.includes("bus_BUS-25_stop_STOP-03")); // Not in affected list
    assert(!topics.includes("bus_BUS-30_stop_STOP-01")); // Original bus must be used, NOT replacement bus

    // Check payload data
    const firstMsg = mockMessaging.sentMessages[0];
    assert.strictEqual(firstMsg.data.notificationType, "BUS_SWAP");
    assert.strictEqual(firstMsg.data.originalBusId, "BUS-25");
    assert.strictEqual(firstMsg.data.replacementBusId, "BUS-30");
    assert.strictEqual(firstMsg.data.status, "ACTIVE");
  });

  // 3. Deduplication / Idempotency
  await itAsync("skips already notified stops and prevents duplicate alerts", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();

    const change = {
      before: { exists: true, data: () => ({ status: "ACTIVE" }) },
      after: {
        exists: true,
        data: () => ({
          status: "ACTIVE",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-30",
          routeId: "ROUTE-25",
          date: "2026-09-15",
          affectedStopIds: ["STOP-01", "STOP-02"],
          notifiedStopIds: ["STOP-01", "STOP-02"] // Already notified
        }),
        ref: { update: async () => {} }
      }
    };

    const result = await processBusSwapWrite(change, { params: { swapId: "SWAP-001" } }, mockDb, mockMessaging);

    assert.strictEqual(result.skipped, true);
    assert.strictEqual(result.reason, "ALREADY_NOTIFIED");
    assert.strictEqual(mockMessaging.sentMessages.length, 0);
  });

  // 4. Added stop to existing swap
  await itAsync("notifies only newly added stops when swap is updated", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();

    const change = {
      before: { exists: true, data: () => ({ status: "ACTIVE", affectedStopIds: ["STOP-01"] }) },
      after: {
        exists: true,
        data: () => ({
          status: "ACTIVE",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-30",
          routeId: "ROUTE-25",
          date: "2026-09-15",
          affectedStopIds: ["STOP-01", "STOP-02"],
          notifiedStopIds: ["STOP-01"] // STOP-01 already sent, only STOP-02 should be sent
        }),
        ref: { update: async () => {} }
      }
    };

    const result = await processBusSwapWrite(change, { params: { swapId: "SWAP-001" } }, mockDb, mockMessaging);

    assert.strictEqual(result.success, true);
    assert.strictEqual(result.notifiedCount, 1);
    assert.strictEqual(mockMessaging.sentMessages.length, 1);
    assert.strictEqual(mockMessaging.sentMessages[0].topic, "bus_BUS-25_stop_STOP-02");
  });

  // 5. Holiday suppression
  await itAsync("suppresses swap notifications on full day holidays when suppressOnHoliday is set", async () => {
    const mockDb = createMockDb({ holiday: true });
    const mockMessaging = createMockMessaging();

    const change = {
      before: { exists: false },
      after: {
        exists: true,
        data: () => ({
          status: "ACTIVE",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-30",
          routeId: "ROUTE-25",
          date: "2026-10-02",
          affectedStopIds: ["STOP-01"],
          suppressOnHoliday: true
        }),
        ref: { update: async () => {} }
      }
    };

    const result = await processBusSwapWrite(change, { params: { swapId: "SWAP-001" } }, mockDb, mockMessaging);

    assert.strictEqual(result.skipped, true);
    assert.strictEqual(result.reason, "FULL_DAY_HOLIDAY");
    assert.strictEqual(mockMessaging.sentMessages.length, 0);
  });

  await itAsync("sends swap notifications on full day holidays for explicit manual bus swaps", async () => {
    const mockDb = createMockDb({ holiday: true });
    const mockMessaging = createMockMessaging();

    const change = {
      before: { exists: false },
      after: {
        exists: true,
        data: () => ({
          status: "ACTIVE",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-30",
          routeId: "ROUTE-25",
          date: "2026-10-02",
          affectedStopIds: ["STOP-01"]
        }),
        ref: { update: async () => {} }
      }
    };

    const result = await processBusSwapWrite(change, { params: { swapId: "SWAP-002" } }, mockDb, mockMessaging);

    assert.strictEqual(result.success, true);
    assert.strictEqual(mockMessaging.sentMessages.length, 1);
  });

  // 6. Invalid stop filtering
  await itAsync("filters out stops that do not belong to the route", async () => {
    const mockDb = createMockDb({
      routeStops: [
        { stopId: "STOP-01", name: "Stop 1" },
        { stopId: "STOP-02", name: "Stop 2" }
      ]
    });
    const mockMessaging = createMockMessaging();

    const change = {
      before: { exists: false },
      after: {
        exists: true,
        data: () => ({
          status: "ACTIVE",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-30",
          routeId: "ROUTE-25",
          date: "2026-09-15",
          affectedStopIds: ["STOP-01", "STOP-INVALID-99"] // STOP-INVALID-99 is invalid
        }),
        ref: { update: async () => {} }
      }
    };

    const result = await processBusSwapWrite(change, { params: { swapId: "SWAP-001" } }, mockDb, mockMessaging);

    assert.strictEqual(result.success, true);
    assert.strictEqual(result.notifiedCount, 1);
    assert.strictEqual(mockMessaging.sentMessages.length, 1);
    assert.strictEqual(mockMessaging.sentMessages[0].topic, "bus_BUS-25_stop_STOP-01");
  });

  // 7. Cancellation notification
  await itAsync("sends cancellation notifications to previously notified stops", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();

    const change = {
      before: { exists: true, data: () => ({ status: "ACTIVE" }) },
      after: {
        exists: true,
        data: () => ({
          status: "CANCELLED",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-30",
          routeId: "ROUTE-25",
          date: "2026-09-15",
          affectedStopIds: ["STOP-01", "STOP-02"],
          notifiedStopIds: ["STOP-01", "STOP-02"],
          cancellationNotified: false
        }),
        ref: { update: async () => {} }
      }
    };

    const result = await processBusSwapWrite(change, { params: { swapId: "SWAP-001" } }, mockDb, mockMessaging);

    assert.strictEqual(result.success, true);
    assert.strictEqual(result.type, "CANCELLED");
    assert.strictEqual(mockMessaging.sentMessages.length, 2);
    assert.strictEqual(mockMessaging.sentMessages[0].data.status, "CANCELLED");
    assert(mockMessaging.sentMessages[0].notification.title.includes("Cancelled"));
  });

  // 8. ALL_STOPS Scope Resolution
  await itAsync("resolves all route stops automatically when scopeType is ALL_STOPS", async () => {
    const mockDb = createMockDb({
      routeStops: [
        { stopId: "STOP-01", name: "Stop 1" },
        { stopId: "STOP-02", name: "Stop 2" },
        { stopId: "STOP-03", name: "Stop 3" }
      ]
    });
    const mockMessaging = createMockMessaging();

    const change = {
      before: { exists: false },
      after: {
        exists: true,
        data: () => ({
          status: "ACTIVE",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-30",
          routeId: "ROUTE-25",
          scopeType: "ALL_STOPS",
          affectedStopIds: [],
          date: "2026-09-15"
        }),
        ref: { update: async () => {} }
      }
    };

    const result = await processBusSwapWrite(change, { params: { swapId: "SWAP-ALL" } }, mockDb, mockMessaging);

    assert.strictEqual(result.success, true);
    assert.strictEqual(result.notifiedCount, 3);
    assert.strictEqual(mockMessaging.sentMessages.length, 3);
  });

  // 9. SELECTED_STOPS Scope
  await itAsync("notifies ONLY selected stops when scopeType is SELECTED_STOPS", async () => {
    const mockDb = createMockDb({
      routeStops: [
        { stopId: "STOP-01", name: "Stop 1" },
        { stopId: "STOP-02", name: "Stop 2" },
        { stopId: "STOP-03", name: "Stop 3" }
      ]
    });
    const mockMessaging = createMockMessaging();

    const change = {
      before: { exists: false },
      after: {
        exists: true,
        data: () => ({
          status: "ACTIVE",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-30",
          routeId: "ROUTE-25",
          scopeType: "SELECTED_STOPS",
          affectedStopIds: ["STOP-02"],
          date: "2026-09-15"
        }),
        ref: { update: async () => {} }
      }
    };

    const result = await processBusSwapWrite(change, { params: { swapId: "SWAP-SEL" } }, mockDb, mockMessaging);

    assert.strictEqual(result.success, true);
    assert.strictEqual(result.notifiedCount, 1);
    assert.strictEqual(mockMessaging.sentMessages.length, 1);
    assert.strictEqual(mockMessaging.sentMessages[0].topic, "bus_BUS-25_stop_STOP-02");
  });

  // 10. Update Replacement Bus in Active Alert
  await itAsync("re-notifies all stops when replacement bus changes on existing active swap", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();

    const change = {
      before: {
        exists: true,
        data: () => ({
          status: "ACTIVE",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-30",
          routeId: "ROUTE-25",
          scopeType: "ALL_STOPS",
          notifiedStopIds: ["STOP-01", "STOP-02"]
        })
      },
      after: {
        exists: true,
        data: () => ({
          status: "ACTIVE",
          originalBusId: "BUS-25",
          replacementBusId: "BUS-99", // Changed replacement bus!
          routeId: "ROUTE-25",
          scopeType: "ALL_STOPS",
          notifiedStopIds: ["STOP-01", "STOP-02"],
          date: "2026-09-15"
        }),
        ref: { update: async () => {} }
      }
    };

    const result = await processBusSwapWrite(change, { params: { swapId: "SWAP-UPD" } }, mockDb, mockMessaging);

    assert.strictEqual(result.success, true);
    assert.strictEqual(result.notifiedCount, 3); // Re-notifies all 3 route stops
    assert.strictEqual(mockMessaging.sentMessages.length, 3);
    assert(mockMessaging.sentMessages[0].notification.body.includes("BUS-99"));
  });

  console.log(`\n==================================================`);
  console.log(`TEST SUMMARY: ${testsPassed} / ${testsTotal} tests passed.`);
  console.log(`==================================================\n`);

  if (testsPassed !== testsTotal) {
    process.exit(1);
  }
}

runTests().catch((err) => {
  console.error("Test suite error:", err);
  process.exit(1);
});
