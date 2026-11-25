/**
 * Driver Management Service
 * Handles driver accounts, profile updates, Staff ID modifications,
 * bus assignments, activation toggles, password resets, and account deletion.
 * Milestone 5: Fleet & Driver Management
 */

import {
  collection,
  doc,
  getDoc,
  getDocs,
  onSnapshot,
  query,
  where,
  DocumentData,
  QuerySnapshot,
  Unsubscribe
} from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { db, functions } from '../lib/firebase';
import {
  DriverRecord,
  CreateDriverInput,
  UpdateDriverProfileInput,
  SetBusDriverAssignmentResponse,
  ToggleDriverActiveResponse
} from '../types/fleet';

/**
 * Natural comparison function for drivers by name or Staff ID.
 */
export function naturalDriverSort(a: DriverRecord, b: DriverRecord): number {
  const nameCompare = (a.name || '').localeCompare(b.name || '');
  if (nameCompare !== 0) return nameCompare;
  return (a.customId || '').localeCompare(b.customId || '');
}

/**
 * Parses raw Firestore document data into a strongly-typed DriverRecord model.
 */
export function parseDriverDocument(docId: string, data: DocumentData): DriverRecord {
  return {
    uid: docId,
    customId: String(data.customId || docId).trim(),
    name: String(data.name || '').trim(),
    phoneNumber: String(data.phoneNumber || '').trim(),
    email: String(data.email || '').trim().toLowerCase(),
    role: 'DRIVER',
    active: data.active !== false,
    assignedBusId: data.assignedBusId ? String(data.assignedBusId).trim() : null,
    createdAt: typeof data.createdAt === 'number' ? data.createdAt : undefined,
    updatedAt: typeof data.updatedAt === 'number' ? data.updatedAt : undefined
  };
}

/**
 * Normalizes Indian phone numbers into standard 10-digit format.
 */
export function normalizePhoneNumber(rawPhone: string): string {
  let cleaned = String(rawPhone || '').replace(/\D/g, '');
  if (cleaned.startsWith('91') && cleaned.length === 12) {
    cleaned = cleaned.substring(2);
  }
  return cleaned;
}

/**
 * Validates driver profile inputs (name, staff ID, phone number).
 */
export function validateDriverInputs(name: string, staffId: string, phoneNumber: string): string | null {
  const cleanName = String(name || '').trim();
  if (!cleanName || cleanName.length < 2 || cleanName.length > 50) {
    return 'Driver name must be between 2 and 50 characters.';
  }

  const cleanStaffId = String(staffId || '').trim().toLowerCase();
  if (!cleanStaffId || !/^[a-z0-9_]{3,20}$/.test(cleanStaffId)) {
    return 'Staff ID must be 3-20 characters long using lowercase letters, numbers, and underscores (e.g. drv_01).';
  }
  if (cleanStaffId.includes('admin')) {
    return 'Staff ID cannot contain reserved keyword "admin".';
  }

  const cleanPhone = normalizePhoneNumber(phoneNumber);
  if (!/^[6-9]\d{9}$/.test(cleanPhone)) {
    return 'Please enter a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9.';
  }

  return null;
}

/**
 * Fetches all drivers from Firestore once, sorted by name.
 */
export async function getDrivers(): Promise<DriverRecord[]> {
  const driversQuery = query(collection(db, 'users'), where('role', '==', 'DRIVER'));
  const snapshot = await getDocs(driversQuery);
  const drivers: DriverRecord[] = [];
  snapshot.forEach((d) => {
    drivers.push(parseDriverDocument(d.id, d.data()));
  });
  return drivers.sort(naturalDriverSort);
}

/**
 * Subscribes to real-time driver collection updates.
 * Returns an unsubscribe callback for proper cleanup on component unmount.
 */
export function subscribeToDrivers(
  onUpdate: (drivers: DriverRecord[]) => void,
  onError?: (error: Error) => void
): Unsubscribe {
  const driversQuery = query(collection(db, 'users'), where('role', '==', 'DRIVER'));
  return onSnapshot(
    driversQuery,
    (snapshot: QuerySnapshot<DocumentData>) => {
      const drivers: DriverRecord[] = [];
      snapshot.forEach((d) => {
        drivers.push(parseDriverDocument(d.id, d.data()));
      });
      onUpdate(drivers.sort(naturalDriverSort));
    },
    (err: Error) => {
      console.error('[driverService] Driver subscription listener error:', err);
      if (onError) onError(err);
    }
  );
}

/**
 * Fetches a single driver profile by UID.
 */
export async function getDriverById(uid: string): Promise<DriverRecord | null> {
  if (!uid) return null;
  const docRef = doc(db, 'users', uid);
  const snap = await getDoc(docRef);
  if (!snap.exists()) return null;
  const data = snap.data();
  if (data.role !== 'DRIVER') return null;
  return parseDriverDocument(snap.id, data);
}

/**
 * Creates a new driver account via backend Cloud Function createDriverAccount.
 * Atomically generates Auth user, Firestore profile, and RTDB driver operator record.
 */
export async function createDriver(input: CreateDriverInput): Promise<{
  success: boolean;
  uid: string;
  customId: string;
  email: string;
}> {
  const validationErr = validateDriverInputs(input.name, input.staffId, input.phoneNumber);
  if (validationErr) throw new Error(validationErr);

  if (!input.password || input.password.length < 6) {
    throw new Error('Temporary password must be at least 6 characters long.');
  }

  const callable = httpsCallable<
    CreateDriverInput,
    { success: boolean; uid: string; customId: string; email: string }
  >(functions, 'createDriverAccount');

  try {
    const res = await callable({
      name: input.name.trim(),
      staffId: input.staffId.trim().toLowerCase(),
      phoneNumber: normalizePhoneNumber(input.phoneNumber),
      password: input.password,
      initialBusId: input.initialBusId ? input.initialBusId.trim() : null
    });
    return res.data;
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error('[driverService] Failed creating driver account:', error);
    throw new Error(error.message || 'Failed to create driver account.');
  }
}

/**
 * Updates a driver's name, Staff ID, and phone number via Cloud Function updateDriverStaffProfile.
 * Updates Auth email, Firestore profile, and RTDB records with automatic rollback if database write fails.
 */
export async function updateDriverProfile(input: UpdateDriverProfileInput): Promise<{
  success: boolean;
  driverUid: string;
  customId: string;
  email: string;
}> {
  if (!input.driverUid) throw new Error('Driver UID is required.');
  const validationErr = validateDriverInputs(input.name, input.staffId, input.phoneNumber);
  if (validationErr) throw new Error(validationErr);

  const callable = httpsCallable<
    UpdateDriverProfileInput,
    { success: boolean; driverUid: string; customId: string; email: string }
  >(functions, 'updateDriverStaffProfile');

  try {
    const res = await callable({
      driverUid: input.driverUid,
      name: input.name.trim(),
      staffId: input.staffId.trim().toLowerCase(),
      phoneNumber: normalizePhoneNumber(input.phoneNumber)
    });
    return res.data;
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error(`[driverService] Failed updating driver profile ${input.driverUid}:`, error);
    throw new Error(error.message || 'Failed to update driver profile.');
  }
}

/**
 * Assigns or transfers a driver to a bus via Cloud Function setBusDriverAssignment.
 */
export async function assignDriverToBus(
  driverUid: string,
  busId: string,
  options?: {
    confirmReplacement?: boolean;
    expectedPreviousDriverId?: string | null;
  }
): Promise<SetBusDriverAssignmentResponse> {
  if (!driverUid) throw new Error('Driver UID is required.');
  if (!busId) throw new Error('Bus ID is required.');

  const callable = httpsCallable<
    {
      busId: string;
      driverUid: string | null;
      confirmReplacement?: boolean;
      expectedPreviousDriverId?: string | null;
    },
    SetBusDriverAssignmentResponse
  >(functions, 'setBusDriverAssignment');

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
    console.error(`[driverService] Failed assigning driver ${driverUid} to bus ${busId}:`, error);
    throw new Error(error.message || 'Failed to assign driver to bus.');
  }
}

/**
 * Unassigns the driver from the specified bus via backend Cloud Function setBusDriverAssignment.
 * Note: Operates on busId. If a driver was previously on this bus but has since been transferred
 * to a new bus by another administrator, this call only clears the specified bus and does not
 * affect the driver's new assignment on the other bus.
 */
export async function unassignDriverFromBus(busId: string): Promise<SetBusDriverAssignmentResponse> {
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
    console.error(`[driverService] Failed unassigning driver from bus ${busId}:`, error);
    throw new Error(error.message || 'Failed to unassign driver from bus.');
  }
}

/**
 * Toggles a driver's active/deactivated status via Cloud Function toggleDriverActiveStatus.
 * Prevents deactivating an assigned driver unless unassignBusIfAssigned is confirmed.
 */
export async function toggleDriverStatus(
  driverUid: string,
  active: boolean,
  unassignBusIfAssigned = false
): Promise<ToggleDriverActiveResponse> {
  if (!driverUid) throw new Error('Driver UID is required.');

  const callable = httpsCallable<
    { driverUid: string; active: boolean; unassignBusIfAssigned: boolean },
    ToggleDriverActiveResponse
  >(functions, 'toggleDriverActiveStatus');

  try {
    const res = await callable({ driverUid, active, unassignBusIfAssigned });
    return res.data;
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error(`[driverService] Failed toggling active status for driver ${driverUid}:`, error);
    throw new Error(error.message || 'Failed to toggle driver active status.');
  }
}

/**
 * Resets a driver's password via Cloud Function resetDriverPassword.
 * Validates active admin authorization and target role is DRIVER.
 */
export async function resetDriverPassword(
  driverUid: string,
  newPassword: string
): Promise<{ success: boolean; driverUid: string }> {
  if (!driverUid) throw new Error('Driver UID is required.');
  if (!newPassword || newPassword.length < 6) {
    throw new Error('New password must be at least 6 characters long.');
  }

  const callable = httpsCallable<
    { driverUid: string; newPassword: string },
    { success: boolean; driverUid: string }
  >(functions, 'resetDriverPassword');

  try {
    const res = await callable({ driverUid, newPassword });
    return res.data;
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error(`[driverService] Failed resetting password for driver ${driverUid}:`, error);
    throw new Error(error.message || 'Failed to reset driver password.');
  }
}

/**
 * Deletes a driver account completely via Cloud Function deleteDriverAccount.
 * Cleans up bus assignments, Firestore profile, RTDB profile, and Auth account.
 */
export async function deleteDriverAccount(
  driverUid: string
): Promise<{ success: boolean; driverUid: string }> {
  if (!driverUid) throw new Error('Driver UID is required.');

  const callable = httpsCallable<
    { driverUid: string },
    { success: boolean; driverUid: string }
  >(functions, 'deleteDriverAccount');

  try {
    const res = await callable({ driverUid });
    return res.data;
  } catch (err: unknown) {
    const error = err as { code?: string; message?: string };
    console.error(`[driverService] Failed deleting driver account ${driverUid}:`, error);
    throw new Error(error.message || 'Failed to delete driver account.');
  }
}
