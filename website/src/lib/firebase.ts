import { initializeApp, getApps, getApp, FirebaseApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, Auth } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, Firestore } from 'firebase/firestore';
import { getDatabase, connectDatabaseEmulator, Database } from 'firebase/database';
import { getFunctions, connectFunctionsEmulator, Functions } from 'firebase/functions';
import { getStorage, connectStorageEmulator, FirebaseStorage } from 'firebase/storage';
import { initializeAppCheck, ReCaptchaEnterpriseProvider, AppCheck } from 'firebase/app-check';
import { getValidatedFirebaseConfig, validateMapsConfig } from './envValidator';

// Strictly validate environment configuration (no hardcoded production fallbacks!)
const firebaseConfig = getValidatedFirebaseConfig(import.meta.env);

// Validate Google Maps independently (warning only, non-blocking for headless routes)
const mapsValidation = validateMapsConfig(import.meta.env);
if (!mapsValidation.isValid && mapsValidation.warning) {
  console.warn(`[FleetX Google Maps] ${mapsValidation.warning}`);
}

// Initialize or reuse Firebase App instance
const app: FirebaseApp = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();

const auth: Auth = getAuth(app);
const db: Firestore = getFirestore(app);
const rtdb: Database = getDatabase(app);
const functions: Functions = getFunctions(app, 'asia-south1'); // Project regional functions are hosted in asia-south1
const storage: FirebaseStorage = getStorage(app);

// App Check initialization (Protects public student submission writes against scraping & bots)
let appCheck: AppCheck | null = null;
const appCheckKey = import.meta.env.VITE_FIREBASE_APP_CHECK_KEY;
const debugToken = import.meta.env.VITE_FIREBASE_APP_CHECK_DEBUG_TOKEN;

if (typeof window !== 'undefined') {
  // 1. Development & Staging Debug Token Support
  const isStaging = import.meta.env.VITE_APP_ENV === 'staging';
  if (import.meta.env.DEV || (isStaging && debugToken)) {
    const globalWithDebug = window as unknown as { FIREBASE_APPCHECK_DEBUG_TOKEN?: boolean | string };
    if (globalWithDebug.FIREBASE_APPCHECK_DEBUG_TOKEN === undefined) {
      globalWithDebug.FIREBASE_APPCHECK_DEBUG_TOKEN = debugToken || true;
      console.info('[Firebase AppCheck] App Check debug token enabled.');
    }
  }

  // 2. App Check Provider Initialization
  const effectiveKey = appCheckKey || (debugToken ? 'debug-key-placeholder' : null);
  if (effectiveKey) {
    try {
      appCheck = initializeAppCheck(app, {
        provider: new ReCaptchaEnterpriseProvider(effectiveKey),
        isTokenAutoRefreshEnabled: true,
      });
    } catch (err) {
      console.warn('[Firebase AppCheck] Failed to initialize App Check:', err);
    }
  }
}

// Development emulator connectivity
const useEmulator = import.meta.env.VITE_USE_FIREBASE_EMULATOR === 'true';
const emulatorHost = import.meta.env.VITE_FIREBASE_EMULATOR_HOST || 'localhost';

if (useEmulator && typeof window !== 'undefined') {
  // Avoid re-attaching emulators on hot reloads
  const globalWithEmulator = window as unknown as { __FIREBASE_EMULATOR_ATTACHED__?: boolean };
  if (!globalWithEmulator.__FIREBASE_EMULATOR_ATTACHED__) {
    console.info(`[Firebase Emulator] Connecting to local emulator on ${emulatorHost}...`);
    try {
      connectAuthEmulator(auth, `http://${emulatorHost}:9099`, { disableWarnings: true });
      connectFirestoreEmulator(db, emulatorHost, 8080);
      connectDatabaseEmulator(rtdb, emulatorHost, 9000);
      connectFunctionsEmulator(functions, emulatorHost, 5001);
      connectStorageEmulator(storage, emulatorHost, 9199);
      globalWithEmulator.__FIREBASE_EMULATOR_ATTACHED__ = true;
      console.info('[Firebase Emulator] Connected successfully.');
    } catch (e) {
      console.warn('[Firebase Emulator] Could not connect to emulators:', e);
    }
  }
}

export { app, auth, db, rtdb, functions, storage, appCheck };
