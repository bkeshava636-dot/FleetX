/**
 * Environment Validation and Safety Hardening
 * Strictly validates that required Firebase and Google Maps environment variables
 * are supplied, prevents cross-environment pollution between staging and production,
 * and eliminates hardcoded production credentials.
 */

export type AppEnvironment = 'development' | 'staging' | 'production';

export interface FirebaseClientConfig {
  apiKey: string;
  authDomain: string;
  projectId: string;
  storageBucket: string;
  messagingSenderId: string;
  appId: string;
  databaseURL: string;
}

export interface EnvironmentValidationResult {
  isValid: boolean;
  appEnv: AppEnvironment;
  firebaseConfig?: FirebaseClientConfig;
  errors: string[];
}

export const PRODUCTION_PROJECT_ID = 'college-bus-tracker-prod';

/**
 * Resolves and normalizes the target application environment.
 */
export function resolveAppEnvironment(rawEnv?: string): AppEnvironment {
  const normalized = (rawEnv || '').trim().toLowerCase();
  if (normalized === 'production' || normalized === 'prod') return 'production';
  if (normalized === 'staging' || normalized === 'stage') return 'staging';
  if (normalized === 'development' || normalized === 'dev') return 'development';
  // Default to development if undefined or empty
  if (!normalized) return 'development';
  throw new Error(`[EnvValidator] Invalid VITE_APP_ENV='${rawEnv}'. Must be one of: 'development', 'staging', 'production'.`);
}

/**
 * Validates environment variables for safety, completeness, and isolation.
 */
export function validateEnvironment(env: Record<string, string | undefined>): EnvironmentValidationResult {
  const errors: string[] = [];

  // 1. Resolve environment
  let appEnv: AppEnvironment = 'development';
  try {
    appEnv = resolveAppEnvironment(env.VITE_APP_ENV);
  } catch (err: unknown) {
    const error = err as Error;
    errors.push(error.message);
  }

  // 2. Validate required Firebase fields (NO hardcoded production fallbacks!)
  const apiKey = env.VITE_FIREBASE_API_KEY?.trim();
  const authDomain = env.VITE_FIREBASE_AUTH_DOMAIN?.trim();
  const projectId = env.VITE_FIREBASE_PROJECT_ID?.trim();
  const storageBucket = env.VITE_FIREBASE_STORAGE_BUCKET?.trim();
  const messagingSenderId = env.VITE_FIREBASE_MESSAGING_SENDER_ID?.trim();
  const appId = env.VITE_FIREBASE_APP_ID?.trim();
  const databaseURL = env.VITE_FIREBASE_DATABASE_URL?.trim();

  const missingFields: string[] = [];
  if (!apiKey) missingFields.push('VITE_FIREBASE_API_KEY');
  if (!authDomain) missingFields.push('VITE_FIREBASE_AUTH_DOMAIN');
  if (!projectId) missingFields.push('VITE_FIREBASE_PROJECT_ID');
  if (!storageBucket) missingFields.push('VITE_FIREBASE_STORAGE_BUCKET');
  if (!messagingSenderId) missingFields.push('VITE_FIREBASE_MESSAGING_SENDER_ID');
  if (!appId) missingFields.push('VITE_FIREBASE_APP_ID');
  if (!databaseURL) missingFields.push('VITE_FIREBASE_DATABASE_URL');

  if (missingFields.length > 0) {
    errors.push(`Missing required Firebase configuration variables: ${missingFields.join(', ')}.`);
  }

  // 3. Staging and Production Cross-Environment Safeguards
  if (projectId) {
    if (appEnv === 'staging') {
      if (projectId === PRODUCTION_PROJECT_ID) {
        errors.push(`CRITICAL SECURITY VIOLATION: Staging environment (VITE_APP_ENV='staging') is configured with production project ID '${PRODUCTION_PROJECT_ID}'. Staging must use an isolated project.`);
      }
    } else if (appEnv === 'production') {
      if (projectId !== PRODUCTION_PROJECT_ID) {
        errors.push(`CRITICAL CONFIGURATION ERROR: Production environment (VITE_APP_ENV='production') cannot be linked to non-production project ID '${projectId}'. Expected '${PRODUCTION_PROJECT_ID}'.`);
      }
      if (projectId.includes('staging') || projectId.includes('test') || projectId.includes('dev')) {
        errors.push(`CRITICAL SECURITY VIOLATION: Production environment cannot use staging/test project ID '${projectId}'.`);
      }
    }
  }

  // 4. Secret Leakage Check
  // Ensure no server-side private keys or service account tokens are prefixed with VITE_
  for (const [key, value] of Object.entries(env)) {
    if (key.startsWith('VITE_')) {
      if (key.includes('PRIVATE_KEY') || key.includes('SECRET_KEY') || key.includes('SERVICE_ACCOUNT')) {
        errors.push(`SECURITY ALERT: Sensitive server secret '${key}' is exposed via VITE_ prefix. Private secrets must never be exposed to the browser.`);
      }
      if (typeof value === 'string' && value.includes('-----BEGIN PRIVATE KEY-----')) {
        errors.push(`SECURITY ALERT: Variable '${key}' contains a private RSA key. Browser bundle exposure detected.`);
      }
    }
  }

  if (errors.length > 0) {
    return {
      isValid: false,
      appEnv,
      errors
    };
  }

  return {
    isValid: true,
    appEnv,
    firebaseConfig: {
      apiKey: apiKey!,
      authDomain: authDomain!,
      projectId: projectId!,
      storageBucket: storageBucket!,
      messagingSenderId: messagingSenderId!,
      appId: appId!,
      databaseURL: databaseURL!
    },
    errors: []
  };
}

/**
 * Validates Google Maps configuration independently without blocking non-map routes.
 */
export function validateMapsConfig(env: Record<string, string | undefined>): { isValid: boolean; warning?: string } {
  const mapsApiKey = env.VITE_GOOGLE_MAPS_API_KEY?.trim();
  if (!mapsApiKey) {
    return {
      isValid: false,
      warning: 'VITE_GOOGLE_MAPS_API_KEY is missing. Interactive map components will fall back to static notice.'
    };
  }
  return { isValid: true };
}

/**
 * Throws a fatal exception if Firebase configuration is invalid or violates environment boundaries.
 */
export function getValidatedFirebaseConfig(env: Record<string, string | undefined>): FirebaseClientConfig {
  const result = validateEnvironment(env);
  if (!result.isValid || !result.firebaseConfig) {
    const header = '[FleetX Firebase Initialization Error]';
    const msg = `${header} Invalid or incomplete environment configuration:\n` + result.errors.map((e) => `  - ${e}`).join('\n');
    console.error(msg);
    throw new Error(msg);
  }
  return result.firebaseConfig;
}
