import {
  collection,
  doc,
  getDoc,
  getDocs,
  deleteDoc,
  setDoc,
  runTransaction,
  query,
  where,
  onSnapshot,
  QuerySnapshot,
  DocumentData,
} from 'firebase/firestore';
import { db } from '../lib/firebase';
import { Route, BusStop } from '../types/admin';

/**
 * Validates whether a bus stop possesses valid, renderable geographic coordinates.
 * Strictly mirrors the Android BusStop.hasCoordinates logic:
 * visibleOnMap && (latitude != 0.0 || longitude != 0.0) && latitude in -90.0..90.0 && longitude in -180.0..180.0
 */
export function checkStopHasCoordinates(stop: Partial<BusStop> | null | undefined): boolean {
  if (!stop) return false;
  if (stop.visibleOnMap === false) return false;
  const lat = stop.latitude;
  const lng = stop.longitude;
  if (typeof lat !== 'number' || typeof lng !== 'number') return false;
  if (isNaN(lat) || isNaN(lng)) return false;
  if (lat === 0 && lng === 0) return false;
  return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
}

/**
 * Natural comparison function for bus & route identifiers (e.g. ROUTE-02 before ROUTE-10).
 */
export function naturalRouteSort(a: Route, b: Route): number {
  const numA = parseInt(a.routeId.replace(/\D/g, ''), 10) || 0;
  const numB = parseInt(b.routeId.replace(/\D/g, ''), 10) || 0;
  if (numA !== numB) return numA - numB;
  return a.name.localeCompare(b.name);
}

/**
 * Parses raw Firestore document data into a strongly-typed Route object,
 * ensuring nested stops are sanitized and sorted by stop order.
 */
export function parseRouteDocument(docId: string, data: DocumentData): Route {
  const rawStops: unknown[] = Array.isArray(data.stops) ? data.stops : [];

  const stops: BusStop[] = rawStops.map((item, index) => {
    const s = (typeof item === 'object' && item !== null) ? (item as Record<string, unknown>) : {};
    const lat = typeof s.latitude === 'number' ? s.latitude : Number(s.latitude) || 0.0;
    const lng = typeof s.longitude === 'number' ? s.longitude : Number(s.longitude) || 0.0;
    const order = typeof s.order === 'number' ? s.order : (typeof s.stopOrder === 'number' ? s.stopOrder : index + 1);

    return {
      stopId: String(s.stopId || `STOP-${index + 1}`),
      name: String(s.name || s.stopName || `Stop #${index + 1}`),
      latitude: lat,
      longitude: lng,
      order: order,
      active: s.active !== false,
      visibleOnMap: s.visibleOnMap !== false,
    };
  }).sort((a, b) => a.order - b.order);

  return {
    routeId: data.routeId || docId,
    name: data.name || docId,
    description: data.description || '',
    active: data.active !== false,
    busId: data.busId || '',
    stops: stops,
    schedules: data.schedules,
    schedulePdfUrl: data.schedulePdfUrl ?? null,
    schedulePdfName: data.schedulePdfName ?? null,
  };
}

/**
 * Subscribes to real-time updates for the entire routes collection.
 * Cleans up the listener when the caller invokes the returned unsubscribe function.
 */
export function subscribeToRoutes(
  onData: (routes: Route[]) => void,
  onError?: (err: Error) => void
): () => void {
  const routesRef = collection(db, 'routes');

  const unsubscribe = onSnapshot(
    routesRef,
    (snapshot: QuerySnapshot<DocumentData>) => {
      try {
        const routes: Route[] = snapshot.docs
          .map((doc) => parseRouteDocument(doc.id, doc.data()))
          .sort(naturalRouteSort);
        onData(routes);
      } catch (err) {
        console.error('[routesService] Failed parsing routes snapshot:', err);
        if (onError && err instanceof Error) onError(err);
      }
    },
    (error) => {
      console.error('[routesService] Firestore listener error:', error);
      if (onError) onError(error);
    }
  );

  return unsubscribe;
}

/**
 * Calculates aggregate stop statistics from a list of routes.
 */
export function computeRouteStopStats(routes: Route[]) {
  let totalStops = 0;
  let mappedStops = 0;
  let missingStops = 0;
  let activeRoutes = 0;

  for (const r of routes) {
    if (r.active) {
      activeRoutes++;
      for (const s of r.stops) {
        if (s.active) {
          totalStops++;
          if (checkStopHasCoordinates(s)) {
            mappedStops++;
          } else {
            missingStops++;
          }
        }
      }
    }
  }

  const coveragePercent = totalStops > 0 ? Math.round((mappedStops / totalStops) * 100) : 0;

  return {
    totalStops,
    mappedStops,
    missingStops,
    activeRoutes,
    coveragePercent,
  };
}

// ============================================================================
// Pure Validation & Stop Manipulation Helpers
// ============================================================================

export interface RouteValidationInput {
  routeId: string;
  name: string;
  description?: string;
  isEdit?: boolean;
}

export interface StopValidationInput {
  name: string;
  latitude: number;
  longitude: number;
  visibleOnMap?: boolean;
}

const ROUTE_ID_REGEX = /^[a-zA-Z0-9_-]{2,64}$/;

/**
 * Validates route creation / edit inputs.
 * Returns a human-readable error string or null if valid.
 */
export function validateRouteInput(input: RouteValidationInput): string | null {
  const cleanId = String(input.routeId ?? '').trim();
  if (!cleanId) {
    return 'Route ID is required.';
  }
  if (!ROUTE_ID_REGEX.test(cleanId)) {
    return 'Route ID must be 2-64 characters using letters, numbers, hyphens, or underscores.';
  }

  const cleanName = String(input.name ?? '').trim();
  if (!cleanName) {
    return 'Route name is required.';
  }
  if (cleanName.length < 2 || cleanName.length > 100) {
    return 'Route name must be between 2 and 100 characters.';
  }

  return null;
}

/**
 * Validates stop coordinates against WGS84 bounds and map visibility rules.
 * When visibleOnMap is false, (0, 0) is allowed for unmapped stops.
 */
export function validateStopCoordinates(
  lat: number,
  lng: number,
  visibleOnMap: boolean = true
): string | null {
  if (typeof lat !== 'number' || typeof lng !== 'number' || !Number.isFinite(lat) || !Number.isFinite(lng)) {
    return 'Latitude and longitude must be valid finite numbers.';
  }
  if (lat < -90 || lat > 90) {
    return 'Latitude must be between -90 and 90 degrees.';
  }
  if (lng < -180 || lng > 180) {
    return 'Longitude must be between -180 and 180 degrees.';
  }
  if (visibleOnMap !== false && lat === 0 && lng === 0) {
    return 'Coordinates cannot be (0, 0) when the stop is visible on the map.';
  }
  return null;
}

/**
 * Validates stop name (1-120 chars) and geographic coordinates.
 */
export function validateStopInput(input: StopValidationInput): string | null {
  const cleanName = String(input.name ?? '').trim();
  if (!cleanName) {
    return 'Stop name is required.';
  }
  if (cleanName.length > 120) {
    return 'Stop name must be 120 characters or fewer.';
  }

  return validateStopCoordinates(
    input.latitude,
    input.longitude,
    input.visibleOnMap !== false
  );
}

/**
 * Returns a new array of stops with sequential 1-based order (1..N),
 * preserving all other stop fields without mutating the input.
 */
export function reindexStops(stops: BusStop[]): BusStop[] {
  return stops.map((stop, index) => ({
    ...stop,
    order: index + 1,
  }));
}

/**
 * Moves a stop from fromIndex to toIndex and reindexes order 1..N.
 */
export function reorderStopsList(
  stops: BusStop[],
  fromIndex: number,
  toIndex: number
): BusStop[] {
  if (
    fromIndex === toIndex ||
    fromIndex < 0 ||
    fromIndex >= stops.length ||
    toIndex < 0 ||
    toIndex >= stops.length
  ) {
    return reindexStops(stops);
  }

  const copy = [...stops];
  const [moved] = copy.splice(fromIndex, 1);
  copy.splice(toIndex, 0, moved);
  return reindexStops(copy);
}

/**
 * Generates a unique <routeId>-STOP-<N> identifier that does not collide
 * with any stopId in existingStops.
 */
export function generateNextStopId(routeId: string, existingStops: BusStop[]): string {
  const cleanRouteId = String(routeId || 'ROUTE').trim();
  const prefix = `${cleanRouteId}-STOP-`;
  const existingIds = new Set(existingStops.map((s) => s.stopId));

  let maxNum = 0;
  for (const s of existingStops) {
    if (s.stopId.startsWith(prefix)) {
      const suffix = s.stopId.slice(prefix.length);
      if (/^\d+$/.test(suffix)) {
        const parsed = parseInt(suffix, 10);
        if (parsed > maxNum) maxNum = parsed;
      }
    }
  }

  let nextNum = maxNum + 1;
  while (existingIds.has(`${prefix}${nextNum}`)) {
    nextNum++;
  }
  return `${prefix}${nextNum}`;
}

// ============================================================================
// Firestore Route & Stop CRUD Operations
// ============================================================================

export interface CreateRouteInput {
  routeId: string;
  name: string;
  description: string;
  active: boolean;
}

export interface UpdateRouteDetailsInput {
  name: string;
  description: string;
  active: boolean;
}

export interface StopReferenceCounts {
  pendingSubmissions: number;
  pendingRequests: number;
}

/**
 * Creates a new route document in Firestore using a transaction to prevent
 * overwriting an existing route ID.
 */
export async function createRoute(input: CreateRouteInput): Promise<void> {
  const validationError = validateRouteInput({ ...input, isEdit: false });
  if (validationError) {
    throw new Error(validationError);
  }

  const routeId = input.routeId.trim();
  const name = input.name.trim();
  const description = (input.description ?? '').trim();
  const active = input.active !== false;

  const routeRef = doc(db, 'routes', routeId);
  await runTransaction(db, async (tx) => {
    const snap = await tx.get(routeRef);
    if (snap.exists()) {
      throw new Error('Route ID already exists');
    }
    tx.set(routeRef, {
      routeId,
      name,
      description,
      active,
      busId: '',
      stops: [],
    });
  });
}

/**
 * Updates route metadata while preserving stops, schedules, schedulePdfUrl, and busId.
 */
export async function updateRouteDetails(
  routeId: string,
  input: UpdateRouteDetailsInput
): Promise<void> {
  const cleanRouteId = routeId.trim();
  const validationError = validateRouteInput({
    routeId: cleanRouteId,
    name: input.name,
    description: input.description,
    isEdit: true,
  });
  if (validationError) {
    throw new Error(validationError);
  }

  const routeRef = doc(db, 'routes', cleanRouteId);
  await setDoc(
    routeRef,
    {
      routeId: cleanRouteId,
      name: input.name.trim(),
      description: (input.description ?? '').trim(),
      active: input.active !== false,
    },
    { merge: true }
  );
}

/**
 * Deletes a route only if no buses reference it in `buses` or `routes/{routeId}.busId`.
 */
export async function deleteRoute(routeId: string): Promise<void> {
  const cleanRouteId = routeId.trim();
  if (!cleanRouteId) {
    throw new Error('Route ID is required.');
  }

  const routeRef = doc(db, 'routes', cleanRouteId);
  const busesQuery = query(
    collection(db, 'buses'),
    where('routeId', '==', cleanRouteId)
  );

  const [busesSnap, routeSnap] = await Promise.all([
    getDocs(busesQuery),
    getDoc(routeRef),
  ]);

  const referencingBuses = new Map<string, string>();
  busesSnap.forEach((busDoc) => {
    const data = busDoc.data();
    const busNumber = data.busNumber ? String(data.busNumber).trim() : '';
    const label = busNumber ? `Bus ${busNumber} (${busDoc.id})` : busDoc.id;
    referencingBuses.set(busDoc.id, label);
  });

  if (routeSnap.exists()) {
    const assignedBusId = String(routeSnap.data().busId || '').trim();
    if (assignedBusId && !referencingBuses.has(assignedBusId)) {
      referencingBuses.set(assignedBusId, assignedBusId);
    }
  }

  if (referencingBuses.size > 0) {
    const busList = Array.from(referencingBuses.values()).join(', ');
    throw new Error(
      `Cannot delete route "${cleanRouteId}" because it is currently assigned to: ${busList}. Unassign the bus(es) first.`
    );
  }

  await deleteDoc(routeRef);
}

/**
 * Atomically saves the route's stops array (reindexed 1..N) in a Firestore transaction
 * without overwriting other route fields such as name, schedules, or busId.
 */
export async function saveRouteStops(routeId: string, stops: BusStop[]): Promise<void> {
  const cleanRouteId = routeId.trim();
  if (!cleanRouteId) {
    throw new Error('Route ID is required.');
  }

  const normalizedStops: BusStop[] = reindexStops(stops).map((stop) => ({
    stopId: String(stop.stopId).trim(),
    name: String(stop.name).trim(),
    latitude: Number(stop.latitude),
    longitude: Number(stop.longitude),
    order: stop.order,
    active: stop.active !== false,
    visibleOnMap: stop.visibleOnMap !== false,
  }));

  const routeRef = doc(db, 'routes', cleanRouteId);
  await runTransaction(db, async (tx) => {
    const snap = await tx.get(routeRef);
    if (!snap.exists()) {
      throw new Error(`Route "${cleanRouteId}" not found.`);
    }
    tx.update(routeRef, {
      routeId: cleanRouteId,
      stops: normalizedStops,
    });
  });
}

/**
 * Checks pending student submissions and driver requests referencing a given stop.
 */
export async function checkStopReferences(
  routeId: string,
  stopId: string
): Promise<StopReferenceCounts> {
  const cleanRouteId = routeId.trim();
  const cleanStopId = stopId.trim();

  const submissionsQuery = query(
    collection(db, 'stop_location_submissions'),
    where('routeId', '==', cleanRouteId),
    where('status', '==', 'PENDING')
  );
  const requestsQuery = query(
    collection(db, 'stop_requests'),
    where('routeId', '==', cleanRouteId),
    where('status', '==', 'PENDING')
  );

  const [submissionsSnap, requestsSnap] = await Promise.all([
    getDocs(submissionsQuery),
    getDocs(requestsQuery),
  ]);

  let pendingSubmissions = 0;
  submissionsSnap.forEach((d) => {
    if (String(d.data().stopId || '').trim() === cleanStopId) {
      pendingSubmissions++;
    }
  });

  let pendingRequests = 0;
  requestsSnap.forEach((d) => {
    if (String(d.data().stopId || '').trim() === cleanStopId) {
      pendingRequests++;
    }
  });

  return {
    pendingSubmissions,
    pendingRequests,
  };
}
