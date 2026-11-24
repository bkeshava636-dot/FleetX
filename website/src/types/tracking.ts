export type GpsDisplayState =
  | 'ACTIVE'
  | 'STALE'
  | 'WAITING_FOR_GPS'
  | 'ARRIVED_AT_CAMPUS'
  | 'INACTIVE';

/**
 * Raw RTDB telemetry payload written by Android LocationTrackingService.kt
 */
export interface LiveTelemetry {
  busId: string;
  latitude: number;
  longitude: number;
  speed?: number; // meters per second
  bearing?: number; // 0..360 degrees
  trackingStatus?: string; // 'ACTIVE', 'INACTIVE', 'WAITING_FOR_GPS', 'STALE', 'ARRIVED_AT_CAMPUS'
  mode?: string; // 'AUTO', 'MANUAL'
  routeId?: string;
  driverId?: string;
  sourceType?: string; // 'PRIMARY_DRIVER', 'SECONDARY_STUDENT_FALLBACK'
  lastUpdated: number; // epoch ms
  operatorEpoch?: number;
  temporaryAssignmentId?: string | null;
}

/**
 * Unified Fleet Bus Model: Fuses Firestore Bus specification with live RTDB telemetry
 */
export interface FleetBus {
  busId: string;
  busNumber: string;
  registrationNumber: string;
  capacity: number;
  active: boolean;
  assignedRouteId: string;
  assignedRouteName?: string;
  assignedDriverId?: string | null;
  assignedDriverName?: string | null;

  // Live Telemetry (null if bus has never transmitted)
  telemetry: LiveTelemetry | null;

  // Computed 5-state display state
  displayState: GpsDisplayState;

  // Telemetry attributes
  speedKmh: number;
  speedMps: number;
  bearingDegrees: number;
  compassDirection: string;
  lastUpdatedMs: number;
}

// Actual College Campus Coordinates: Ballari Institute of Technology & Management (BITM)
export const DEFAULT_CAMPUS_LOCATION = {
  name: 'BITM Campus, Ballari',
  lat: 15.168307,
  lng: 76.831722,
};

/**
 * Converts meters per second to kilometers per hour.
 */
export function speedMpsToKmh(mps: number): number {
  if (typeof mps !== 'number' || isNaN(mps) || mps <= 0) return 0;
  return Math.round(mps * 3.6);
}

/**
 * Converts a 0..360 degree compass bearing to a human-readable 8-point direction.
 */
export function bearingToCompassDirection(bearing: number): string {
  if (typeof bearing !== 'number' || isNaN(bearing)) return 'N/A';
  const normalized = ((bearing % 360) + 360) % 360;
  const directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
  const index = Math.round(normalized / 45) % 8;
  return directions[index];
}

/**
 * Formats elapsed time since last GPS update into a compact relative string (e.g. "4s ago", "2m ago").
 */
export function formatRelativeTime(epochMs: number, nowMs: number = Date.now()): string {
  if (!epochMs || epochMs <= 0) return 'Never';
  const elapsedSeconds = Math.max(0, Math.floor((nowMs - epochMs) / 1000));
  if (elapsedSeconds < 5) return 'Just now';
  if (elapsedSeconds < 60) return `${elapsedSeconds}s ago`;
  const elapsedMinutes = Math.floor(elapsedSeconds / 60);
  if (elapsedMinutes < 60) return `${elapsedMinutes}m ago`;
  const elapsedHours = Math.floor(elapsedMinutes / 60);
  if (elapsedHours < 24) return `${elapsedHours}h ago`;
  return `${Math.floor(elapsedHours / 24)}d ago`;
}

/**
 * Evaluates the 5-state GPS display state based on transmitted trackingStatus and 90-second freshness window.
 * Strictly respects ARRIVED_AT_CAMPUS independently of the staleness timeout.
 */
export function evaluateBusGpsState(
  telemetry: LiveTelemetry | null | undefined,
  nowMs: number = Date.now()
): GpsDisplayState {
  if (!telemetry) return 'INACTIVE';

  const status = String(telemetry.trackingStatus || 'INACTIVE').toUpperCase();

  // 1. Campus arrival status is preserved
  if (status === 'ARRIVED_AT_CAMPUS') {
    return 'ARRIVED_AT_CAMPUS';
  }

  // 2. Waiting for satellite lock
  if (status === 'WAITING_FOR_GPS') {
    return 'WAITING_FOR_GPS';
  }

  // 3. Stale signal explicitly reported
  if (status === 'STALE') {
    return 'STALE';
  }

  // 4. Active signal evaluation
  if (status === 'ACTIVE') {
    // Zero coordinates indicate awaiting satellite lock
    if (telemetry.latitude === 0.0 && telemetry.longitude === 0.0) {
      return 'WAITING_FOR_GPS';
    }
    // Missing or invalid timestamp
    if (!telemetry.lastUpdated || telemetry.lastUpdated <= 0) {
      return 'STALE';
    }
    // Independent 90-second freshness threshold
    const elapsed = Math.max(0, nowMs - telemetry.lastUpdated);
    if (elapsed > 90_000) {
      return 'STALE';
    }
    return 'ACTIVE';
  }

  return 'INACTIVE';
}
