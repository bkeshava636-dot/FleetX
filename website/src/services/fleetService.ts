/**
 * Fleet Management Service
 * Handles bus registration, updates, route assignments, driver assignments,
 * activation toggling, and RTDB operational reconciliation.
 * Milestone 5: Fleet & Driver Management
 */

import {
  collection,
  doc,
  getDoc,
  getDocs,
  updateDoc,
  onSnapshot,
  DocumentData,
  QuerySnapshot,
  Unsubscribe
} from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { db, functions } from '../lib/firebase';
import {
  BusRecord,
  CreateBusInput,
  UpdateBusDetailsInput,
  SetBusDriverAssignmentResponse,
  ToggleBusActiveResponse,
  ReconcileBusRtdbResponse,
  RegisterBusResponse,
  UpdateBusDetailsResponse
} from '../types/fleet';

/**
 * Natural comparison function for bus identifiers (e.g. BUS-01, BUS-02, BUS-10).
 */
export function naturalBusSort(a: BusRecord, b: BusRecord): number {
  const numA = parseInt((a.busNumber || a.busId).replace(/\D/g, ''), 10) || 0;
  const numB = parseInt((b.busNumber || b.busId).replace(/\D/g, ''), 10) || 0;
  if (numA !== numB) return numA - numB;
  return (a.busNumber || a.busId).localeCompare(b.busNumber || b.busId);
}

/**
 * Parses raw Firestore document data into a strongly-typed BusRecord model.
 */
export function parseBusDocument(docId: string, data: DocumentData): BusRecord {
  return {
    busId: docId,
    busNumber: String(data.busNumber || docId).trim(),
    registrationNumber: String(data.registrationNumber || '').trim().toUpperCase(),
    capacity: typeof data.capacity === 'number' ? data.capacity : Number(data.capacity) || 50,
    active: data.active !== false,
    routeId: data.routeId ? String(data.routeId).trim() : null,
    driverId: data.driverId ? String(data.driverId).trim() : null,
    driverName: data.driverName ? String(data.driverName).trim() : null,
    assignmentVersion: typeof data.assignmentVersion === 'number' ? data.assignmentVersion : 0,
    assignmentEpoch: typeof data.assignmentEpoch === 'number' ? data.assignmentEpoch : 0,
    rtdbSyncStatus: data.rtdbSyncStatus || 'SYNCED',
    rtdbSyncError: data.rtdbSyncError || null,
    rtdbSyncLastAttempt: typeof data.rtdbSyncLastAttempt === 'number' ? data.rtdbSyncLastAttempt : null,
    rtdbSyncRetryCount: typeof data.rtdbSyncRetryCount === 'number' ? data.rtdbSyncRetryCount : 0,
    createdAt: typeof data.createdAt === 'number' ? data.createdAt : undefined,
    updatedAt: typeof data.updatedAt === 'number' ? data.updatedAt : undefined
  };
}

/**
 * Fetches all buses from Firestore once, sorted by natural bus number.
 */
export async function getBuses(): Promise<BusRecord[]> {
  const busesCol = collection(db, 'buses');
  const snapshot = await getDocs(busesCol);
  const buses: BusRecord[] = [];
  snapshot.forEach((d) => {
    buses.push(parseBusDocument(d.id, d.data()));
  });
  return buses.sort(naturalBusSort);
}

/**
 * Subscribes to real-time bus collection updates.
 * Returns an unsubscribe callback for proper cleanup on component unmount.
 */
export function subscribeToBuses(
  onUpdate: (buses: BusRecord[]) => void,
  onError?: (error: Error) => void
): Unsubscribe {
  const busesCol = collection(db, 'buses');
  return onSnapshot(
    busesCol,
    (snapshot: QuerySnapshot<DocumentData>) => {
      const buses: BusRecord[] = [];
      snapshot.forEach((d) => {
        buses.push(parseBusDocument(d.id, d.data()));
      });
      onUpdate(buses.sort(naturalBusSort));
    },
    (err: Error) => {
      console.error('[fleetService] Bus subscription listener error:', err);
      if (onError) onError(err);
    }
  );
}

/**
 * Fetches a single bus by ID.
 */
export async function getBusById(busId: string): Promise<BusRecord | null> {
  if (!busId) return null;
  const docRef = doc(db, 'buses', busId);
  const snap = await getDoc(docRef);
  if (!snap.exists()) return null;
  return parseBusDocument(snap.id, snap.data());
}

/**
 * Validates bus input fields (bus number, registration number, capacity).
 */
export function validateBusInput(input: CreateBusInput | UpdateBusDetailsInput): string | null {
  if (input.busNumber !== undefined) {
    const cleanNum = input.busNumber.trim();
    if (!cleanNum) return 'Bus number is required.';
    if (!/^[a-zA-Z0-9_-]{1,10}$/.test(cleanNum)) {
      return 'Bus number must be 1-10 alphanumeric characters (e.g. 01, 25, B-01).';
    }
  }

  if (input.registrationNumber !== undefined) {
    const cleanReg = input.registrationNumber.trim().toUpperCase();
    if (!cleanReg) return 'Registration number is required.';
    if (cleanReg.length < 5 || cleanReg.length > 20) {
      return 'Registration number must be between 5 and 20 characters (e.g. KA-34-A-1234).';
    }
  }

  if (input.capacity !== undefined) {
    const cap = Number(input.capacity);
    if (isNaN(cap) || cap < 1 || cap > 120 || !Number.isInteger(cap)) {
      return 'Bus seating capacity must be a whole number between 1 and 120.';
    }
  }

  return null;
}

/**
 * Registers a new bus via backend Cloud Function registerBusAccount.
 * Atomically enforces uniqueness of busNumber and registrationNumber using transactions
 * and index locks, eliminating client-side race conditions and blind overwrites.
 */
export async function createBus(input: CreateBusInput): Promise<BusRecord> {
  const validationErr = validateBusInput(input);
  if (validationErr) throw new Error(validationErr);

  const cleanBusNum = input.busNumber.trim();
  const cleanReg = input.registrationNumber.trim().toUpperCase();

  const callable = httpsCallable<CreateBusInput, RegisterBusResponse>(
    functions,
    'registerBusAccount'
  );

  try {
    const res = await callable({
      busNumber: cleanBusNum,
      registrationNumber: cleanReg,
      capacity: Number(input.capacity),
      routeId: input.routeId ? input.routeId.trim() : null
    });

    const now = Date.now();
    return {
      busId: res.data.busId,
      busNumber: res.data.busNumber,
      registrationNumber: res.data.registrationNumber,
      capacity: res.data.capacity,
      active: res.data.active,
      routeId: res.data.routeId,
      driverId: null,
      driverName: null,
      assignmentVersion: 0,
      assignmentEpoch: now,
      rtdbSyncStatus: 'SYNCED',
      rtdbSyncError: null,
      rtdbSyncLastAttempt: now,
      rtdbSyncRetryCount: 0,
      createdAt: now,
      updatedAt: now
    };
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error('[fleetService] Failed to register bus:', error);
    throw new Error(error.message || 'Failed to register bus.');
  }
}

/**
 * Updates bus details (registration number, capacity, routeId) via Cloud Function updateBusDetails.
 * Atomically updates registration index documents and handles legacy buses safely.
 */
export async function updateBusDetails(
  busId: string,
  input: UpdateBusDetailsInput
): Promise<void> {
  if (!busId) throw new Error('Bus ID is required.');
  const validationErr = validateBusInput(input);
  if (validationErr) throw new Error(validationErr);

  const callable = httpsCallable<
    { busId: string } & UpdateBusDetailsInput,
    UpdateBusDetailsResponse
  >(functions, 'updateBusDetails');

  try {
    await callable({
      busId,
      busNumber: input.busNumber ? input.busNumber.trim() : undefined,
      registrationNumber: input.registrationNumber ? input.registrationNumber.trim().toUpperCase() : undefined,
      capacity: input.capacity !== undefined ? Number(input.capacity) : undefined,
      routeId: input.routeId !== undefined ? (input.routeId ? input.routeId.trim() : null) : undefined
    });
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error(`[fleetService] Failed updating bus details for ${busId}:`, error);
    throw new Error(error.message || 'Failed to update bus details.');
  }
}

/**
 * Assigns or unassigns a route to a bus.
 * Validates that non-null routeId corresponds to an existing, active route document.
 * Modifies only the bus document's routeId without altering route stops or schedule arrays.
 */
export async function assignBusRoute(busId: string, routeId: string | null): Promise<void> {
  if (!busId) throw new Error('Bus ID is required.');

  if (routeId) {
    const cleanRouteId = routeId.trim();
    const routeRef = doc(db, 'routes', cleanRouteId);
    const routeSnap = await getDoc(routeRef);
    if (!routeSnap.exists()) {
      throw new Error(`Route '${cleanRouteId}' does not exist.`);
    }
    const routeData = routeSnap.data();
    if (routeData && routeData.active === false) {
      throw new Error(`Cannot assign deactivated route '${cleanRouteId}'.`);
    }
  }

  const busDocRef = doc(db, 'buses', busId);
  await updateDoc(busDocRef, {
    routeId: routeId ? routeId.trim() : null,
    updatedAt: Date.now()
  });
}

/**
 * Assigns a driver to a bus using the authoritative backend Cloud Function setBusDriverAssignment.
 */
export async function assignBusDriver(
  busId: string,
  driverUid: string,
  options?: { confirmReplacement?: boolean; expectedPreviousDriverId?: string | null }
): Promise<SetBusDriverAssignmentResponse> {
  if (!busId) throw new Error('Bus ID is required.');
  if (!driverUid) throw new Error('Driver UID is required.');

  const callable = httpsCallable<
    {
      busId: string;
      driverUid: string | null;
      confirmReplacement?: boolean;
      expectedPreviousDriverId?: string | null;
    },
    SetBusDriverAssignmentResponse
  >(
    functions,
    'setBusDriverAssignment'
  );

  try {
    const res = await callable({
      busId,
      driverUid,
      confirmReplacement: options?.confirmReplacement,
      expectedPreviousDriverId: options?.expectedPreviousDriverId
    });
    return res.data;
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error(`[fleetService] Failed assigning driver ${driverUid} to bus ${busId}:`, error);
    throw new Error(error.message || 'Failed to assign driver to bus.');
  }
}

/**
 * Unassigns the primary driver from a bus using the backend Cloud Function setBusDriverAssignment.
 */
export async function unassignBusDriver(busId: string): Promise<SetBusDriverAssignmentResponse> {
  if (!busId) throw new Error('Bus ID is required.');

  const callable = httpsCallable<{ busId: string; driverUid: string | null }, SetBusDriverAssignmentResponse>(
    functions,
    'setBusDriverAssignment'
  );

  try {
    const res = await callable({ busId, driverUid: null });
    return res.data;
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error(`[fleetService] Failed unassigning driver from bus ${busId}:`, error);
    throw new Error(error.message || 'Failed to unassign driver from bus.');
  }
}

/**
 * Toggles a bus's active/deactivated status via Cloud Function toggleBusActiveStatus.
 * Prevents deactivating a bus with an active driver assignment unless unassignDriverIfAssigned is confirmed.
 */
export async function toggleBusStatus(
  busId: string,
  active: boolean,
  unassignDriverIfAssigned = false
): Promise<ToggleBusActiveResponse> {
  if (!busId) throw new Error('Bus ID is required.');

  const callable = httpsCallable<
    { busId: string; active: boolean; unassignDriverIfAssigned: boolean },
    ToggleBusActiveResponse
  >(functions, 'toggleBusActiveStatus');

  try {
    const res = await callable({ busId, active, unassignDriverIfAssigned });
    return res.data;
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error(`[fleetService] Failed toggling active status for bus ${busId}:`, error);
    throw new Error(error.message || 'Failed to toggle bus active status.');
  }
}

/**
 * Forces authoritative synchronization between Firestore and RTDB for a bus via reconcileBusRtdb.
 */
export async function reconcileBusSync(busId: string): Promise<ReconcileBusRtdbResponse> {
  if (!busId) throw new Error('Bus ID is required.');

  const callable = httpsCallable<{ busId: string }, ReconcileBusRtdbResponse>(
    functions,
    'reconcileBusRtdb'
  );

  try {
    const res = await callable({ busId });
    return res.data;
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error(`[fleetService] Failed reconciling RTDB for bus ${busId}:`, error);
    throw new Error(error.message || 'Failed to reconcile bus operational data.');
  }
}

/**
 * Permanently deletes a bus via backend Cloud Function deleteBusAccount.
 */
export async function deleteBus(
  busId: string
): Promise<{ success: boolean; busId: string; message?: string }> {
  if (!busId) throw new Error('Bus ID is required.');

  const callable = httpsCallable<{ busId: string }, { success: boolean; busId: string; message?: string }>(
    functions,
    'deleteBusAccount'
  );

  try {
    const res = await callable({ busId });
    return res.data;
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error(`[fleetService] Failed deleting bus ${busId}:`, error);
    throw new Error(error.message || 'Failed to delete bus.');
  }
}
