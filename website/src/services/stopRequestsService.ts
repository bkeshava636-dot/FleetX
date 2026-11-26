import {
  collection,
  doc,
  onSnapshot,
  orderBy,
  query,
  runTransaction,
  DocumentData,
  QuerySnapshot,
} from 'firebase/firestore';
import { db } from '../lib/firebase';
import { BusStop } from '../types/admin';

export type StopRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface StopRequest {
  requestId: string;
  driverUid: string;
  driverId: string;
  driverName: string;
  busId: string;
  routeId: string;
  proposedStopName: string;
  latitude: number;
  longitude: number;
  status: StopRequestStatus;
  createdAt: number;
  reviewedAt: number | null;
  reviewedBy: string | null;
  adminNote: string | null;
  rejectionReason: string | null;
  finalStopName: string | null;
  finalLatitude: number | null;
  finalLongitude: number | null;
}

export interface ApproveStopRequestInput {
  requestId: string;
  finalStopName: string;
  finalLatitude: number;
  finalLongitude: number;
  adminNote?: string | null;
  reviewedBy: string;
}

export interface RejectStopRequestInput {
  requestId: string;
  rejectionReason?: string | null;
  reviewedBy: string;
}

function toTimestampMillis(value: unknown, fallback: number): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (
    value &&
    typeof value === 'object' &&
    'toMillis' in value &&
    typeof (value as { toMillis: () => number }).toMillis === 'function'
  ) {
    return (value as { toMillis: () => number }).toMillis();
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function toNullableTimestampMillis(value: unknown): number | null {
  if (value === null || value === undefined) return null;
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (
    value &&
    typeof value === 'object' &&
    'toMillis' in value &&
    typeof (value as { toMillis: () => number }).toMillis === 'function'
  ) {
    return (value as { toMillis: () => number }).toMillis();
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function normalizeStatus(raw: unknown): StopRequestStatus {
  if (raw === 'APPROVED' || raw === 'REJECTED' || raw === 'PENDING') {
    return raw;
  }
  return 'PENDING';
}

/**
 * Parses a raw Firestore `/stop_requests/{requestId}` document into a typed StopRequest.
 */
export function parseStopRequestDocument(
  docId: string,
  data: DocumentData
): StopRequest {
  const safeData = data ?? {};
  const lat = Number(safeData.latitude);
  const lng = Number(safeData.longitude);
  const finalLat =
    safeData.finalLatitude !== null && safeData.finalLatitude !== undefined
      ? Number(safeData.finalLatitude)
      : null;
  const finalLng =
    safeData.finalLongitude !== null && safeData.finalLongitude !== undefined
      ? Number(safeData.finalLongitude)
      : null;

  return {
    requestId: String(safeData.requestId || docId),
    driverUid: String(safeData.driverUid || ''),
    driverId: String(safeData.driverId || ''),
    driverName: String(safeData.driverName || ''),
    busId: String(safeData.busId || ''),
    routeId: String(safeData.routeId || ''),
    proposedStopName: String(safeData.proposedStopName || ''),
    latitude: Number.isFinite(lat) ? lat : 0,
    longitude: Number.isFinite(lng) ? lng : 0,
    status: normalizeStatus(safeData.status),
    createdAt: toTimestampMillis(safeData.createdAt, 0),
    reviewedAt: toNullableTimestampMillis(safeData.reviewedAt),
    reviewedBy: safeData.reviewedBy ? String(safeData.reviewedBy) : null,
    adminNote: safeData.adminNote ? String(safeData.adminNote) : null,
    rejectionReason: safeData.rejectionReason ? String(safeData.rejectionReason) : null,
    finalStopName: safeData.finalStopName ? String(safeData.finalStopName) : null,
    finalLatitude: finalLat !== null && Number.isFinite(finalLat) ? finalLat : null,
    finalLongitude: finalLng !== null && Number.isFinite(finalLng) ? finalLng : null,
  };
}

/**
 * Validates final stop name and WGS84 coordinates (non-zero, finite).
 */
export function validateStopRequestApprovalInput(
  finalStopName: string,
  finalLatitude: number,
  finalLongitude: number
): { valid: boolean; reason?: string } {
  if (typeof finalStopName !== 'string' || !finalStopName.trim()) {
    return { valid: false, reason: 'Final stop name must not be empty.' };
  }
  if (
    typeof finalLatitude !== 'number' ||
    typeof finalLongitude !== 'number' ||
    !Number.isFinite(finalLatitude) ||
    !Number.isFinite(finalLongitude)
  ) {
    return { valid: false, reason: 'Latitude and Longitude must be finite numbers.' };
  }
  if (finalLatitude < -90 || finalLatitude > 90) {
    return { valid: false, reason: 'Latitude must be between -90 and 90 degrees.' };
  }
  if (finalLongitude < -180 || finalLongitude > 180) {
    return { valid: false, reason: 'Longitude must be between -180 and 180 degrees.' };
  }
  if (finalLatitude === 0 || finalLongitude === 0) {
    return { valid: false, reason: 'Latitude and Longitude must be non-zero GPS coordinates.' };
  }
  return { valid: true };
}

/**
 * Builds an updated route stops array by appending a new active, visible BusStop
 * while preserving all existing stops and their order.
 */
export function buildAppendedBusStopList(
  existingStops: BusStop[],
  params: {
    stopId: string;
    name: string;
    latitude: number;
    longitude: number;
  }
): BusStop[] {
  const safeExisting = Array.isArray(existingStops) ? existingStops : [];
  const maxOrder = safeExisting.reduce((max, stop) => {
    const orderVal = typeof stop?.order === 'number' && Number.isFinite(stop.order) ? stop.order : 0;
    return orderVal > max ? orderVal : max;
  }, 0);

  const nextOrder = maxOrder + 1;
  const newStop: BusStop = {
    stopId: params.stopId,
    name: params.name.trim(),
    latitude: params.latitude,
    longitude: params.longitude,
    order: nextOrder,
    active: true,
    visibleOnMap: true,
  };

  return [...safeExisting, newStop];
}

function generateUniqueStopId(existingStops: BusStop[], requestId: string, now: number): string {
  const existingIds = new Set(existingStops.map((s) => s?.stopId).filter(Boolean));
  const cleanPrefix = requestId.replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 8) || String(now);
  let candidate = `STOP-${cleanPrefix}`;
  if (!existingIds.has(candidate)) {
    return candidate;
  }
  candidate = `STOP-${cleanPrefix}-${now}`;
  return candidate;
}

/**
 * Subscribes to real-time updates on `stop_requests` ordered by `createdAt` descending.
 */
export function subscribeToStopRequests(
  onData: (requests: StopRequest[]) => void,
  onError?: (err: Error) => void
): () => void {
  const colRef = collection(db, 'stop_requests');
  const q = query(colRef, orderBy('createdAt', 'desc'));

  return onSnapshot(
    q,
    (snapshot: QuerySnapshot<DocumentData>) => {
      try {
        const requests = snapshot.docs.map((docSnap) =>
          parseStopRequestDocument(docSnap.id, docSnap.data())
        );
        onData(requests);
      } catch (err) {
        console.error('[stopRequestsService] Failed parsing stop requests snapshot:', err);
        if (onError && err instanceof Error) {
          onError(err);
        }
      }
    },
    (error) => {
      console.error('[stopRequestsService] Firestore listener error:', error);
      if (onError) {
        onError(error);
      }
    }
  );
}

/**
 * Atomically approves a PENDING stop request and appends the new BusStop to `routes/{routeId}.stops`.
 */
export async function approveStopRequest(
  input: ApproveStopRequestInput
): Promise<{ success: boolean; stopId: string; routeId: string }> {
  const requestId = input.requestId.trim();
  const reviewedBy = input.reviewedBy.trim();
  const finalStopName = input.finalStopName.trim();
  const adminNote = input.adminNote?.trim() || null;

  if (!requestId) {
    throw new Error('Request ID is required.');
  }
  if (!reviewedBy) {
    throw new Error('Reviewer ID is required.');
  }

  const validation = validateStopRequestApprovalInput(
    finalStopName,
    input.finalLatitude,
    input.finalLongitude
  );
  if (!validation.valid) {
    throw new Error(validation.reason || 'Invalid stop approval input.');
  }

  const requestRef = doc(db, 'stop_requests', requestId);

  return await runTransaction(db, async (transaction) => {
    const reqSnap = await transaction.get(requestRef);
    if (!reqSnap.exists()) {
      throw new Error(`Stop request ${requestId} not found.`);
    }

    const reqData = reqSnap.data() || {};
    if (reqData.status !== 'PENDING') {
      throw new Error(`Stop request ${requestId} has already been reviewed (${reqData.status}).`);
    }

    const routeId = typeof reqData.routeId === 'string' ? reqData.routeId.trim() : '';
    if (!routeId) {
      throw new Error(`Stop request ${requestId} does not specify a valid routeId.`);
    }

    const routeRef = doc(db, 'routes', routeId);
    const routeSnap = await transaction.get(routeRef);
    if (!routeSnap.exists()) {
      throw new Error(`Route ${routeId} not found.`);
    }

    const routeData = routeSnap.data() || {};
    const existingStops: BusStop[] = Array.isArray(routeData.stops) ? routeData.stops : [];
    const now = Date.now();
    const newStopId = generateUniqueStopId(existingStops, requestId, now);

    const updatedStops = buildAppendedBusStopList(existingStops, {
      stopId: newStopId,
      name: finalStopName,
      latitude: input.finalLatitude,
      longitude: input.finalLongitude,
    });

    transaction.update(routeRef, {
      stops: updatedStops,
      updatedAt: now,
    });

    transaction.update(requestRef, {
      status: 'APPROVED',
      reviewedAt: now,
      reviewedBy,
      finalStopName,
      finalLatitude: input.finalLatitude,
      finalLongitude: input.finalLongitude,
      adminNote,
    });

    return {
      success: true,
      stopId: newStopId,
      routeId,
    };
  });
}

/**
 * Atomically rejects a PENDING stop request without modifying any route document.
 */
export async function rejectStopRequest(
  input: RejectStopRequestInput
): Promise<{ success: boolean }> {
  const requestId = input.requestId.trim();
  const reviewedBy = input.reviewedBy.trim();
  const rejectionReason = input.rejectionReason?.trim() || null;

  if (!requestId) {
    throw new Error('Request ID is required.');
  }
  if (!reviewedBy) {
    throw new Error('Reviewer ID is required.');
  }

  const requestRef = doc(db, 'stop_requests', requestId);

  return await runTransaction(db, async (transaction) => {
    const reqSnap = await transaction.get(requestRef);
    if (!reqSnap.exists()) {
      throw new Error(`Stop request ${requestId} not found.`);
    }

    const reqData = reqSnap.data() || {};
    if (reqData.status !== 'PENDING') {
      throw new Error(`Stop request ${requestId} has already been reviewed (${reqData.status}).`);
    }

    transaction.update(requestRef, {
      status: 'REJECTED',
      reviewedAt: Date.now(),
      reviewedBy,
      rejectionReason,
    });

    return { success: true };
  });
}
