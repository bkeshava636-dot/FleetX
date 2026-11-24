import { initializeApp, getApps, getApp, FirebaseApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, Auth } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, Firestore } from 'firebase/firestore';
import { getDatabase, connectDatabaseEmulator, Database } from 'firebase/database';
import { getFunctions, connectFunctionsEmulator, Functions } from 'firebase/functions';
import { initializeAppCheck, ReCaptchaV3Provider, AppCheck } from 'firebase/app-check';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || '***REMOVED***',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || 'college-bus-tracker-prod.firebaseapp.com',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || 'college-bus-tracker-prod',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || 'college-bus-tracker-prod.firebasestorage.app',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '1060047769100',
  appId: import.meta.env.VITE_FIREBASE_APP_ID || '1:1060047769100:android:e5aba5fc13ce18c72c6829',
  databaseURL: import.meta.env.VITE_FIREBASE_DATABASE_URL || 'https://college-bus-tracker-prod-default-rtdb.firebaseio.com',
};

// Initialize or reuse Firebase App instance
const app: FirebaseApp = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();

const auth: Auth = getAuth(app);
const db: Firestore = getFirestore(app);
const rtdb: Database = getDatabase(app);
const functions: Functions = getFunctions(app, 'asia-south1'); // Project regional functions are hosted in asia-south1

// App Check initialization (Protects public student submission writes against scraping & bots)
let appCheck: AppCheck | null = null;
const appCheckKey = import.meta.env.VITE_FIREBASE_APP_CHECK_KEY;

if (typeof window !== 'undefined' && appCheckKey) {
  try {
    appCheck = initializeAppCheck(app, {
      provider: new ReCaptchaV3Provider(appCheckKey),
      isTokenAutoRefreshEnabled: true,
    });
  } catch (err) {
    console.warn('[Firebase AppCheck] Failed to initialize App Check:', err);
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
      globalWithEmulator.__FIREBASE_EMULATOR_ATTACHED__ = true;
      console.info('[Firebase Emulator] Connected successfully.');
    } catch (e) {
      console.warn('[Firebase Emulator] Could not connect to emulators:', e);
    }
  }
}

export { app, auth, db, rtdb, functions, appCheck };
