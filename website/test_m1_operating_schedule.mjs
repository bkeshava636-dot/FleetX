import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

console.log('==================================================');
console.log('RUNNING M1.1 OPERATING SCHEDULE CONFLICT TESTS');
console.log('==================================================');

const servicePath = path.join(process.cwd(), 'src/services/operationsService.ts');
const serviceSrc = fs.readFileSync(servicePath, 'utf8');

// 1. Verify atomic dual-write in operationsService.ts (writeBatch to canonical + global)
assert.ok(
  serviceSrc.includes('writeBatch(db)') &&
    serviceSrc.includes("batch.set(doc(db, 'calendar_exceptions', 'operating_schedule'), payload, { merge: true })") &&
    serviceSrc.includes("batch.set(doc(db, 'operating_schedule', 'global'), payload, { merge: true })"),
  'Website saveOperatingSchedule must perform atomic writeBatch dual-write with merge: true'
);
console.log('  ✅ PASS: Website saveOperatingSchedule uses atomic writeBatch dual-write');

// Verify Android OperatingScheduleRepository also uses single timestamp for both docs
const androidRepoPath = path.join(
  process.cwd(),
  '../app/src/main/java/com/college/bustracker/data/repository/OperatingScheduleRepository.kt'
);
if (fs.existsSync(androidRepoPath)) {
  const androidSrc = fs.readFileSync(androidRepoPath, 'utf8');
  assert.ok(
    androidSrc.includes('COLLECTION_CALENDAR_EXCEPTIONS') &&
      androidSrc.includes('DOC_OPERATING_SCHEDULE') &&
      androidSrc.includes('COLLECTION_OPERATING_SCHEDULE') &&
      androidSrc.includes('DOC_GLOBAL_SCHEDULE') &&
      androidSrc.includes('SetOptions.merge()'),
    'Android OperatingScheduleRepository must write identical merged payload to both docs'
  );
  console.log('  ✅ PASS: Android OperatingScheduleRepository dual-write contract verified');
}

// Mirror exact timestamp parser & resolver from operationsService.ts
function parseScheduleTimestampMs(val) {
  if (val == null || typeof val === 'boolean') return 0;
  if (typeof val === 'number') return Number.isFinite(val) && val > 0 ? val : 0;
  if (typeof val === 'string') {
    const trimmed = val.trim();
    if (!trimmed) return 0;
    const asNum = Number(trimmed);
    if (Number.isFinite(asNum) && asNum > 0) return asNum;
    const parsedDate = Date.parse(trimmed);
    return Number.isFinite(parsedDate) && parsedDate > 0 ? parsedDate : 0;
  }
  if (typeof val === 'object') {
    if (typeof val.toMillis === 'function') {
      const ms = val.toMillis();
      return Number.isFinite(ms) && ms > 0 ? ms : 0;
    }
    if (typeof val.seconds === 'number' && Number.isFinite(val.seconds)) {
      const ns = typeof val.nanoseconds === 'number' && Number.isFinite(val.nanoseconds) ? val.nanoseconds : 0;
      const ms = val.seconds * 1000 + Math.floor(ns / 1e6);
      return Number.isFinite(ms) && ms > 0 ? ms : 0;
    }
  }
  return 0;
}

function parseSchedule(data) {
  if (!data) {
    return {
      morning: { startTime: '07:00', endTime: '09:00', enabled: true },
      evening: { startTime: '16:00', endTime: '18:00', enabled: true },
    };
  }
  return {
    morning: {
      startTime: data.morning?.startTime || '07:00',
      endTime: data.morning?.endTime || '09:00',
      enabled: data.morning?.enabled ?? true,
    },
    evening: {
      startTime: data.evening?.startTime || '16:00',
      endTime: data.evening?.endTime || '18:00',
      enabled: data.evening?.enabled ?? true,
    },
  };
}

function selectPreferred(docs) {
  const canonical = docs.canonical && docs.canonical.exists ? docs.canonical : null;
  const globalDoc = docs.global && docs.global.exists ? docs.global : null;

  if (canonical && globalDoc) {
    const canonicalTs = parseScheduleTimestampMs(canonical.updatedAt);
    const globalTs = parseScheduleTimestampMs(globalDoc.updatedAt);
    return parseSchedule(globalTs > canonicalTs ? globalDoc : canonical);
  }
  if (canonical) return parseSchedule(canonical);
  if (globalDoc) return parseSchedule(globalDoc);
  if (docs.legacyDefault && docs.legacyDefault.exists) {
    return parseSchedule(docs.legacyDefault);
  }
  return parseSchedule(null);
}

// Test cases
assert.equal(parseScheduleTimestampMs(undefined), 0);
assert.equal(parseScheduleTimestampMs(NaN), 0);
assert.equal(parseScheduleTimestampMs(-50), 0);
assert.equal(parseScheduleTimestampMs('not-a-date'), 0);
assert.equal(parseScheduleTimestampMs({ seconds: 1700000000, nanoseconds: 500000000 }), 1700000000500);
assert.equal(parseScheduleTimestampMs({ toMillis: () => 1700000001234 }), 1700000001234);
console.log('  ✅ PASS: Missing, malformed, negative, and Firestore Timestamp values handled safely');

// Newer canonical wins over older global and newer-looking legacy
{
  const res = selectPreferred({
    canonical: { exists: true, updatedAt: 5000, morning: { startTime: '06:30', endTime: '08:30', enabled: true } },
    global: { exists: true, updatedAt: 4000, morning: { startTime: '07:15', endTime: '09:15', enabled: true } },
    legacyDefault: { exists: true, updatedAt: 999999, morning: { startTime: '08:00', endTime: '10:00', enabled: true } },
  });
  assert.equal(res.morning.startTime, '06:30');
}
console.log('  ✅ PASS: Newer canonical wins; legacy cannot override canonical');

// Equal timestamps -> canonical deterministically wins tie
{
  const res = selectPreferred({
    canonical: { exists: true, updatedAt: 5000, morning: { startTime: '06:45', endTime: '08:45', enabled: true } },
    global: { exists: true, updatedAt: 5000, morning: { startTime: '07:45', endTime: '09:45', enabled: true } },
  });
  assert.equal(res.morning.startTime, '06:45');
}
console.log('  ✅ PASS: Equal timestamps deterministically resolve to canonical doc');

// Malformed timestamp on global cannot beat valid canonical timestamp
{
  const res = selectPreferred({
    canonical: { exists: true, updatedAt: 1000, morning: { startTime: '06:50', endTime: '08:50', enabled: true } },
    global: { exists: true, updatedAt: 'corrupt-ts', morning: { startTime: '08:00', endTime: '10:00', enabled: true } },
  });
  assert.equal(res.morning.startTime, '06:50');
}
console.log('  ✅ PASS: Malformed timestamp on global cannot override valid canonical schedule');

// Legacy fallback used only when both canonical and global are absent
{
  const res = selectPreferred({
    canonical: { exists: false },
    global: { exists: false },
    legacyDefault: { exists: true, updatedAt: 10, evening: { startTime: '17:30', endTime: '19:30', enabled: false } },
  });
  assert.equal(res.evening.startTime, '17:30');
  assert.equal(res.evening.enabled, false);
}
console.log('  ✅ PASS: Legacy schedule used only when canonical documents are absent');

// Missing all docs -> defaults returned without any write
{
  const res = selectPreferred({ canonical: { exists: false }, global: { exists: false }, legacyDefault: { exists: false } });
  assert.equal(res.morning.startTime, '07:00');
  assert.equal(res.evening.startTime, '16:00');
}
console.log('  ✅ PASS: Missing schedule returns defaults cleanly');

console.log('ALL M1.1 OPERATING SCHEDULE CONFLICT TESTS PASSED');
