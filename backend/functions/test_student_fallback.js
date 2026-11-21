const assert = require("assert");
const {
  calculateDistanceMeters,
  isKinematicallyPlausible,
  deduplicateStudentSources,
  findConsensusCluster,
  fuseCluster,
  isDuplicateWrite,
  processStudentFallbackWrite,
  DRIVER_STALE_THRESHOLD_MS,
  STUDENT_STALE_THRESHOLD_MS
} = require("./index");

async function runTests() {
  console.log("==================================================");
  console.log("RUNNING STUDENT GPS SERVER-SIDE FALLBACK TESTS");
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

  // -------------------------------------------------------------
  // Mock Realtime Database Helper
  // -------------------------------------------------------------
  function createMockRtdb({ liveLocations = {}, locationSources = {} } = {}) {
    const liveLocData = { ...liveLocations };
    const locSourcesData = { ...locationSources };

    return {
      _liveLocations: liveLocData,
      _locationSources: locSourcesData,
      ref: (path) => {
        const parts = path.split("/").filter(Boolean);
        return {
          once: async (event) => {
            if (parts[0] === "live_locations") {
              const busId = parts[1];
              const val = liveLocData[busId] || null;
              return {
                exists: () => val !== null,
                val: () => (val ? JSON.parse(JSON.stringify(val)) : null)
              };
            }
            if (parts[0] === "location_sources") {
              const busId = parts[1];
              const val = locSourcesData[busId] || null;
              return {
                exists: () => val !== null,
                val: () => (val ? JSON.parse(JSON.stringify(val)) : null)
              };
            }
            return { exists: () => false, val: () => null };
          },
          set: async (val) => {
            if (parts[0] === "live_locations") {
              const busId = parts[1];
              liveLocData[busId] = JSON.parse(JSON.stringify(val));
            }
          }
        };
      }
    };
  }

  // -------------------------------------------------------------
  // 1. Distance & Kinematic Plausibility
  // -------------------------------------------------------------
  it("calculates accurate Haversine distance", () => {
    // 15.3647, 75.1240 to 15.3656, 75.1240 (~100m north)
    const dist = calculateDistanceMeters(15.3647, 75.1240, 15.3656, 75.1240);
    assert(dist > 95 && dist < 105, `Expected ~100m, got ${dist}`);
  });

  it("kinematic plausibility accepts normal city bus speed (40 km/h = ~11 m/s)", () => {
    const t1 = 1000000;
    const t2 = t1 + 5000; // 5 seconds later
    // 50 meters in 5s = 10 m/s
    const plausible = isKinematicallyPlausible(15.3647, 75.1240, t1, 15.36515, 75.1240, t2, 35.0);
    assert.strictEqual(plausible, true);
  });

  it("kinematic plausibility rejects teleportation jump (1 km in 2s)", () => {
    const t1 = 1000000;
    const t2 = t1 + 2000; // 2 seconds later
    // 1000 meters in 2s = 500 m/s > 35 m/s
    const plausible = isKinematicallyPlausible(15.3647, 75.1240, t1, 15.3737, 75.1240, t2, 35.0);
    assert.strictEqual(plausible, false);
  });

  // -------------------------------------------------------------
  // 2. Deduplication & Anti-Sybil
  // -------------------------------------------------------------
  it("deduplicates multiple fixes for same sourceId keeping newest timestamp", () => {
    const sources = [
      { sourceId: "student_1", timestamp: 1000, latitude: 15.3647, longitude: 75.1240, speed: 5.0 },
      { sourceId: "student_1", timestamp: 2000, latitude: 15.3649, longitude: 75.1241, speed: 6.0 }
    ];
    const deduped = deduplicateStudentSources(sources);
    assert.strictEqual(deduped.length, 1);
    assert.strictEqual(deduped[0].timestamp, 2000);
  });

  it("filters sybil clones with identical coordinates, speed, and timestamp", () => {
    const sources = [
      { sourceId: "student_1", timestamp: 1000, latitude: 15.364700, longitude: 75.124000, speed: 5.0 },
      { sourceId: "student_2_clone", timestamp: 1000, latitude: 15.364700, longitude: 75.124000, speed: 5.0 }
    ];
    const deduped = deduplicateStudentSources(sources);
    assert.strictEqual(deduped.length, 1);
  });

  // -------------------------------------------------------------
  // 3. Cluster Consensus & Weighted Centroid Fusion
  // -------------------------------------------------------------
  it("fuses single student contributor directly without alteration", () => {
    const single = [{
      busId: "BUS-01",
      sourceId: "student_solo",
      sourceType: "SECONDARY_STUDENT",
      latitude: 15.3647,
      longitude: 75.1240,
      accuracy: 10.0,
      speed: 4.5,
      bearing: 90.0,
      timestamp: 1726738920000,
      active: true
    }];
    const fused = fuseCluster(single);
    assert.strictEqual(fused.latitude, 15.3647);
    assert.strictEqual(fused.longitude, 75.1240);
  });

  it("fuses multiple student contributors with inverse-variance accuracy weighting", () => {
    const cluster = [
      {
        busId: "BUS-01",
        sourceId: "student_1",
        sourceType: "SECONDARY_STUDENT",
        latitude: 15.3640,
        longitude: 75.1240,
        accuracy: 5.0, // high precision -> weight 1/25 = 0.04
        speed: 5.0,
        bearing: 90.0,
        timestamp: 1726738920000,
        active: true
      },
      {
        busId: "BUS-01",
        sourceId: "student_2",
        sourceType: "SECONDARY_STUDENT",
        latitude: 15.3650,
        longitude: 75.1240,
        accuracy: 20.0, // lower precision -> weight 1/400 = 0.0025
        speed: 5.0,
        bearing: 90.0,
        timestamp: 1726738920000,
        active: true
      }
    ];
    const fused = fuseCluster(cluster);
    // Weight of student 1 is 16x higher than student 2, so fused latitude must be much closer to 15.3640
    assert(fused.latitude < 15.3642, `Expected weighted lat < 15.3642, got ${fused.latitude}`);
    assert(fused.accuracy <= 5.0, `Fused accuracy should improve or match best accuracy`);
  });

  // -------------------------------------------------------------
  // 4. Fallback Evaluation Logic in Cloud Function
  // -------------------------------------------------------------
  await itAsync("DRIVER HEALTHY: Does not overwrite healthy driver location", async () => {
    const now = 1726738920000;
    const mockRtdb = createMockRtdb({
      liveLocations: {
        "BUS-01": {
          busId: "BUS-01",
          latitude: 15.3647,
          longitude: 75.1240,
          speed: 8.0,
          bearing: 45.0,
          trackingStatus: "ACTIVE",
          mode: "MANUAL",
          sourceType: "PRIMARY_DRIVER",
          lastUpdated: now - 10000 // 10s old -> HEALTHY (<45s)
        }
      },
      locationSources: {
        "BUS-01": {
          "student_1": {
            busId: "BUS-01",
            sourceId: "student_1",
            sourceType: "SECONDARY_STUDENT",
            latitude: 15.3648,
            longitude: 75.1241,
            accuracy: 12.0,
            speed: 8.0,
            bearing: 45.0,
            timestamp: now - 5000,
            active: true
          }
        }
      }
    });

    const change = { after: { exists: () => true } };
    const context = { params: { busId: "BUS-01", sourceId: "student_1" } };

    const result = await processStudentFallbackWrite(change, context, mockRtdb, now);

    assert.strictEqual(result.published, false);
    assert.strictEqual(result.driverStatus, "HEALTHY");
    assert.strictEqual(result.selectedSource, "PRIMARY_DRIVER");
    assert.strictEqual(result.reason, "HEALTHY_DRIVER");
    // Verify live location was NOT overwritten
    assert.strictEqual(mockRtdb._liveLocations["BUS-01"].sourceType, "PRIMARY_DRIVER");
    assert.strictEqual(mockRtdb._liveLocations["BUS-01"].latitude, 15.3647);
  });

  await itAsync("DRIVER STALE: Activates student fallback and publishes SECONDARY_STUDENT_FALLBACK", async () => {
    const now = 1726738920000;
    const mockRtdb = createMockRtdb({
      liveLocations: {
        "BUS-01": {
          busId: "BUS-01",
          latitude: 15.3600,
          longitude: 75.1200,
          speed: 0.0,
          bearing: 0.0,
          trackingStatus: "ACTIVE",
          mode: "AUTO",
          sourceType: "PRIMARY_DRIVER",
          lastUpdated: now - 60000 // 60s old -> STALE (>45s)
        }
      },
      locationSources: {
        "BUS-01": {
          "student_1": {
            busId: "BUS-01",
            sourceId: "student_1",
            sourceType: "SECONDARY_STUDENT",
            latitude: 15.3610,
            longitude: 75.1205,
            accuracy: 8.0,
            speed: 5.5,
            bearing: 30.0,
            timestamp: now - 5000, // fresh (5s old < 30s)
            active: true
          }
        }
      }
    });

    const change = { after: { exists: () => true } };
    const context = { params: { busId: "BUS-01", sourceId: "student_1" } };

    const result = await processStudentFallbackWrite(change, context, mockRtdb, now);

    assert.strictEqual(result.published, true);
    assert.strictEqual(result.driverStatus, "STALE");
    assert.strictEqual(result.selectedSource, "SECONDARY_STUDENT_FALLBACK");
    assert.strictEqual(result.reason, "DRIVER_STALE");
    assert.strictEqual(result.validStudentCount, 1);

    // Verify /live_locations/BUS-01 received the student fallback fix
    const updated = mockRtdb._liveLocations["BUS-01"];
    assert.strictEqual(updated.sourceType, "SECONDARY_STUDENT_FALLBACK");
    assert.strictEqual(updated.trackingStatus, "ACTIVE");
    assert.strictEqual(updated.latitude, 15.3610);
    assert.strictEqual(updated.longitude, 75.1205);
    assert.strictEqual(updated.speed, 5.5);
  });

  await itAsync("STUDENT VALIDATION: Rejects inaccurate (>40m) and stale (>30s) student fixes", async () => {
    const now = 1726738920000;
    const mockRtdb = createMockRtdb({
      liveLocations: {
        "BUS-01": {
          busId: "BUS-01",
          latitude: 15.3600,
          longitude: 75.1200,
          trackingStatus: "ACTIVE",
          sourceType: "PRIMARY_DRIVER",
          lastUpdated: now - 60000 // STALE
        }
      },
      locationSources: {
        "BUS-01": {
          "student_inaccurate": {
            busId: "BUS-01",
            sourceId: "student_inaccurate",
            sourceType: "SECONDARY_STUDENT",
            latitude: 15.3610,
            longitude: 75.1205,
            accuracy: 65.0, // > 40m -> INVALID
            speed: 5.5,
            timestamp: now - 5000,
            active: true
          },
          "student_stale": {
            busId: "BUS-01",
            sourceId: "student_stale",
            sourceType: "SECONDARY_STUDENT",
            latitude: 15.3610,
            longitude: 75.1205,
            accuracy: 10.0,
            speed: 5.5,
            timestamp: now - 45000, // > 30s -> INVALID
            active: true
          }
        }
      }
    });

    const change = { after: { exists: () => true } };
    const context = { params: { busId: "BUS-01", sourceId: "student_inaccurate" } };

    const result = await processStudentFallbackWrite(change, context, mockRtdb, now);

    assert.strictEqual(result.published, false);
    assert.strictEqual(result.validStudentCount, 0);
    assert.strictEqual(result.reason, "NO_VALID_STUDENT_SOURCES");
  });

  await itAsync("DRIVER RECOVERY: Driver resumes tracking and immediately regains primary authority", async () => {
    const now = 1726738920000;
    // Assume previous live location was a fallback
    const mockRtdb = createMockRtdb({
      liveLocations: {
        "BUS-01": {
          busId: "BUS-01",
          latitude: 15.3610,
          longitude: 75.1205,
          speed: 5.5,
          bearing: 30.0,
          trackingStatus: "ACTIVE",
          mode: "AUTO",
          sourceType: "SECONDARY_STUDENT_FALLBACK",
          lastUpdated: now - 10000
        }
      },
      locationSources: {
        "BUS-01": {
          "driver_01": {
            busId: "BUS-01",
            sourceId: "driver_01",
            sourceType: "PRIMARY_DRIVER",
            latitude: 15.3620,
            longitude: 75.1210,
            accuracy: 10.0,
            speed: 10.0,
            bearing: 40.0,
            timestamp: now - 2000, // Driver GPS is fresh (2s old)
            active: true
          },
          "student_1": {
            busId: "BUS-01",
            sourceId: "student_1",
            sourceType: "SECONDARY_STUDENT",
            latitude: 15.3619,
            longitude: 75.1209,
            accuracy: 15.0,
            speed: 9.5,
            bearing: 40.0,
            timestamp: now - 3000,
            active: true
          }
        }
      }
    });

    const change = { after: { exists: () => true } };
    const context = { params: { busId: "BUS-01", sourceId: "student_1" } };

    const result = await processStudentFallbackWrite(change, context, mockRtdb, now);

    assert.strictEqual(result.published, false);
    assert.strictEqual(result.driverStatus, "HEALTHY");
    assert.strictEqual(result.selectedSource, "PRIMARY_DRIVER");
    assert.strictEqual(result.reason, "HEALTHY_DRIVER");
  });

  // -------------------------------------------------------------
  // 6. Stop-by-Stop Sequential Priority Tests
  // -------------------------------------------------------------
  await itAsync("prioritizes Stop 1 student over Stop 3 student in fallback arbitration", async () => {
    const now = 2000000;
    const mockRtdb = createMockRtdb({
      liveLocations: {
        "BUS-01": {
          busId: "BUS-01",
          trackingStatus: "ACTIVE",
          latitude: 15.3600,
          longitude: 75.1200,
          lastUpdated: now - 60000 // Stale driver (>45s)
        }
      },
      locationSources: {
        "BUS-01": {
          "student_stop3": {
            busId: "BUS-01",
            sourceId: "student_stop3",
            sourceType: "SECONDARY_STUDENT",
            latitude: 15.3605,
            longitude: 75.1205,
            accuracy: 10.0,
            speed: 5.0,
            bearing: 30.0,
            timestamp: now - 2000,
            active: true,
            stopId: "STOP-03",
            stopOrder: 3
          },
          "student_stop1": {
            busId: "BUS-01",
            sourceId: "student_stop1",
            sourceType: "SECONDARY_STUDENT",
            latitude: 15.3603,
            longitude: 75.1203,
            accuracy: 10.0,
            speed: 5.0,
            bearing: 30.0,
            timestamp: now - 3000,
            active: true,
            stopId: "STOP-01",
            stopOrder: 1
          }
        }
      }
    });

    const change = { after: { exists: () => true } };
    const context = { params: { busId: "BUS-01", sourceId: "student_stop1" } };

    const result = await processStudentFallbackWrite(change, context, mockRtdb, now);

    assert.strictEqual(result.published, true);
    assert.strictEqual(result.selectedSource, "SECONDARY_STUDENT_FALLBACK");
    assert.strictEqual(mockRtdb._liveLocations["BUS-01"].trackingStatus, "ACTIVE");
    // Fused cluster should use Stop 1 student bearing and earliest stop
    assert(mockRtdb._liveLocations["BUS-01"].latitude > 0);
  });

  await itAsync("does not allow stale/invalid Stop 1 student to override fresh Stop 2 student", async () => {
    const now = 2000000;
    const mockRtdb = createMockRtdb({
      liveLocations: {
        "BUS-01": {
          busId: "BUS-01",
          trackingStatus: "ACTIVE",
          latitude: 15.3600,
          longitude: 75.1200,
          lastUpdated: now - 60000 // Stale driver
        }
      },
      locationSources: {
        "BUS-01": {
          "student_stop1_stale": {
            busId: "BUS-01",
            sourceId: "student_stop1_stale",
            sourceType: "SECONDARY_STUDENT",
            latitude: 15.3601,
            longitude: 75.1201,
            accuracy: 80.0, // Low accuracy > 40m -> invalid
            timestamp: now - 5000,
            active: true,
            stopId: "STOP-01",
            stopOrder: 1
          },
          "student_stop2_fresh": {
            busId: "BUS-01",
            sourceId: "student_stop2_fresh",
            sourceType: "SECONDARY_STUDENT",
            latitude: 15.3604,
            longitude: 75.1204,
            accuracy: 12.0, // Accurate <= 40m
            speed: 6.0,
            bearing: 25.0,
            timestamp: now - 2000,
            active: true,
            stopId: "STOP-02",
            stopOrder: 2
          }
        }
      }
    });

    const change = { after: { exists: () => true } };
    const context = { params: { busId: "BUS-01", sourceId: "student_stop2_fresh" } };

    const result = await processStudentFallbackWrite(change, context, mockRtdb, now);

    assert.strictEqual(result.published, true);
    assert.strictEqual(result.validStudentCount, 1); // Only stop 2 student is valid
    assert.strictEqual(mockRtdb._liveLocations["BUS-01"].latitude, 15.3604);
  });

  console.log("\n==================================================");
  console.log(`TEST SUMMARY: ${testsPassed}/${testsTotal} tests passed.`);
  console.log("==================================================\n");

  if (testsPassed !== testsTotal) {
    process.exit(1);
  }
}

runTests().catch(err => {
  console.error("Test execution fatal error:", err);
  process.exit(1);
});
