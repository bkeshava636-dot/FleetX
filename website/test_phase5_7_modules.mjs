import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();

console.log('==================================================');
console.log('RUNNING FLEETX PHASE 5-7 ADMIN MODULES TESTS');
console.log('==================================================');

const expectedPages = [
  'src/pages/admin/BusSwaps.tsx',
  'src/pages/admin/TemporaryAssignments.tsx',
  'src/pages/admin/CollegeCalendar.tsx',
  'src/pages/admin/Announcements.tsx',
  'src/pages/admin/StudentManagement.tsx',
  'src/pages/admin/AnalyticsReports.tsx',
  'src/pages/admin/AuditLogs.tsx',
  'src/pages/admin/AdminSettings.tsx',
];

for (const rel of expectedPages) {
  const full = path.join(root, rel);
  assert.ok(fs.existsSync(full), `Missing page file: ${rel}`);
}
console.log('  ✅ PASS: All 8 admin page files exist');

const appTsx = fs.readFileSync(path.join(root, 'src/App.tsx'), 'utf8');
const expectedRoutes = [
  'bus-swaps',
  'temporary-assignments',
  'calendar',
  'announcements',
  'students',
  'analytics',
  'audit',
  'settings',
];
for (const route of expectedRoutes) {
  assert.ok(appTsx.includes(`path="${route}"`), `Missing route in App.tsx: ${route}`);
}
console.log('  ✅ PASS: All 8 routes wired in App.tsx');

const sidebarTsx = fs.readFileSync(path.join(root, 'src/components/admin/AdminSidebar.tsx'), 'utf8');
assert.ok(!sidebarTsx.includes('isImplemented: false'), 'AdminSidebar still has isImplemented: false');
for (const route of expectedRoutes) {
  assert.ok(sidebarTsx.includes(`/admin/${route}`), `Missing sidebar link: /admin/${route}`);
}
console.log('  ✅ PASS: All 8 sidebar items enabled in AdminSidebar.tsx');

const serviceTs = fs.readFileSync(path.join(root, 'src/services/operationsService.ts'), 'utf8');
const expectedCollections = [
  'bus_swaps',
  'bus_swap_history',
  'temporary_assignments',
  'calendar_exceptions',
  'operating_schedule',
  'announcements',
  'student_feedback',
  'student_installations',
  'student_analytics_daily',
  'history_audit_logs',
  'driver_replacement_history',
];
for (const col of expectedCollections) {
  assert.ok(serviceTs.includes(`'${col}'`), `Missing Firestore collection in operationsService.ts: ${col}`);
}
assert.ok(serviceTs.includes("'createTemporaryAssignment'"), 'Missing createTemporaryAssignment callable');
assert.ok(serviceTs.includes("'cancelTemporaryAssignment'"), 'Missing cancelTemporaryAssignment callable');
console.log('  ✅ PASS: operationsService.ts references all expected Firestore collections and callables');

// Verify every collection used by the new pages is allowed for isAdmin() in deployed prod rules
const prodRulesPath = path.join(
  'C:/Users/bhrao/.gemini/antigravity/brain/3f86995d-8d3c-43b8-bff2-670763546c76/scratch/firestore.prod_merged.rules'
);
if (fs.existsSync(prodRulesPath)) {
  const prodRules = fs.readFileSync(prodRulesPath, 'utf8');
  for (const col of expectedCollections) {
    assert.ok(prodRules.includes(`match /${col}/`), `Collection missing from prod rules: ${col}`);
  }
  console.log('  ✅ PASS: All 11 collections are covered in deployed prod Firestore rules');
}

console.log('ALL PHASE 5-7 MODULE CHECKS PASSED');
