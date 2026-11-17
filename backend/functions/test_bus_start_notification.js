const assert = require("assert");
const { processBusStartNotification, sanitizeTopic } = require("./index");

async function runTests() {
  console.log("==================================================");
  console.log("RUNNING CLOUD FUNCTION BUS START NOTIFICATION TESTS");
  console.log("==================================================\n");

  let testsPassed = 0;
  let testsTotal = 0;

  async function it(desc, fn) {
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

  // Mock RTDB database
  function createMockDb(initialTimestamps = {}) {
    const store = { ...initialTimestamps };
    return {
      store,
      ref: (path) => ({
        once: async () => ({
          val: () => store[path] || null
        }),
        set: async (val) => {
          store[path] = val;
        }
      })
    };
  }

  // Mock FCM Messaging
  function createMockMessaging() {
    const sentMessages = [];
    return {
      sentMessages,
      send: async (msg) => {
        sentMessages.push(msg);
        return "msg_id_" + sentMessages.length;
      }
    };
  }

  // 1. Transition INACTIVE -> ACTIVE with valid coordinates
  await it("sends push notification on transition to ACTIVE with valid coordinates", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();

    const change = {
      before: {
        exists: () => true,
        val: () => ({ trackingStatus: "INACTIVE", latitude: 15.15, longitude: 76.90 })
      },
      after: {
        exists: () => true,
        val: () => ({ trackingStatus: "ACTIVE", latitude: 15.15, longitude: 76.90, busNumber: "Bus 25" })
      }
    };
    const context = { params: { busId: "BUS-25" } };

    const result = await processBusStartNotification(change, context, mockDb, mockMessaging);
    assert.strictEqual(result.success, true);
    assert.strictEqual(result.topic, "bus_start_BUS-25");
    assert.strictEqual(mockMessaging.sentMessages.length, 1);

    const sent = mockMessaging.sentMessages[0];
    assert.strictEqual(sent.topic, "bus_start_BUS-25");
    assert.strictEqual(sent.notification.title, "🚌 Bus Started");
    assert.strictEqual(sent.notification.body, "Bus 25 is now en route and tracking live.");
    assert.strictEqual(sent.data.notificationType, "BUS_STARTED");
    assert.strictEqual(sent.data.busId, "BUS-25");
    assert.strictEqual(sent.android.notification.channelId, "bus_alerts_channel");
    assert.strictEqual(sent.android.priority, "high");
  });

  // 2. Transition WAITING_FOR_GPS -> ACTIVE
  await it("sends push notification on transition from WAITING_FOR_GPS to ACTIVE", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();

    const change = {
      before: {
        exists: () => true,
        val: () => ({ trackingStatus: "WAITING_FOR_GPS", latitude: 0.0, longitude: 0.0 })
      },
      after: {
        exists: () => true,
        val: () => ({ trackingStatus: "ACTIVE", latitude: 15.155, longitude: 76.905, busNumber: "BUS-01" })
      }
    };
    const context = { params: { busId: "BUS-01" } };

    const result = await processBusStartNotification(change, context, mockDb, mockMessaging);
    assert.strictEqual(result.success, true);
    assert.strictEqual(result.topic, "bus_start_BUS-01");
    assert.strictEqual(mockMessaging.sentMessages.length, 1);
  });

  // 3. Coordinate update while already ACTIVE is skipped (no spam)
  await it("skips notification when tracking was already ACTIVE (continuous GPS fixes)", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();

    const change = {
      before: {
        exists: () => true,
        val: () => ({ trackingStatus: "ACTIVE", latitude: 15.150, longitude: 76.900 })
      },
      after: {
        exists: () => true,
        val: () => ({ trackingStatus: "ACTIVE", latitude: 15.152, longitude: 76.902 })
      }
    };
    const context = { params: { busId: "BUS-25" } };

    const result = await processBusStartNotification(change, context, mockDb, mockMessaging);
    assert.strictEqual(result.skipped, true);
    assert.strictEqual(result.reason, "ALREADY_ACTIVE");
    assert.strictEqual(mockMessaging.sentMessages.length, 0);
  });

  // 4. Invalid coordinates (0.0, 0.0) is skipped
  await it("skips notification if coordinates are 0.0, 0.0", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();

    const change = {
      before: {
        exists: () => true,
        val: () => ({ trackingStatus: "INACTIVE" })
      },
      after: {
        exists: () => true,
        val: () => ({ trackingStatus: "ACTIVE", latitude: 0.0, longitude: 0.0 })
      }
    };
    const context = { params: { busId: "BUS-25" } };

    const result = await processBusStartNotification(change, context, mockDb, mockMessaging);
    assert.strictEqual(result.skipped, true);
    assert.strictEqual(result.reason, "INVALID_COORDINATES");
    assert.strictEqual(mockMessaging.sentMessages.length, 0);
  });

  // 5. Non-ACTIVE status is skipped
  await it("skips notification if status is not ACTIVE", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();

    const change = {
      before: {
        exists: () => true,
        val: () => ({ trackingStatus: "ACTIVE" })
      },
      after: {
        exists: () => true,
        val: () => ({ trackingStatus: "INACTIVE", latitude: 15.15, longitude: 76.90 })
      }
    };
    const context = { params: { busId: "BUS-25" } };

    const result = await processBusStartNotification(change, context, mockDb, mockMessaging);
    assert.strictEqual(result.skipped, true);
    assert.strictEqual(result.reason, "NOT_ACTIVE");
    assert.strictEqual(mockMessaging.sentMessages.length, 0);
  });

  // 6. Cooldown suppresses repeated notifications within window
  await it("suppresses notification when within cooldown period", async () => {
    const now = Date.now();
    const mockDb = createMockDb({
      "/bus_notifications/BUS-25/lastStartedAt": now - 5 * 60 * 1000 // 5 minutes ago (< 30 min cooldown)
    });
    const mockMessaging = createMockMessaging();

    const change = {
      before: {
        exists: () => true,
        val: () => ({ trackingStatus: "INACTIVE" })
      },
      after: {
        exists: () => true,
        val: () => ({ trackingStatus: "ACTIVE", latitude: 15.15, longitude: 76.90 })
      }
    };
    const context = { params: { busId: "BUS-25" } };

    const result = await processBusStartNotification(change, context, mockDb, mockMessaging, 30 * 60 * 1000);
    assert.strictEqual(result.skipped, true);
    assert.strictEqual(result.reason, "COOLDOWN_ACTIVE");
    assert.strictEqual(mockMessaging.sentMessages.length, 0);
  });

  // 7. Notification allowed after cooldown expires
  await it("allows notification after cooldown period has elapsed", async () => {
    const now = Date.now();
    const mockDb = createMockDb({
      "/bus_notifications/BUS-25/lastStartedAt": now - 35 * 60 * 1000 // 35 minutes ago (> 30 min cooldown)
    });
    const mockMessaging = createMockMessaging();

    const change = {
      before: {
        exists: () => true,
        val: () => ({ trackingStatus: "INACTIVE" })
      },
      after: {
        exists: () => true,
        val: () => ({ trackingStatus: "ACTIVE", latitude: 15.15, longitude: 76.90 })
      }
    };
    const context = { params: { busId: "BUS-25" } };

    const result = await processBusStartNotification(change, context, mockDb, mockMessaging, 30 * 60 * 1000);
    assert.strictEqual(result.success, true);
    assert.strictEqual(mockMessaging.sentMessages.length, 1);
  });

  // 8. Sanitizes topic with spaces and special characters
  await it("sanitizes topic name properly for bus IDs with special characters", async () => {
    const mockDb = createMockDb();
    const mockMessaging = createMockMessaging();

    const change = {
      before: null,
      after: {
        exists: () => true,
        val: () => ({ trackingStatus: "ACTIVE", latitude: 15.15, longitude: 76.90 })
      }
    };
    const context = { params: { busId: "BUS #25 Special" } };

    const result = await processBusStartNotification(change, context, mockDb, mockMessaging);
    assert.strictEqual(result.success, true);
    assert.strictEqual(result.topic, "bus_start_BUS__25_Special");
  });

  console.log("\n==================================================");
  console.log(`TEST SUMMARY: ${testsPassed} / ${testsTotal} tests passed.`);
  console.log("==================================================");

  if (testsPassed !== testsTotal) {
    process.exit(1);
  }
}

runTests().catch((err) => {
  console.error("Test execution failed:", err);
  process.exit(1);
});
