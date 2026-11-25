/**
 * Automated Environment Safety & Isolation Test Suite
 * Tests envValidator.ts logic:
 * 1. Missing Firebase configuration errors
 * 2. Missing project ID errors
 * 3. Staging configured correctly
 * 4. Staging accidentally configured with production project ID (CRITICAL GUARD)
 * 5. Production configured correctly
 * 6. Production configured with staging/test project ID (CRITICAL GUARD)
 * 7. Invalid VITE_APP_ENV values
 * 8. Server secret leakage detection
 * 9. Google Maps configuration validation
 */

import assert from 'node:assert';
import {
  validateEnvironment,
  resolveAppEnvironment,
  validateMapsConfig,
  getValidatedFirebaseConfig,
  PRODUCTION_PROJECT_ID
} from './src/lib/envValidator.ts';

console.log('==================================================');
console.log('RUNNING FLEETX ENVIRONMENT VALIDATION TESTS');
console.log('==================================================\n');

// Valid baseline configs
const validProdConfig = {
  VITE_APP_ENV: 'production',
  VITE_FIREBASE_API_KEY: 'mock-prod-api-key',
  VITE_FIREBASE_AUTH_DOMAIN: 'college-bus-tracker-prod.firebaseapp.com',
  VITE_FIREBASE_PROJECT_ID: 'college-bus-tracker-prod',
  VITE_FIREBASE_STORAGE_BUCKET: 'college-bus-tracker-prod.firebasestorage.app',
  VITE_FIREBASE_MESSAGING_SENDER_ID: '1060047769100',
  VITE_FIREBASE_APP_ID: '1:1060047769100:web:mockappid',
  VITE_FIREBASE_DATABASE_URL: 'https://college-bus-tracker-prod-default-rtdb.firebaseio.com',
  VITE_GOOGLE_MAPS_API_KEY: 'mock-maps-key'
};

const validStagingConfig = {
  VITE_APP_ENV: 'staging',
  VITE_FIREBASE_API_KEY: 'mock-staging-api-key',
  VITE_FIREBASE_AUTH_DOMAIN: 'fleetx-staging.firebaseapp.com',
  VITE_FIREBASE_PROJECT_ID: 'fleetx-staging',
  VITE_FIREBASE_STORAGE_BUCKET: 'fleetx-staging.firebasestorage.app',
  VITE_FIREBASE_MESSAGING_SENDER_ID: '202020202020',
  VITE_FIREBASE_APP_ID: '1:202020202020:web:stagingappid',
  VITE_FIREBASE_DATABASE_URL: 'https://fleetx-staging-default-rtdb.firebaseio.com',
  VITE_GOOGLE_MAPS_API_KEY: 'mock-staging-maps-key'
};

// --------------------------------------------------------------------------
// Test Suite 1: Missing Firebase Configuration
// --------------------------------------------------------------------------
console.log('--- Test Suite 1: Missing Configuration Errors ---');

const emptyResult = validateEnvironment({});
assert.strictEqual(emptyResult.isValid, false);
assert.ok(emptyResult.errors.some((e) => e.includes('Missing required Firebase configuration variables')));
assert.ok(emptyResult.errors.some((e) => e.includes('VITE_FIREBASE_API_KEY')));
assert.ok(emptyResult.errors.some((e) => e.includes('VITE_FIREBASE_PROJECT_ID')));
console.log('  ✅ PASS: 1.1 Completely empty environment rejected with exhaustive list of missing variables');

const missingProjectIdResult = validateEnvironment({
  ...validProdConfig,
  VITE_FIREBASE_PROJECT_ID: ''
});
assert.strictEqual(missingProjectIdResult.isValid, false);
assert.ok(missingProjectIdResult.errors.some((e) => e.includes('VITE_FIREBASE_PROJECT_ID')));
console.log('  ✅ PASS: 1.2 Missing VITE_FIREBASE_PROJECT_ID cleanly rejected');

// --------------------------------------------------------------------------
// Test Suite 2: Staging Configuration & Anti-Pollution Guardrails
// --------------------------------------------------------------------------
console.log('\n--- Test Suite 2: Staging Isolation Guardrails ---');

const stagingOk = validateEnvironment(validStagingConfig);
assert.strictEqual(stagingOk.isValid, true);
assert.strictEqual(stagingOk.appEnv, 'staging');
assert.strictEqual(stagingOk.firebaseConfig?.projectId, 'fleetx-staging');
console.log('  ✅ PASS: 2.1 Staging environment correctly configured and validated');

// Staging accidentally pointing to production project
const stagingWithProdProject = validateEnvironment({
  ...validStagingConfig,
  VITE_FIREBASE_PROJECT_ID: 'college-bus-tracker-prod'
});
assert.strictEqual(stagingWithProdProject.isValid, false);
assert.ok(
  stagingWithProdProject.errors.some((e) => e.includes('CRITICAL SECURITY VIOLATION: Staging environment')),
  'Must block staging from using production project ID'
);
console.log('  ✅ PASS: 2.2 Staging with production project ID blocked with critical security error');

// --------------------------------------------------------------------------
// Test Suite 3: Production Configuration & Anti-Pollution Guardrails
// --------------------------------------------------------------------------
console.log('\n--- Test Suite 3: Production Validation Guardrails ---');

const prodOk = validateEnvironment(validProdConfig);
assert.strictEqual(prodOk.isValid, true);
assert.strictEqual(prodOk.appEnv, 'production');
assert.strictEqual(prodOk.firebaseConfig?.projectId, 'college-bus-tracker-prod');
console.log('  ✅ PASS: 3.1 Production environment correctly configured and validated');

// Production configured with staging project ID
const prodWithStagingProject = validateEnvironment({
  ...validProdConfig,
  VITE_FIREBASE_PROJECT_ID: 'fleetx-staging'
});
assert.strictEqual(prodWithStagingProject.isValid, false);
assert.ok(
  prodWithStagingProject.errors.some((e) => e.includes('CRITICAL CONFIGURATION ERROR: Production environment')),
  'Must block production from using non-production project'
);
console.log('  ✅ PASS: 3.2 Production configured with non-prod project blocked with critical error');

// --------------------------------------------------------------------------
// Test Suite 4: Invalid Environment Identifiers
// --------------------------------------------------------------------------
console.log('\n--- Test Suite 4: Environment Identifier Validation ---');

assert.strictEqual(resolveAppEnvironment('development'), 'development');
assert.strictEqual(resolveAppEnvironment('dev'), 'development');
assert.strictEqual(resolveAppEnvironment('staging'), 'staging');
assert.strictEqual(resolveAppEnvironment('stage'), 'staging');
assert.strictEqual(resolveAppEnvironment('production'), 'production');
assert.strictEqual(resolveAppEnvironment('prod'), 'production');
assert.strictEqual(resolveAppEnvironment(''), 'development');
assert.strictEqual(resolveAppEnvironment(undefined), 'development');

assert.throws(
  () => resolveAppEnvironment('qa-testing'),
  /Invalid VITE_APP_ENV='qa-testing'/
);
console.log('  ✅ PASS: 4.1 Environment identifier normalization and rejection of unknown environments verified');

// --------------------------------------------------------------------------
// Test Suite 5: Secret Leakage & Google Maps Checks
// --------------------------------------------------------------------------
console.log('\n--- Test Suite 5: Secret Leakage & Google Maps Checks ---');

// Attempting to inject a service account key into Vite
const leakedSecretConfig = {
  ...validStagingConfig,
  VITE_FIREBASE_SERVICE_ACCOUNT_KEY: '{"private_key": "secret"}',
  VITE_SERVER_PRIVATE_KEY: '-----BEGIN PRIVATE KEY-----\nMIIE...'
};
const leakedResult = validateEnvironment(leakedSecretConfig);
assert.strictEqual(leakedResult.isValid, false);
assert.ok(leakedResult.errors.some((e) => e.includes('Sensitive server secret')));
assert.ok(leakedResult.errors.some((e) => e.includes('private RSA key')));
console.log('  ✅ PASS: 5.1 Server secret and private key leakage into browser bundle detected and blocked');

// Google maps check
const mapsWithoutKey = validateMapsConfig({});
assert.strictEqual(mapsWithoutKey.isValid, false);
assert.ok(mapsWithoutKey.warning?.includes('VITE_GOOGLE_MAPS_API_KEY is missing'));

const mapsWithKey = validateMapsConfig({ VITE_GOOGLE_MAPS_API_KEY: 'valid-key' });
assert.strictEqual(mapsWithKey.isValid, true);
console.log('  ✅ PASS: 5.2 Google Maps configuration validation verified independently');

// --------------------------------------------------------------------------
// Test Suite 6: getValidatedFirebaseConfig Throw Behavior
// --------------------------------------------------------------------------
console.log('\n--- Test Suite 6: Startup Guardrail Execution ---');

assert.throws(
  () => getValidatedFirebaseConfig({}),
  /FleetX Firebase Initialization Error/
);

const validatedConfig = getValidatedFirebaseConfig(validProdConfig);
assert.strictEqual(validatedConfig.projectId, 'college-bus-tracker-prod');
console.log('  ✅ PASS: 6.1 getValidatedFirebaseConfig throws descriptive startup error on invalid config');

console.log('\n==================================================');
console.log('ALL ENVIRONMENT VALIDATION TESTS PASSED (100%)');
console.log('==================================================');
