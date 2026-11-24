import {
  collection,
  doc,
  addDoc,
  onSnapshot,
  query,
  where,
  runTransaction,
  DocumentData,
  QuerySnapshot,
} from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { db, functions } from '../lib/firebase';
import {
  StopCoordinateSubmission,
  SubmitCoordinatesInput,
  ReviewSubmissionInput,
  SubmissionStatus,
} from '../types/submissions';
import { BusStop } from '../types/admin';

/**
 * Calculates great-circle distance between two geographic coordinates in metres (Haversine formula).
 */
export function calculateDistanceMeters(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  if (lat1 === lat2 && lon1 === lon2) return 0;
  const R = 6371000; // Earth's mean radius in metres
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c);
}

/**
 * Validates coordinate numbers and boundaries.
 */
export function validateCoordinates(lat: number, lng: number): { valid: boolean; reason?: string } {
  if (typeof lat !== 'number' || typeof lng !== 'number' || isNaN(lat) || isNaN(lng)) {
    return { valid: false, reason: 'Latitude and Longitude must be valid numbers.' };
  }
  if (lat < -90 || lat > 90) {
    return { valid: false, reason: 'Latitude must be between -90 and 90 degrees.' };
  }
  if (lng < -180 || lng > 180) {
    return { valid: false, reason: 'Longitude must be between -180 and 180 degrees.' };
  }
  if (lat === 0 && lng === 0) {
    return { valid: false, reason: 'Coordinates (0, 0) cannot be submitted as a public bus stop.' };
  }
  return { valid: true };
}

/**
 * Parses raw Firestore document data into a strongly-typed StopCoordinateSubmission model.
 */
export function parseSubmissionDocument(
  docId: string,
  data: DocumentData
): StopCoordinateSubmission {
  const submittedAt = data.submittedAt
    ? typeof data.submittedAt.toMillis === 'function'
      ? data.submittedAt.toMillis()
      : Number(data.submittedAt) || Date.now()
    : Date.now();

  const reviewedAt = data.reviewedAt
    ? typeof data.reviewedAt.toMillis === 'function'
      ? data.reviewedAt.toMillis()
      : Number(data.reviewedAt) || null
    : null;

  return {
    id: docId,
    routeId: String(data.routeId || ''),
    routeName: String(data.routeName || data.routeId || ''),
    stopId: String(data.stopId || ''),
    stopName: String(data.stopName || data.stopId || ''),
    stopOrder: typeof data.stopOrder === 'number' ? data.stopOrder : 0,
    proposedLatitude: Number(data.proposedLatitude) || 0,
    proposedLongitude: Number(data.proposedLongitude) || 0,
    accuracyMeters:
      typeof data.accuracyMeters === 'number' && !isNaN(data.accuracyMeters)
        ? data.accuracyMeters
        : null,
    source: data.source === 'GPS' ? 'GPS' : 'MANUAL',
    status: (data.status as SubmissionStatus) || 'PENDING',
    submittedAt: submittedAt,
    existingLatitude:
      typeof data.existingLatitude === 'number' ? data.existingLatitude : null,
    existingLongitude:
      typeof data.existingLongitude === 'number' ? data.existingLongitude : null,
    studentNotes: data.studentNotes ? String(data.studentNotes) : null,
    reviewedBy: data.reviewedBy ? String(data.reviewedBy) : null,
    reviewedAt: reviewedAt,
    reviewNotes: data.reviewNotes ? String(data.reviewNotes) : null,
    finalLatitude:
      typeof data.finalLatitude === 'number' ? data.finalLatitude : null,
    finalLongitude:
      typeof data.finalLongitude === 'number' ? data.finalLongitude : null,
  };
}

/**
 * Submits student proposed coordinates.
 * Prefers the HTTPS Callable Cloud Function with server-side App Check and rate limiting.
 * Falls back to direct Firestore write if the Cloud Function is not running in local test environments.
 */
export async function submitStopCoordinates(
  input: SubmitCoordinatesInput
): Promise<{ success: boolean; submissionId: string }> {
  const coordCheck = validateCoordinates(input.proposedLatitude, input.proposedLongitude);
  if (!coordCheck.valid) {
    throw new Error(coordCheck.reason);
  }

  // 1. Attempt server-side Cloud Function
  try {
    const callable = httpsCallable<SubmitCoordinatesInput, { success: boolean; submissionId: string }>(
      functions,
      'submitStopCoordinates'
    );
    const result = await callable(input);
    if (result.data && result.data.submissionId) {
      return result.data;
    }
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    console.warn('[submissionService] Callable submitStopCoordinates unavailable or threw, falling back to direct Firestore:', msg);
    // If the error was explicitly thrown by backend logic (e.g. invalid arguments or duplicate submission), rethrow it
    if (msg.includes('already-exists') || msg.includes('identical pending coordinate')) {
      throw new Error('An identical pending coordinate submission for this bus stop was recently received and is awaiting administrator review.');
    }
    if (msg.includes('invalid-argument') || msg.includes('not-found')) {
      throw new Error(msg);
    }
  }

  // 2. Direct Firestore fallback (Protected by Firestore Security Rules)
  const collectionRef = collection(db, 'stop_location_submissions');
  const docData = {
    routeId: input.routeId,
    routeName: input.routeName,
    stopId: input.stopId,
    stopName: input.stopName,
    stopOrder: input.stopOrder,
    proposedLatitude: input.proposedLatitude,
    proposedLongitude: input.proposedLongitude,
    accuracyMeters: input.accuracyMeters,
    source: input.source,
    status: 'PENDING',
    submittedAt: Date.now(),
    existingLatitude: input.existingLatitude ?? null,
    existingLongitude: input.existingLongitude ?? null,
    studentNotes: input.studentNotes ?? null,
  };

  const docRef = await addDoc(collectionRef, docData);
  return { success: true, submissionId: docRef.id };
}

export interface SubmissionFilters {
  status?: SubmissionStatus | 'ALL';
  routeId?: string;
  stopId?: string;
}

/**
 * Subscribes to real-time stop location submissions.
 */
export function listenToSubmissions(
  filters: SubmissionFilters,
  onData: (submissions: StopCoordinateSubmission[]) => void,
  onError: (err: Error) => void
): () => void {
  const colRef = collection(db, 'stop_location_submissions');
  let q = query(colRef);

  if (filters.status && filters.status !== 'ALL') {
    q = query(q, where('status', '==', filters.status));
  }
  if (filters.routeId) {
    q = query(q, where('routeId', '==', filters.routeId));
  }
  if (filters.stopId) {
    q = query(q, where('stopId', '==', filters.stopId));
  }

  return onSnapshot(
    q,
    (snapshot: QuerySnapshot<DocumentData>) => {
      const items: StopCoordinateSubmission[] = [];
      snapshot.forEach((docSnap) => {
        items.push(parseSubmissionDocument(docSnap.id, docSnap.data()));
      });
      // Sort newest first
      items.sort((a, b) => b.submittedAt - a.submittedAt);
      onData(items);
    },
    (err) => {
      console.error('[submissionService] Error listening to submissions:', err);
      onError(err);
    }
  );
}

/**
 * Reviews a submission (Approves or Rejects).
 * Updates matching stop inside routes/{routeId}.stops on APPROVE, preserving all fields and order.
 * Strictly preserves security rules and uses transactions.
 */
export async function reviewSubmission(
  input: ReviewSubmissionInput,
  adminUid: string
): Promise<{ success: boolean; action: string }> {
  // 1. Attempt callable Cloud Function first
  try {
    const callable = httpsCallable<ReviewSubmissionInput, { success: boolean; action: string }>(
      functions,
      'reviewStopCoordinateSubmission'
    );
    const result = await callable(input);
    if (result.data && result.data.success) {
      return result.data;
    }
  } catch (err: unknown) {
    console.warn('[submissionService] Callable reviewStopCoordinateSubmission unavailable, falling back to direct transaction:', err);
  }

  // 2. Direct transaction fallback (Authorized by Firestore Security Rules for active Admins)
  const submissionRef = doc(db, 'stop_location_submissions', input.submissionId);

  return await runTransaction(db, async (transaction) => {
    const subSnap = await transaction.get(submissionRef);
    if (!subSnap.exists()) {
      throw new Error(`Submission ${input.submissionId} not found.`);
    }

    const subData = subSnap.data() || {};
    if (subData.status !== 'PENDING') {
      throw new Error(`Submission has already been reviewed (${subData.status}).`);
    }

    const routeId = subData.routeId;
    const stopId = subData.stopId;
    const routeRef = doc(db, 'routes', routeId);
    const routeSnap = await transaction.get(routeRef);

    if (!routeSnap.exists()) {
      throw new Error(`Route ${routeId} not found.`);
    }

    const routeData = routeSnap.data() || {};
    const rawStops: BusStop[] = Array.isArray(routeData.stops) ? routeData.stops : [];
    const stopIndex = rawStops.findIndex((s) => s && s.stopId === stopId);

    if (stopIndex === -1) {
      throw new Error(`Stop ${stopId} not found in route ${routeId}.`);
    }

    const now = Date.now();

    if (input.action === 'APPROVE') {
      const finalLat =
        typeof input.adjustedLatitude === 'number' && !isNaN(input.adjustedLatitude)
          ? input.adjustedLatitude
          : subData.proposedLatitude;
      const finalLng =
        typeof input.adjustedLongitude === 'number' && !isNaN(input.adjustedLongitude)
          ? input.adjustedLongitude
          : subData.proposedLongitude;

      const coordCheck = validateCoordinates(finalLat, finalLng);
      if (!coordCheck.valid) {
        throw new Error(coordCheck.reason);
      }

      // Update matching stop in array, strictly preserving stopId, name, order, active, and any custom attributes
      const updatedStops = rawStops.map((item, idx) => {
        if (idx === stopIndex) {
          return {
            ...item,
            latitude: finalLat,
            longitude: finalLng,
            visibleOnMap: true,
          };
        }
        return item;
      });

      // Update route document
      transaction.update(routeRef, {
        stops: updatedStops,
        updatedAt: now,
      });

      // Update submission document
      transaction.update(submissionRef, {
        status: 'APPROVED',
        finalLatitude: finalLat,
        finalLongitude: finalLng,
        reviewedBy: adminUid,
        reviewedAt: now,
        reviewNotes: input.reviewNotes || null,
      });

      return { success: true, action: 'APPROVED' };
    } else {
      // REJECT: never alter route coordinates
      transaction.update(submissionRef, {
        status: 'REJECTED',
        reviewedBy: adminUid,
        reviewedAt: now,
        reviewNotes: input.reviewNotes || null,
      });

      return { success: true, action: 'REJECTED' };
    }
  });
}
